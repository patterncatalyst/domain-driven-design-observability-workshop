---
title: "Cross-Context Debugging"
order: 4
part: "The Workshop"
description: "Propagate trace context across bounded-context boundaries via HTTP and messaging, then debug a cross-service issue end-to-end."
duration: "20 min"
label: "Module 4"
---

## Learning objectives

By the end of this module you will be able to:

- Debug a cross-context failure using only observability tools (no reading source code)
- Use trace-to-log pivoting to narrow down the root cause of a distributed bug
- Understand how domain context propagation can break at async boundaries

---

## 4.1 The scenario: something is wrong

A new deployment went out last night. The on-call engineer pings you:

> "The product team built a customer engagement dashboard against our notification metrics, and the tier breakdown has been flat all week -- like nobody with a real tier has ever received a notification. But customer support says gold customers are definitely getting their order confirmations. The orders themselves seem fine. Can you take a look?"

This is a realistic production scenario: a regression that does not break functionality but **degrades observability**. Notifications are still being sent. The system is doing its job -- it is just lying about how well it is doing.

**Load the broken build first.** This regression lives on the `cp-4-broken` branch -- your track's `workshop/<lang>` branch has the correct code, so you must switch to `cp-4-broken` to reproduce it. In your terminal, from your track's exercise directory (`exercises/<lang>`, where `<lang>` is `python`, `quarkus`, or `dotnet`):

```bash
git stash                                            # park any edits from earlier modules
git checkout cp-4-broken
docker compose up --build -d notification-service    # rebuild so the regression is live
```

Then generate traffic so the anomaly has data to show (run this from `exercises/<lang>`):

```bash
newman run ../../tests/collections/04-debugging-exercise.json \
  -e ../../tests/environments/local.json
```

Wait ~30-60 seconds for Prometheus to scrape the new metrics, then start the investigation below. You will switch back to your track at the end of the module.

---

## 4.2 Investigation: dashboard anomaly

Open {% include open-grafana.html %}. Navigate to **Dashboards** in the left sidebar and open the **Checkout Saga** dashboard, and set the time range (top-right) to **Last 15 minutes**. (The traffic you generated in §4.1 populates these panels; if they look empty, rerun the §4.1 Newman command and wait ~30-60 seconds for the scrape.)

Two panels show tier breakdowns:

- **Checkout success rate by tier** -- shows four lines (BRONZE, SILVER, GOLD, PLATINUM) with healthy success rates
- **Notifications sent by tier** -- should mirror that breakdown, but instead shows all traffic labeled `tier=unknown`

The asymmetry is the first clue. The Order service knows the customer tier (the checkout metrics prove it). But by the time the data reaches the Notification service, the tier information is gone. Whatever happened, it happened **between** Order and Notification -- or **inside** Notification.

The Order-to-Notification path goes through Kafka. So either Order is not putting tier information on the message, or Notification is not reading it.

---

## 4.3 Investigation: trace inspection

First, run a checkout with a **known GOLD-tier customer** so you have one specific trace to inspect. The tier is derived from the customer-id suffix, so `cust_dave_gold` resolves to `GOLD`:

{% include checkout-curl.html cart="cart_module4_trace" customer="cust_dave_gold" %}

Note the `orderId` in the response. Then, in Grafana, navigate to **Explore** (compass icon in the left sidebar) and select **Tempo** as the data source from the dropdown at the top. Click **Search** and find the trace for that `orderId` (or open the newest trace). Click it to open the trace tree.

Walk through the spans and note the `customer.tier` attribute on each one:

| Span | `customer.tier` value |
|---|---|
| `Order.Checkout` | `GOLD` |
| `Order.Acl.InventoryReserve` | `GOLD` (via baggage) |
| `Inventory.Reserve` | `GOLD` (via baggage) |
| `Order.Payment.Authorize` | `GOLD` (via baggage) |
| `Payment.Authorize` | `GOLD` (via baggage) |
| `Order.Shipping.Schedule` | `GOLD` (via baggage) |
| `Shipping.Schedule` | `GOLD` (via baggage) |
| `Order.Events.Publish` | `GOLD` (via baggage) |
| `Notification.Consume` | `unknown` |
| `Notification.Send` | `unknown` |

The break happens at the Kafka boundary. Every synchronous HTTP span carries the correct tier via OTel baggage. But the Notification service's Kafka consumer shows `unknown`.

The diagram below shows how OTel context flows through the pipeline -- notice how the Kafka boundary is the only place where manual extraction is required.

{% include excalidraw.html file="otel-pipeline" alt="OpenTelemetry pipeline" caption="The OTel Collector sits between services and backends, propagating trace context across all transports" %}

---

## 4.4 Investigation: log correlation

Switch to **Explore > Loki** (change the data source dropdown from Tempo to **Loki**). Take the `order.id` value from the trace you just examined (e.g., `ord_a1b2c3d4...`) and run this query:

```logql
{service_namespace="workshop"} | json | order_id="ord_xxx"
```

Replace `ord_xxx` with the actual order ID from your trace.

Walk through the log lines in timestamp order:

- **order-service** logs: `customer.tier=GOLD`
- **inventory-service** logs: `customer.tier=GOLD`
- **payment-service** logs: `customer.tier=GOLD`
- **shipping-service** logs: `customer.tier=GOLD`
- **notification-service** logs: `customer.tier=unknown`

The pattern is clear. Every service that communicates synchronously (via HTTP) reads the correct tier from OTel baggage. The Notification service, which consumes asynchronously (via Kafka), does not.

---

## 4.5 The root cause

Now that you know the bug is in the Notification service's Kafka consumer, open the file for your language:

| Language | File |
|----------|------|
| Quarkus | `notification-service/src/main/java/com/example/notification/infrastructure/kafka/OrderEventConsumer.java` |
| Python | `notification_service/infrastructure/kafka_consumer.py` |
| C# | `NotificationService/Infrastructure/KafkaConsumer.cs` |

Find the line where `customer.tier` is read. On the `cp-4-broken` branch, it looks like this:

{% include codetabs.html langs="Quarkus|Python|C#" %}

```java
// notification-service/.../infrastructure/kafka/OrderEventConsumer.java

// BROKEN version (cp-4-broken branch):
String customerTier = "unknown";  // BUG: forgot to read from baggage

// The downstream effect:
span.setAttribute("customer.tier", customerTier);       // always "unknown"
useCase.send(event, customerTier);                       // metrics get tier=unknown
```

```python
# notification_service/infrastructure/kafka_consumer.py

# BROKEN version (cp-4-broken branch):
customer_tier = "unknown"  # BUG: forgot to read from baggage

# The downstream effect:
span.set_attribute("customer.tier", customer_tier)      # always "unknown"
self._use_case.send(event, customer_tier)                # metrics get tier=unknown
```

```csharp
// NotificationService/Infrastructure/KafkaConsumer.cs

// BROKEN version (cp-4-broken branch):
var customerTier = "unknown";  // BUG: forgot to read from baggage

// The downstream effect:
activity?.SetTag("customer.tier", customerTier);        // always "unknown"
_useCase.Send(orderEvent, customerTier);                 // metrics get tier=unknown
```

---

## 4.6 The fix

In the same file you opened in the previous step, replace the hardcoded line with the correct version that reads from OTel baggage.

Find and replace the broken line in your language:

- **Quarkus:** Find `String customerTier = "unknown";` and replace it with the baggage read shown below.
- **Python:** Find `customer_tier = "unknown"` and replace it with the baggage read shown below.
- **C#:** Find `var customerTier = "unknown";` and replace it with the baggage read shown below.

The correct version reads `customer.tier` from OTel baggage, which was propagated from the Order service through the Kafka message headers:

{% include codetabs.html langs="Quarkus|Python|C#" %}

> **Replace only the one hardcoded line.** The snippets below show *just* the line that
> changes. Leave every surrounding line — the `event`/`orderEvent` assignment, the
> span/activity attribute calls, and the `send(...)` call — exactly as it is. Do **not**
> paste over that whole block: the assignment line lives in the middle of it (in Python and
> C#), and deleting it breaks the consumer.

```java
// notification-service/.../infrastructure/kafka/OrderEventConsumer.java

// CORRECT version -- replace ONLY the broken line:
//   String customerTier = "unknown";
// The Quarkus OTel Kafka interceptor extracts the W3C 'baggage' header
// from the record and makes it the current context's baggage automatically.
String customerTier = BaggageHelpers.get("customer.tier");
if (customerTier == null) customerTier = "unknown";
```

```python
# notification_service/infrastructure/kafka_consumer.py

# CORRECT version -- replace ONLY the broken line:
#   customer_tier = "unknown"
# extract() call above restored OTel context from Kafka headers,
# making baggage available in the current context.
customer_tier = get_baggage("customer.tier") or "unknown"
```

```csharp
// NotificationService/Infrastructure/KafkaConsumer.cs

// CORRECT version -- replace ONLY the broken line:
//   var customerTier = "unknown";
// Propagators.DefaultTextMapPropagator.Extract() above restored OTel context
// from Kafka headers, making baggage available via Baggage.Current.
var customerTier = BaggageHelpers.Get("customer.tier") ?? "unknown";
```

The key insight: at **async boundaries** like Kafka, OTel context does not propagate automatically the way it does over HTTP. The producer must explicitly inject the `traceparent` and `baggage` headers into the Kafka message, and the consumer must explicitly extract them. In our workshop code, the producer side (`OrderEventKafkaPublisher`) does this correctly. The broken branch simply failed to read the extracted baggage on the consumer side.

---

## 4.7 Verify the fix

Rebuild and restart the notification service to pick up your code change. Because
the services run as built container images, use `--build` (a plain
`docker compose restart` would reuse the old image):

```bash
docker compose up --build -d notification-service
```

> If the tier is still wrong after the rebuild, the Docker layer cache may have reused
> the old compiled output. Force a clean rebuild with `docker compose build --no-cache
> notification-service` followed by `docker compose up -d notification-service`, then
> run a fresh checkout. See [Troubleshooting](/docs/troubleshooting/).

Run the validation tests (from your exercise directory, `exercises/<lang>`):

```bash
newman run ../../tests/collections/04-debugging-exercise.json \
  -e ../../tests/environments/local.json
```

Then verify in Grafana:

1. **Checkout Saga dashboard** -- the "Notifications sent by tier" panel now shows the same four-tier breakdown as "Checkout success rate by tier"
2. **Tempo** -- `Notification.Consume` and `Notification.Send` spans now carry the correct `customer.tier` attribute
3. **Loki** -- Notification service logs show real tier values instead of `unknown`

**Return to your track before Module 5.** You made the fix on the `cp-4-broken` branch; switch back to your track's branch (which already has the correct code) so the rest of the workshop runs against it. From `exercises/<lang>`:

```bash
git checkout -f workshop/<lang>                       # discard the throwaway fix edit; back to your track
git stash pop                                          # restore your earlier-module edits (skip if you did not stash)
docker compose up --build -d notification-service      # rebuild against your track's code
```

---

## Checkpoint

You are ready for Module 5 when:

- You found the bug using only observability tools (dashboard anomaly, then trace inspection, then log correlation)
- You understand how context propagation breaks at async boundaries (Kafka) and why the consumer must explicitly extract OTel context
- You fixed the bug and verified that tier values are restored across all three signals

### The lesson

A generic trace ID would have told you "the Notification service span has no tier." But you would not have been able to filter the metrics panel by tier in the first place -- because the `tier` metric label only exists when someone propagated `customer.tier` through baggage. The bug *and* the visibility into the bug both depend on the same piece of domain modeling discipline from Module 3.
