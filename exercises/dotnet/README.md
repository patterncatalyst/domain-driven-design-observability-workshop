# C# Implementation -- DDD + OpenTelemetry Workshop

Five microservices implementing a DDD e-commerce checkout saga with full
OpenTelemetry observability. Built on .NET 10, ASP.NET Minimal APIs, C# records,
`ActivitySource` for tracing, and `System.Diagnostics.Metrics` for metrics.

## Prerequisites

- **.NET 10 SDK** (10.0.100 or later)
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
| OrderService | 8080 | Order / Checkout | Saga orchestrator, Anti-Corruption Layer, domain events, interface-based ports |
| InventoryService | 8081 | Inventory | Stock reservation aggregate, vocabulary translation (SKU to ProductCode) |
| PaymentService | 8082 | Payment | Authorization aggregate, deterministic simulation |
| ShippingService | 8083 | Shipping | Shipment scheduling, shipping-class routing |
| NotificationService | 8084 | Notification | Kafka consumer (`BackgroundService`), inbound event deserialization (own types) |

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

- **Out of stock**: Use a SKU prefixed with `OUT_` or `OUT-` (e.g., `OUT_LAPTOP`)
- **Payment decline**: Use a customer ID containing `decline` (e.g., `cust_bob_decline`)
- **Payment failure**: Use a customer ID containing `fail`
- **Partial reservation**: Use a SKU prefixed with `PARTIAL_`

## Project Structure

.NET solution with six projects and a shared observability library:

```
exercises/dotnet/
├── DddWorkshop.sln                      # Solution file
├── global.json                           # .NET 10.0.100 SDK constraint
├── compose.yaml                          # 5 services + 7 infrastructure includes
│
├── SharedObservability/                  # Class library (Microsoft.NET.Sdk)
│   ├── SharedObservability.csproj        # OTel packages: Api, Hosting, OTLP exporter,
│   │                                     #   ASP.NET Core + Http + Runtime instrumentation
│   ├── IDomainIdentifier.cs              # Interface: Key + Value properties
│   ├── DomainContext.cs                  # IDisposable logging scope + OTel baggage manager
│   ├── BaggageHelpers.cs                 # Static Get/Set/Remove over OpenTelemetry.Baggage
│   ├── KafkaHeaderPropagator.cs          # domain.* prefix header injection/extraction
│   └── OtelSetup.cs                      # AddOpenTelemetryWorkshop() extension method
│                                          #   -- tracing, metrics, logging via OTLP
│
├── OrderService/                          # Saga orchestrator
│   ├── OrderService.csproj                # Confluent.Kafka 2.6.1, Grpc.Net.Client 2.67.0
│   ├── Program.cs                         # Minimal API: POST /api/orders/checkout
│   │                                      #   Named HttpClients, DI wiring, shutdown hook
│   ├── Dockerfile
│   ├── Domain/
│   │   ├── Models.cs                      # Order (aggregate), OrderId, CustomerId, CartId,
│   │   │                                  #   Sku, Money, LineItem, OrderStatus, CustomerTier
│   │   │                                  #   -- C# records with prefix validation
│   │   ├── Events.cs                      # DomainEvent (abstract), OrderPlaced, OrderConfirmed,
│   │   │                                  #   OrderCancelled -- ToDict() serialization
│   │   ├── Ports.cs                       # IInventoryPort, IPaymentPort, IShippingPort,
│   │   │                                  #   IOrderEventPublisher (interfaces with discriminated
│   │   │                                  #   union outcomes via nested records)
│   │   ├── Services.cs                    # ICustomerProfileLookup, CustomerProfile record
│   │   └── Identifiers.cs                # OrderContextKey with nested static classes
│   │                                      #   (order.id, customer.id, cart.id, customer.tier)
│   ├── Application/
│   │   └── CheckoutSaga.cs               # ActivitySource spans, Meter counters/histograms,
│   │                                      #   DomainContext, baggage propagation
│   └── Infrastructure/
│       ├── InventoryAdapter.cs            # InventoryRestAdapter (ACL, custom span)
│       ├── PaymentAdapter.cs              # PaymentRestAdapter (thin client)
│       ├── ShippingAdapter.cs             # ShippingRestAdapter (thin client)
│       ├── KafkaPublisher.cs              # Confluent.Kafka Producer, topic "order-events"
│       │                                  #   OTel context + domain headers injection
│       └── CustomerLookup.cs              # InMemoryCustomerProfileLookup (tier from ID suffix)
│
├── InventoryService/                      # Stock reservation
│   ├── InventoryService.csproj            # Grpc.AspNetCore 2.67.0
│   ├── Program.cs                         # POST /api/inventory/reserve
│   ├── Domain/
│   │   ├── Models.cs                      # Reservation, ReservationId, ProductCode,
│   │   │                                  #   ReservationLine, ReservationStatus
│   │   └── Identifiers.cs
│   └── Application/
│       └── ReserveStockUseCase.cs         # SKU-prefix simulation, counter metric
│
├── PaymentService/                        # Payment authorization
│   ├── Program.cs                         # POST /api/payments/authorize
│   ├── Domain/
│   │   ├── Models/
│   │   │   ├── Authorization.cs           # Authorization aggregate
│   │   │   ├── AuthorizationId.cs         # Prefix auth_
│   │   │   └── AuthorizationOutcome.cs    # AUTHORIZED, DECLINED, FAILURE
│   │   └── Identifiers/
│   │       └── PaymentContextKey.cs
│   ├── Application/
│   │   └── AuthorizePaymentUseCase.cs     # Customer ID pattern matching
│   └── Infrastructure/
│       └── PaymentDtos.cs                 # [JsonPropertyName] DTOs
│
├── ShippingService/                       # Shipment scheduling
│   ├── Program.cs                         # POST /api/shipments/schedule
│   ├── Domain/
│   │   ├── Models/
│   │   │   ├── Shipment.cs                # Shipping-class routing (overnight=1..standard=5)
│   │   │   └── ShipmentId.cs              # Prefix ship_
│   │   └── Identifiers/
│   │       └── ShippingContextKey.cs
│   ├── Application/
│   │   └── ScheduleShipmentUseCase.cs     # Always succeeds
│   └── Infrastructure/
│       └── ShippingDtos.cs
│
└── NotificationService/                   # Kafka consumer (no REST business endpoints)
    ├── Program.cs                         # Health endpoints only
    ├── Domain/
    │   ├── Models.cs                      # Notification, NotificationId, NotificationKind
    │   ├── Events.cs                      # InboundOrderEvent base, InboundOrderPlaced/
    │   │                                  #   Confirmed/Cancelled (own types, not shared)
    │   └── Identifiers.cs
    ├── Application/
    │   └── SendNotificationUseCase.cs     # Maps event kind to notification
    └── Infrastructure/
        └── KafkaConsumer.cs               # BackgroundService, consumer group
                                            #   "notification-service-v2", OTel context
                                            #   extraction from Kafka headers
```

## Observability

### Spans (custom, beyond auto-instrumented ASP.NET Core spans)

| Span Name | Service | What It Captures |
|-----------|---------|------------------|
| `Order.Checkout` | OrderService | Full saga, `order.id`, `order.value`, `customer.id`, `customer.tier` |
| `Order.Acl.InventoryReserve` | OrderService | ACL translation, `acl.context=inventory`, `acl.transport=rest` |
| `Order.Payment.Authorize` | OrderService | Payment adapter call |
| `Order.Shipping.Schedule` | OrderService | Shipping adapter call |
| `Order.Events.Publish` | OrderService | Kafka event publication |
| `Inventory.Reserve` | InventoryService | Stock reservation logic |
| `Payment.Authorize` | PaymentService | Authorization decision |
| `Shipping.Schedule` | ShippingService | Shipment scheduling |
| `Notification.Consume` | NotificationService | Kafka message consumption |
| `Notification.Send` | NotificationService | Notification delivery |

### Metrics (custom business metrics)

| Metric | Type | Labels | Service |
|--------|------|--------|---------|
| `checkout_outcomes_total` | Counter | `outcome`, `tier` | OrderService |
| `checkout_duration_seconds` | Histogram | `outcome`, `tier` | OrderService |
| `inventory_reservations_total` | Counter | `status`, `tier` | InventoryService |
| `payment_authorizations_total` | Counter | `outcome`, `tier` | PaymentService |
| `shipping_shipments_scheduled_total` | Counter | `class`, `tier` | ShippingService |
| `notifications_sent_total` | Counter | `kind`, `tier` | NotificationService |

### Structured Logs

All services use the standard .NET `ILogger` with OTel log export enabled
(`IncludeFormattedMessage`, `IncludeScopes`). Domain identifiers are injected
into logging scopes via `DomainContext`, making `order.id`, `customer.id`, etc.
appear in every log entry within that scope.

### Domain Context Propagation

Domain identifiers flow through the system via three channels:
1. **`ILogger` scopes** -- `DomainContext` creates a logging scope from `IDomainIdentifier[]` and sets OTel baggage entries; disposes both on scope exit
2. **OTel Baggage** -- `BaggageHelpers.Set(key, value)` for cross-service propagation (e.g., `customer.tier`)
3. **Kafka Headers** -- `KafkaHeaderPropagator.Inject/Extract` writes `domain.*` prefixed headers for event-driven services

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

# Restore NuGet packages
dotnet restore

# Run a service (from the solution root)
OTEL__EXPORTER__OTLP__ENDPOINT=http://localhost:4317 \
OTEL_SERVICE_NAME=order-service \
Kafka__BootstrapServers=localhost:9092 \
dotnet run --project OrderService

# Other services in separate terminals:
dotnet run --project InventoryService
dotnet run --project PaymentService
dotnet run --project ShippingService
dotnet run --project NotificationService
```

Service URLs default to `http://inventory-service:8081`, etc. (Docker DNS).
Override for local dev via environment variables:

```bash
INVENTORY_URL=http://localhost:8081 \
PAYMENT_URL=http://localhost:8082 \
SHIPPING_URL=http://localhost:8083 \
dotnet run --project OrderService
```

## C#-Specific Patterns

- **C# records** for all value objects, events, commands, and results -- immutability with `with` expressions for state transitions
- **Discriminated union outcomes** via nested records on interfaces (`ReservationOutcome` with `Reserved`, `Unavailable`, `ReservationFailure` subtypes) -- pattern-matched with `switch` expressions
- **`ActivitySource`** for custom span creation -- each service defines its own `ActivitySource` named after the service
- **`Meter` / `Counter<long>` / `Histogram<double>`** from `System.Diagnostics.Metrics` for business metrics, exported to OTel via the OTLP exporter
- **`IDisposable` `DomainContext`** -- creates a logging scope from domain identifiers and manages OTel baggage lifetime in a single `using` block
- **Named `HttpClient` registration** via `builder.Services.AddHttpClient("inventory", ...)` for typed downstream service calls
- **`BackgroundService`** for the notification Kafka consumer -- `ExecuteAsync` runs a `ConsumeResult` loop with manual OTel context restoration
- **Minimal API style** -- `app.MapPost("/api/orders/checkout", ...)` with inline handler delegates, no controller classes
- **Quarkus-compatible health endpoints** (`/q/health/ready`, `/q/health/live`) for cross-implementation test compatibility
- **`[JsonPropertyName]`** attributes for camelCase JSON wire format

## NuGet Packages

SharedObservability (used by all services):
- `OpenTelemetry.Api` 1.11.2
- `OpenTelemetry.Extensions.Hosting` 1.11.2
- `OpenTelemetry.Exporter.OpenTelemetryProtocol` 1.11.2
- `OpenTelemetry.Instrumentation.AspNetCore` 1.11.1
- `OpenTelemetry.Instrumentation.Http` 1.11.1
- `OpenTelemetry.Instrumentation.Runtime` 1.11.1

Service-specific:
- OrderService adds `Confluent.Kafka` 2.6.1, `Grpc.Net.Client` 2.67.0
- InventoryService adds `Grpc.AspNetCore` 2.67.0
- NotificationService adds `Confluent.Kafka` 2.6.1

## Links

- [Tutorial site](https://patterncatalyst.github.io/domain-driven-design-observability-workshop/)
- [Repository README](../../README.md)
- [Test collections](../../tests/)
