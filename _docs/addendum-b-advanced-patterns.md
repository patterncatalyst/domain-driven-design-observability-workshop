---
title: "Advanced Patterns"
order: 11
part: "Addendums"
description: "Sagas with distributed traces, CQRS read-model observability, and anti-corruption layer telemetry."
label: "Addendum B"
---

The workshop covered the foundation: domain-named spans, structured logging, business metrics, cross-context debugging, and observability economics. This addendum goes further. Each section introduces a pattern that the workshop touches on or omits, explains where it applies, and describes the observability strategy it requires.

These patterns are not academic exercises. They show up in production systems that outgrow the workshop's simplified model. Read them as a menu -- pick the ones that apply to the system you are building.

---

## 1. CQRS (Command Query Responsibility Segregation)

### The pattern

CQRS separates the **write model** (commands that change state) from the **read model** (queries that return data). Instead of a single model that handles both writes and reads, you maintain two:

- The **command side** validates business rules, enforces invariants, and persists state changes. This is the aggregate from the workshop -- `Order.place()`, `Order.confirm()`, `Order.cancel()`.
- The **query side** projects state into read-optimized views. A checkout history page, a customer dashboard, or an analytics pipeline reads from a projection, not from the aggregate directly.

### When to use it

CQRS is justified when the read and write models have different shapes, different scaling needs, or different performance characteristics. The classic signal: your aggregate is well-designed for writes but requires multiple joins and transformations to answer read queries. A denormalized read model eliminates those joins.

### Observability implications

The command and query paths deserve separate instrumentation. They have different latency profiles, different failure modes, and different consumers.

**Span names.** Use the convention `{Context}.Command.{Operation}` and `{Context}.Query.{Operation}`:

```
Order.Command.PlaceOrder      → writes to the aggregate store
Order.Query.CheckoutHistory   → reads from the projection
```

This makes it trivial to filter traces by side: `{ name =~ "*.Command.*" }` for writes, `{ name =~ "*.Query.*" }` for reads.

**Separate metrics.** Track command latency and query latency independently:

```
order_command_duration_seconds{operation="PlaceOrder"}
order_query_duration_seconds{operation="CheckoutHistory"}
```

If the query side degrades because the projection is lagging, the command metrics remain healthy. Separate metrics make this visible.

**Projection lag.** The read model is eventually consistent -- it is updated asynchronously after the command side persists a change. The lag between a write and the corresponding projection update is a business-critical metric:

```
order_projection_lag_seconds
```

If this number grows, the read model is serving stale data. Pair this metric with an alert threshold tied to your SLA.

---

## 2. Event Sourcing

### The pattern

In the workshop, domain events (`OrderPlaced`, `OrderConfirmed`, `OrderCancelled`) are published to Kafka for downstream consumers. But the aggregate's current state is stored directly -- the `Order` record holds the latest status.

**Event Sourcing** inverts this: the domain events themselves are the source of truth. Instead of storing the current state and publishing events as a side effect, you store only the events. The current state of an aggregate is derived by replaying its event stream from the beginning.

```
Event stream for order ord_abc123:
  1. OrderPlaced    { customerId: "cust_alice_silver", total: 49.99 }
  2. InventoryReserved { reservationId: "res_xyz" }
  3. PaymentAuthorized { authorizationId: "auth_789" }
  4. OrderConfirmed { }

Current state = replay(1, 2, 3, 4) → Order(status=CONFIRMED, ...)
```

### Observability implications

**The event store is an audit log.** Every state transition is recorded as an immutable event with a timestamp, an event type, and a payload. This is richer than any log line -- it is the complete behavioral history of the aggregate.

**Event replay is traceable.** When you rebuild the read model (or debug a state inconsistency), you replay events. Each replay should produce a trace:

```
Order.EventReplay
  ├── Apply(OrderPlaced)
  ├── Apply(InventoryReserved)
  ├── Apply(PaymentAuthorized)
  └── Apply(OrderConfirmed)
```

The replay trace tells you exactly which events were applied, in what order, and whether any failed. If you are debugging a projection that has incorrect state, compare the replay trace to the original command trace.

**Snapshot metrics.** For aggregates with long event streams, replaying from the beginning is expensive. Snapshots (periodic materialization of the aggregate's state) reduce replay cost. Track:

```
order_event_stream_length        → number of events per aggregate
order_snapshot_age_seconds       → time since last snapshot
order_replay_duration_seconds    → time to rebuild state from events
```

### Tradeoffs

Event Sourcing adds significant complexity. The event schema must be versioned carefully (you cannot change the shape of an event after it is persisted). Debugging requires understanding the replay model, not just the current state. For most systems, the workshop's approach -- store current state, publish events for downstream consumers -- is sufficient. Adopt Event Sourcing when auditability or temporal queries ("what was the order's state at 3 PM yesterday?") are hard requirements.

---

## 3. Saga Compensation

### The pattern

The workshop's checkout saga uses a simple cancel-on-failure strategy: if any step fails, the Order is marked `CANCELLED` and the saga ends. This works for the workshop because the steps are idempotent or short-lived.

Production sagas need **compensating actions** -- operations that undo the effect of a previously successful step. If Payment authorization succeeds but Shipping scheduling fails, the saga must release the payment hold. If Inventory reservation succeeds but Payment fails, the saga must release the reserved stock.

```
Happy path:
  ReserveStock ✓ → AuthorizePayment ✓ → ScheduleShipment ✓ → Confirm

Compensation path (Shipping fails):
  ReserveStock ✓ → AuthorizePayment ✓ → ScheduleShipment ✗
  → ReversePayment (compensate) → ReleaseStock (compensate) → Cancel
```

### Observability implications

Compensation adds a second dimension to the trace tree. The forward path is what you saw in the workshop. The compensation path is a reverse sequence that should be equally visible.

**Compensation spans.** Each compensating action gets its own span, named to reflect its compensating nature:

```
Order.Checkout
  ├── Inventory.Reserve             status=RESERVED ✓
  ├── Payment.Authorize             outcome=AUTHORIZED ✓
  ├── Shipping.Schedule             status=FAILED ✗
  ├── Payment.Compensate.Reverse    outcome=REVERSED        ← compensation
  ├── Inventory.Compensate.Release  status=RELEASED          ← compensation
  └── Order.Events.Publish          event_type=OrderCancelled
```

The `Compensate` segment in the span name makes compensation visible in TraceQL queries: `{ name =~ "*.Compensate.*" }` finds every compensating action across all services.

**Dead-letter queues.** When a compensation action fails (the payment gateway is down and you cannot reverse the hold), the message goes to a dead-letter queue. This requires its own metrics:

```
saga_compensation_failures_total{step="Payment.Reverse", reason="timeout"}
saga_dead_letter_queue_depth{context="payment"}
```

A non-zero dead-letter queue depth is an operational emergency -- it means the system is in an inconsistent state that requires manual intervention.

**Retry counters.** Compensating actions are often retried with exponential backoff. Track the retry count on the span:

```
span.setAttribute("compensation.retry_count", retryCount)
span.setAttribute("compensation.max_retries", maxRetries)
```

When `retry_count` approaches `max_retries`, the saga is about to give up and send the message to the dead-letter queue.

Alessandro Colla's *Domain-Driven Refactoring* covers saga compensation patterns in depth, including choreography-based sagas (where services coordinate via events rather than a central orchestrator) and their observability challenges.

---

## 4. gRPC and Transport Comparison

### The pattern

The workshop's Inventory service supports both REST and gRPC transports. By default, the Order service communicates with Inventory over REST. But the codebase includes a gRPC adapter that can be activated via configuration:

- **Quarkus**: `@IfBuildProperty(name = "inventory.transport", stringValue = "grpc")`
- **Python**: `INVENTORY_TRANSPORT=grpc` environment variable
- **C#**: `InventoryTransport` configuration key

The adapter selection follows the hexagonal architecture pattern from the workshop: the domain layer defines a port (`InventoryPort`), and the infrastructure layer provides two adapters (`InventoryRestAdapter` and `InventoryGrpcAdapter`). The domain logic is unaware of which transport is active.

### REST vs gRPC tradeoffs

| Dimension | REST (JSON) | gRPC (protobuf) |
|---|---|---|
| Wire size | Larger (text-based) | Smaller (binary, ~30-50% reduction) |
| Latency | Higher (JSON parse overhead) | Lower (binary deserialization) |
| Tooling | Excellent (curl, Postman, browser) | Requires specialized tools (grpcurl, Bloom RPC) |
| Debugging | Easy (human-readable) | Harder (binary on the wire) |
| Schema enforcement | Optional (OpenAPI) | Mandatory (protobuf contract) |
| Streaming | Awkward (SSE, chunked responses) | Native (server/client/bidirectional streaming) |

For the workshop, REST is the right default -- it is easier to teach, debug, and demonstrate. In production systems with high-throughput internal traffic, gRPC often wins on latency and efficiency.

### Observability implications

**The span tells you the transport.** The ACL span carries an `acl.transport` attribute:

```
span.setAttribute("acl.transport", "rest")   // or "grpc"
```

This lets you compare latency distributions by transport in Prometheus:

```promql
histogram_quantile(0.95,
  rate(checkout_duration_seconds_bucket{acl_transport="rest"}[5m]))
vs
histogram_quantile(0.95,
  rate(checkout_duration_seconds_bucket{acl_transport="grpc"}[5m]))
```

**ACL drift detection across transports.** If both REST and gRPC adapters are active (for a migration or A/B test), monitor for response divergence. The same request sent to both transports should produce the same domain outcome. If it does not, something is wrong with one of the adapters.

```
acl_drift_total{context="inventory", transport="grpc", type="response_mismatch"}
```

This is a production pattern for safe transport migrations: run both adapters in shadow mode, compare results, alert on divergence, and cut over only when drift is zero.

---

## 5. Outbox Pattern

### The problem

The workshop's Order service does two things when a checkout completes: it writes the Order to the database and publishes an event to Kafka. These are two separate operations -- a database write and a Kafka produce. If the database write succeeds but the Kafka publish fails (network partition, Kafka broker down), the Order is persisted but the Notification service never learns about it. If the Kafka publish succeeds but the database write fails, the Notification service processes an event for an Order that does not exist.

This is the **dual-write problem**: two systems need to be updated atomically, but there is no distributed transaction spanning them.

### The solution

The **Outbox Pattern** avoids the dual write by writing everything to a single system -- the database. Instead of publishing directly to Kafka, the application writes the event to an **outbox table** in the same database transaction that writes the Order:

```sql
BEGIN;
  INSERT INTO orders (id, customer_id, status, ...) VALUES (...);
  INSERT INTO outbox (event_id, event_type, payload, created_at)
    VALUES ('evt_abc', 'OrderConfirmed', '{"orderId":"ord_123",...}', NOW());
COMMIT;
```

A separate process (a change-data-capture connector like Debezium, or a polling publisher) reads the outbox table and publishes events to Kafka. Because the order and the event are written in the same transaction, they are guaranteed to be consistent.

### Observability implications

**Outbox lag.** The time between writing to the outbox table and publishing to Kafka is a critical metric:

```
outbox_lag_seconds          → time from outbox write to Kafka publish
outbox_pending_count        → number of events waiting in the outbox
outbox_publish_failures     → publish attempts that failed (retried)
```

**Span structure.** The outbox introduces an asynchronous boundary inside a single service. The command span writes to the outbox; a separate publisher span reads from the outbox and publishes to Kafka. Link these spans using a shared `event.id` attribute so the full lifecycle is traceable:

```
Order.Checkout
  └── Outbox.Write            event.id=evt_abc

(later, in the publisher process)
Outbox.Publish                event.id=evt_abc
  └── Kafka.Produce           topic=order-events
```

**Alerting.** A growing `outbox_pending_count` means the publisher is falling behind. If it reaches a threshold, events are being delayed, which means downstream consumers (like Notification) are receiving stale data. Alert on this the same way you would alert on Kafka consumer lag.

---

## 6. Observability in Production

The workshop runs on a single Docker Compose stack with five services and modest traffic. Production systems operate at a different scale. This section covers the observability patterns that matter when you move from workshop to production.

### Tail sampling with domain-aware policies

Module 5 introduced tail sampling with error-based and latency-based policies. In production, you can add **domain-aware sampling policies** that use business attributes from spans:

```yaml
# Production tail sampling policies
processors:
  tail_sampling:
    policies:
      # Always keep error traces
      - name: errors
        type: status_code
        status_code:
          status_codes: [ERROR]

      # Always keep slow traces
      - name: slow
        type: latency
        latency:
          threshold_ms: 2000

      # Always keep platinum-tier customer traces
      - name: platinum_customers
        type: string_attribute
        string_attribute:
          key: customer.tier
          values: [PLATINUM]

      # Always keep cancelled orders
      - name: cancelled_orders
        type: string_attribute
        string_attribute:
          key: order.status
          values: [CANCELLED]

      # Sample 5% of everything else
      - name: baseline
        type: probabilistic
        probabilistic:
          sampling_percentage: 5
```

The domain attributes you added in Module 2 (`customer.tier`, `order.status`) now drive sampling decisions. Platinum customers always get full-fidelity traces. Cancelled orders are always retained for debugging. The happy path for bronze-tier customers is aggressively sampled.

### SLO-driven alerting from domain metrics

Service Level Objectives (SLOs) define the reliability targets for your system. They are most useful when tied to domain metrics, not infrastructure metrics.

Instead of alerting on "HTTP 500 rate > 1%", define SLOs around domain outcomes:

```yaml
# SLO: 99.5% of checkouts for gold and platinum customers
# complete within 3 seconds
- alert: CheckoutSLOBreach
  expr: |
    (
      sum(rate(checkout_outcomes_total{outcome="success", tier=~"GOLD|PLATINUM"}[5m]))
      /
      sum(rate(checkout_outcomes_total{tier=~"GOLD|PLATINUM"}[5m]))
    ) < 0.995
  for: 5m
  annotations:
    summary: "Checkout SLO breach for premium customers"
```

This alert fires when premium customers experience degraded checkout reliability -- regardless of whether the root cause is an HTTP error, a timeout, a Kafka lag, or a payment decline. The SLO is defined in domain terms, and the alert uses the domain metrics from Module 3.

### Cardinality management at scale

Module 5 covered cardinality discipline for individual metrics. At scale, cardinality management becomes an operational concern across the entire metrics pipeline.

**Cardinality limits in the OTel Collector.** The `transform` processor can drop or aggregate high-cardinality attributes before they reach your metrics backend:

```yaml
processors:
  transform/cardinality:
    metric_statements:
      - context: datapoint
        statements:
          # Drop any metric attribute with more than 100 unique values
          - delete_key(attributes, "http.target")
            where attributes["http.target"] != nil
```

**Metrics pipeline tiering.** Not all metrics need the same retention or resolution:

| Tier | Resolution | Retention | Example metrics |
|---|---|---|---|
| **Critical** | 15s scrape, 1s recording rules | 90 days | `checkout_outcomes_total`, SLO-related |
| **Standard** | 30s scrape | 30 days | `inventory_reservations_total`, service health |
| **Debug** | 60s scrape | 7 days | Per-endpoint latency, internal queue depth |

This tiered approach keeps costs proportional to business value.

### Multi-tenancy and observability isolation

If your system serves multiple tenants (customers, organizations, teams), observability data needs isolation:

- **Tenant as a resource attribute.** Add `tenant.id` as an OTel resource attribute (set at service startup, not per-request). This propagates to every span, metric, and log line automatically.
- **Data routing in the Collector.** Use the OTel Collector's `routing` connector to send different tenants' data to different backends or retention tiers.
- **Query isolation.** In Grafana, use dashboard variables and data source filters to scope queries to a single tenant. This prevents one tenant's noisy data from drowning out another's.

Avoid putting `tenant.id` as a metric label unless you have a small, fixed number of tenants. For SaaS systems with hundreds or thousands of tenants, `tenant.id` on metrics is a cardinality bomb.

### Cost optimization strategies

A practical framework for reducing observability costs without losing debugging capability:

1. **Sample traces, not metrics.** Metrics are cheap (bounded cardinality) and answer aggregate questions. Traces are expensive (one per request) and answer incident-specific questions. Invest in metrics for dashboards and alerting; use traces only for drill-down.

2. **Use exemplars to bridge the gap.** Exemplars link a metric data point to a specific trace. You get the cost efficiency of metrics with the drill-down capability of traces -- without storing every trace.

3. **Drop verbose logs in the Collector.** Application-level DEBUG and TRACE logs should not reach Loki in production. Use the OTel Collector's `filter` processor to drop them:

   ```yaml
   processors:
     filter/logs:
       logs:
         log_record:
           - 'severity_number < SEVERITY_NUMBER_INFO'
   ```

4. **Compress before exporting.** Enable gzip compression on OTLP exporters. This reduces network bandwidth by 60-80% for text-heavy signals like logs.

5. **Review regularly.** Run a monthly review of your top-10 metrics by cardinality, top-10 log streams by volume, and trace retention costs. Observability sprawl is gradual -- regular review catches it early.

---

## 7. Further Reading

### Books referenced in this addendum

- Alessandro Colla -- *Domain-Driven Refactoring* (Independently published, 2024). Saga compensation patterns, choreography vs orchestration, and practical refactoring techniques.
- Vlad Khononov -- *Balancing Coupling in Software Design* (Addison-Wesley, 2024). Coupling dimensions that guide observability investment decisions.

### OpenTelemetry resources

- [OpenTelemetry Collector configuration](https://opentelemetry.io/docs/collector/configuration/) -- the reference for tail sampling, filtering, and routing processors.
- [Tail Sampling Processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/tailsamplingprocessor) -- complete documentation for sampling policies.
- [OpenTelemetry Baggage](https://opentelemetry.io/docs/concepts/signals/baggage/) -- the mechanism used in Module 2 for cross-service context propagation.

### Patterns

- Chris Richardson -- [microservices.io/patterns/data/transactional-outbox.html](https://microservices.io/patterns/data/transactional-outbox.html). The definitive writeup of the Outbox Pattern.
- Martin Fowler -- [CQRS](https://martinfowler.com/bliki/CQRS.html). The original pattern description.
- Greg Young -- [Event Sourcing](https://cqrs.files.wordpress.com/2010/11/cqrs_documents.pdf). The foundational paper connecting CQRS and Event Sourcing.
