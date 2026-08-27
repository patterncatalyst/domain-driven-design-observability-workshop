# DDD + OpenTelemetry Workshop -- Exercise Implementations

Three implementations of the same five e-commerce microservices, each applying
Domain-Driven Design patterns with full OpenTelemetry observability. Same API
contract, same observability output, different language idioms.

## Choose Your Language

| | Quarkus | Python | C# |
|--|---------|--------|----|
| **Framework** | Quarkus 3.33 | FastAPI | ASP.NET Minimal APIs |
| **Runtime** | JDK 25 | Python 3.14 | .NET 10 |
| **OTel integration** | Micrometer + OTel bridge | opentelemetry-sdk | System.Diagnostics + OTel |
| **Kafka client** | SmallRye Reactive Messaging | confluent-kafka | Confluent.Kafka |
| **Build tool** | Maven (`mvnw`) | pip | `dotnet build` |
| **Base image** | UBI 10 OpenJDK 25 | UBI 9 Python 3.14 | `dotnet/aspnet:10.0` |

Pick a language and follow the README in that directory:

- [`quarkus/`](quarkus/) -- Java with Quarkus, sealed interfaces, records, virtual threads
- [`python/`](python/) -- Python with FastAPI, frozen dataclasses, Protocols, structlog
- [`dotnet/`](dotnet/) -- C# with Minimal APIs, records, ActivitySource, System.Diagnostics

## Shared Architecture

All three implementations deploy the same five microservices on the same ports,
backed by the same shared infrastructure:

```
                        +------------------+
                        |  order-service   |  :8080  (saga orchestrator)
                        +--------+---------+
                                 |
              +------------------+------------------+
              |                  |                  |
    +---------v------+  +--------v--------+  +------v----------+
    | inventory-svc  |  |  payment-svc    |  |  shipping-svc   |
    | :8081          |  |  :8082          |  |  :8083           |
    +----------------+  +-----------------+  +-----------------+
              |
    +---------v----------+
    | notification-svc   |  :8084  (Kafka consumer)
    +--------------------+
```

## Shared Infrastructure

Every implementation includes these infrastructure services via Docker Compose:

| Component | Port | Purpose |
|-----------|------|---------|
| Grafana | 3000 | Dashboards and visualization (`admin`/`admin`) |
| Prometheus | 9090 | Metrics storage with exemplar support |
| Tempo | 3200 | Distributed trace storage |
| Loki | 3100 | Log aggregation |
| OTel Collector | 4317 (gRPC), 4318 (HTTP) | Central telemetry receiver, fans out to backends |
| Kafka | 9092 | Event streaming (KRaft mode, no ZooKeeper) |
| PostgreSQL | 5432 | Optional persistence (`appuser`/`apppass`/`appdb`) |

## Testing

All implementations expose the same API contract, so the Newman test collections
in [`../tests/`](../tests/) validate any implementation identically:

```bash
# Smoke test -- verify all services and infra are healthy
newman run ../tests/collections/00-smoke-test.json \
  -e ../tests/environments/local.json

# Happy-path checkout -- full saga with inventory, payment, shipping
newman run ../tests/collections/01-checkout-happy-path.json \
  -e ../tests/environments/local.json

# Failure paths -- out-of-stock, payment decline, saga compensation
newman run ../tests/collections/02-checkout-failure-paths.json \
  -e ../tests/environments/local.json

# Observability validation -- traces in Tempo, logs in Loki, metrics in Prometheus
newman run ../tests/collections/03-domain-events-validation.json \
  -e ../tests/environments/local.json
```

There is also a comprehensive verification script:

```bash
../tests/verify.sh
```

## Pre-provisioned Grafana Dashboards

All implementations share five dashboards at http://localhost:3000:

| Dashboard | What It Shows |
|-----------|---------------|
| Workshop / Service Health | Request rates, error rates, latency per service |
| Workshop / Checkout Saga | Checkout outcomes by tier, saga duration, step-by-step flow |
| Workshop / Trace Explorer | TraceQL queries, span attributes, service graph |
| Workshop / Observability Cost | Span volume, metric cardinality, log throughput |
| Workshop / Transport Comparison | REST vs gRPC latency and throughput |

## Tutorial Site

Full guided workshop with six modules:
https://patterncatalyst.github.io/domain-driven-design-observability-workshop/
