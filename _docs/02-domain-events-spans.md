---
title: "Domain Events as Spans"
order: 2
part: "The Workshop"
description: "Model domain events, then represent each as an OpenTelemetry span with semantic attributes."
duration: "25 min"
label: "Module 2"
---

## Learning Objectives

By the end of this module you will:

- Add domain-named spans to use cases instead of relying on generic HTTP span names
- Attach business attributes (order ID, customer tier, payment amount) to spans
- Understand domain events as first-class concepts that carry business data
- See domain-aware traces in Grafana and compare them to the generic traces from Module 1

---

**Step 1: Browse domain-named spans in use cases**

In Module 1 we saw that auto-instrumented spans are named after HTTP verbs and paths: `HTTP POST /api/orders/checkout`, `HTTP POST /api/payments/authorize`. These names describe the transport, not the business operation. When you are paged at 2 AM and staring at a trace, you want to see `Order.Checkout` and `Payment.Authorize`, not `HTTP POST`.

The fix is simple: create a span in the use case layer and give it a domain-meaningful name. Each language has its own idiom for this, but the span name is always the same.

Open the file for your language:

| Language | File |
|----------|------|
| Quarkus | `payment-service/src/main/java/com/example/payment/application/AuthorizePaymentUseCase.java` |
| Python | `payment_service/application/authorize_payment.py` |
| C# | `PaymentService/Application/AuthorizePaymentUseCase.cs` |

Find the `authorize` method. Notice the span is created with a domain name `Payment.Authorize` instead of a generic HTTP span:

{% include codetabs.html langs="Quarkus|Python|C#" %}

```java
// payment-service/.../application/AuthorizePaymentUseCase.java

@ApplicationScoped
public class AuthorizePaymentUseCase {

    private final String declineSuffix;
    private final MeterRegistry meterRegistry;

    // ...constructor omitted for brevity...

    @WithSpan("Payment.Authorize")                           // <-- domain-named span
    public Authorization authorize(AuthorizePaymentCommand command) {

        try (var ctx = DomainContext.open(
                PaymentContextKey.ORDER_ID.of(command.orderId()),
                PaymentContextKey.CUSTOMER_ID.of(command.customerId()))) {

            String tier = BaggageHelpers.get("customer.tier");
            if (tier == null) tier = "unknown";

            Span span = Span.current();
            span.setAttribute("order.id", command.orderId());
            span.setAttribute("customer.id", command.customerId());
            span.setAttribute("customer.tier", tier);
            span.setAttribute("payment.method", command.paymentMethod());
            span.setAttribute("payment.amount", command.amount().doubleValue());
            span.setAttribute("payment.currency", command.currency());

            Authorization auth = decideOutcome(command);

            span.setAttribute("authorization.id", auth.id().value());
            span.setAttribute("authorization.outcome", auth.outcome().name());

            // ...metrics and logging omitted...
            return auth;
        }
    }
}
```

```python
# payment_service/application/authorize_payment.py

tracer = trace.get_tracer(__name__)

class AuthorizePaymentUseCase:

    @tracer.start_as_current_span("Payment.Authorize")       # <-- domain-named span
    def authorize(self, command: AuthorizePaymentCommand) -> Authorization:
        span = trace.get_current_span()

        with DomainContext(
            PaymentContextKey.ORDER_ID.of(command.order_id),
            PaymentContextKey.CUSTOMER_ID.of(command.customer_id),
        ):
            tier = get_baggage("customer.tier") or "unknown"

            span.set_attribute("order.id", command.order_id)
            span.set_attribute("customer.id", command.customer_id)
            span.set_attribute("customer.tier", tier)
            span.set_attribute("payment.method", command.payment_method)
            span.set_attribute("payment.amount", command.amount)
            span.set_attribute("payment.currency", command.currency)

            authorization = self._decide_outcome(command)

            span.set_attribute("authorization.id", authorization.id.value)
            span.set_attribute("authorization.outcome", authorization.outcome.value)

            # ...metrics and logging omitted...
            return authorization
```

```csharp
// PaymentService/Application/AuthorizePaymentUseCase.cs

public class AuthorizePaymentUseCase
{
    private static readonly ActivitySource Source = new("PaymentService");

    public Authorization Authorize(AuthorizePaymentCommand command)
    {
        using var activity = Source.StartActivity("Payment.Authorize");  // <-- domain-named span

        using var ctx = new DomainContext(_logger,
            PaymentContextKey.OrderId.Of(command.OrderId),
            PaymentContextKey.CustomerId.Of(command.CustomerId));

        var tier = BaggageHelpers.Get("customer.tier") ?? "unknown";

        activity?.SetTag("order.id", command.OrderId);
        activity?.SetTag("customer.id", command.CustomerId);
        activity?.SetTag("customer.tier", tier);
        activity?.SetTag("payment.method", command.PaymentMethod);
        activity?.SetTag("payment.amount", (double)command.Amount);
        activity?.SetTag("payment.currency", command.Currency);

        var authorization = DecideOutcome(command);

        activity?.SetTag("authorization.id", authorization.Id.Value);
        activity?.SetTag("authorization.outcome", authorization.Outcome.ToString());

        // ...metrics and logging omitted...
        return authorization;
    }
}
```

Three different OTel APIs, three different languages -- but the span is always called `"Payment.Authorize"` and the attributes are always `order.id`, `customer.tier`, `authorization.outcome`. This consistency is deliberate: it means your Grafana dashboards and alerts work across all three stacks without modification.

**Key API differences:**

| Concept | Quarkus | Python | C# |
|---------|---------|--------|----|
| Create a span | `@WithSpan("Name")` | `@tracer.start_as_current_span("Name")` | `Source.StartActivity("Name")` |
| Get current span | `Span.current()` | `trace.get_current_span()` | (the `activity` variable) |
| Set attribute | `span.setAttribute("k", v)` | `span.set_attribute("k", v)` | `activity?.SetTag("k", v)` |

---

**Step 2: Browse span attributes**

Adding `span.setAttribute("order.id", orderId)` may seem small, but it unlocks powerful queries. In Grafana's Tempo, you can now search for all traces involving a specific order:

```
{ span.order.id = "ord_a1b2c3d4-..." }
```

Or find all checkouts for Platinum customers:

```
{ span.customer.tier = "PLATINUM" }
```

Or find all declined payments:

```
{ span.authorization.outcome = "DECLINED" }
```

Without these attributes, you would need to grep through logs or scan traces manually. With them, Tempo becomes a domain-aware search engine.

The checkout saga in the Order service sets attributes at a higher level -- the saga span carries the overall order context.

Open the file for your language:

| Language | File |
|----------|------|
| Quarkus | `order-service/src/main/java/com/example/order/application/CheckoutSaga.java` |
| Python | `order_service/application/checkout_saga.py` |
| C# | `OrderService/Application/CheckoutSaga.cs` |

Find the `checkout` method. Notice how business attributes are set on the span:

{% include codetabs.html langs="Quarkus|Python|C#" %}

```java
// order-service/.../application/CheckoutSaga.java

@WithSpan("Order.Checkout")
public CheckoutResult checkout(CheckoutCommand command) {
    var orderId = OrderId.generate();

    try (var ctx = DomainContext.open(
            OrderContextKey.ORDER_ID.of(orderId.value()),
            OrderContextKey.CUSTOMER_ID.of(command.customerId().value()),
            OrderContextKey.CART_ID.of(command.cartId().value()))) {

        var order = Order.place(orderId, command.customerId(),
                command.cartId(), command.lineItems());
        CustomerProfile profile = customerLookup.lookup(command.customerId());

        Span span = Span.current();
        span.setAttribute("order.id", order.id().value());
        span.setAttribute("order.value", order.total().amount().doubleValue());
        span.setAttribute("order.line_items_count", order.lineItems().size());
        span.setAttribute("customer.id", order.customerId().value());
        span.setAttribute("customer.tier", profile.tier().name());

        // Propagate customer.tier downstream as OTel baggage
        try (Scope baggageScope = BaggageHelpers.put(
                OrderContextKey.CUSTOMER_TIER.of(profile.tier().name()))) {
            return runSaga(order, profile);
        }
    }
}
```

```python
# order_service/application/checkout_saga.py

@tracer.start_as_current_span("Order.Checkout")
def checkout(self, command: CheckoutCommand) -> CheckoutResult:
    order_id = OrderId.generate()

    with DomainContext(
        OrderContextKey.ORDER_ID.of(order_id.value),
        OrderContextKey.CUSTOMER_ID.of(command.customer_id),
        OrderContextKey.CART_ID.of(command.cart_id),
    ):
        order = Order.place(order_id, ...)
        profile = self._customer_lookup.lookup(
            CustomerId.of(command.customer_id))

        span = trace.get_current_span()
        span.set_attribute("order.id", order.id.value)
        span.set_attribute("order.value", float(order.total().amount))
        span.set_attribute("order.line_items_count", len(order.line_items))
        span.set_attribute("customer.id", order.customer_id.value)
        span.set_attribute("customer.tier", profile.tier.value)

        # Propagate customer.tier downstream as OTel baggage
        set_baggage("customer.tier", profile.tier.value)
        return self._run_saga(order, profile)
```

```csharp
// OrderService/Application/CheckoutSaga.cs

public async Task<CheckoutResult> Checkout(CheckoutCommand command)
{
    var orderId = OrderId.Generate();

    using var domainContext = new DomainContext(_logger,
        OrderContextKey.OrderId.Of(orderId.Value),
        OrderContextKey.CustomerId.Of(command.CustomerId),
        OrderContextKey.CartId.Of(command.CartId));

    var order = Order.Place(orderId, ...);
    var profile = _customerLookup.Lookup(CustomerId.Of(command.CustomerId));

    using var activity = ActivitySource.StartActivity("Order.Checkout");
    activity?.SetTag("order.id", orderId.Value);
    activity?.SetTag("order.value", (double)order.Total().Amount);
    activity?.SetTag("order.line_items_count", order.TotalLineItemCount());
    activity?.SetTag("customer.id", command.CustomerId);
    activity?.SetTag("customer.tier", profile.Tier.ToValue());

    // Propagate customer.tier downstream as OTel baggage
    BaggageHelpers.Set(OrderContextKey.CustomerTier.Key, profile.Tier.ToValue());

    // ...saga steps follow...
}
```

Notice the `BaggageHelpers.put()` / `set_baggage()` / `BaggageHelpers.Set()` call. OTel **baggage** is a mechanism for propagating key-value pairs across service boundaries within a trace. The Order service sets `customer.tier` in baggage, and every downstream service (Inventory, Payment, Shipping) reads it without the tier appearing in any REST API contract. This is how the Payment service's span can include `customer.tier` even though the payment request does not carry it.

---

**Step 3: Browse domain events**

Domain events are things that happened in the domain that other parts of the system care about. In our checkout flow, the Order context publishes three events:

- **OrderPlaced** -- the customer's checkout intent has been recorded (fires before inventory/payment/shipping calls)
- **OrderConfirmed** -- all saga steps succeeded, the order is confirmed
- **OrderCancelled** -- a saga step failed, the order is cancelled (carries `failedAt` and `reason`)

Each event carries enough business data to be useful on its own -- the consumer does not need to call back to the Order service for context.

Open the file for your language:

| Language | File |
|----------|------|
| Quarkus | `order-service/src/main/java/com/example/order/domain/event/OrderPlaced.java` |
| Python | `order_service/domain/events.py` |
| C# | `OrderService/Domain/Events.cs` |

Open the `OrderPlaced` event class. Notice how it carries enough business data to be useful standalone:

{% include codetabs.html langs="Quarkus|Python|C#" %}

```java
// order-service/.../domain/event/OrderPlaced.java

public record OrderPlaced(
        UUID eventId,
        OrderId orderId,
        Instant occurredAt,
        CustomerId customerId,
        CartId cartId,
        Money total,
        int lineItemCount
) implements DomainEvent {

    public static OrderPlaced from(Order order) {
        return new OrderPlaced(
                UUID.randomUUID(),
                order.id(),
                Instant.now(),
                order.customerId(),
                order.cartId(),
                order.total(),
                order.totalLineItemCount());
    }
}
```

```python
# order_service/domain/events.py

@dataclass(frozen=True)
class OrderPlaced(DomainEvent):
    customer_id: str = ""
    cart_id: str = ""
    total_amount: float = 0.0
    total_currency: str = "USD"
    line_item_count: int = 0

    @classmethod
    def from_order(cls, order: Order) -> OrderPlaced:
        total = order.total()
        return cls(
            order_id=order.id.value,
            customer_id=order.customer_id.value,
            cart_id=order.cart_id.value,
            total_amount=float(total.amount),
            total_currency=total.currency,
            line_item_count=order.total_line_item_count(),
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "eventType": self.event_type,
            "eventId": self.event_id,
            "orderId": self.order_id,
            "occurredAt": self.occurred_at,
            "customerId": self.customer_id,
            "cartId": self.cart_id,
            "total": {
                "amount": self.total_amount,
                "currency": self.total_currency,
            },
            "lineItemCount": self.line_item_count,
        }
```

```csharp
// OrderService/Domain/Events.cs

public sealed record OrderPlaced(
    string OrderId,
    string CustomerId,
    string CartId,
    double TotalAmount,
    string TotalCurrency,
    int LineItemCount) : DomainEvent(OrderId)
{
    public static OrderPlaced FromOrder(Order order)
    {
        var total = order.Total();
        return new OrderPlaced(
            order.Id.Value,
            order.CustomerId.Value,
            order.CartId.Value,
            (double)total.Amount,
            total.Currency,
            order.TotalLineItemCount());
    }

    public override Dictionary<string, object?> ToDict() => new()
    {
        ["eventType"] = EventType,
        ["eventId"] = EventId,
        ["orderId"] = OrderId,
        ["occurredAt"] = OccurredAt,
        ["customerId"] = CustomerId,
        ["cartId"] = CartId,
        ["total"] = new Dictionary<string, object?>
        {
            ["amount"] = TotalAmount,
            ["currency"] = TotalCurrency
        },
        ["lineItemCount"] = LineItemCount
    };
}
```

Events are published to the `order-events` Kafka topic. The wire format is always camelCase JSON with an `eventType` discriminator field. The Notification service deserializes these events into its own types (`InboundOrderPlaced`, `InboundOrderConfirmed`, `InboundOrderCancelled`) -- it does not import Order's domain model. This is the Published Language pattern in action.

Domain events are natural span boundaries. When the saga publishes `OrderPlaced`, that is a span. When it publishes `OrderConfirmed`, that is another. The trace tells the full story: Order.Checkout started, OrderPlaced was published, Inventory.Reserve succeeded, Payment.Authorize succeeded, Shipping.Schedule succeeded, OrderConfirmed was published.

---

**Step 4: Browse value objects**

Every domain identifier in this workshop is a value object -- a small, immutable type that wraps a primitive and adds meaning. The `AuthorizationId` is a good example.

Open the file for your language:

| Language | File |
|----------|------|
| Quarkus | `payment-service/src/main/java/com/example/payment/domain/model/AuthorizationId.java` |
| Python | `payment_service/domain/models.py` |
| C# | `PaymentService/Domain/Models/AuthorizationId.cs` |

Open the `AuthorizationId` class. Notice the prefix pattern and the `generate` factory method:

{% include codetabs.html langs="Quarkus|Python|C#" %}

```java
// payment-service/.../domain/model/AuthorizationId.java

public record AuthorizationId(String value) {

    private static final String PREFIX = "auth_";

    public AuthorizationId {
        Objects.requireNonNull(value, "AuthorizationId value");
        if (value.isBlank())
            throw new IllegalArgumentException(
                "AuthorizationId value must not be blank");
    }

    public static AuthorizationId generate() {
        return new AuthorizationId(PREFIX + UUID.randomUUID());
    }

    @Override
    public String toString() { return value; }
}
```

```python
# payment_service/domain/models.py

@dataclass(frozen=True)
class AuthorizationId:
    """Value object wrapping a payment authorization identifier."""

    value: str
    _PREFIX = "auth_"

    @classmethod
    def generate(cls) -> AuthorizationId:
        return cls(f"{cls._PREFIX}{uuid.uuid4()}")
```

```csharp
// PaymentService/Domain/Models/AuthorizationId.cs

public record AuthorizationId(string Value)
{
    private const string Prefix = "auth_";

    public static AuthorizationId Generate() =>
        new($"{Prefix}{Guid.NewGuid()}");

    public override string ToString() => Value;
}
```

Every identifier type in the workshop follows this pattern:

| Context | Identifier | Prefix |
|---------|-----------|--------|
| Order | `OrderId` | `ord_` |
| Order | `CustomerId` | `cust_` |
| Order | `CartId` | `cart_` |
| Inventory | `ReservationId` | `res_` |
| Payment | `AuthorizationId` | `auth_` |
| Shipping | `ShipmentId` | `shp_` |
| Notification | `NotificationId` | `notif_` |

The prefixes serve two purposes. First, they make identifiers **grep-friendly** -- you can search logs for `auth_` and instantly find payment-related entries. Second, they prevent **cross-ID confusion** -- you cannot accidentally pass an `OrderId` where an `AuthorizationId` is expected, because the type system (and the prefix) catch the mistake.

When these identifiers appear as span attributes (`span.setAttribute("authorization.id", auth.id().value())`), they carry their prefix into the trace. This means you can search Tempo for `{ span.authorization.id =~ "auth_.*" }` and know you are looking at payment spans.

---

**Step 5: Run a checkout and observe the trace**

Now let's see the difference.

**Try it:** Run a checkout request to generate a domain-named trace:

```bash
curl -s -X POST http://localhost:8080/api/orders/checkout \
  -H "Content-Type: application/json" \
  -d '{
    "cartId": "cart_module2_001",
    "customerId": "cust_bob_gold",
    "lineItems": [
      {
        "sku": "SKU-MONITOR-27",
        "quantity": 2,
        "unitPrice": 299.99
      }
    ],
    "paymentMethod": "credit_card",
    "shippingClass": "express"
  }' | python3 -m json.tool
```

Or use the Newman collection:

```bash
newman run tests/collections/01-checkout-happy-path.json -e tests/environments/local.json
```

Open **Grafana > Explore > Tempo** and find the new trace. Compare it to the trace from Module 1:

{% include excalidraw.html file="trace-comparison" alt="Trace comparison: generic vs domain-named" caption="Top: generic auto-instrumented spans. Bottom: domain-named spans with business attributes." %}

**Before (generic auto-instrumentation):**
```
HTTP POST /api/orders/checkout
  ├── HTTP POST (to inventory)
  │     └── HTTP POST /api/inventory/reserve
  ├── HTTP POST (to payment)
  │     └── HTTP POST /api/payments/authorize
  ├── HTTP POST (to shipping)
  │     └── HTTP POST /api/shipments/schedule
  └── (kafka producer span)
```

**After (domain-aware instrumentation):**
```
Order.Checkout                          order.id=ord_..., customer.tier=GOLD
  ├── Order.Acl.InventoryReserve        order.id=ord_...
  │     └── Inventory.Reserve           reservation.id=res_..., status=RESERVED
  ├── Order.Payment.Authorize           order.id=ord_...
  │     └── Payment.Authorize           authorization.id=auth_..., outcome=AUTHORIZED
  ├── Order.Shipping.Schedule           order.id=ord_...
  │     └── Shipping.Schedule           shipment.id=shp_..., class=express
  └── Order.Events.Publish              event_type=OrderConfirmed
```

Click on any span to see the business attributes. The `Payment.Authorize` span now shows `customer.tier=GOLD`, `payment.amount=599.98`, `authorization.outcome=AUTHORIZED`. The trace tells a business story, not just a transport story.

---

**Step 6: Key Takeaways**

- **Span names should reflect domain operations, not HTTP verbs.** `"Payment.Authorize"` tells you what happened; `"HTTP POST"` tells you how it was transported.
- **Business attributes answer business questions from traces.** `order.id`, `customer.tier`, `authorization.outcome` make Tempo a domain-aware search engine.
- **Domain events are natural span boundaries.** Each event (OrderPlaced, OrderConfirmed, OrderCancelled) marks a significant moment in the saga's lifecycle.
- **Value objects enforce invariants at the domain level.** Prefixed identifiers (`ord_`, `auth_`, `res_`) are grep-friendly and prevent cross-ID confusion in both code and telemetry.
- **OTel baggage propagates business context across service boundaries.** The Order service sets `customer.tier`; downstream services read it without any REST API changes.

---

## Checkpoint

Before moving on, verify:

- [ ] Traces in Tempo show domain-named spans (`Order.Checkout`, `Inventory.Reserve`, `Payment.Authorize`, `Shipping.Schedule`)
- [ ] Clicking on a span reveals business attributes (`order.id`, `customer.tier`, `authorization.outcome`)
- [ ] You understand how each language creates spans: `@WithSpan` (Quarkus), `@tracer.start_as_current_span` (Python), `Source.StartActivity` (C#)
- [ ] You can explain the difference between a generic trace and a domain-aware trace
