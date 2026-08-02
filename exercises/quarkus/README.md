# Quarkus Implementation -- DDD + OpenTelemetry Workshop

Five microservices implementing a DDD e-commerce checkout saga with full
OpenTelemetry observability. Built on Quarkus 3.33, Java 25, sealed interfaces,
immutable records, and virtual threads.

## Prerequisites

- **JDK 25** (the included Maven wrapper handles Maven itself)
- **Docker** and **Docker Compose** (for the full stack)
- **Newman** (`npm install -g newman`) for API tests

## Quick Start

```bash
docker compose up --build -d

# Wait about 60 seconds for all services to start, then verify:
../../tests/verify.sh
```

To tear down:

```bash
docker compose down -v
```

## Services

| Service | Port | Bounded Context | Key DDD Patterns |
|---------|------|-----------------|------------------|
| order-service | 8080 | Order / Checkout | Saga orchestrator, Anti-Corruption Layer, domain events, outbound ports |
| inventory-service | 8081 (REST), 9001 (gRPC) | Inventory | Stock reservation aggregate, vocabulary translation (SKU to ProductCode) |
| payment-service | 8082 | Payment | Authorization aggregate, deterministic simulation |
| shipping-service | 8083 | Shipping | Shipment scheduling, shipping-class routing |
| notification-service | 8084 | Notification | Kafka consumer, inbound event deserialization (own event types, not shared) |

## API Examples

### Full checkout (happy path)

```bash
curl -s -X POST http://localhost:8080/api/orders/checkout \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": "cust_alice_silver",
    "cartId": "cart_demo_001",
    "lineItems": [
      { "sku": "SKU-100", "quantity": 2, "unitPrice": { "amount": 29.99, "currency": "USD" } },
      { "sku": "SKU-242", "quantity": 1, "unitPrice": { "amount": 49.50, "currency": "USD" } }
    ]
  }' | jq .
```

Returns `201 Created` with `status: "CONFIRMED"`, or `422` with `status: "CANCELLED"`.

### Individual services

```bash
# Reserve inventory
curl -s -X POST http://localhost:8081/api/inventory/reserve \
  -H 'Content-Type: application/json' \
  -d '{
    "orderId": "ord_test_001",
    "customerId": "cust_alice_silver",
    "items": [{ "sku": "SKU-100", "quantity": 2 }]
  }' | jq .

# Authorize payment
curl -s -X POST http://localhost:8082/api/payments/authorize \
  -H 'Content-Type: application/json' \
  -d '{
    "orderId": "ord_test_001",
    "customerId": "cust_alice_silver",
    "amount": 109.48,
    "currency": "USD",
    "paymentMethod": "credit_card"
  }' | jq .

# Schedule shipment
curl -s -X POST http://localhost:8083/api/shipments/schedule \
  -H 'Content-Type: application/json' \
  -d '{
    "orderId": "ord_test_001",
    "customerId": "cust_alice_silver",
    "shippingClass": "standard"
  }' | jq .
```

### Trigger specific outcomes

- **Out of stock**: Use a SKU prefixed with `OUT_` (e.g., `OUT_LAPTOP`)
- **Payment decline**: Use a customer ID ending in `_decline` (e.g., `cust_bob_decline`)
- **Partial reservation**: Use a SKU prefixed with `PARTIAL_`

## Project Structure

Multi-module Maven project with a shared observability library:

```
exercises/quarkus/
├── pom.xml                              # Parent POM (Quarkus 3.33.1, Java 25)
├── mvnw / mvnw.cmd                      # Maven wrapper (3.9.9)
├── compose.yaml                         # 5 services + 7 infrastructure includes
├── shared-observability/                # Plain Java library (no Quarkus runtime)
│   └── src/main/java/com/example/workshop/observability/
│       ├── DomainIdentifier.java        # Interface: key() + value()
│       ├── DomainContext.java           # SLF4J MDC scope manager
│       ├── BaggageHelpers.java          # OTel Baggage put/get wrappers
│       └── KafkaHeaderPropagator.java   # domain.* prefix header injection/extraction
│
├── order-service/                       # Saga orchestrator
│   └── src/main/java/com/example/order/
│       ├── domain/
│       │   ├── model/                   # Order (aggregate), OrderId, CustomerId, CartId,
│       │   │                            #   Sku, Money, LineItem, OrderStatus, CustomerTier
│       │   ├── event/                   # DomainEvent (sealed): OrderPlaced, OrderConfirmed,
│       │   │                            #   OrderCancelled -- Jackson @JsonTypeInfo
│       │   ├── outbound/               # InventoryPort, PaymentPort, ShippingPort,
│       │   │                            #   OrderEventPublisher (interfaces with sealed outcomes)
│       │   ├── service/                 # CustomerProfileLookup, CustomerProfile
│       │   └── identifier/             # OrderContextKey enum (order.id, customer.id, etc.)
│       ├── application/
│       │   ├── CheckoutSaga.java        # @WithSpan("Order.Checkout"), Micrometer metrics,
│       │   │                            #   exhaustive sealed-interface switches
│       │   ├── CheckoutCommand.java
│       │   └── CheckoutResult.java      # Sealed: Confirmed | Cancelled
│       └── infrastructure/
│           ├── web/                     # JAX-RS CheckoutResource (POST /api/orders/checkout)
│           ├── inventory/               # InventoryRestAdapter (full ACL, drift detection)
│           │                            #   InventoryGrpcAdapter (Module 5 toggle)
│           ├── payment/                 # PaymentRestAdapter (thin client, shared vocabulary)
│           ├── shipping/                # ShippingRestAdapter (thin client)
│           ├── kafka/                   # OrderEventKafkaPublisher (SmallRye Reactive Messaging)
│           └── customer/               # InMemoryCustomerProfileLookup
│
├── inventory-service/                   # Stock reservation
│   └── src/main/java/com/example/inventory/
│       ├── domain/model/                # Reservation, ReservationId, ProductCode, ReservationLine
│       ├── application/                 # ReserveStockUseCase (@WithSpan("Inventory.Reserve"))
│       └── infrastructure/
│           ├── web/                     # REST resource (POST /api/inventory/reserve)
│           └── grpc/                    # gRPC service (port 9001, Module 5)
│
├── payment-service/                     # Payment authorization
│   └── src/main/java/com/example/payment/
│       ├── domain/model/                # Authorization, AuthorizationId, AuthorizationOutcome
│       ├── application/                 # AuthorizePaymentUseCase (@WithSpan("Payment.Authorize"))
│       └── infrastructure/web/          # POST /api/payments/authorize
│
├── shipping-service/                    # Shipment scheduling
│   └── src/main/java/com/example/shipping/
│       ├── domain/model/                # Shipment, ShipmentId
│       ├── application/                 # ScheduleShipmentUseCase (@WithSpan("Shipping.Schedule"))
│       └── infrastructure/web/          # POST /api/shipments/schedule
│
└── notification-service/                # Kafka consumer (no REST business endpoints)
    └── src/main/java/com/example/notification/
        ├── domain/model/                # Notification, NotificationId, NotificationKind
        │                                # InboundOrderEvent (sealed, own types)
        ├── application/                 # SendNotificationUseCase (@WithSpan("Notification.Send"))
        └── infrastructure/kafka/        # OrderEventConsumer (@RunOnVirtualThread)
```

## Observability

### Spans (custom, beyond auto-instrumented HTTP/Kafka spans)

| Span Name | Service | What It Captures |
|-----------|---------|------------------|
| `Order.Checkout` | order | Full saga duration, `order.id`, `order.value`, `customer.id`, `customer.tier` |
| `Order.Acl.InventoryReserve` | order | ACL translation, `acl.context=inventory`, `acl.transport=rest\|grpc` |
| `Order.Payment.Authorize` | order | Payment adapter call |
| `Order.Shipping.Schedule` | order | Shipping adapter call |
| `Order.Events.Publish` | order | Kafka event publication |
| `Inventory.Reserve` | inventory | Stock reservation logic |
| `Payment.Authorize` | payment | Authorization decision |
| `Shipping.Schedule` | shipping | Shipment scheduling |
| `Notification.Consume` | notification | Kafka message consumption |
| `Notification.Send` | notification | Notification delivery |

### Metrics (custom business metrics)

| Metric | Type | Labels | Service |
|--------|------|--------|---------|
| `checkout_outcomes_total` | counter | `outcome`, `tier` | order |
| `checkout_duration_seconds` | timer/histogram | -- | order |
| `checkout_orders_in_payment_verification` | gauge | -- | order |
| `acl_drift_total` | counter | `context`, `transport`, `type` | order |
| `inventory_reservations_total` | counter | `status`, `tier` | inventory |
| `payment_authorizations_total` | counter | `outcome`, `tier` | payment |
| `shipping_shipments_scheduled_total` | counter | `class`, `tier` | shipping |
| `notifications_sent_total` | counter | `kind`, `tier` | notification |

### Structured Logs

All services emit JSON logs via `quarkus-logging-json` with domain identifiers
injected into the MDC via `DomainContext` (`order.id`, `customer.id`, `cart.id`).

### Domain Context Propagation

Domain identifiers flow through the system via three channels:
1. **SLF4J MDC** -- `DomainContext.open(identifiers...)` for structured log correlation
2. **OTel Baggage** -- `BaggageHelpers.put(identifier)` for cross-service propagation (e.g., `customer.tier`)
3. **Kafka Headers** -- `KafkaHeaderPropagator` writes `domain.*` prefixed headers for event-driven services

## Running Tests

```bash
# Smoke test -- all services and infra healthy
newman run ../../tests/collections/00-smoke-test.json \
  -e ../../tests/environments/local.json

# Happy-path checkout
newman run ../../tests/collections/01-checkout-happy-path.json \
  -e ../../tests/environments/local.json

# Failure paths (out of stock, payment decline)
newman run ../../tests/collections/02-checkout-failure-paths.json \
  -e ../../tests/environments/local.json

# Observability validation (traces, logs, metrics present)
newman run ../../tests/collections/03-domain-events-validation.json \
  -e ../../tests/environments/local.json

# Debugging exercise traffic generation
newman run ../../tests/collections/04-debugging-exercise.json \
  -e ../../tests/environments/local.json

# Light load test (sequential checkouts, no 500s)
newman run ../../tests/collections/05-load-test-validation.json \
  -e ../../tests/environments/local.json
```

## Grafana Dashboards

Open http://localhost:3000 (credentials: `admin` / `admin`).

Five pre-provisioned dashboards:

| Dashboard | What to Look For |
|-----------|------------------|
| **Service Health** | Request rates, error rates, p50/p95/p99 latency per service |
| **Checkout Saga** | `checkout_outcomes_total` by tier, saga duration distribution |
| **Trace Explorer** | TraceQL queries, span waterfall, service graph |
| **Observability Cost** | Span volume, metric cardinality, log throughput |
| **Transport Comparison** | REST vs gRPC latency (requires Module 5 gRPC toggle) |

### Quick Exploration

1. **Traces**: Go to Explore, select Tempo, run `{resource.service.namespace="workshop"}` to see all workshop traces. Click a trace to see the full saga waterfall.
2. **Logs**: Select Loki, run `{service_namespace="workshop"}` to see structured logs with `order.id` and `customer.id` fields.
3. **Metrics**: Select Prometheus, query `checkout_outcomes_total` to see checkout success/failure rates by customer tier.

## Development Mode

Run individual services outside Docker for faster iteration:

```bash
# Start only infrastructure
docker compose up -d kafka postgres otel-collector tempo prometheus loki grafana

# Run a service in dev mode (hot reload)
cd order-service
../mvnw quarkus:dev \
  -Dquarkus.otel.exporter.otlp.endpoint=http://localhost:4317 \
  -Dquarkus.otel.service.name=order-service

# Other services in separate terminals:
cd inventory-service && ../mvnw quarkus:dev -Dquarkus.http.port=8081
cd payment-service   && ../mvnw quarkus:dev -Dquarkus.http.port=8082
cd shipping-service  && ../mvnw quarkus:dev -Dquarkus.http.port=8083
cd notification-service && ../mvnw quarkus:dev -Dquarkus.http.port=8084
```

## Quarkus-Specific Patterns

- **Sealed interfaces** for all domain outcomes (`ReservationOutcome`, `AuthorizationOutcome`, `ShipmentOutcome`, `DomainEvent`, `CheckoutResult`) -- exhaustive `switch` with no default arm
- **Immutable records** for all value objects, commands, events, and aggregates
- **`@IfBuildProperty`** for compile-time adapter selection (REST vs gRPC for inventory, Module 5)
- **`@WithSpan`** annotation on application and infrastructure methods for custom span creation
- **`@RunOnVirtualThread`** on the notification Kafka consumer with JFR-based virtual thread metrics
- **Micrometer** counters, gauges, and timers bridged to OTel via `quarkus-micrometer-opentelemetry`
- **MicroProfile REST Client** for type-safe downstream service calls
- **SmallRye Reactive Messaging** for Kafka production and consumption
- **Anti-Corruption Layer** with drift detection counters on the inventory adapter (translates `Sku` to `ProductCode` vocabulary)

## Links

- [Tutorial site](https://patterncatalyst.github.io/domain-driven-design-observability-workshop/)
- [Repository README](../../README.md)
- [Test collections](../../tests/)
