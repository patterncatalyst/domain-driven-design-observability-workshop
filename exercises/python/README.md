# Python Implementation -- DDD + OpenTelemetry Workshop

Five microservices implementing a DDD e-commerce checkout saga with full
OpenTelemetry observability. Built on FastAPI, Python 3.12, frozen dataclasses,
Protocol-based ports, and structlog.

## Prerequisites

- **Python 3.12+** (Docker images use UBI 9 Python 3.12)
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
| order-service | 8080 | Order / Checkout | Saga orchestrator, Anti-Corruption Layer, domain events, Protocol-based ports |
| inventory-service | 8081 | Inventory | Stock reservation aggregate, vocabulary translation (SKU to ProductCode) |
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

- **Out of stock**: Use a SKU prefixed with `OUT_` or `OUT-` (e.g., `OUT_LAPTOP`)
- **Payment decline**: Use a customer ID containing `decline` (e.g., `cust_bob_decline`)
- **Payment failure**: Use a customer ID containing `fail`
- **Partial reservation**: Use a SKU prefixed with `PARTIAL_`

## Project Structure

Six packages with a shared observability library:

```
exercises/python/
├── pyproject.toml                       # Project config (Python >=3.14, Ruff)
├── compose.yaml                         # 5 services + 7 infrastructure includes
│
├── shared_observability/                # Shared observability helpers
│   ├── __init__.py                     # Package exports
│   ├── domain_identifier.py            # Protocol: key() + value()
│   ├── domain_context.py               # structlog contextvars scope manager
│   ├── baggage_helpers.py              # OTel Baggage set/get wrappers
│   ├── kafka_header_propagator.py      # domain.* prefix header injection/extraction
│   └── otel_setup.py                   # configure_otel(): TracerProvider, MeterProvider, structlog
│
├── order_service/                       # Saga orchestrator
│   ├── main.py                          # FastAPI app, OTel bootstrap, health endpoints
│   ├── requirements.txt                 # fastapi, uvicorn, httpx, confluent-kafka, otel-*
│   ├── Dockerfile
│   ├── domain/
│   │   ├── models.py                    # Order (aggregate), OrderId, CustomerId, CartId,
│   │   │                                #   Sku, Money, LineItem, OrderStatus, CustomerTier
│   │   │                                #   -- all @dataclass(frozen=True) with prefix validation
│   │   ├── events.py                    # DomainEvent base, OrderPlaced, OrderConfirmed,
│   │   │                                #   OrderCancelled -- camelCase serialization
│   │   ├── ports.py                     # InventoryPort, PaymentPort, ShippingPort,
│   │   │                                #   OrderEventPublisher (Protocols with tagged unions)
│   │   ├── services.py                  # CustomerProfileLookup Protocol, CustomerProfile
│   │   └── identifiers.py              # OrderContextKey enum (order.id, customer.id, etc.)
│   ├── application/
│   │   └── checkout_saga.py             # CheckoutSaga, CheckoutCommand, CheckoutResult
│   │                                    #   -- DomainContext, baggage, business metrics
│   └── infrastructure/
│       ├── routes.py                    # FastAPI router, Pydantic DTOs (POST /api/orders/checkout)
│       ├── inventory_adapter.py         # Full ACL: Sku->sku, LineItem->quantity translation
│       │                                #   Span: Order.Acl.InventoryReserve
│       ├── payment_adapter.py           # Thin client (shared vocabulary)
│       ├── shipping_adapter.py          # Thin client
│       ├── kafka_publisher.py           # confluent-kafka Producer, topic "order-events"
│       └── customer_lookup.py           # InMemoryCustomerProfileLookup (tier from ID suffix)
│
├── inventory_service/                   # Stock reservation
│   ├── main.py
│   ├── requirements.txt                 # Includes grpcio (for Module 5)
│   ├── domain/
│   │   ├── models.py                    # Reservation, ReservationId, ProductCode, ReservationLine
│   │   └── identifiers.py
│   ├── application/
│   │   └── reserve_stock.py             # ReserveStockUseCase -- SKU-prefix simulation
│   └── infrastructure/
│       └── routes.py                    # POST /api/inventory/reserve
│
├── payment_service/                     # Payment authorization
│   ├── main.py
│   ├── domain/
│   │   ├── models.py                    # Authorization, AuthorizationId, AuthorizationOutcome
│   │   └── identifiers.py
│   ├── application/
│   │   └── authorize_payment.py         # customer ID pattern matching
│   └── infrastructure/
│       └── routes.py                    # POST /api/payments/authorize
│
├── shipping_service/                    # Shipment scheduling
│   ├── main.py
│   ├── domain/
│   │   ├── models.py                    # Shipment, ShipmentId
│   │   └── identifiers.py
│   ├── application/
│   │   └── schedule_shipment.py         # Always succeeds
│   └── infrastructure/
│       └── routes.py                    # POST /api/shipments/schedule
│
└── notification_service/                # Kafka consumer (no REST business endpoints)
    ├── main.py
    ├── requirements.txt                 # Includes confluent-kafka
    ├── domain/
    │   ├── models.py                    # Notification, NotificationId, NotificationKind
    │   ├── events.py                    # InboundOrderPlaced/Confirmed/Cancelled (own types)
    │   └── identifiers.py
    ├── application/
    │   └── send_notification.py         # SendNotificationUseCase
    └── infrastructure/
        └── kafka_consumer.py            # OrderEventConsumer -- daemon thread, OTel context
                                         #   extraction from Kafka headers
```

## Observability

### Spans (custom, beyond auto-instrumented FastAPI spans)

| Span Name | Service | What It Captures |
|-----------|---------|------------------|
| `Order.Checkout` | order | Full saga, `order.id`, `order.value`, `customer.id`, `customer.tier` |
| `Order.Acl.InventoryReserve` | order | ACL translation with drift detection |
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
| `checkout_duration_seconds` | histogram | `outcome`, `tier` | order |
| `inventory_reservations_total` | counter | `status`, `tier` | inventory |
| `payment_authorizations_total` | counter | `outcome`, `tier` | payment |
| `shipping_shipments_scheduled_total` | counter | `tier` | shipping |
| `notifications_sent_total` | counter | `kind`, `tier` | notification |

### Structured Logs

All services use **structlog** with context variables. Domain identifiers
(`order.id`, `customer.id`, `cart.id`) are injected into the logging context via
`DomainContext` and appear in every log line within that scope.

Log format is controlled by the `LOG_FORMAT` environment variable:
- `json` (default in containers) -- JSON lines for Loki ingestion
- `console` -- human-readable colored output for local development

### Domain Context Propagation

Domain identifiers flow through the system via three channels:
1. **structlog contextvars** -- `DomainContext(identifiers...)` for structured log correlation
2. **OTel Baggage** -- `set_baggage(key, value)` for cross-service propagation (e.g., `customer.tier`)
3. **Kafka Headers** -- `inject_domain_identifiers()` writes `domain.*` prefixed headers for event-driven services

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

# Install a service's dependencies (shared_observability is on PYTHONPATH automatically)
export PYTHONPATH=$PWD
pip install -r order_service/requirements.txt

# Run a service with uvicorn (hot reload)
cd order_service
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 \
OTEL_SERVICE_NAME=order-service \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
python -m uvicorn main:app --host 0.0.0.0 --port 8080 --reload

# Other services in separate terminals:
cd inventory_service && python -m uvicorn main:app --port 8081 --reload
cd payment_service   && python -m uvicorn main:app --port 8082 --reload
cd shipping_service  && python -m uvicorn main:app --port 8083 --reload
cd notification_service && python -m uvicorn main:app --port 8084 --reload
```

## Python-Specific Patterns

- **Frozen dataclasses** for all value objects and aggregates -- immutability enforced at the language level, factory methods return new instances for state transitions
- **Protocols** (structural subtyping) for all outbound ports -- no abstract base classes, just duck typing with type-checker support
- **Tagged union results** via dataclass inheritance for outcome types (`Reserved | Unavailable | ReservationFailure`) -- pattern-matched with `isinstance()` checks
- **Pydantic models** for API DTOs with `alias_generator=to_camel` for camelCase JSON serialization
- **structlog** with `merge_contextvars` for automatic domain identifier injection into every log line
- **httpx** for synchronous downstream REST calls with OTel context injection via `opentelemetry.propagate.inject`
- **confluent-kafka** for Kafka production (order-service) and consumption (notification-service)
- **Quarkus-compatible health endpoints** (`/q/health/ready`, `/q/health/live`) for cross-implementation test compatibility
- **Prefix-validated value objects** -- `OrderId.of("ord_...")` raises `ValueError` on invalid prefix

## Dependencies

Common to all services:
- `fastapi >=0.115`
- `uvicorn[standard] >=0.32`
- `pydantic >=2.10`
- `opentelemetry-api >=1.28`
- `opentelemetry-sdk >=1.28`
- `opentelemetry-instrumentation-fastapi >=0.49b0`
- `opentelemetry-exporter-otlp >=1.28`
- `structlog >=24.4`

Service-specific:
- order-service adds `httpx >=0.28`, `confluent-kafka >=2.6`
- inventory-service adds `grpcio >=1.68` (Module 5 transport comparison)
- notification-service adds `confluent-kafka >=2.6`

## Links

- [Tutorial site](https://patterncatalyst.github.io/domain-driven-design-observability-workshop/)
- [Repository README](../../README.md)
- [Test collections](../../tests/)
