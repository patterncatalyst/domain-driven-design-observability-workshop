---
title: "Structured Observability"
order: 3
part: "The Workshop"
description: "Add structured logging with trace correlation, define domain-specific metrics, and connect logs/traces/metrics in Grafana."
duration: "20 min"
label: "Module 3"
---

## Learning objectives

By the end of this module you will be able to:

- Add structured logging with domain context so every log line carries identifiers like `order.id` and `customer.id`
- Create business metrics that answer domain questions ("how many checkouts failed today?")
- Implement the Anti-Corruption Layer (ACL) pattern for cross-context communication
- Wire cross-signal correlation so you can pivot between traces, logs, and metrics in Grafana

---

## Step 1 -- Browse DomainContext usage in CheckoutSaga

Generic log lines like `Processing request...` are nearly useless in a distributed system. When five services handle one checkout, you need every log line to carry the identifiers that tie it back to a specific order, customer, and cart.

The `DomainContext` pattern solves this. It is a scope manager that adds domain identifiers to every log line emitted within its scope -- and cleans them up when the scope ends.

Open the CheckoutSaga file for your language:

| Language | File path |
|---|---|
| Quarkus | `order-service/src/main/java/com/example/order/application/CheckoutSaga.java` |
| Python | `order_service/application/checkout_saga.py` |
| C# | `OrderService/Application/CheckoutSaga.cs` |

Find the `checkout` method. Look for the `DomainContext.open(...)` / `with DomainContext(...)` / `new DomainContext(...)` block near the top. This is the scope that attaches domain identifiers to every log line emitted during the checkout, so every downstream log automatically carries `order.id`, `customer.id`, and `cart.id`.

{% include codetabs.html langs="Quarkus|Python|C#" %}

```java
// order-service/.../application/CheckoutSaga.java

// Module 3a: populate MDC with this context's identifiers for the
// duration of the saga. All log lines in this scope carry these.
try (var ctx = DomainContext.open(
        OrderContextKey.ORDER_ID.of(orderId.value()),
        OrderContextKey.CUSTOMER_ID.of(command.customerId().value()),
        OrderContextKey.CART_ID.of(command.cartId().value()))) {

    var order = Order.place(orderId, command.customerId(),
            command.cartId(), command.lineItems());

    log.info("Checkout starting: total={} items={}",
            order.total().amount(), order.lineItems().size());
    // ...
}
// MDC is restored to its prior state here, even on exception
```

```python
# order_service/application/checkout_saga.py

# Module 3a: populate structlog context with domain identifiers
with DomainContext(
    OrderContextKey.ORDER_ID.of(order_id.value),
    OrderContextKey.CUSTOMER_ID.of(command.customer_id),
    OrderContextKey.CART_ID.of(command.cart_id),
):
    order = Order.place(order_id, customer_id, cart_id, line_items)

    logger.info(
        "Checkout starting",
        total=str(order.total().amount),
        items=len(order.line_items),
    )
    # ...
# structlog context is cleaned up automatically
```

```csharp
// OrderService/Application/CheckoutSaga.cs

using var domainContext = new DomainContext(
    _logger,
    OrderContextKey.OrderId.Of(orderId.Value),
    OrderContextKey.CustomerId.Of(customerId.Value),
    OrderContextKey.CartId.Of(cartId.Value));

var order = Order.Place(orderId, customerId, cartId, command.LineItems);

_logger.LogInformation("Checkout starting");
// ...
// DomainContext.Dispose() removes baggage entries and ends the logging scope
```

### Why this matters

When you query Loki for `order.id = "ord_abc123"`, you get logs from **all five services** that handled that order -- because the context propagates via OTel baggage across HTTP calls and Kafka messages. Without `DomainContext`, you would need to correlate logs manually using trace IDs, which tells you *when* things happened but not *what domain entity* they happened to.

---

## Step 2 -- Browse DomainContext internals (optional deep dive)

Open the DomainContext class to see how it works under the hood:

| Language | File path |
|---|---|
| Quarkus | `shared-observability/src/main/java/com/example/workshop/observability/DomainContext.java` |
| Python | `shared_observability/domain_context.py` |
| C# | `SharedObservability/DomainContext.cs` |

{% include codetabs.html langs="Quarkus|Python|C#" %}

```java
// shared-observability/.../DomainContext.java

public static DomainContext open(DomainIdentifier... identifiers) {
    var owned = new LinkedHashSet<String>();
    for (var id : identifiers) {
        MDC.put(id.key(), id.value());
        owned.add(id.key());
    }
    return new DomainContext(owned);
}

@Override
public void close() {
    for (var key : ownedKeys) {
        MDC.remove(key);
    }
    ownedKeys.clear();
}
```

```python
# shared_observability/domain_context.py

@contextmanager
def DomainContext(*identifiers: DomainIdentifier):
    merged = {**_domain_ctx.get()}
    bindings: dict[str, Any] = {}
    for ident in identifiers:
        merged[ident.key()] = ident.value()
        bindings[ident.key()] = ident.value()

    token = _domain_ctx.set(merged)
    try:
        with structlog.contextvars.bound_contextvars(**bindings):
            yield
    finally:
        _domain_ctx.reset(token)
```

```csharp
// SharedObservability/DomainContext.cs

public sealed class DomainContext : IDisposable
{
    private readonly IDisposable? _loggingScope;
    private readonly IDomainIdentifier[] _identifiers;

    public DomainContext(ILogger logger, params IDomainIdentifier[] identifiers)
    {
        _identifiers = identifiers;
        var scopeState = new Dictionary<string, object>();
        foreach (var id in identifiers)
        {
            scopeState[id.Key] = id.Value;
            BaggageHelpers.Set(id.Key, id.Value);
        }
        _loggingScope = logger.BeginScope(scopeState);
    }

    public void Dispose()
    {
        foreach (var id in _identifiers)
            BaggageHelpers.Remove(id.Key);
        _loggingScope?.Dispose();
    }
}
```

---

## Step 3 -- Browse the business metrics

Auto-instrumented metrics tell you about HTTP status codes and response times. They cannot tell you "how many checkouts failed because of inventory issues?" or "what's the p95 checkout latency for gold-tier customers?"

Custom business metrics bridge that gap. The checkout saga defines two.

Open CheckoutSaga again (same file as Step 1):

| Language | File path |
|---|---|
| Quarkus | `order-service/src/main/java/com/example/order/application/CheckoutSaga.java` |
| Python | `order_service/application/checkout_saga.py` |
| C# | `OrderService/Application/CheckoutSaga.cs` |

Find the metric definitions near the top of the class -- a counter (`checkout_outcomes_total`) and a histogram/timer (`checkout_duration_seconds`). Then find the `recordOutcome` / `_record_outcome` / `RecordOutcome` helper method that records both metrics at the end of each checkout.

{% include codetabs.html langs="Quarkus|Python|C#" %}

```java
// order-service/.../application/CheckoutSaga.java

// Module 3c: gauge for "orders currently in payment verification"
meterRegistry.gauge(
        "checkout_orders_in_payment_verification",
        ordersInPaymentVerification,
        AtomicInteger::get);

// Module 3c: timer for end-to-end checkout duration
this.checkoutDurationTimer = Timer.builder("checkout_duration_seconds")
        .description("End-to-end checkout saga duration")
        .publishPercentileHistogram()
        .register(meterRegistry);

// Recording an outcome:
private void recordOutcome(String outcome, CustomerProfile profile, long startNanos) {
    meterRegistry.counter("checkout_outcomes_total",
            "outcome", outcome,
            "tier", profile.tier().name()).increment();
    checkoutDurationTimer.record(java.time.Duration.ofNanos(
            System.nanoTime() - startNanos));
}
```

```python
# order_service/application/checkout_saga.py

checkout_outcomes_counter = meter.create_counter(
    "checkout_outcomes_total",
    description="Total checkout outcomes by result and customer tier",
)

checkout_duration_histogram = meter.create_histogram(
    "checkout_duration_seconds",
    description="End-to-end checkout saga duration",
    unit="s",
)

# Recording an outcome:
def _record_outcome(self, outcome: str, profile: CustomerProfile, start_time: float) -> None:
    duration = time.monotonic() - start_time
    checkout_outcomes_counter.add(
        1, {"outcome": outcome, "tier": profile.tier.value},
    )
    checkout_duration_histogram.record(
        duration, {"outcome": outcome, "tier": profile.tier.value},
    )
```

```csharp
// OrderService/Application/CheckoutSaga.cs

private static readonly Counter<long> OutcomeCounter =
    Meter.CreateCounter<long>("checkout_outcomes_total");
private static readonly Histogram<double> DurationHistogram =
    Meter.CreateHistogram<double>("checkout_duration_seconds", "s");

// Recording an outcome:
private void RecordOutcome(string outcome, string tier, long startTime)
{
    var elapsed = Stopwatch.GetElapsedTime(startTime);
    OutcomeCounter.Add(1,
        new KeyValuePair<string, object?>("outcome", outcome),
        new KeyValuePair<string, object?>("tier", tier));
    DurationHistogram.Record(elapsed.TotalSeconds,
        new KeyValuePair<string, object?>("outcome", outcome),
        new KeyValuePair<string, object?>("tier", tier));
}
```

### What business questions these answer

| Metric | Question it answers | Example query |
|---|---|---|
| `checkout_outcomes_total` | How many checkouts succeeded vs failed today? | `sum by (outcome) (checkout_outcomes_total)` |
| `checkout_outcomes_total{tier="GOLD"}` | Are gold-tier customers experiencing more failures? | `checkout_outcomes_total{tier="GOLD", outcome=~"cancelled.*"}` |
| `checkout_duration_seconds` | What is the p95 checkout latency? | `histogram_quantile(0.95, rate(checkout_duration_seconds_bucket[5m]))` |

Notice the label discipline: `outcome` is a small enum (success, cancelled_inventory, cancelled_payment, cancelled_shipping) and `tier` is bounded (BRONZE, SILVER, GOLD, PLATINUM). This keeps cardinality low -- we will return to this in Module 5.

---

## Step 4 -- Browse the ACL adapter

When two bounded contexts have different vocabularies, the **Anti-Corruption Layer** (ACL) translates between them. This is a core DDD pattern from Eric Evans and Vlad Khononov: it prevents one context's model from leaking into another.

In our system, the Order context talks about **SKUs** and **line items**. The Inventory context talks about **product codes** and **reservation lines**. The ACL sits in the Order service's infrastructure layer and translates between these vocabularies.

{% include excalidraw.html file="acl-translation" alt="ACL vocabulary translation" caption="Order's Sku/LineItem translated to Inventory's ProductCode/ReservationLine at the ACL boundary" %}

Open the inventory adapter for your language:

| Language | File path |
|---|---|
| Quarkus | `order-service/src/main/java/com/example/order/infrastructure/inventory/InventoryRestAdapter.java` |
| Python | `order_service/infrastructure/inventory_adapter.py` |
| C# | `OrderService/Infrastructure/InventoryAdapter.cs` |

Find the `reserve` method. Notice the three-phase structure: outbound translation (Order vocabulary to wire format), wire call (HTTP request), and inbound translation (wire response back to Order vocabulary). This is where drift dies -- if the Inventory service changes its contract, only this adapter breaks, not the domain model.

{% include codetabs.html langs="Quarkus|Python|C#" %}

```java
// order-service/.../infrastructure/inventory/InventoryRestAdapter.java

@Override
@WithSpan("Order.Acl.InventoryReserve")
public ReservationOutcome reserve(Order order) {
    Span span = Span.current();
    span.setAttribute("acl.context", "inventory");
    span.setAttribute("acl.transport", "rest");

    try {
        // 1. Outbound translation: Order -> wire
        InventoryReserveRequestDto wireRequest = toWire(order);

        // 2. Wire call
        InventoryReserveResponseDto wireResponse = client.reserve(wireRequest);

        // 3. Inbound translation - drift dies here
        return fromWire(wireResponse);
    } catch (WebApplicationException e) {
        recordTranslationFailure("transport", e);
        return new ReservationOutcome.Failure(
                "transport",
                "Inventory REST call failed: " + e.getMessage(), e);
    }
}

// Outbound: Order.Sku -> Inventory.productCode
private static InventoryReserveRequestDto toWire(Order order) {
    List<InventoryReserveRequestDto.Line> wireLines = order.lineItems().stream()
            .map(li -> new InventoryReserveRequestDto.Line(
                    li.sku().value(),     // Order.Sku -> Inventory.productCode
                    li.quantity()))
            .toList();
    return new InventoryReserveRequestDto(
            order.id().value(), order.customerId().value(), wireLines);
}
```

```python
# order_service/infrastructure/inventory_adapter.py

def reserve(self, order: Order) -> ReservationOutcome:
    with tracer.start_as_current_span("Order.Acl.InventoryReserve") as span:
        span.set_attribute("acl.context", "inventory")
        span.set_attribute("acl.transport", "rest")

        # 1. Outbound translation: Order -> wire
        wire_request = self._to_wire(order)

        # Inject OTel trace context for distributed tracing
        headers: dict[str, str] = {}
        inject(headers)

        # 2. Wire call
        response = self._client.post(url, json=wire_request, headers=headers)
        wire_response = response.json()

        # 3. Inbound translation -- drift dies here
        return self._from_wire(wire_response)

# Outbound: Order.Sku -> Inventory.sku field
@staticmethod
def _to_wire(order: Order) -> dict[str, Any]:
    wire_items = [
        {"sku": li.sku.value, "quantity": li.quantity}
        for li in order.line_items
    ]
    return {"orderId": order.id.value, "items": wire_items}
```

```csharp
// OrderService/Infrastructure/InventoryAdapter.cs

public async Task<ReservationOutcome> Reserve(Order order)
{
    using var activity = ActivitySource.StartActivity("Order.Acl.InventoryReserve");
    activity?.SetTag("acl.context", "inventory");
    activity?.SetTag("acl.transport", "rest");

    // 1. Outbound translation: Order -> wire
    var wireRequest = new
    {
        orderId = order.Id.Value,
        items = order.LineItems.Select(li => new
        {
            sku = li.Sku.Value,      // Order.Sku -> Inventory wire format
            quantity = li.Quantity
        }).ToArray()
    };

    // 2. Wire call
    var response = await _httpClient.PostAsJsonAsync(
        "/api/inventory/reserve", wireRequest, JsonOptions);

    // 3. Inbound translation -- drift dies here
    var status = root.GetProperty("status").GetString();
    return status switch
    {
        "RESERVED" => new ReservationOutcome.Reserved(
            root.GetProperty("reservationId").GetString()!),
        "UNAVAILABLE" => new ReservationOutcome.Unavailable(reason),
        _ => new ReservationOutcome.ReservationFailure(
            $"Unknown inventory status: {status}")
    };
}
```

### The span name tells the story

The span is named `Order.Acl.InventoryReserve` -- not `POST /api/inventory/reserve`. Reading the trace tree, you see exactly where a bounded context boundary was crossed and which ACL performed the translation. If the Inventory service changes its wire contract and breaks the ACL, the `acl_drift_total` counter increments and the failure is confined to the adapter -- the domain model never sees the drift.

---

## Step 5 -- Run checkout and Newman tests

Try it: send a single checkout request through the system, then run the full validation suite.

First, run a single checkout so you have a known order to trace:

```bash
curl -s -X POST http://localhost:8080/api/orders/checkout \
  -H "Content-Type: application/json" \
  -d '{
    "cartId": "cart_module3_001",
    "customerId": "cust_carol_platinum",
    "lineItems": [
      {
        "sku": "SKU-KEYBOARD-MX",
        "quantity": 1,
        "unitPrice": 149.99
      }
    ],
    "paymentMethod": "credit_card",
    "shippingClass": "express"
  }' | python3 -m json.tool
```

Note the `orderId` in the response -- you will use it in the next step to find your trace and logs.

Then run the Newman validation suite to generate a wider set of traffic (successful checkouts, inventory failures, different customer tiers):

```bash
newman run tests/collections/03-domain-events-validation.json \
  -e tests/environments/local.json
```

---

## Step 6 -- Explore Grafana: cross-signal correlation

The real power of structured observability is pivoting between the three signals -- traces, logs, and metrics -- using domain identifiers as the thread that connects them.

### 6a. Check the business metrics dashboard

1. Open **Grafana** at `http://localhost:3000`.
2. Navigate to **Dashboards** and open the **Checkout Saga** dashboard.
3. Look for the `checkout_outcomes_total` counter and `checkout_duration_seconds` histogram panels. You should see per-tier and per-outcome breakdowns from the traffic you generated in Step 5.

### 6b. Trace to logs -- follow a single checkout

4. Navigate to **Explore > Tempo**. Find the trace from your checkout (search by the `order.id` you noted in Step 5, or browse recent traces).
5. Click on the `Order.Checkout` span. Verify the business attributes are present: `order.id`, `customer.tier`, `order.value`.
6. From the trace view, click **Logs for this span**. Grafana queries Loki using the `trace_id` from the span and shows every structured log line emitted during that span's execution -- with the domain context fields (`order.id`, `customer.id`) already present. This is the traces-to-logs pivot.

### 6c. Metrics to traces -- follow a spike

7. Back on the **Checkout Saga** dashboard, look for an exemplar dot on one of the metric panels. Exemplars on metrics link to specific traces. When you see a spike in `checkout_outcomes_total{outcome="cancelled_inventory"}`, the exemplar on that data point links to one of the traces that contributed to it. Click the exemplar to jump directly into the trace view.

### 6d. Logs to traces -- follow an order ID

8. Navigate to **Explore > Loki**. Run the following query (replace the order ID with the one from your checkout in Step 5):

   ```
   {service_namespace="workshop"} | json | order_id="ord_xxx"
   ```

   You will see logs from all five services that handled that order -- because the `DomainContext` propagated the identifiers via OTel baggage across HTTP calls and Kafka messages.

9. The `trace_id` field in each structured log line is a clickable link. Click the trace ID on any log line to open the full distributed trace in Tempo. This is the logs-to-traces pivot.

### All three together

The three signals form a triangle. You can enter from any vertex:

- **Dashboard** (metrics) shows a spike in failures
- **Click an exemplar** to jump to a specific trace
- **Click "Logs"** on a span to see the structured log lines with domain context
- **The domain identifiers** (`order.id`, `customer.tier`) are the same across all three

---

## Checkpoint

You are ready for Module 4 when:

- Structured logs include `order.id` and `customer.id` across all services that handle a checkout
- Business metrics (`checkout_outcomes_total`, `checkout_duration_seconds`) appear in the Checkout Saga dashboard
- You can pivot between traces, logs, and metrics in Grafana using domain identifiers
