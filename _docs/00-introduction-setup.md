---
title: "Introduction and Setup"
order: 0
part: "The Workshop"
description: "Meet the workshop domain, clone the repos, and verify your local stack is running."
duration: "15 min"
label: "Module 0"
---

## Learning Objectives

By the end of this module you will:

- Understand the workshop scenario -- an e-commerce checkout flowing across five bounded contexts
- Have a running development environment (GitHub Codespaces or local Docker Compose)
- Verify the Grafana LGTM observability stack is healthy
- Submit your first checkout request and see it in Grafana

---

## 1. Welcome and Workshop Overview

This workshop teaches you to build **observable domain-driven services** -- microservices whose telemetry speaks the language of the business, not just the language of the framework. Over the next two hours you will instrument an e-commerce checkout system so that its traces, logs, and metrics answer domain questions ("Which customer tier generates the most cancelled orders?") rather than infrastructure questions ("How many HTTP 500s did we get?").

The scenario is a simplified checkout flow. A single POST request triggers a saga that crosses five bounded contexts: **Order** places the order and orchestrates the flow, **Inventory** reserves stock, **Payment** authorizes a charge, **Shipping** schedules delivery, and **Notification** sends confirmations asynchronously via Kafka. Each context is its own service, its own deployable, its own domain model.

{% include excalidraw.html file="checkout-saga-flow" alt="Checkout saga flow" caption="The checkout saga orchestrates Inventory → Payment → Shipping synchronously, then publishes events to Notification via Kafka" %}

You will work through seven modules. Each module builds on the previous one, and every concept is demonstrated in three languages simultaneously -- Java/Quarkus, Python/FastAPI, and C#/.NET -- so you can follow along in whichever stack you are most comfortable with.

---

## 2. The Multi-Language Approach

Every service in this workshop is implemented in all three stacks. The domain concepts are identical; only the idioms change. Span names, metric names, and the wire (JSON) format are the same in every language. The table below shows how the naming conventions map:

| Concept | Java/Quarkus | Python | C#/.NET |
|---------|-------------|--------|---------|
| Package/module | `com.example.payment` | `payment_service` | `PaymentService` |
| Value object | `AuthorizationId` (record) | `AuthorizationId` (frozen dataclass) | `AuthorizationId` (record) |
| Aggregate | `Authorization` (record) | `Authorization` (frozen dataclass) | `Authorization` (record) |
| Factory method | `Authorization.authorized()` | `Authorization.authorized()` | `Authorization.Authorized()` |
| Use case class | `AuthorizePaymentUseCase` | `AuthorizePaymentUseCase` | `AuthorizePaymentUseCase` |
| OTel span name | `"Payment.Authorize"` | `"Payment.Authorize"` | `"Payment.Authorize"` |
| Metric name | `payment_authorizations_total` | `payment_authorizations_total` | `payment_authorizations_total` |
| Field naming | `camelCase` | `snake_case` | `PascalCase` |
| Wire/JSON | `camelCase` | `camelCase` (via alias) | `camelCase` (via JsonPropertyName) |

The directory layout in each service follows the same three-layer DDD pattern:

```
QUARKUS                          PYTHON                          C#
payment-service/                 payment_service/                PaymentService/
├── domain/                      ├── domain/                     ├── Domain/
│   ├── model/                   │   ├── models.py               │   ├── Models/
│   │   ├── Authorization.java   │   │                           │   │   ├── Authorization.cs
│   │   ├── AuthorizationId.java │   │                           │   │   ├── AuthorizationId.cs
│   │   └── AuthorizationOut..   │   │                           │   │   └── AuthorizationOutcome.cs
│   └── identifier/              │   └── identifiers.py          │   └── Identifiers/
│       └── PaymentContextKey    │                               │       └── PaymentContextKey.cs
├── application/                 ├── application/                ├── Application/
│   ├── AuthorizePaymentCmd..    │   └── authorize_payment.py    │   ├── AuthorizePaymentCommand.cs
│   └── AuthorizePaymentUC..    │                               │   └── AuthorizePaymentUseCase.cs
└── infrastructure/              └── infrastructure/             └── Infrastructure/
    └── web/                         └── routes.py                   └── PaymentDtos.cs
        └── PaymentRestResource                                  Program.cs
```

Three things are constant across all languages:

1. **The three-layer separation** -- `domain/`, `application/`, `infrastructure/` -- keeps business logic free of framework imports.
2. **Span names and metric names are identical.** `"Payment.Authorize"` and `payment_authorizations_total` are the same string in Java, Python, and C#.
3. **The wire format is always camelCase JSON.** Each language maps its native convention (`camelCase`, `snake_case`, `PascalCase`) to camelCase on the wire via serialization annotations or aliases.

Pick the language you are most comfortable with and follow along in that track. The domain concepts and observability patterns are the same everywhere.

---

## 3. Setup: GitHub Codespaces

The fastest way to get started is with GitHub Codespaces. The repository includes a full devcontainer configuration that provisions the observability stack automatically.

**Step 1. Fork the repository**

Navigate to [github.com/patterncatalyst/domain-driven-design-observability-workshop](https://github.com/patterncatalyst/domain-driven-design-observability-workshop) and click **Fork**.

**Step 2. Create a Codespace on your language branch**

From your fork, switch to the branch for your language:

- `workshop/quarkus` for Java/Quarkus
- `workshop/python` for Python/FastAPI
- `workshop/dotnet` for C#/.NET

Click **Code > Codespaces > Create codespace on [branch]**.

**Step 3. Wait for the post-create script**

The devcontainer runs a post-create script that pulls container images and builds the services. This takes approximately 5-10 minutes on first launch. Watch the terminal for completion.

**Step 4. Services start automatically**

The `post-start.sh` script brings up the full Docker Compose stack -- all five services plus the observability infrastructure (Kafka, OpenTelemetry Collector, Grafana, Tempo, Loki, Prometheus). No manual `docker compose up` needed.

---

## 4. Setup: Local Docker Compose (Alternative)

If you prefer to work locally, clone the repository and start the stack with Docker Compose.

**Prerequisites:** Docker (or Podman) with Compose v2, at least 8 GB RAM allocated to the container runtime, and `git`.

```bash
git clone https://github.com/patterncatalyst/domain-driven-design-observability-workshop.git
cd domain-driven-design-observability-workshop/exercises/python  # or quarkus, dotnet
docker compose up --build -d
```

Wait for all containers to report healthy. You can check with:

```bash
docker compose ps
```

All five services and the infrastructure containers (kafka, otel-collector, tempo, prometheus, loki, grafana) should show `healthy` or `running`.

---

## 5. Verify Your Environment

Run the smoke test to confirm everything is wired up:

```bash
# If the verify script is available:
./tests/verify.sh

# Or use Newman (Postman CLI) with the provided collection:
newman run tests/collections/00-smoke-test.json -e tests/environments/local.json
```

You should see all checks pass. If any fail, check the [Troubleshooting](/docs/troubleshooting/) page.

---

## 6. Grafana Orientation

Open Grafana at [http://localhost:3000](http://localhost:3000) (no login required -- anonymous access is enabled).

Take a quick tour:

- **Dashboards.** The workshop ships with five pre-provisioned dashboards. You will explore these in later modules, but glance at the list now so you know what is available.
- **Explore > Tempo.** This is where you will query distributed traces. Select the "Tempo" data source and run a simple query -- even if no traces exist yet, confirm the data source is connected.
- **Explore > Loki.** Structured logs flow here via the OpenTelemetry Collector. Select "Loki" and run `{service_name="order-service"}` to confirm log ingestion is working.
- **Explore > Prometheus.** Metrics are scraped by Prometheus from the OTel Collector's Prometheus exporter. Select "Prometheus" and try `up` to see which targets are being scraped.

You do not need to understand these tools deeply yet -- we will use them progressively through the workshop.

---

## 7. Your First Request

Let's submit a checkout and see what happens end to end. Run this curl command (or use the equivalent in your HTTP client of choice):

```bash
curl -s -X POST http://localhost:8080/api/orders/checkout \
  -H "Content-Type: application/json" \
  -d '{
    "cartId": "cart_setup_001",
    "customerId": "cust_alice_silver",
    "lineItems": [
      {
        "sku": "SKU-LAPTOP-PRO",
        "quantity": 1,
        "unitPrice": 49.99
      }
    ],
    "paymentMethod": "credit_card",
    "shippingClass": "standard"
  }' | python3 -m json.tool
```

You should get back a `201 Created` response with a JSON body containing:

- `status`: `"CONFIRMED"`
- `orderId`: a generated ID like `ord_a1b2c3d4-...`
- `reservationId`, `authorizationId`, `shipmentId`: IDs from each downstream service

This single request triggered a saga across all five bounded contexts. In the next module we will look at the trace this generated and understand the domain landscape it traversed.

---

## Checkpoint

Before moving on, verify:

- [ ] `docker compose ps` (or Codespaces terminal) shows all services healthy
- [ ] Grafana is accessible at [http://localhost:3000](http://localhost:3000)
- [ ] The Tempo, Loki, and Prometheus data sources are connected in Grafana > Explore
- [ ] Your first checkout request returned `201` with a `CONFIRMED` status
