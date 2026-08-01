# Building Observable Domains

A hands-on workshop exploring how Domain-Driven Design and OpenTelemetry observability evolve together, creating systems that are both well-modeled and deeply transparent.

## Workshop Overview

In this 2-hour workshop, you'll work through a realistic e-commerce scenario where a "checkout" operation spans multiple bounded contexts (Inventory, Payments, Shipping, Notifications). You'll model these contexts, implement key domain events, and progressively add observability that reflects the domain model.

**Languages:** Choose one — Quarkus (Java), Python (FastAPI), or C# (.NET 10)

## Quick Start

### Option 1: GitHub Codespaces (Recommended for Workshop)

1. Fork this repository
2. Click **Code → Codespaces → Create codespace** on your preferred language branch:
   - `workshop/quarkus` — Java/Quarkus
   - `workshop/python` — Python/FastAPI
   - `workshop/dotnet` — C#/.NET 10
3. Wait for the environment to initialize (~5-10 minutes)
4. Open the [tutorial site](https://patterncatalyst.github.io/domain-driven-design-observability-workshop/) in a separate tab
5. Follow along!

### Option 2: Run Locally with Docker Compose

```bash
git clone https://github.com/patterncatalyst/domain-driven-design-observability-workshop.git
cd domain-driven-design-observability-workshop/exercises/python  # or quarkus, dotnet
docker compose up --build -d
# Wait for services to start (~60 seconds)
../../tests/verify.sh
```

## What You'll Learn

- How DDD concepts (bounded contexts, domain events, ACL) map to observability concerns
- Implementing domain-named traces and spans across distributed services
- Creating business metrics that answer domain questions
- Debugging cross-context failures using observability tools
- Sampling strategies and observability cost management

## Workshop Structure

| Module | Topic | Duration |
|--------|-------|----------|
| 0 | Introduction & Setup | 15 min |
| 1 | The Domain Landscape | 15 min |
| 2 | Domain Events & Spans | 25 min |
| 3 | Structured Observability | 20 min |
| 4 | Cross-Context Debugging | 20 min |
| 5 | Observability Economics | 15 min |
| 6 | Wrap-up & Next Steps | 10 min |

## Architecture

Five bounded contexts as microservices:

```
┌──────────┐    ┌───────────┐    ┌──────────┐
│  Order   │───▶│ Inventory │    │ Payment  │
│ (Saga)   │───▶│           │    │          │
│  :8080   │    │   :8081   │    │  :8082   │
└────┬─────┘    └───────────┘    └──────────┘
     │
     ├─────────▶┌───────────┐
     │          │ Shipping  │
     │          │   :8083   │
     │          └───────────┘
     │
     └──kafka──▶┌──────────────┐
                │ Notification │
                │    :8084     │
                └──────────────┘
```

## Observability Stack

- **OpenTelemetry Collector** — Receives traces, metrics, and logs via OTLP
- **Grafana** — Visualization and dashboards (:3000)
- **Tempo** — Distributed tracing
- **Prometheus** — Metrics
- **Loki** — Log aggregation

## References

- Vlad Khononov — *Learning Domain-Driven Design*, *Balancing Coupling in Software Design*
- Eric Evans — *Domain-Driven Design*
- Vaughn Vernon — *Implementing Domain-Driven Design*
- Alessandro Colla — *Domain-Driven Refactoring*
- Annegret Junker — *Crafting Great APIs with Domain-Driven Design*

## License

Apache License 2.0
