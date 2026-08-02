---
title: "The Domain Landscape"
order: 1
part: "The Workshop"
description: "Map bounded contexts and identify the aggregates, entities, and value objects in an order-management domain."
duration: "15 min"
label: "Module 1"
---

## Learning Objectives

By the end of this module you will:

- Understand bounded contexts and why they matter for observability
- Map the relationships between the five contexts in our checkout domain
- See what auto-instrumented traces look like before domain-aware instrumentation
- Identify what is missing in generic traces and what we will fix in the rest of the workshop

---

## 1. What is Domain-Driven Design?

Domain-Driven Design (DDD) is a software design approach that puts the business domain at the center of every architectural decision. It was codified by Eric Evans and later refined by Vaughn Vernon and Vlad Khononov, among others. DDD is not a framework or a technology -- it is a way of thinking about where complexity lives and how to manage it.

DDD operates at two levels. **Strategic design** deals with the big picture: how do you divide a large system into bounded contexts, each with its own ubiquitous language and clear boundaries? What are the relationships between those contexts -- who is upstream, who is downstream, where do you need an anti-corruption layer? **Tactical design** deals with the internals of a single bounded context: how do you model the domain using aggregates, entities, value objects, and domain events?

This workshop uses both levels. The five services in our checkout system are five bounded contexts (strategic design), and within each context we use aggregates, value objects, and domain events (tactical design). But here is the key insight this workshop adds: **your observability should reflect both levels too.** Traces should be named after domain operations, not HTTP verbs. Metrics should be bucketed by business dimensions, not just status codes. Logs should carry domain identifiers, not just request IDs.

---

## 2. Our Domain: E-Commerce Checkout

The workshop models a simplified e-commerce checkout as a **saga** — a sequence of coordinated operations across multiple bounded contexts.

{% include excalidraw.html file="bounded-context-map" alt="Bounded Context Map" caption="Five bounded contexts and their integration relationships" %}

Here are the five contexts and their roles:

### Order Context (Core Subdomain)

The saga orchestrator. This is the competitive advantage -- the logic that decides how a checkout flows, what happens on failure, and how to compensate. The Order context owns the `Order` aggregate and publishes domain events (`OrderPlaced`, `OrderConfirmed`, `OrderCancelled`) to Kafka.

### Inventory Context (Supporting Subdomain)

Handles stock reservation. When the Order context asks to reserve stock, Inventory checks availability and returns a `Reservation` with a status of `RESERVED`, `PARTIALLY_RESERVED`, or `UNAVAILABLE`. Inventory uses its own vocabulary -- what Order calls a `Sku`, Inventory calls a `ProductCode`. The translation happens in an anti-corruption layer (ACL) inside the Order service's infrastructure layer.

### Payment Context (Generic Subdomain)

Authorizes a charge. In a real system this might be an off-the-shelf payment gateway. Payment returns an `Authorization` with an outcome of `AUTHORIZED`, `DECLINED`, or `FAILURE`. The vocabulary aligns closely with Order's, so no ACL is needed -- a thin REST adapter is sufficient.

### Shipping Context (Supporting Subdomain)

Schedules delivery. Returns a `Shipment` with an estimated delivery date based on the shipping class (overnight, express, priority, standard). Like Payment, the vocabulary aligns closely with Order's, so a thin adapter works.

### Notification Context (Supporting Subdomain)

An asynchronous event consumer. Notification listens to the `order-events` Kafka topic and sends customer-facing notifications (order acknowledgment, confirmation, or cancellation). It defines its own view of Order's events -- `InboundOrderPlaced`, `InboundOrderConfirmed`, `InboundOrderCancelled` -- as a Published Language boundary.

### Context Map

The relationships between these contexts follow well-known DDD patterns:

- **Order to Inventory**: Customer-Supplier with an Anti-Corruption Layer. Order is the customer; Inventory is the supplier. The ACL in Order's infrastructure layer translates between `Sku` and `ProductCode`.
- **Order to Payment**: Customer-Supplier (thin adapter). The vocabularies are close enough that no ACL is needed.
- **Order to Shipping**: Customer-Supplier (thin adapter). Same reasoning as Payment.
- **Order to Notification**: Published Language via Kafka. Order publishes domain events in a shared wire format. Notification deserializes them into its own types.

---

## 3. The Three-Layer Architecture

Each bounded context follows the same internal architecture -- a hexagonal (ports and adapters) pattern organized into three layers:

- **Domain** -- Pure business logic. No framework imports, no HTTP, no Kafka, no JSON. Contains aggregates, value objects, enums, domain events, and port interfaces (outbound contracts).
- **Application** -- Use cases that orchestrate domain logic. Each use case is a single class with a single public method. This is where OpenTelemetry spans are created and domain context is established.
- **Infrastructure** -- Adapters that implement the ports. REST clients, Kafka producers/consumers, in-memory lookups. This is where wire translation happens.

The Order aggregate -- the central domain object in the Order context -- illustrates how each language expresses the same concepts:

{% include codetabs.html langs="Quarkus|Python|C#" %}

```java
// order-service/src/.../domain/model/Order.java
public record Order(
        OrderId id,
        CustomerId customerId,
        CartId cartId,
        List<LineItem> lineItems,
        OrderStatus status,
        Instant placedAt,
        String cancelReason
) {
    /** Place a new order. Returns an Order in PLACED status. */
    public static Order place(OrderId id, CustomerId customerId,
                              CartId cartId, List<LineItem> lineItems) {
        return new Order(id, customerId, cartId, lineItems,
                OrderStatus.PLACED, Instant.now(), null);
    }

    /** Transition to CONFIRMED. Legal only from PLACED. */
    public Order confirm() {
        if (status != OrderStatus.PLACED)
            throw new IllegalStateException(
                "Cannot confirm order in status " + status);
        return new Order(id, customerId, cartId, lineItems,
                OrderStatus.CONFIRMED, placedAt, null);
    }

    /** Transition to CANCELLED. Legal only from PLACED. */
    public Order cancel(String reason) {
        if (status != OrderStatus.PLACED)
            throw new IllegalStateException(
                "Cannot cancel order in status " + status);
        return new Order(id, customerId, cartId, lineItems,
                OrderStatus.CANCELLED, placedAt, reason);
    }
}
```

```python
# order_service/domain/models.py
@dataclass(frozen=True)
class Order:
    id: OrderId
    customer_id: CustomerId
    cart_id: CartId
    line_items: tuple[LineItem, ...]
    status: OrderStatus
    placed_at: str
    cancel_reason: str | None = None

    @classmethod
    def place(cls, order_id, customer_id, cart_id, line_items):
        return cls(
            id=order_id, customer_id=customer_id,
            cart_id=cart_id,
            line_items=tuple(line_items),
            status=OrderStatus.PLACED,
            placed_at=datetime.now(timezone.utc).isoformat(),
        )

    def confirm(self) -> Order:
        if self.status is not OrderStatus.PLACED:
            raise ValueError(f"Cannot confirm order in {self.status}")
        return replace(self, status=OrderStatus.CONFIRMED)

    def cancel(self, reason: str) -> Order:
        if self.status is not OrderStatus.PLACED:
            raise ValueError(f"Cannot cancel order in {self.status}")
        return replace(self, status=OrderStatus.CANCELLED,
                       cancel_reason=reason)
```

```csharp
// OrderService/Domain/Models.cs
public sealed record Order(
    OrderId Id,
    CustomerId CustomerId,
    CartId CartId,
    IReadOnlyList<LineItem> LineItems,
    OrderStatus Status,
    DateTime PlacedAt,
    string? CancelReason = null)
{
    public static Order Place(
        OrderId orderId, CustomerId customerId,
        CartId cartId, IReadOnlyList<LineItem> lineItems) =>
        new(orderId, customerId, cartId, lineItems,
            OrderStatus.Placed, DateTime.UtcNow);

    public Order Confirm()
    {
        if (Status != OrderStatus.Placed)
            throw new IllegalStateException(
                $"Cannot confirm order in {Status.ToValue()} status");
        return this with { Status = OrderStatus.Confirmed };
    }

    public Order Cancel(string reason)
    {
        if (Status != OrderStatus.Placed)
            throw new IllegalStateException(
                $"Cannot cancel order in {Status.ToValue()} status");
        return this with { Status = OrderStatus.Cancelled,
                           CancelReason = reason };
    }
}
```

Notice the pattern across all three languages:

- **Immutable aggregates.** State transitions (`confirm()`, `cancel()`) return new instances rather than mutating in place. In Java this is a new record constructor call, in Python it is `dataclasses.replace()`, and in C# it is the `with` expression.
- **Factory methods.** `Order.place()` / `Order.Place()` encapsulates the initial state. You cannot create an Order in an invalid state.
- **Guard clauses.** Each transition validates the current state. You cannot confirm a cancelled order or cancel a confirmed one.
- **No framework dependencies.** The domain layer imports nothing from Quarkus, FastAPI, or ASP.NET. It is pure business logic.

---

## 4. Your First Trace

You submitted a checkout request in Module 0. Now let's find the trace it generated.

1. Open **Grafana** at [http://localhost:3000](http://localhost:3000).
2. Navigate to **Explore** (compass icon in the sidebar).
3. Select **Tempo** as the data source.
4. In the query type dropdown, select **Search**.
5. Set the **Service Name** filter to `order-service`.
6. Click **Run query**.

You should see at least one trace from your checkout. Click on it to open the trace detail view.

### What the auto-instrumented trace looks like

Without any domain-aware instrumentation, the trace contains spans generated automatically by the OpenTelemetry framework instrumentation:

- `HTTP POST /api/orders/checkout` -- the inbound request to the Order service
- `HTTP POST` -- outbound calls from Order to Inventory, Payment, and Shipping
- A Kafka producer span for the event publication
- `HTTP POST /api/inventory/reserve`, `HTTP POST /api/payments/authorize`, `HTTP POST /api/shipments/schedule` -- the inbound requests at each downstream service

The spans are named after HTTP methods and paths. The attributes are HTTP-level: `http.method`, `http.status_code`, `http.url`. If you click on a span, the attributes tell you about the transport, not about the business operation.

---

## 5. What's Missing?

The auto-instrumented trace tells you that a request flowed through five services. That is useful for latency analysis and error detection. But it does not answer any domain questions:

- **What domain operation happened?** The span says `HTTP POST /api/orders/checkout`. Was this a new checkout? A retry? A return? The span name does not say.
- **What business entity was involved?** Which order? Which customer? Which cart? None of these appear in the span attributes.
- **What was the business outcome?** The HTTP status was 201 -- but was the order confirmed or cancelled? A cancelled order also returns a response (with a 422 status), but you have to parse the body to know.
- **What is the customer tier?** The same checkout may behave differently for a Platinum customer versus a Bronze customer. The trace has no way to distinguish them.
- **How do I correlate logs across services for this specific order?** Each service logs independently. Without a shared domain identifier (`order.id`), correlating logs requires matching on the trace ID -- which works, but does not answer domain questions directly.

This is the gap between **infrastructure observability** (what the framework gives you for free) and **domain observability** (what you need to operate the business). Closing this gap is what the rest of the workshop is about.

In Module 2, we will add domain-named spans, attach business attributes, and introduce domain events as first-class observable concepts.

---

## Checkpoint

Before moving on, verify:

- [ ] You can navigate to Grafana > Explore > Tempo and find a trace from your checkout
- [ ] You understand the five bounded contexts and their relationships (Order orchestrates; Inventory, Payment, Shipping are called synchronously; Notification consumes events asynchronously)
- [ ] You can articulate the gap: auto-instrumented traces tell you *that* requests flowed, but not *what* domain operation happened or *why*
