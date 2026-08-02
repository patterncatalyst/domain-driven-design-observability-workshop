---
title: "Event Storming for Observability"
order: 10
part: "Addendums"
description: "Run an Event Storming session that produces both a domain model and an observability plan in one pass."
label: "Addendum A"
---

The workshop you just completed is an individual, hands-on exercise. But the domain model you worked with -- the five bounded contexts, the saga, the domain events -- did not appear from nowhere. In a real project, someone had to discover those concepts. **Event Storming** is the collaborative technique most commonly used for that discovery, and it has a direct relationship to the observability patterns you learned in the workshop.

This addendum covers Event Storming as a method, walks through how the workshop's checkout domain would be discovered in a session, and explains why the artifacts of Event Storming map naturally to trace structure and instrumentation points.

---

## 1. What is Event Storming?

Event Storming is a collaborative workshop format created by **Alberto Brandolini** for rapid domain discovery. It brings together domain experts (product managers, business analysts, customer support, operations) and engineers (developers, architects, SREs) in front of a large modeling surface -- typically a long wall covered in paper -- where they map out domain events on a timeline using colored sticky notes.

The key principles:

- **Big Picture first.** Start with what happens in the system, not how it is built. Events come before code.
- **Unlimited modeling space.** Use a wall, not a whiteboard. The physical constraint of a small surface forces premature summarization. A long wall lets the timeline breathe.
- **All voices matter equally.** A support engineer who talks to customers every day often knows more about edge cases than the architect who designed the system. Event Storming flattens hierarchy by giving everyone the same sticky notes.
- **Conflict is signal.** When two people put different sticky notes for the same event, that is not a bug in the process -- it is the process working. The disagreement reveals a boundary in the domain model that needs to be made explicit.

Brandolini's insight was that most domain modeling techniques start with structure (entities, relationships, schemas) and work toward behavior. Event Storming inverts this: start with behavior (what happens?) and let structure emerge from the patterns in the timeline.

---

## 2. The Four Passes

Vlad Khononov, in *Learning Domain-Driven Design*, describes a structured approach to Event Storming that proceeds in four passes. Each pass adds a layer of understanding on top of the previous one.

### Pass 1: Domain Events (orange sticky notes)

The facilitator asks one question: **"What significant things happen in this system?"**

Everyone writes domain events on orange sticky notes and places them on the timeline. Events are written in past tense -- something that already happened. Do not filter, do not debate, do not organize. Just capture.

Examples from a checkout domain:

```
OrderPlaced    InventoryReserved    PaymentAuthorized    ShipmentScheduled
OrderCancelled InventoryUnavailable PaymentDeclined      NotificationSent
CartCreated    StockReplenished     RefundIssued         DeliveryCompleted
```

At this stage the wall is messy and full of duplicates. That is fine. The facilitator looks for **hot spots** -- clusters of sticky notes that indicate areas of complexity or disagreement. Hot spots are marked with a distinct color (often pink or red) and revisited later.

### Pass 2: Commands (blue sticky notes)

Now ask: **"What triggers each event?"**

A command is an action that someone or something initiates. Place blue sticky notes to the left of the events they trigger.

```
PlaceOrder → OrderPlaced
ReserveStock → InventoryReserved / InventoryUnavailable
AuthorizePayment → PaymentAuthorized / PaymentDeclined
ScheduleShipment → ShipmentScheduled
SendNotification → NotificationSent
```

Commands make causality explicit. An event without a clear command is a sign that either the command is implicit (a scheduled job, a timeout, a message arrival) or the event is actually a side effect that belongs to a different part of the timeline.

### Pass 3: Aggregates (yellow sticky notes)

Ask: **"What entity enforces the rules for this command?"**

An aggregate is the domain object that decides whether a command succeeds or fails. It guards invariants. Place yellow sticky notes between the commands and events they mediate.

```
PlaceOrder → [Order] → OrderPlaced
ReserveStock → [Reservation] → InventoryReserved
AuthorizePayment → [Authorization] → PaymentAuthorized
ScheduleShipment → [Shipment] → ShipmentScheduled
SendNotification → [Notification] → NotificationSent
```

Aggregates introduce vocabulary. The `Order` aggregate belongs to whoever decides the rules for placing an order. The `Reservation` aggregate belongs to whoever decides whether stock is available. These are different responsibilities -- and they are starting to look like different bounded contexts.

### Pass 4: Bounded Contexts (draw the boundaries)

The final pass draws boundaries around related aggregates. Step back from the wall and look for clusters where:

- The same vocabulary is used consistently (ubiquitous language)
- The aggregates share a common lifecycle
- Changes to one aggregate frequently require changes to another

Draw a line around each cluster. Give it a name. These are your bounded contexts.

In the checkout domain, four boundaries typically emerge naturally:

| Boundary | Aggregates inside | Events inside |
|---|---|---|
| **Order** | Order | OrderPlaced, OrderConfirmed, OrderCancelled |
| **Inventory** | Reservation | InventoryReserved, InventoryUnavailable |
| **Payment** | Authorization | PaymentAuthorized, PaymentDeclined |
| **Shipping** | Shipment | ShipmentScheduled |

A fifth boundary -- **Notification** -- emerges when the group realizes that sending confirmations is a different concern from placing orders. Notification reacts to Order's events but does not share its model.

---

## 3. Applying Event Storming to Our Checkout Domain

If you ran an Event Storming session against the workshop's domain, here is how the four passes would unfold. This section maps the abstract technique to the concrete system you already know.

### The event timeline

Lay out the orange sticky notes in the order things happen during a checkout:

```
OrderPlaced → InventoryReserved → PaymentAuthorized → ShipmentScheduled → NotificationSent
                                                                           OrderConfirmed
```

And the failure paths:

```
OrderPlaced → InventoryUnavailable → OrderCancelled → NotificationSent
OrderPlaced → InventoryReserved → PaymentDeclined → OrderCancelled → NotificationSent
```

The timeline already looks like a trace. Each event is a moment in the saga's lifecycle. The branching paths (success vs failure) correspond to different span outcomes in the trace tree.

### Commands and aggregates

| Command | Aggregate | Success Event | Failure Event |
|---|---|---|---|
| PlaceOrder | Order | OrderPlaced | (validation error) |
| ReserveStock | Reservation | InventoryReserved | InventoryUnavailable |
| AuthorizePayment | Authorization | PaymentAuthorized | PaymentDeclined |
| ScheduleShipment | Shipment | ShipmentScheduled | (timeout) |
| SendNotification | Notification | NotificationSent | (dead-letter) |

### Boundaries

The boundaries emerge from vocabulary differences:

- **Order** talks about `OrderId`, `CustomerId`, `CartId`, `Sku`, `LineItem`
- **Inventory** talks about `ReservationId`, `ProductCode`, `ReservationLine` -- notice that `Sku` became `ProductCode` and `LineItem` became `ReservationLine`
- **Payment** talks about `AuthorizationId`, `AuthorizationOutcome`, `PaymentMethod`
- **Shipping** talks about `ShipmentId`, `ShippingClass`, `EstimatedDelivery`
- **Notification** talks about `NotificationId`, `NotificationType` -- and defines its own view of Order events (`InboundOrderPlaced`, `InboundOrderConfirmed`)

The vocabulary shift between Order and Inventory is the clearest signal of a bounded context boundary. In the workshop, this is exactly where the Anti-Corruption Layer sits -- and in Module 3, it is where we placed the `Order.Acl.InventoryReserve` span.

---

## 4. Event Storming and Observability

Here is the insight this addendum exists to convey: **the artifacts of Event Storming map directly to the artifacts of domain-aware observability.**

### Every domain event is a natural span boundary

In the workshop, each domain event corresponds to a span:

| Event Storming artifact | Workshop span name |
|---|---|
| OrderPlaced (event) | `Order.Checkout` (the span that produces this event) |
| InventoryReserved (event) | `Inventory.Reserve` |
| PaymentAuthorized (event) | `Payment.Authorize` |
| ShipmentScheduled (event) | `Shipping.Schedule` |
| NotificationSent (event) | `Notification.Send` |

If you have an Event Storming timeline on the wall, you already have your span structure. The orange sticky notes become span names. The timeline becomes the trace tree.

### The event timeline IS the trace structure

Look at the trace from Module 2 again:

```
Order.Checkout
  ├── Order.Acl.InventoryReserve
  │     └── Inventory.Reserve
  ├── Order.Payment.Authorize
  │     └── Payment.Authorize
  ├── Order.Shipping.Schedule
  │     └── Shipping.Schedule
  └── Order.Events.Publish
```

This is the same sequence as the Event Storming timeline. The nesting (parent-child spans) reflects the command-event causality from Pass 2. The Order service issues commands; the downstream services execute them and produce events. The trace records this exact flow.

### Identifier flows map to correlation IDs

In Pass 3, when aggregates appear, so do their identifiers. The `Order` aggregate carries an `OrderId`. The `Reservation` aggregate carries a `ReservationId`. In the Event Storming, you can trace the `OrderId` flowing from the PlaceOrder command through every downstream event -- it appears on InventoryReserved, PaymentAuthorized, ShipmentScheduled, and NotificationSent.

This is exactly the span attribute flow from Module 2. The `order.id` attribute appears on every span in the trace because it is the identifier that ties the saga together. Event Storming makes these flows visible before anyone writes a line of code.

### Translation points map to ACL instrumentation

In Pass 4, when you draw bounded context boundaries, you can see where vocabulary changes. `Sku` becomes `ProductCode` at the Order-Inventory boundary. In the Event Storming, this shows up as a sticky note in one vocabulary on one side of the boundary and a different vocabulary on the other.

These translation points are exactly where the workshop places ACL spans (`Order.Acl.InventoryReserve`) and drift detection metrics (`acl_drift_total`). The Event Storming session identifies these points; the observability plan instruments them.

### The practical implication

If your team runs an Event Storming session before building a new domain, you can derive the following from the session artifacts:

1. **Span names** -- from domain events (Pass 1)
2. **Span hierarchy** -- from command-event causality (Pass 2)
3. **Span attributes** -- from aggregate identifiers (Pass 3)
4. **ACL instrumentation points** -- from bounded context boundaries (Pass 4)
5. **Metric dimensions** -- from the bounded enums that aggregates use (status, outcome, tier)

You walk out of the session with both a domain model and an observability plan.

---

## 5. Running Your Own Event Storming Session

### Materials

- **Physical**: a long wall (6-8 meters), butcher paper or a paper roll, sticky notes in four colors (orange, blue, yellow, plus pink for hot spots), thick markers (Sharpies -- not pens, people need to read them from across the room)
- **Remote**: Miro, Mural, or Excalidraw with a shared board. Use colored frames or shapes to simulate sticky note colors. Remote sessions work but lose some of the energy that comes from physical movement.

### Duration

- **Big Picture Event Storming**: 2-3 hours for a medium-sized domain. Larger domains may need a full day.
- **Design-Level Event Storming** (focused on one bounded context): 1-2 hours.
- The workshop you just completed is closer to a Design-Level session -- it focuses on one saga within a known set of contexts.

### Participants

- **6-12 people** is the sweet spot. Fewer than 6 and you do not get enough diverse perspectives. More than 12 and people start standing at the back instead of participating.
- The mix matters more than the count. You need at least one **domain expert** who knows the business rules (product manager, business analyst, support engineer) and at least one **engineer** who will build the system. The best sessions happen when these two groups learn from each other.
- A **facilitator** who is not a domain expert and not a developer is ideal -- they can ask naive questions that surface hidden assumptions.

### Facilitation tips

These come from Vlad Khononov's *Learning Domain-Driven Design* and from Brandolini's workshops:

- **Start with a chaotic dump.** Give everyone 5 minutes to write as many domain events as they can think of. Do not organize. Do not discuss. Just capture. This breaks the ice and fills the wall quickly.
- **Hot spots are the most valuable artifact.** When two people disagree about what an event means, or where it belongs on the timeline, mark it with a pink sticky note. These disagreements reveal real complexity in the domain -- the kind that causes bugs in production if left implicit.
- **Avoid "golden diagrams."** The temptation is to produce a clean, final model. Resist it. The value is in the conversation, not the diagram. The diagram is a side effect. If the sticky notes look messy at the end, the session probably went well.
- **Commands without clear owners are design smells.** If nobody can name the aggregate that decides whether a command succeeds, the domain model is not yet clear enough to implement.
- **Do not model the database.** Event Storming is about behavior, not storage. If the conversation drifts toward "where do we store this?", redirect to "what happens next?"

### After the session

Photograph the wall. Transcribe the events, commands, and aggregates into a digital format. The bounded context boundaries become the service boundaries in your architecture. The domain events become the span names in your observability plan. The aggregate identifiers become the span attributes and correlation IDs.

---

## 6. References

- Alberto Brandolini -- *Introducing EventStorming* (Leanpub, 2021). The definitive guide from the technique's creator.
- Vlad Khononov -- *Learning Domain-Driven Design*, Chapters 4-5 on knowledge sharing and domain analysis (O'Reilly, 2021). Khononov's four-pass approach is the clearest structured walkthrough of the technique.
- [eventstorming.com](https://www.eventstorming.com) -- Brandolini's site with articles, videos, and facilitation resources.
- Vlad Khononov -- *Balancing Coupling in Software Design* (Addison-Wesley, 2024). Chapters on strategic design show how Event Storming discoveries feed into coupling analysis.
- Nick Tune -- [Domain-Driven Architecture blog](https://medium.com/nick-tune-tech-strategy-blog). Practical articles on combining Event Storming with team topologies and context mapping.
