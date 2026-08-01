# Reconciliation Plan: What Exists vs What's Needed

**Date:** 2026-08-01
**Baseline:** `smoke-test-pass-1` branch of ddd-observability-workshop (reference repo)
**New Repo:** github.com/patterncatalyst/domain-driven-design-observability-workshop

---

## 1. Executive Summary

The existing workshop is **~60% complete for Java/Quarkus** and **~0% for Python and C#**. The domain model, observability stack, Grafana dashboards, and 7 modules of tutorial content all exist and have been through a rigorous smoke-test and audit pass. The major work items are:

1. **Python/FastAPI implementation** of all 5 services (NEW)
2. **C#/.NET 10 implementation** of all 5 services (NEW)
3. **Site migration** from just-the-docs to the cloud-native-design-patterns tutorial layout with codetabs (REWRITE)
4. **Newman/Postman test collections** (NEW)
5. **Infrastructure restructuring** to compose-fragment pattern (REFACTOR)
6. **Workshop restructuring** from 7 modules / 3h 20m to 6 modules / 2h (RESTRUCTURE)
7. **Presentation deck** (NEW)
8. **Remaining Quarkus audit items** from smoke-test-pass-1 (FINISH)

---

## 2. What Exists (Current State)

### 2.1 Domain Model — COMPLETE (Quarkus)
All five bounded contexts are fully implemented with production-quality code:

| Service | Files | Lines | Status |
|---------|-------|-------|--------|
| order-service | 41 Java | ~2500 | Complete — saga, ACL, gRPC/REST adapters, Kafka producer |
| inventory-service | 12 Java | ~800 | Complete — REST + gRPC server, stock simulation |
| payment-service | 7 Java | ~400 | Complete — authorization simulation |
| shipping-service | 6 Java | ~350 | Complete — shipment scheduling (always succeeds) |
| notification-service | 12 Java | ~700 | Complete — Kafka consumer, notification sending |
| shared-observability | 4 Java | ~250 | Complete — DomainContext, BaggageHelpers, KafkaHeaderPropagator |

**Key DDD patterns implemented:** Hexagonal architecture (ports/adapters), aggregates with invariants, value objects with validation, sealed domain events, anti-corruption layer with drift detection, saga orchestration.

### 2.2 OpenTelemetry Integration — COMPLETE (Quarkus)
- 11 `@WithSpan` annotations with domain-named spans
- Business span attributes (order.id, order.value, customer.tier, etc.)
- Baggage propagation (customer.tier flows cross-context)
- Custom Micrometer metrics (checkout_outcomes_total, checkout_duration_seconds, etc.)
- KafkaHeaderPropagator for domain identifiers across async boundary
- DomainContext (MDC scope manager) for structured logging
- All services configured for OTLP push to Collector

### 2.3 Observability Stack — COMPLETE
Docker Compose with 7 containers:
- Kafka (KRaft), PostgreSQL, OTel Collector, Tempo, Prometheus, Loki, Grafana
- All have healthchecks, depends_on ordering, named volumes, memory limits
- OTel Collector configured with OTLP receivers, batch/memory-limit processors, exporters to Tempo/Prometheus/Loki
- Tail-sampling processor defined (activated in Module 6)

### 2.4 Grafana Dashboards — COMPLETE
5 pre-provisioned dashboards (843 lines of JSON):
1. Service Health (golden signals)
2. Checkout Saga (business metrics by tier)
3. Trace Explorer (Tempo search shortcuts)
4. Observability Cost (Collector span volume, memory, export rates)
5. Transport Comparison (REST vs gRPC, Module 5)

Cross-signal correlation configured: Prometheus→Tempo exemplars, Tempo→Loki trace-to-logs, Loki label filters.

### 2.5 Codespaces — MOSTLY COMPLETE (Quarkus only)
- `.devcontainer/devcontainer.json` — JDK 25 via SDKMAN, Docker-in-Docker, 4-core/8GB/32GB
- `post-create.sh` — installs tools, pre-pulls images, graceful failure
- `post-start.sh` — auto-starts observability stack
- 14 forwarded ports with labels
- 8 VS Code extensions
- **Gap:** Only supports Java/Quarkus. No Python or C# devcontainer configs.

### 2.6 Tutorial Content — COMPLETE (7 modules, needs restructure)
All written in Markdown with `just-the-docs` Jekyll theme:

| Current Module | Lines | Status |
|----------------|-------|--------|
| 01-setup.md | ~200 | Complete |
| 02-eventstorming.md | ~300 | Complete |
| 03-observable-events/ (3 checkpoints) | ~900 | Complete |
| 04-debugging.md | ~250 | Complete |
| 05-rest-vs-grpc.md | ~270 | Complete |
| 06-sampling-performance.md | ~200 | Complete |
| 07-reflection.md | ~200 | Complete |
| architecture.md | ~200 | Complete |
| prerequisites.md | ~100 | Complete |
| troubleshooting.md | ~395 | Complete |
| cost-estimation.md | ~100 | Complete |

**Gap:** Content is Java-only. No code tabs. No Python/C# code examples. Uses just-the-docs theme, not the cloud-native-design-patterns tutorial layout.

### 2.7 Scripts — COMPLETE
- start-services.sh, restart-services.sh, verify.sh
- Load test scripts (light, heavy, with-bug, rest, grpc, compare-wire)
- Test payloads (happy path, out-of-stock, payment decline, gold tier)
- Retrograde branch generation tools

### 2.8 CI/CD — PARTIAL
- `.github/workflows/pages.yml` — Jekyll build + deploy (exists, not yet deployed)
- **Gap:** No CI for building/testing the three language implementations
- **Gap:** GitHub Pages not yet enabled in repo settings

### 2.9 Reference Projects — AVAILABLE

**cloud-native-design-patterns (patterncatalyst):**
- Jekyll 4.3 tutorial site with codetabs mechanism — ready to adopt
- Compose fragment pattern in `_infra/` — ready to adopt
- Multi-language example structure — ready to adopt
- Unified API contract + shared verification — ready to adopt

**zero-cve-hummingbird-showroom:**
- Antora-based (different SSG) — can borrow interactive patterns but not structure
- Copy-to-clipboard, conditional content, attribute substitution — implementable in Jekyll

---

## 3. Gap Analysis

### 3.1 NEW — Python/FastAPI Implementation

**Effort:** HIGH (estimated 40-50 hours)

Must implement all 5 services matching the Quarkus API contract exactly:

| Service | Key Implementation Challenges |
|---------|-------------------------------|
| order-service | Saga orchestration, Kafka producer, REST clients to other services, gRPC client (optional for Module 5) |
| inventory-service | REST server, gRPC server (optional), stock simulation |
| payment-service | REST server, authorization simulation |
| shipping-service | REST server, shipment scheduling |
| notification-service | Kafka consumer, notification sending |
| shared-observability | OTel SDK setup, context propagation, structured logging |

**Python Stack:**
- FastAPI + Uvicorn
- opentelemetry-api + opentelemetry-sdk
- opentelemetry-instrumentation-fastapi (auto-instrumentation)
- aiokafka or confluent-kafka-python
- grpcio + protobuf (for Module 5 gRPC variant)
- structlog or python-json-logger for structured logging
- prometheus-client or opentelemetry-exporter-prometheus for metrics

**Key decisions:**
- Async (asyncio) vs sync — FastAPI is async-native; use async for idiomatic Python
- OTel instrumentation approach — combination of auto-instrumentation + manual spans
- Domain modeling — dataclasses or Pydantic models for value objects, protocols for ports

### 3.2 NEW — C#/.NET 10 Implementation

**Effort:** HIGH (estimated 40-50 hours)

Same 5 services matching the same API contract:

**C# Stack:**
- ASP.NET Minimal APIs or Controllers
- OpenTelemetry.Api + OpenTelemetry.Extensions.Hosting
- OpenTelemetry.Instrumentation.AspNetCore (auto-instrumentation)
- Confluent.Kafka for Kafka
- Grpc.AspNetCore for gRPC (Module 5)
- Microsoft.Extensions.Logging with JSON formatter
- System.Diagnostics.Metrics for custom metrics

**Key decisions:**
- Minimal APIs vs Controllers — Minimal APIs for brevity in a workshop
- OTel instrumentation — ActivitySource API (native .NET) + OTel bridge
- Domain modeling — Records for value objects, classes for aggregates
- Sealed interfaces — C# doesn't have sealed interfaces; use abstract record hierarchies

### 3.3 REWRITE — Tutorial Site

**Effort:** MEDIUM-HIGH (estimated 25-35 hours)

**From:** just-the-docs Jekyll theme with Java-only content
**To:** Custom Jekyll 4.3 tutorial layout with codetabs (3 languages)

Tasks:
1. **Copy layouts from cloud-native-design-patterns:**
   - `_layouts/default.html` — base shell with fonts, header, footer, codetabs.js
   - `_layouts/tutorial.html` — breadcrumbs, chapter marker, duration chips, prev/next
   - `_layouts/part_index.html` — Part landing page with chapter cards
2. **Copy includes:**
   - `_includes/codetabs.html` — tab marker
   - `_includes/excalidraw.html` — diagram renderer
   - `_includes/header.html`, `_includes/footer.html`
3. **Copy assets:**
   - `assets/js/codetabs.js` — tab switching with localStorage persistence
   - `assets/css/site.css` — full stylesheet (adapt colors/branding)
4. **Restructure content:**
   - Move from `docs/modules/*.md` to `_docs/*.md` with proper frontmatter
   - Create `_parts/` collection for workshop sections
   - Add `{% include codetabs.html langs="Quarkus|Python|C#" %}` before every code block
   - Rewrite code blocks to show all three languages
5. **Add new content:**
   - Card-grid homepage
   - Prerequisites page with language-specific setup
   - Each module gets Python and C# code examples alongside Java
6. **Update _config.yml:**
   - Collections: docs, parts, example_pages, plans
   - Plugins: jekyll-seo-tag, jekyll-sitemap
   - URL: https://patterncatalyst.github.io
   - Baseurl: /ddd-observability-workshop

### 3.4 NEW — Newman/Postman Test Collections

**Effort:** MEDIUM (estimated 15-20 hours)

Create collections that validate the API contract regardless of language:

| Collection | Tests | Purpose |
|------------|-------|---------|
| 00-smoke-test | 15-20 | Health endpoints, stack connectivity, Grafana datasources |
| 01-domain-landscape | 10-15 | Basic checkout flow, verify trace appears in Tempo |
| 02-domain-events | 15-20 | Domain events, span attributes, structured logs |
| 03-structured-observability | 15-20 | Metrics, ACL, cross-signal correlation |
| 04-debugging | 10-15 | Bug scenario, verify symptoms, verify fix |
| 05-economics | 5-10 | Sampling verification, cost metrics |

Plus environment files for Codespaces vs local.

### 3.5 REFACTOR — Infrastructure to Compose Fragments

**Effort:** LOW-MEDIUM (estimated 8-12 hours)

**From:** Single `infrastructure/docker-compose.yml` with all 7 containers
**To:** Composable fragments in `infrastructure/_infra/`:

```
infrastructure/
  _infra/
    compose-grafana.yaml        # Grafana + datasource provisioning + dashboards
    compose-tempo.yaml          # Tempo
    compose-prometheus.yaml     # Prometheus
    compose-loki.yaml           # Loki
    compose-otel-collector.yaml # OTel Collector
    compose-kafka.yaml          # Kafka (KRaft)
    compose-postgres.yaml       # PostgreSQL (optional)
  otel-collector/config.yaml    # KEEP as-is
  grafana/                      # KEEP as-is
  prometheus/                   # KEEP as-is
  tempo/                        # KEEP as-is
  loki/                         # KEEP as-is
```

Per-language compose files:
```
exercises/
  quarkus/compose.yaml          # Includes fragments + defines 5 Quarkus services
  python/compose.yaml           # Includes fragments + defines 5 Python services
  dotnet/compose.yaml           # Includes fragments + defines 5 .NET services
```

### 3.6 RESTRUCTURE — Workshop Modules (7 → 6, 3h20m → 2h)

**Effort:** MEDIUM (estimated 10-15 hours)

Current structure (3h 20m):
```
Module 1: Setup & Orientation (30 min)
Module 2: EventStorming the Checkout (30 min) — COLLABORATIVE
Module 3: Observable Domain Events (45 min, 3 checkpoints)
Module 4: Cross-Context Debugging (30 min)
Module 5: REST vs gRPC / ACL (30 min)
Module 6: Sampling & Performance (25 min)
Module 7: Reflection (10 min)
```

New structure (2h):
```
Module 0: Introduction & Setup (15 min) — COMPRESSED from Module 1
Module 1: Domain Landscape (15 min) — NEW, overview + first trace
Module 2: Domain Events & Spans (25 min) — CONDENSED from Module 3a+3b
Module 3: Structured Observability (20 min) — CONDENSED from Module 3c + parts of Module 5
Module 4: Cross-Context Debugging (20 min) — KEPT from Module 4
Module 5: Observability Economics (15 min) — CONDENSED from Module 6
Module 6: Wrap-up (10 min) — CONDENSED from Module 7
Addendum A: Event Storming — MOVED from Module 2
Addendum B: Advanced Patterns — MOVED from Module 5 (REST/gRPC) + future modules
```

**Key changes:**
- EventStorming moved to addendum (collaborative exercises don't fit individual workshop)
- REST vs gRPC ACL content partially absorbed into Module 3 (ACL concept), partially to addendum
- Setup compressed from 30 min to 15 min (pre-pull images, faster verify)
- New Module 1 provides domain overview before coding starts
- Module 3 checkpoints merged into two larger modules (2+3)

### 3.7 NEW — Presentation Deck

**Effort:** LOW (estimated 4-6 hours)

10 slides, programmatically generated, neutral branding. Can use pptxgenjs pattern from cloud-native-design-patterns.

### 3.8 FINISH — Outstanding Quarkus Items

**Effort:** MEDIUM (estimated 15-20 hours)

From the existing reconciliation-pass.md:
- [ ] Screenshots (22 placeholders) — coordinate capture session
- [ ] Retrograde checkpoint branches (cp-0 through cp-7) — run generation scripts
- [ ] Smoke test: verify cp-4-broken produces expected symptoms
- [ ] GitHub Pages publication (enable in repo settings)
- [ ] Speaker script for facilitation
- [ ] Remaining cleanup nits (8 items)
- [ ] Port-conflict detection in start-services.sh
- [ ] End-to-end verification in verify.sh (drive checkout, verify Notification consumed)
- [ ] Decide on Hibernate Validator for DTOs

---

## 4. Repository Structure (Target State)

```
ddd-observability-workshop/
  _config.yml                        # Jekyll config
  Gemfile                            # Jekyll 4.3 dependencies
  index.md                           # Card-grid homepage
  _layouts/
    default.html                     # Base shell
    tutorial.html                    # Chapter layout with prev/next
    part_index.html                  # Part landing with chapter cards
  _includes/
    codetabs.html                    # Language tab marker
    excalidraw.html                  # Diagram renderer
    header.html / footer.html
  _docs/
    00-introduction-setup.md
    01-domain-landscape.md
    02-domain-events-spans.md
    03-structured-observability.md
    04-cross-context-debugging.md
    05-observability-economics.md
    06-wrap-up.md
    addendum-a-event-storming.md
    addendum-b-advanced-patterns.md
    prerequisites.md
    architecture.md
    troubleshooting.md
  _parts/
    00-workshop.md
    01-addendums.md
  assets/
    css/site.css
    js/codetabs.js
    images/                          # Screenshots, diagrams
  _plans/
    PRD.md
    RECONCILIATION.md
    ITERATION.md
  infrastructure/
    _infra/                          # Compose fragments (shared)
      compose-grafana.yaml
      compose-tempo.yaml
      compose-prometheus.yaml
      compose-loki.yaml
      compose-otel-collector.yaml
      compose-kafka.yaml
      compose-postgres.yaml
    otel-collector/config.yaml
    grafana/
      datasources/datasources.yaml
      dashboards/*.json
    prometheus/prometheus.yml
    tempo/tempo.yaml
    loki/loki.yaml
  exercises/
    quarkus/
      compose.yaml                   # Includes _infra fragments + Quarkus services
      .devcontainer/
        devcontainer.json
        post-create.sh
        post-start.sh
      shared-observability/          # Shared module
      order-service/
      inventory-service/
      payment-service/
      shipping-service/
      notification-service/
      pom.xml
    python/
      compose.yaml
      .devcontainer/
        devcontainer.json
        post-create.sh
        post-start.sh
      shared_observability/          # Shared package
      order_service/
      inventory_service/
      payment_service/
      shipping_service/
      notification_service/
      pyproject.toml
    dotnet/
      compose.yaml
      .devcontainer/
        devcontainer.json
        post-create.sh
        post-start.sh
      SharedObservability/           # Shared project
      OrderService/
      InventoryService/
      PaymentService/
      ShippingService/
      NotificationService/
      DddWorkshop.sln
  tests/
    collections/                     # Newman/Postman collections
      00-smoke-test.json
      01-domain-landscape.json
      02-domain-events.json
      03-structured-observability.json
      04-debugging-exercise.json
      05-observability-economics.json
    environments/
      codespaces.json
      local.json
    verify.sh                        # Language-agnostic validation
  scripts/
    load-test-light.sh
    load-test-heavy.sh
    load-test-with-bug.sh
  presentation/
    deck.js                          # pptxgenjs slide generation
    slides/                          # Generated assets
    building-observable-domains.pptx
  .github/
    workflows/
      pages.yml                      # Jekyll → GitHub Pages
      ci.yml                         # Build + test all three languages
  README.md
```

---

## 5. What Can Be Reused Directly

| Asset | Source | Destination | Notes |
|-------|--------|-------------|-------|
| codetabs.js | cloud-native-design-patterns | assets/js/ | Copy verbatim, change localStorage key |
| codetabs.html | cloud-native-design-patterns | _includes/ | Copy verbatim |
| excalidraw.html | cloud-native-design-patterns | _includes/ | Copy verbatim |
| site.css | cloud-native-design-patterns | assets/css/ | Adapt colors, remove Red Hat fonts |
| tutorial.html layout | cloud-native-design-patterns | _layouts/ | Adapt for workshop structure |
| part_index.html layout | cloud-native-design-patterns | _layouts/ | Adapt for workshop structure |
| default.html layout | cloud-native-design-patterns | _layouts/ | Adapt branding |
| pages.yml workflow | cloud-native-design-patterns | .github/workflows/ | Adapt paths |
| Gemfile | cloud-native-design-patterns | ./ | Copy, adjust plugins |
| _infra/ pattern | cloud-native-design-patterns | infrastructure/_infra/ | Adapt to workshop containers |
| OTel Collector config | existing workshop | infrastructure/ | Keep as-is |
| Grafana dashboards | existing workshop | infrastructure/grafana/ | Keep as-is |
| Grafana datasources | existing workshop | infrastructure/grafana/ | Keep as-is |
| Prometheus config | existing workshop | infrastructure/ | Keep as-is |
| Tempo config | existing workshop | infrastructure/ | Keep as-is |
| Loki config | existing workshop | infrastructure/ | Keep as-is |
| Domain model (Java) | existing workshop | exercises/quarkus/ | Move from root to exercises/quarkus/ |
| Scripts | existing workshop | scripts/ | Adapt for new structure |
| Test payloads | existing workshop | tests/ or scripts/payloads/ | Keep |
| Tutorial content (prose) | existing workshop | _docs/ | Rewrite with codetabs, add Python/C# |

---

## 6. Risk Assessment

### 6.1 Critical Path
The critical path runs through the language implementations:
```
Python implementation → Newman tests → Site content with tabs → Codespaces testing → Ship
C# implementation   →   (parallel)  →        (parallel)       →    (parallel)     →
```

The Python and C# implementations are the longest-pole items. Newman tests can start once either implementation exists. Site content can be written with placeholder code blocks and filled in as implementations complete.

### 6.2 High Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Python OTel instrumentation gaps | Medium | High | Research FastAPI + OTel integration early; prototype shared_observability first |
| .NET 10 OTel SDK maturity | Medium | High | Verify .NET 10 + OTel SDK compatibility; .NET has mature OTel support |
| Codespaces 4-core can't handle all containers + 5 services | Medium | High | Test early; set memory limits; consider staggered startup |
| 30 days insufficient for 3 languages + site + tests | High | High | Prioritize Quarkus + Python; C# can be iteration 2 if needed |
| Kafka consumer semantics differ across languages | Medium | Medium | Prototype Kafka consumer in Python/C# early |
| gRPC setup complexity in Python/C# | Low | Medium | Make gRPC Module 5 content an "advanced" addendum option |

### 6.3 Dependencies

```
Infrastructure refactor ──→ Per-language compose files ──→ Codespaces testing
                                     │
Java audit completion ──────────────→│
                                     │
Python implementation ──────────────→│──→ Newman tests ──→ Site content ──→ Ship
                                     │
C# implementation ──────────────────→│
                                     │
Site layout migration ──────────────→│
```
