# Product Requirements Document: Building Observable Domains Workshop

**Version:** 1.0
**Date:** 2026-08-01
**Authors:** Rob Sedor, Jeremy Davis
**Target Completion:** 2026-09-01 (~30 days)
**Repository:** github.com/patterncatalyst/domain-driven-design-observability-workshop
**Reference Repo:** github.com/patterncatalyst/domain-driven-design-observability-workshop (existing Quarkus implementation)

---

## 1. Vision

A 2-hour hands-on workshop that teaches developers how Domain-Driven Design and OpenTelemetry observability evolve together — not as bolt-on afterthoughts, but as co-designed concerns. Participants work through a realistic e-commerce checkout scenario spanning multiple bounded contexts, implementing domain events and progressively adding observability that reflects the domain model.

## 2. Conference Abstract

**Title:** Building Observable Domains — A Hands-on Journey
**Type:** Short workshop (2 hours)
**Speakers:** Rob Sedor (Senior Principal Chief Architect, Application Development), Jeremy Davis (Principal Specialist Solutions Architect)

> Observability in distributed systems is often treated as an afterthought—something we bolt on once the domain logic is "done." This hands-on session flips that approach by exploring how Domain-Driven Design and observability can evolve together, creating systems that are both well-modeled and deeply transparent.

## 3. Target Audience

**Level:** Intermediate

Developers and architects who want practical experience applying DDD principles to observability challenges. Participants should be comfortable writing code in at least one of the supported languages and have basic understanding of DDD concepts (bounded contexts, domain events). Prior experience with distributed systems helpful but not required.

## 4. Participant Experience

### 4.1 What They Bring
- Laptop with a modern browser
- Internet access
- GitHub account (free tier sufficient)

### 4.2 What They Do
1. Clone the workshop repo from github.com/patterncatalyst
2. Open GitHub Codespaces (pre-configured dev environment)
3. Open the GitHub Pages tutorial site in a separate browser tab
4. Follow along section by section — copy code from tutorial into Codespaces
5. Browse to Grafana (forwarded port in Codespaces) to observe telemetry
6. Complete exercises progressively building observability into the domain

### 4.3 What They Walk Away With
- Working code implementing DDD + observability in their chosen language
- A personal fork/clone they can revisit in Codespaces or run locally via Docker Compose
- Practical understanding of how DDD concepts (bounded contexts, domain events, ACL) map to observability concerns (traces, spans, metrics, structured logs)
- A reference implementation they can adapt for their own projects

## 5. Technical Stack

### 5.1 Languages (participant chooses one)
| Language | Framework | Runtime | Base Image |
|----------|-----------|---------|------------|
| Java | Quarkus (latest stable, currently 3.33.x) | JDK 25 | Red Hat UBI 10 |
| Python | FastAPI | Python 3.14 | Red Hat UBI 10 |
| C# | ASP.NET | .NET 10 | Red Hat UBI 10 |

### 5.2 Observability Stack (shared, language-agnostic)
| Component | Purpose | Image |
|-----------|---------|-------|
| OpenTelemetry Collector | Receive, process, export telemetry | otel/opentelemetry-collector-contrib |
| Grafana | Visualization, dashboards, Explore | grafana/grafana |
| Tempo | Distributed tracing backend | grafana/tempo |
| Prometheus (Mimir) | Metrics backend | prom/prometheus |
| Loki | Log aggregation backend | grafana/loki |
| Kafka (KRaft) | Async messaging between bounded contexts | confluentinc/cp-kafka |
| PostgreSQL | Persistence (optional, available) | postgres:16-alpine |

### 5.3 Testing
- **Newman** (CLI) as the primary test runner for API validation
- **Postman** collections available for participants who prefer the GUI
- All services expose REST APIs — no UI required
- Language-agnostic `verify.sh` script validates any implementation
- Shared Postman/Newman collections per exercise validate the same contract regardless of language

### 5.4 Infrastructure Composition
Adopt the compose fragment pattern from cloud-native-design-patterns:
```
infrastructure/
  _infra/
    compose-lgtm.yaml          # Grafana + Tempo + Loki + Prometheus
    compose-kafka.yaml          # Kafka (KRaft, no Zookeeper)
    compose-otel-collector.yaml # OTel Collector
    compose-postgres.yaml       # PostgreSQL (optional)
  otel-collector/config.yaml
  grafana/
    datasources/datasources.yaml
    dashboards/*.json
  prometheus/prometheus.yml
  tempo/tempo.yaml
  loki/loki.yaml
```

Per-language `compose.yaml` files include shared fragments:
```yaml
include:
  - path: ../../_infra/compose-lgtm.yaml
  - path: ../../_infra/compose-kafka.yaml
  - path: ../../_infra/compose-otel-collector.yaml
```

## 6. Domain Model — E-Commerce Checkout

### 6.1 Bounded Contexts (5 services)
| Context | Service | Port | Responsibility |
|---------|---------|------|----------------|
| Order | order-service | 8080 | Saga orchestrator — coordinates checkout across contexts |
| Inventory | inventory-service | 8081 | Stock reservation and availability |
| Payment | payment-service | 8082 | Payment authorization |
| Shipping | shipping-service | 8083 | Shipment scheduling |
| Notification | notification-service | 8084 | Async event consumer, sends confirmations |

### 6.2 Key DDD Concepts Demonstrated

**Strategic Design:**
- **Business Domains & Subdomains** — Core (Order orchestration), Supporting (Inventory, Shipping, Notification), Generic (Payment)
- **Ubiquitous Language** — Each context has its own vocabulary; translation happens at boundaries
- **Bounded Contexts** — Five contexts with clear semantic boundaries
- **Context Mapping** — Customer-Supplier (Order→Inventory/Payment/Shipping), ACL (Order↔Inventory translation layer)

**Tactical Design:**
- **Aggregates** — Order aggregate with transactional consistency
- **Entities** — Order, Reservation, Authorization, Shipment, Notification (identity-bearing)
- **Value Objects** — OrderId, Money, Sku, LineItem (immutable, identity-free)
- **Domain Events** — OrderPlaced, OrderConfirmed, OrderCancelled (async via Kafka)
- **Anti-Corruption Layer** — Translation between Order's vocabulary and Inventory's vocabulary
- **Ports & Adapters** — Hexagonal architecture within each bounded context

**From Khononov (Learning DDD + Balancing Coupling):**
- Subdomain classification drives design investment decisions
- Coupling dimensions (strength, distance, volatility) guide integration patterns
- ACL placement follows coupling-distance rules

**From Evans/Vernon:**
- Ubiquitous language as communication bridge
- Aggregate as consistency boundary
- Domain events as first-class modeling tool

### 6.3 Key OpenTelemetry Concepts Demonstrated

**Signals:**
- **Traces** — End-to-end request journey through all 5 services
- **Spans** — Domain-named units of work (Order.Checkout, Inventory.Reserve, etc.)
- **Metrics** — Business metrics (checkout outcomes by tier, orders in payment verification)
- **Logs** — Structured logs with domain context (order.id, customer.tier in MDC/context)
- **Baggage** — Cross-context metadata propagation (customer.tier flows through all services)

**Code & Implementation:**
- **API vs SDK** — Clean separation of instrumentation concerns
- **Instrumentation** — Manual spans with domain semantics, auto-instrumented framework spans
- **Context Propagation** — HTTP headers (W3C Trace Context) + Kafka headers (custom propagation)
- **Semantic Conventions** — Consistent attribute naming across services

**Pipeline & Infrastructure:**
- **OTel Collector** — Receivers → Processors → Exporters pipeline
- **OTLP** — Standardized telemetry transport
- **Sampling** — Head vs tail sampling tradeoffs, cost implications
- **Cross-signal correlation** — Trace↔Log linking, metric exemplars, trace-to-metrics

**Where observability fits in DDD:**
- Domain-named spans replace generic framework spans
- Business attributes on spans answer business questions
- Domain events are natural span boundaries
- ACL boundaries are natural instrumentation points
- Structured logs speak the ubiquitous language

## 7. Workshop Structure (2 Hours)

### 7.1 Section Flow
Each section follows a consistent pattern:
1. **Setup** — What to start/configure for this section
2. **Concept** — DDD and/or OTel theory with visual diagrams
3. **Code** — Hands-on implementation (copy-paste from tutorial, tabbed by language)
4. **Observe** — View results in Grafana (traces, logs, metrics, dashboards)
5. **Review** — What you learned, key takeaways
6. **Cleanup** — Any teardown before next section

### 7.2 Module Outline

| # | Title | Duration | DDD Concepts | OTel Concepts |
|---|-------|----------|--------------|---------------|
| 0 | Introduction & Setup | 15 min | Workshop overview, domain scenario | Observability stack orientation |
| 1 | The Domain Landscape | 15 min | Subdomains, bounded contexts, ubiquitous language, context mapping | Traces as domain journey maps |
| 2 | Domain Events & Spans | 25 min | Domain events, aggregates, value objects | Custom spans, span attributes, domain-named traces |
| 3 | Structured Observability | 20 min | ACL, ports & adapters, translation | Structured logs, business metrics, cross-signal correlation |
| 4 | Cross-Context Debugging | 20 min | Context boundaries, identifier translation | Trace-to-log pivoting, metric anomaly detection, baggage propagation |
| 5 | Observability Economics | 15 min | Coupling dimensions, design investment | Sampling strategies, cardinality control, cost dashboards |
| 6 | Wrap-up & Next Steps | 10 min | Strategic vs tactical review, where to go deeper | Production observability patterns |
| — | Addendum A: Event Storming | — | Collaborative domain modeling techniques | — |
| — | Addendum B: Advanced Patterns | — | CQRS, Event Sourcing, Saga compensation | Tail sampling, custom exporters |

**Total active time:** ~120 minutes

### 7.3 Section Detail

**Module 0: Introduction & Setup (15 min)**
- Instructor introduces workshop goals and the e-commerce scenario
- Participants clone repo, open Codespaces, verify stack is running
- Quick Grafana tour — dashboards, Explore, datasources
- Newman/verify.sh smoke test confirms services respond
- **Gate:** All participants have green verify output before proceeding

**Module 1: The Domain Landscape (15 min)**
- Walk through the 5 bounded contexts and their relationships
- Explain subdomain classification (core/supporting/generic)
- Show the context map — who talks to whom and how
- First trace: hit the checkout endpoint, view the auto-instrumented trace
- Identify what's missing — generic span names, no business context, opaque Kafka boundary
- **Observe:** Tempo trace view showing the "before" state

**Module 2: Domain Events & Spans (25 min)**
- Implement domain-named spans on key operations
- Add domain identifiers as span attributes (order.id, customer.tier)
- Create domain events (OrderPlaced, OrderConfirmed) with proper serialization
- Wire Kafka producer/consumer with context propagation
- Run Newman collection to generate traffic
- **Observe:** Tempo showing domain-named spans with business attributes; Loki showing structured logs with domain context

**Module 3: Structured Observability (20 min)**
- Add structured logging with domain context (MDC/context vars)
- Create business metrics (checkout_outcomes_total, orders_in_payment_verification)
- Implement the Anti-Corruption Layer between Order and Inventory
- Wire cross-signal correlation (trace→logs, metrics→traces via exemplars)
- **Observe:** Grafana dashboards showing business metrics; Loki with domain-scoped log queries; trace-to-log pivots working

**Module 4: Cross-Context Debugging (20 min)**
- Introduce a deliberate bug — customer.tier lost at the Notification boundary
- Participants use observability to find the root cause:
  - Dashboard anomaly: tier=unknown only in Notification metrics
  - Trace inspection: customer.tier missing on Notification spans
  - Log correlation: tier=unknown only in Notification logs
  - Root cause: baggage not propagated across Kafka boundary
- Fix the bug, verify via Newman + Grafana
- **Observe:** Before/after comparison showing the fix in action

**Module 5: Observability Economics (15 min)**
- Discuss coupling dimensions and how they affect observability cost
- Head sampling vs tail sampling — when and why
- Cardinality discipline — why unbounded label values are dangerous
- View the cost dashboard under load
- Change sampling configuration, observe the cost reduction
- **Observe:** Cost dashboard showing span volumes, Collector memory, export rates

**Module 6: Wrap-up & Next Steps (10 min)**
- Review the journey: from generic auto-instrumentation to domain-aware observability
- Key patterns to take home
- How to run locally with Docker Compose
- Links to reference material (Khononov, Evans, Vernon, Junker, Colla)
- Q&A

## 8. Tutorial Site

### 8.1 Technology
- **Jekyll 4.3** with custom layouts (not just-the-docs theme)
- Adopt layout and styling patterns from cloud-native-design-patterns project
- GitHub Pages deployment via GitHub Actions

### 8.2 Key Features
- **Code tabs** — Language switcher (Quarkus | Python | C#) with localStorage persistence
  - Adopt the codetabs.js mechanism from cloud-native-design-patterns
  - All code blocks immediately after a `{% include codetabs.html %}` marker
- **Copy-to-clipboard** on all code blocks
- **Part/Chapter navigation** — Two-level (Parts → Chapters) with prev/next paging
- **Card-grid homepage** — Visual landing page with workshop sections as cards
- **Responsive** — Works on tablets for in-workshop use
- **Diagrams** — Excalidraw-sourced SVGs for architecture, context maps, trace flows
- **Duration/label chips** — Time estimates and difficulty indicators per section

### 8.3 Content Format
Each module page includes:
- Frontmatter: title, order, part, duration, description, label
- Learning objectives callout
- Concept explanation with diagrams
- Tabbed code blocks (Quarkus | Python | C#) with clear file-path indicators
- "Where to put this code" instructions (exact file path, which method/class)
- Newman/curl commands to validate
- Grafana observation instructions with expected screenshots
- Key takeaways callout
- Checkpoint: what should be true before moving to the next section

## 9. Codespaces Environment

### 9.1 Requirements
- **Boot time target:** < 10 minutes from "Create codespace" to green verify
- **Machine spec:** 4-core, 8 GB RAM, 32 GB storage
- **Pre-pulled images:** All Docker images pre-pulled in post-create to avoid first-run delays

### 9.2 Language Support Strategy
Three approaches considered:

**Option A: Branch-per-language** (recommended)
- `main` — Reference solution (all languages)
- `workshop/quarkus` — Starter code for Quarkus participants
- `workshop/python` — Starter code for Python participants
- `workshop/dotnet` — Starter code for C# participants
- Each branch has its own `.devcontainer/devcontainer.json` with language-specific tooling
- Participants select their language branch when creating the codespace

**Option B: Single branch, language directories**
- All languages in one branch under `exercises/{quarkus,python,dotnet}/`
- Single devcontainer with all three runtimes
- Heavier image, longer boot time
- Pro: simpler git workflow; Con: bloated codespace

**Option C: Separate repos per language**
- github.com/patterncatalyst/ddd-workshop-quarkus
- github.com/patterncatalyst/ddd-workshop-python
- github.com/patterncatalyst/ddd-workshop-dotnet
- Pro: cleanest separation; Con: content drift between repos

### 9.3 Devcontainer Configuration (per-language)

**Quarkus:**
- Base: `mcr.microsoft.com/devcontainers/java:3-21-bookworm`
- Features: JDK 25 (SDKMAN), Docker-in-Docker
- Extensions: Java Pack, Quarkus, REST Client, Docker

**Python:**
- Base: `mcr.microsoft.com/devcontainers/python:3.14-bookworm`
- Features: Docker-in-Docker
- Extensions: Python, Pylance, REST Client, Docker

**C#:**
- Base: `mcr.microsoft.com/devcontainers/dotnet:10.0-bookworm`
- Features: Docker-in-Docker
- Extensions: C# Dev Kit, REST Client, Docker

**Common (all languages):**
- Docker Compose observability stack auto-starts via `post-start.sh`
- 14+ forwarded ports with labels (Grafana:3000, services:8080-8084, OTLP:4317/4318, Kafka:9092)
- Newman pre-installed for test running
- `verify.sh` pre-installed for smoke testing

## 10. Local Development (Docker Compose)

Participants can run the full workshop locally after the conference:
```bash
git clone https://github.com/patterncatalyst/domain-driven-design-observability-workshop
cd domain-driven-design-observability-workshop/exercises/python  # or quarkus, dotnet
docker compose up --build -d
# Wait for services to start
./verify.sh
newman run ../../tests/collections/smoke-test.json
```

### 10.1 Docker Compose Requirements
- Docker Compose v2 (not v1) — uses `include:` directive for shared infra
- Docker Desktop or Docker Engine on Linux
- ~8 GB RAM available for all containers
- Ports: 3000, 4317, 4318, 8080-8084, 9090, 9092

### 10.2 Base Images
All application containers use Red Hat UBI 10:
- `registry.access.redhat.com/ubi10/openjdk-25-runtime` (Quarkus)
- `registry.access.redhat.com/ubi10/python-314` (Python)
- `registry.access.redhat.com/ubi10/dotnet-100` (.NET 10)

## 11. Testing Strategy

### 11.1 Newman Collections
```
tests/
  collections/
    00-smoke-test.json           # Health checks, stack verification
    01-domain-landscape.json     # Module 1 validation
    02-domain-events.json        # Module 2 validation
    03-structured-observability.json  # Module 3 validation
    04-debugging-exercise.json   # Module 4 validation (before/after bug)
    05-observability-economics.json   # Module 5 validation
  environments/
    codespaces.json              # Codespaces-specific variables
    local.json                   # Local Docker Compose variables
```

### 11.2 Test Contract
All three language implementations expose identical REST APIs:
- Same endpoints, same ports, same request/response shapes
- Same container names (prefixed `workshop-`)
- Newman collections are language-agnostic — they validate the API contract, not the implementation
- `verify.sh` wraps Newman for quick validation

### 11.3 Grafana Validation
Selected Newman tests include assertions that verify:
- Traces appear in Tempo within 10 seconds of a request
- Metrics increment in Prometheus after operations
- Logs appear in Loki with expected domain attributes

## 12. Presentation

### 12.1 Format
- 10 slides, 16:9 aspect ratio
- Generated programmatically (pptxgenjs or equivalent)
- No Red Hat branding — neutral/professional theme
- PDF export for distribution

### 12.2 Slide Outline
1. Title: Building Observable Domains
2. The problem: Observability as afterthought
3. Workshop goals and what you'll build
4. The e-commerce scenario — 5 bounded contexts
5. DDD key concepts (strategic + tactical overview)
6. OpenTelemetry key concepts (signals, collector, correlation)
7. Where DDD meets observability (the intersection)
8. Workshop structure — what we'll do in 2 hours
9. Setup instructions (QR code to repo, Codespaces steps)
10. Let's get started / contact info

## 13. Reference Material

### 13.1 Primary Sources
- **Vlad Khononov** — *Learning Domain-Driven Design*, *Balancing Coupling in Software Design*
- **Eric Evans** — *Domain-Driven Design: Tackling Complexity in the Heart of Software*
- **Vaughn Vernon** — *Implementing Domain-Driven Design*, *Domain-Driven Design Distilled*
- **Alessandro Colla** — *Domain-Driven Refactoring*
- **Annegret Junker** — *Crafting Great APIs with Domain-Driven Design*
- **YouTube** — Vlad Khononov interview on DDD at Scale (Seniors @ Scale podcast)

### 13.2 OpenTelemetry
- OpenTelemetry documentation (opentelemetry.io)
- Language-specific instrumentation guides (Java, Python, .NET)
- Grafana LGTM stack documentation

## 14. Non-Goals

- **No UI/frontend** — All services are REST APIs tested via Newman/Postman
- **No persistence layer** — In-memory by design (simplifies setup, focuses on domain + observability)
- **No authentication/authorization** — Workshop scope is DDD + observability, not security
- **No production deployment** — Codespaces and local Docker Compose only
- **No Kubernetes** — Docker Compose is sufficient for the learning objectives
- **No CI/CD pipeline for participants** — Just the GitHub Actions for the tutorial site itself
- **No real payment/shipping integration** — Simulated responses with configurable outcomes
- **No Red Hat branding** — Neutral/professional styling throughout
- **No collaborative exercises in main flow** — Event storming and collaborative modeling are addendums

## 15. Success Criteria

1. A participant can go from "Create codespace" to running verify.sh in < 10 minutes
2. Every code block in the tutorial is copy-pasteable and produces the expected result
3. All three language implementations pass the same Newman test suites
4. Grafana dashboards populate with meaningful data within 30 seconds of running test traffic
5. The debugging exercise (Module 4) is solvable using only observability tools
6. The full workshop completes in ≤ 2 hours with 5 minutes of buffer
7. Local Docker Compose setup works on macOS and Linux with Docker Desktop
8. Tutorial site renders correctly and all navigation works on GitHub Pages

## 16. Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Codespaces boot time > 15 min | Delays entire workshop | Pre-pull images, optimize devcontainer, test on free-tier machines |
| Codespaces free tier limits | Some participants can't participate | Document local Docker Compose alternative; have a few spare Codespaces |
| Three language implementations drift | Newman tests pass on one language but not others | Shared Newman collections enforce contract; CI runs tests on all three |
| Observability stack resource usage | 4-core Codespace can't handle 5 services + 7 infra containers | Memory limits on all containers; test on 4-core machine; stagger service startup |
| Network issues at conference venue | Can't pull images, slow Codespaces | Pre-built Codespaces; participants can pre-provision the night before |
| 2-hour time constraint too tight | Can't cover all modules | Modules 5-6 designed as "stretch" — core value delivered in Modules 0-4 |

## 17. Deliverables Checklist

### Content
- [ ] Tutorial site (Jekyll, GitHub Pages) with all 6 modules + 2 addendums
- [ ] Code tabs for all three languages in every code block
- [ ] Architecture diagrams (Excalidraw → SVG)
- [ ] Screenshots for key Grafana views
- [ ] 10-slide presentation deck

### Code
- [ ] Quarkus implementation (5 services + shared module) — EXISTS, needs audit
- [ ] Python/FastAPI implementation (5 services)
- [ ] .NET 10/C# implementation (5 services)
- [ ] Shared observability stack (Docker Compose fragments)
- [ ] Newman/Postman test collections (per module)
- [ ] verify.sh (language-agnostic validation)
- [ ] Grafana dashboards (5 pre-provisioned)

### Infrastructure
- [ ] Devcontainer configs (one per language)
- [ ] Docker Compose for local development
- [ ] GitHub Actions for Pages deployment
- [ ] GitHub Actions for CI (build + test all three languages)
- [ ] Starter branch per language (workshop/quarkus, workshop/python, workshop/dotnet)

### Speaker Materials
- [ ] Presentation deck (10 slides, neutral branding)
- [ ] Facilitator notes per module
- [ ] Pre-workshop email template
- [ ] Printed quick-reference card for setup
