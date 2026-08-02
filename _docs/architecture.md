---
title: "Architecture Overview"
order: 21
description: "The workshop domain model and infrastructure stack."
label: "Reference"
---

This page is a reference for the workshop's domain model, service architecture, observability pipeline, and naming conventions. Use it to orient yourself or to look up a port number, span name, or context relationship during the exercises.

## System architecture

{% include excalidraw.html file="bounded-context-map" alt="E-commerce bounded context map" caption="Five bounded contexts and their integration relationships" %}

{% include excalidraw.html file="checkout-saga-flow" alt="Checkout saga orchestration flow" caption="Sequential saga: Inventory → Payment → Shipping, then async Notification via Kafka" %}

{% include excalidraw.html file="ddd-three-layers" alt="Hexagonal architecture layers" caption="Domain (pure) → Application (use cases) → Infrastructure (adapters)" %}

---

## Bounded context map

The system models an **order checkout** flow with five bounded contexts:

| Bounded Context | Service | Role | Communication |
|---|---|---|---|
| **Order** | order-service | Saga orchestrator. Places orders, coordinates the checkout flow, publishes domain events. | HTTP (outbound to Inventory, Payment, Shipping), Kafka (publishes to `order-events`) |
| **Inventory** | inventory-service | Manages stock. Receives reservation requests, checks availability, returns reservation outcomes. | HTTP (inbound from Order) |
| **Payment** | payment-service | Authorizes payments. Receives authorization requests, validates payment method, returns authorization outcomes. | HTTP (inbound from Order) |
| **Shipping** | shipping-service | Schedules shipments. Receives scheduling requests, estimates delivery, returns shipment outcomes. | HTTP (inbound from Order) |
| **Notification** | notification-service | Sends notifications. Consumes domain events from Kafka, maps them to notification types. | Kafka (consumes from `order-events`) |

### Context relationships

- **Order <-> Inventory**: Customer/Supplier with ACL. Order translates between its `Sku`/`LineItem` vocabulary and Inventory's `ProductCode`/`ReservationLine` vocabulary.
- **Order <-> Payment**: Customer/Supplier. Order sends authorization requests using its domain types.
- **Order <-> Shipping**: Customer/Supplier. Order sends scheduling requests using its domain types.
- **Order -> Notification**: Published Language via Kafka. Order publishes domain events (`OrderPlaced`, `OrderConfirmed`, `OrderCancelled`) that Notification consumes and maps to its own domain model.

---

## The three-layer DDD structure

Every service follows the same layered architecture:

| Layer | Responsibility | Example |
|---|---|---|
| **Domain** | Business logic, entities, value objects, domain events, port interfaces | `Order`, `OrderId`, `Sku`, `LineItem`, `OrderPlaced`, `InventoryPort` |
| **Application** | Use cases that orchestrate domain objects and ports | `CheckoutSaga`, `ReserveStockUseCase`, `SendNotificationUseCase` |
| **Infrastructure** | Adapters implementing ports -- REST clients, Kafka producers/consumers, HTTP routes | `InventoryRestAdapter`, `OrderEventKafkaPublisher`, `OrderEventConsumer` |

Dependencies flow **inward**: Infrastructure depends on Application, Application depends on Domain. Domain depends on nothing outside itself.

---

## Observability pipeline

{% include excalidraw.html file="otel-pipeline" alt="OpenTelemetry pipeline architecture" caption="OTLP from services → Collector → Tempo/Prometheus/Loki → Grafana" %}

| Signal | Flow | Storage | Query |
|---|---|---|---|
| **Traces** | Service SDK exports spans via OTLP gRPC (:4317) to Collector, which batches and forwards to Tempo | Tempo | TraceQL in Grafana Explore |
| **Metrics** | Service SDK exports metrics via OTLP gRPC to Collector, which exposes a Prometheus scrape endpoint (:8889). Prometheus scrapes the Collector. | Prometheus | PromQL in Grafana dashboards |
| **Logs** | Service SDK exports logs via OTLP gRPC to Collector, which forwards to Loki via OTLP/HTTP | Loki | LogQL in Grafana Explore |

---

## Port mapping

### Application services

| Service | Port | Health check |
|---|---|---|
| order-service | 8080 | `GET /q/health/ready` |
| inventory-service | 8081 | `GET /q/health/ready` |
| payment-service | 8082 | `GET /q/health/ready` |
| shipping-service | 8083 | `GET /q/health/ready` |
| notification-service | 8084 | `GET /q/health/ready` |

### Infrastructure

| Component | Port | Purpose |
|---|---|---|
| Grafana | 3000 | Dashboards, Explore (traces, logs, metrics) |
| Tempo | 3200 | Trace storage HTTP API (Grafana datasource) |
| Prometheus | 9090 | Metrics storage and query |
| Loki | 3100 | Log storage and query |
| OTel Collector (gRPC) | 4317 | OTLP receiver for traces, metrics, logs |
| OTel Collector (HTTP) | 4318 | OTLP receiver (HTTP variant) |
| Kafka | 9092 | Message broker for domain events |
| PostgreSQL | 5432 | Database (available for future modules) |

---

## Docker Compose fragment composition

The infrastructure stack is assembled from reusable fragments using Docker Compose's `include` directive:

```yaml
# exercises/python/compose.yaml (same pattern for quarkus and dotnet)
include:
  - path: ../../infrastructure/_infra/compose-kafka.yaml
  - path: ../../infrastructure/_infra/compose-postgres.yaml
  - path: ../../infrastructure/_infra/compose-otel-collector.yaml
  - path: ../../infrastructure/_infra/compose-tempo.yaml
  - path: ../../infrastructure/_infra/compose-prometheus.yaml
  - path: ../../infrastructure/_infra/compose-loki.yaml
  - path: ../../infrastructure/_infra/compose-grafana.yaml

services:
  order-service:
    # ...language-specific service definition
```

This pattern means infrastructure is shared across all three language implementations. The only difference between `exercises/python/compose.yaml`, `exercises/quarkus/compose.yaml`, and `exercises/dotnet/compose.yaml` is the service build configuration.

---

## Naming conventions

| What | Convention | Example |
|---|---|---|
| Span names | `{Context}.{Operation}` | `Order.Checkout`, `Inventory.Reserve` |
| ACL span names | `{Context}.Acl.{TargetContext}{Operation}` | `Order.Acl.InventoryReserve` |
| Metric names | `{snake_case_domain_concept}_{unit}` | `checkout_outcomes_total`, `checkout_duration_seconds` |
| Metric labels | Bounded enums only | `outcome`, `tier`, `kind`, `status` |
| Domain identifiers | `{context_noun}.{field}` | `order.id`, `customer.tier`, `reservation.id` |
| Kafka topics | `{context}-{event_category}` | `order-events` |
| Service names | `{context}-service` | `order-service`, `inventory-service` |
| Docker containers | `workshop-{context}` | `workshop-order`, `workshop-inventory` |
