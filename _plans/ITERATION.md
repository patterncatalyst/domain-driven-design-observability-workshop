# Iteration Plan: 30-Day Build Schedule

**Start:** 2026-08-01 (Friday)
**Target:** 2026-09-01 (Monday)
**Working days:** ~22 (weekdays)
**Repository:** github.com/patterncatalyst/domain-driven-design-observability-workshop
**Reference:** github.com/patterncatalyst/ddd-observability-workshop (existing Quarkus work)

---

## Iteration Overview

| Week | Theme | Key Deliverables |
|------|-------|------------------|
| 0 | Foundation | Repo setup, site scaffold, infra refactor, project structure |
| 1 | Quarkus Port + Python Start | Port Quarkus code to new structure, begin Python implementation |
| 2 | Python Complete + C# Start | Finish Python services, begin C# implementation, Newman tests |
| 3 | C# Complete + Content | Finish C# services, write tutorial content with codetabs |
| 4 | Polish + Ship | Codespaces testing, screenshots, presentation, final QA |

---

## Week 0: Foundation (Aug 1-3, 3 days — Fri-Sun)

### Sprint Goal
Stand up the new repo with the correct structure, migrate the Jekyll site to the cloud-native-design-patterns tutorial layout, refactor infrastructure to compose fragments, and establish the multi-language project skeleton.

### Tasks

#### W0.1 — Repo Creation & Scaffold (Day 1: Aug 1)
- [ ] Create github.com/patterncatalyst/domain-driven-design-observability-workshop
- [ ] Initialize with README, LICENSE (Apache 2.0), .gitignore
- [ ] Create directory structure per RECONCILIATION.md target state
- [ ] Copy Jekyll site scaffolding from cloud-native-design-patterns:
  - `_layouts/` (default.html, tutorial.html, part_index.html)
  - `_includes/` (codetabs.html, excalidraw.html, header.html, footer.html)
  - `assets/js/codetabs.js`
  - `assets/css/site.css` (adapt: remove Red Hat branding, neutral palette)
  - `Gemfile` (Jekyll 4.3, kramdown-parser-gfm, rouge, plugins)
- [ ] Create `_config.yml` with collections (docs, parts, example_pages, plans)
- [ ] Create card-grid homepage (index.md)
- [ ] Create `_parts/` entries for workshop sections
- [ ] Verify Jekyll builds locally: `bundle exec jekyll serve`
- [ ] Set up GitHub Pages workflow (`.github/workflows/pages.yml`)
- [ ] Enable GitHub Pages in repo settings (source: GitHub Actions)

**Acceptance:** Jekyll site builds and deploys to GitHub Pages with the homepage showing workshop section cards.

#### W0.2 — Infrastructure Refactor (Day 2: Aug 2)
- [ ] Create compose fragments in `infrastructure/_infra/`:
  - `compose-grafana.yaml` (Grafana + datasource provisioning + dashboards)
  - `compose-tempo.yaml`
  - `compose-prometheus.yaml`
  - `compose-loki.yaml`
  - `compose-otel-collector.yaml`
  - `compose-kafka.yaml`
  - `compose-postgres.yaml`
- [ ] Copy config files from reference repo:
  - `infrastructure/otel-collector/config.yaml`
  - `infrastructure/grafana/` (datasources + dashboards)
  - `infrastructure/prometheus/prometheus.yml`
  - `infrastructure/tempo/tempo.yaml`
  - `infrastructure/loki/loki.yaml`
- [ ] Verify fragments compose correctly: `docker compose -f infrastructure/_infra/compose-*.yaml up -d`
- [ ] Copy test payloads to `tests/payloads/`
- [ ] Create language-agnostic `tests/verify.sh`

**Acceptance:** `docker compose up -d` brings up all 7 infra containers; Grafana accessible at :3000 with all 5 dashboards.

#### W0.3 — Project Skeletons (Day 3: Aug 3)
- [ ] Create `exercises/quarkus/` skeleton:
  - `compose.yaml` including infra fragments + service definitions
  - `.devcontainer/` (from reference repo, adapted)
  - `pom.xml` (parent, adapted from reference)
- [ ] Create `exercises/python/` skeleton:
  - `compose.yaml` including infra fragments
  - `.devcontainer/devcontainer.json` (Python 3.14 + Docker-in-Docker)
  - `pyproject.toml` with OTel + FastAPI dependencies
  - Package structure for all 5 services
- [ ] Create `exercises/dotnet/` skeleton:
  - `compose.yaml` including infra fragments
  - `.devcontainer/devcontainer.json` (.NET 10 + Docker-in-Docker)
  - `DddWorkshop.sln` with 6 projects (5 services + shared)
  - Project structure for all 5 services
- [ ] Create Dockerfiles for each language (Red Hat UBI 10 base images)
- [ ] Create `tests/collections/00-smoke-test.json` (Newman collection)

**Acceptance:** All three language directories have valid project files. Docker builds succeed (even if services don't do anything yet). Newman smoke test validates infrastructure.

---

## Week 1: Quarkus Port + Python Start (Aug 4-8)

### Sprint Goal
Port existing Quarkus code to the new structure and begin the Python/FastAPI implementation.

### Tasks

#### W1.1 — Port Quarkus Implementation (Aug 4-5, 2 days)
- [ ] Copy all Java source from reference repo `smoke-test-pass-1` branch to `exercises/quarkus/`
- [ ] Adapt for new directory structure (paths, compose includes)
- [ ] Verify Quarkus services build: `./mvnw install -DskipTests`
- [ ] Verify compose up works: all 5 services + infra
- [ ] Run existing verify.sh and confirm green
- [ ] Drive checkout end-to-end, verify traces/metrics/logs appear
- [ ] Address outstanding audit items from reference repo reconciliation pass
- [ ] Verify all 5 Grafana dashboards populate with data

**Acceptance:** Full Quarkus checkout flow works in the new repo structure. Traces, metrics, and logs all flow correctly. 5 dashboards show data.

#### W1.2 — Python Shared Module + First Service (Aug 6-8, 3 days)
- [ ] Implement `shared_observability/` Python package:
  - `domain_context.py` — context manager for structured logging (equivalent of DomainContext)
  - `baggage_helpers.py` — OTel baggage read/write wrappers
  - `kafka_header_propagator.py` — domain identifier propagation over Kafka headers
  - `domain_identifier.py` — protocol (interface) for domain identifiers
  - `otel_setup.py` — OTel SDK configuration helper
- [ ] Implement `payment_service/` (simplest service — good for establishing patterns):
  - Domain: Authorization, AuthorizationId, AuthorizationOutcome
  - Application: authorize_payment use case
  - Infrastructure: FastAPI routes, OTel instrumentation
  - Dockerfile (UBI 10 + Python 3.14)
- [ ] Implement `shipping_service/` (second simplest):
  - Domain: Shipment, ShipmentId
  - Application: schedule_shipment use case
  - Infrastructure: FastAPI routes, OTel instrumentation
  - Dockerfile
- [ ] Verify both services start and respond to health checks
- [ ] Verify OTel spans appear in Tempo

**Acceptance:** Payment and Shipping services respond to REST requests. Traces with domain-named spans appear in Tempo. Structured logs appear in Loki.

---

## Week 2: Python Complete + C# Start (Aug 11-15)

### Sprint Goal
Complete the Python implementation. Start C# implementation. Begin Newman test collections.

### Tasks

#### W2.1 — Python Remaining Services (Aug 11-13, 3 days)
- [ ] Implement `inventory_service/`:
  - Domain: Reservation, ReservationId, ProductCode, ReservationLine, ReservationStatus
  - Application: reserve_stock use case
  - Infrastructure: FastAPI routes, gRPC server (optional), OTel instrumentation
  - Stock simulation matching Quarkus behavior (SKU-prefix-driven)
  - Dockerfile
- [ ] Implement `notification_service/`:
  - Kafka consumer (aiokafka or confluent-kafka)
  - Domain: Notification, NotificationId, NotificationKind, InboundOrderEvent
  - Application: send_notification use case
  - OTel context propagation from Kafka headers
  - Dockerfile
- [ ] Implement `order_service/`:
  - Domain: Order aggregate, value objects, domain events, sealed result types
  - Application: CheckoutSaga (saga orchestrator), REST clients to other services
  - Infrastructure: FastAPI routes, Kafka producer, ACL for Inventory
  - Baggage propagation (customer.tier)
  - Custom metrics (checkout_outcomes_total, etc.)
  - Dockerfile
- [ ] Full end-to-end checkout flow
- [ ] Verify all 5 dashboards populate with Python traffic

**Acceptance:** Full Python checkout flow works identically to Quarkus. Same API contract, same traces, same metrics, same logs. Newman smoke test passes.

#### W2.2 — Newman Test Collections (Aug 13-14, 2 days)
- [ ] Create `01-domain-landscape.json` — basic checkout, verify response shape
- [ ] Create `02-domain-events.json` — checkout + verify Tempo trace attributes
- [ ] Create `03-structured-observability.json` — checkout + verify metrics + logs
- [ ] Create `04-debugging-exercise.json` — bug scenario validation
- [ ] Create `05-observability-economics.json` — sampling verification
- [ ] Create environment files (codespaces.json, local.json)
- [ ] Validate all collections pass against both Quarkus and Python

**Acceptance:** All 6 Newman collections pass against both Quarkus and Python implementations.

#### W2.3 — C# Skeleton + First Services (Aug 14-15, 2 days)
- [ ] Implement `SharedObservability/` .NET project:
  - DomainContext, BaggageHelpers, KafkaHeaderPropagator, IDomainIdentifier
  - OTel SDK setup with ActivitySource
- [ ] Implement `PaymentService/` (ASP.NET Minimal APIs)
- [ ] Implement `ShippingService/`
- [ ] Verify both services start and produce spans in Tempo

**Acceptance:** C# Payment and Shipping services respond to REST requests with correct OTel instrumentation.

---

## Week 3: C# Complete + Content (Aug 18-22)

### Sprint Goal
Complete C# implementation. Write tutorial content with all three languages in codetabs.

### Tasks

#### W3.1 — C# Remaining Services (Aug 18-20, 3 days)
- [ ] Implement `InventoryService/`
- [ ] Implement `NotificationService/` (Kafka consumer)
- [ ] Implement `OrderService/` (saga, ACL, Kafka producer)
- [ ] Full end-to-end checkout flow
- [ ] Newman tests pass against C# implementation
- [ ] Verify all 5 dashboards populate with C# traffic

**Acceptance:** Full C# checkout flow works. All 6 Newman collections pass against all three implementations.

#### W3.2 — Tutorial Content (Aug 20-22, 3 days)
- [ ] Write Module 0: Introduction & Setup (with codetabs for language-specific setup)
- [ ] Write Module 1: Domain Landscape (mostly language-agnostic with diagram)
- [ ] Write Module 2: Domain Events & Spans (heavy codetabs — key implementation module)
- [ ] Write Module 3: Structured Observability (heavy codetabs — ACL, metrics, logs)
- [ ] Write Module 4: Cross-Context Debugging (mostly language-agnostic — observability tool usage)
- [ ] Write Module 5: Observability Economics (mostly language-agnostic — config changes)
- [ ] Write Module 6: Wrap-up & Next Steps
- [ ] Write Addendum A: Event Storming (prose, no code)
- [ ] Write Addendum B: Advanced Patterns (prose with code examples)
- [ ] Write prerequisites.md, architecture.md, troubleshooting.md

**Acceptance:** All tutorial pages render correctly with codetabs showing Quarkus, Python, and C# code. Navigation works. All links resolve.

---

## Week 4: Polish + Ship (Aug 25-29)

### Sprint Goal
Test everything end-to-end. Create the presentation. Take screenshots. Final QA.

### Tasks

#### W4.1 — Codespaces Testing (Aug 25-26, 2 days)
- [ ] Create and test Codespaces for Quarkus branch
- [ ] Create and test Codespaces for Python branch
- [ ] Create and test Codespaces for C# branch
- [ ] Verify boot time < 10 minutes for each
- [ ] Walk through all 6 modules as a participant in each language
- [ ] Fix any issues discovered during walkthrough
- [ ] Test on free-tier GitHub account (4-core machine)

**Acceptance:** All three Codespaces boot in < 10 minutes. Full workshop completable in each language.

#### W4.2 — Screenshots + Diagrams (Aug 26-27, 2 days)
- [ ] Capture Grafana screenshots for each module:
  - Module 0: Grafana home, auto-instrumented trace
  - Module 2: Domain-named trace with attributes
  - Module 3: Structured logs, Saga dashboard, cross-signal correlation
  - Module 4: Before/after debugging screenshots
  - Module 5: Cost dashboard before/after sampling
- [ ] Create/update architecture diagrams (Excalidraw → SVG):
  - E-commerce bounded context map
  - Checkout saga flow
  - OTel pipeline architecture
  - ACL translation diagram
  - Trace flow diagram (before/after domain naming)
- [ ] Embed all screenshots and diagrams in tutorial pages

**Acceptance:** All TODO screenshot placeholders replaced. All diagrams render in the site.

#### W4.3 — Presentation Deck (Aug 27, 1 day)
- [ ] Create 10-slide deck (pptxgenjs, neutral branding):
  1. Title slide
  2. The problem
  3. Workshop goals
  4. E-commerce scenario
  5. DDD concepts
  6. OTel concepts
  7. Where DDD meets OTel
  8. Workshop structure
  9. Setup instructions (QR code)
  10. Let's go / contact info
- [ ] Export to PPTX and PDF
- [ ] Review with co-presenter

**Acceptance:** 10-slide deck reviewed and exported.

#### W4.4 — CI + Final QA (Aug 28-29, 2 days)
- [ ] Create CI workflow (`.github/workflows/ci.yml`):
  - Build all three language implementations
  - Run Newman tests against each
  - Build Jekyll site
- [ ] Create starter branches:
  - `workshop/quarkus` — stripped-back starter code
  - `workshop/python` — stripped-back starter code
  - `workshop/dotnet` — stripped-back starter code
- [ ] Final end-to-end QA:
  - Fork the repo as a new GitHub user
  - Create Codespaces from each workshop/* branch
  - Follow the tutorial page-by-page
  - Verify every copy-paste block works
  - Verify every Newman collection passes at each checkpoint
  - Verify Grafana shows expected data at each module
- [ ] Create pre-workshop email template
- [ ] Update README with final instructions

**Acceptance:** CI green. All three Codespaces pass full workshop flow. Tutorial site live on GitHub Pages.

---

## Buffer: Aug 30-Sep 1 (2 working days)

Reserved for:
- Bug fixes discovered during final QA
- Content polish
- Co-presenter review feedback
- Any slipped items from Week 4

---

## Milestone Summary

| Date | Milestone | Exit Criteria |
|------|-----------|---------------|
| Aug 3 | M0: Foundation | Repo created, site scaffold deployed, infra fragments working, project skeletons in place |
| Aug 8 | M1: Quarkus + Python Started | Quarkus ported and working, Python payment+shipping services running |
| Aug 15 | M2: Python Done + C# Started | Python full checkout working, Newman tests passing, C# payment+shipping running |
| Aug 22 | M3: All Languages + Content | C# full checkout working, all Newman tests pass all 3 languages, tutorial content written |
| Aug 29 | M4: Ship-Ready | Codespaces tested, screenshots captured, presentation done, CI green, starter branches created |
| Sep 1 | M5: Final | Buffer consumed, all QA issues resolved, ready for workshop delivery |

---

## Effort Estimates

| Work Stream | Estimated Hours | Week |
|-------------|----------------|------|
| Repo + scaffold + infra refactor | 15-20h | W0 |
| Quarkus port + audit | 10-15h | W1 |
| Python implementation (5 services + shared) | 35-45h | W1-W2 |
| C# implementation (5 services + shared) | 35-45h | W2-W3 |
| Newman test collections (6 collections) | 12-18h | W2 |
| Tutorial content (8 modules + 2 addendums) | 25-35h | W3 |
| Site layout + styling | 8-12h | W0 + W3 |
| Codespaces configs + testing | 10-15h | W4 |
| Screenshots + diagrams | 8-12h | W4 |
| Presentation deck | 4-6h | W4 |
| CI + starter branches | 6-10h | W4 |
| Buffer/QA | 10-15h | W4 + buffer |
| **Total** | **~180-250h** | **30 days** |

### Parallel Work Streams (with Claude Code)
Using ultracode workflows and the relay pattern, multiple streams can run in parallel:
- Python and C# implementations can be developed concurrently
- Newman tests can be written as soon as any one implementation exists
- Tutorial content can be drafted with placeholder code and filled in as implementations complete
- Infrastructure refactor is independent of language implementations
- Presentation is independent of everything else

### Minimum Viable Workshop (if time runs short)
If 30 days proves insufficient for all three languages:
1. **Must have:** Quarkus + Python + site + Newman + Codespaces + presentation
2. **Should have:** C# implementation
3. **Nice to have:** gRPC module, advanced patterns addendum

C# can be added as a fast-follow after the workshop date if needed.

---

## Tracking

This plan lives at `_plans/ITERATION.md`. Update task status as work completes:
- `[ ]` — Not started
- `[~]` — In progress
- `[x]` — Complete
- `[-]` — Skipped/deferred

Review and update this plan at the start of each week.
