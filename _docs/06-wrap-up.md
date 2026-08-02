---
title: "Wrap-Up and Next Steps"
order: 6
part: "The Workshop"
description: "Review what you built, discuss production readiness, and explore where to go from here."
duration: "10 min"
label: "Module 6"
---

## What we built

Over the course of this workshop, you transformed a set of five microservices from "auto-instrumented but anonymous" to "deeply transparent in domain terms."

Here is the journey:

| Module | What we added | What it unlocked |
|---|---|---|
| **Module 0** | Environment setup, domain orientation | A running system with generic auto-instrumented traces |
| **Module 1** | Bounded context mapping | Understanding of the domain landscape -- Order, Inventory, Payment, Shipping, Notification |
| **Module 2** | Domain-named spans | Trace trees that speak the ubiquitous language (`Order.Checkout`, `Inventory.Reserve`, not `POST /api/...`) |
| **Module 3** | Structured logging, business metrics, ACL instrumentation | Logs carry `order.id` and `customer.tier`; metrics answer business questions; cross-signal correlation works |
| **Module 4** | Cross-context debugging exercise | Found a real bug using only dashboards, traces, and logs -- no source code required |
| **Module 5** | Sampling strategies, cardinality discipline | Understanding of observability costs and how to manage them in production |

---

## Key patterns to take home

### Span names should speak the ubiquitous language

`Order.Checkout` and `Order.Acl.InventoryReserve` tell you what the system is doing in domain terms. `POST /api/orders/checkout` tells you what HTTP method was used. When you are debugging at 2 AM, the domain language version is the one that helps.

### Domain events are natural span and metric boundaries

Every domain event (`OrderPlaced`, `OrderConfirmed`, `OrderCancelled`) corresponds to a span transition and a metric increment. The DDD model and the observability model reinforce each other.

### The ACL is the right place to instrument boundary crossings

The Anti-Corruption Layer translates between bounded contexts. It is also the natural place to detect drift -- when the downstream service changes its wire contract, the ACL catches it and increments `acl_drift_total` instead of corrupting the domain model.

### Context propagation across async boundaries needs explicit handling

Over HTTP, OTel context (traces and baggage) propagates automatically via headers. Over Kafka, the producer must explicitly inject `traceparent` and `baggage` into message headers, and the consumer must explicitly extract them. Module 4's bug was exactly this: the consumer side failed to read from the extracted baggage.

### Coupling dimensions guide observability investment

From Vlad Khononov's *Balancing Coupling in Software Design*: the strength, distance, and volatility of coupling between components should guide where you invest in observability. High-coupling boundaries (like the Order-to-Inventory ACL) deserve rich instrumentation. Low-coupling internal operations can rely on auto-instrumentation.

---

## Running locally after the workshop

After the workshop, you can continue experimenting with the full stack:

```bash
cd exercises/python  # or quarkus, dotnet
docker compose up --build -d

# Run the happy-path checkout test
newman run ../../tests/collections/01-checkout-happy-path.json \
  -e ../../tests/environments/local.json
```

Open Grafana at `http://localhost:3000` to explore dashboards, traces, and logs.

---

## References

### Books

- Vlad Khononov -- *Learning Domain-Driven Design* (O'Reilly, 2021)
- Vlad Khononov -- *Balancing Coupling in Software Design* (Addison-Wesley, 2024)
- Eric Evans -- *Domain-Driven Design: Tackling Complexity in the Heart of Software* (Addison-Wesley, 2003)
- Vaughn Vernon -- *Implementing Domain-Driven Design* (Addison-Wesley, 2013)
- Alessandro Colla -- *Domain-Driven Refactoring* (Independently published, 2024)
- Annegret Junker -- *Crafting Great APIs with Domain-Driven Design* (Manning, 2025)

### Online resources

- OpenTelemetry documentation: [opentelemetry.io](https://opentelemetry.io)
- Workshop repository: [github.com/patterncatalyst/domain-driven-design-observability-workshop](https://github.com/patterncatalyst/domain-driven-design-observability-workshop)

---

## What is next?

This workshop covered the foundation. Here are directions to explore:

- **Event Storming** for collaborative domain modeling -- see [Addendum A](addendum-a-event-storming.html) for a session format that produces both a domain model and an observability plan in one pass
- **Advanced patterns** including CQRS, Event Sourcing, and saga compensation -- see [Addendum B](addendum-b-advanced-patterns.html) for how observability applies to these patterns
- **gRPC transport** and ACL drift detection -- the workshop includes a gRPC variant of the Inventory adapter that can be activated via configuration
- **Production deployment** with Kubernetes, including the OpenTelemetry Operator for auto-instrumentation and the Grafana LGTM stack for managed observability
