---
layout: default
title: "Building Observable Domains"
---

<section class="hero">
  <div class="container">
    <span class="hero__eyebrow">DDD + OpenTelemetry Workshop</span>
    <h1>Building Observable Domains</h1>
    <p class="hero__lead">
      Learn to design domain-driven services that are observable from day one.
      Wire OpenTelemetry traces, metrics, and logs to your bounded contexts in Quarkus, Python, and C#.
    </p>
    <div class="hero__chips">
      <span class="chip">Quarkus</span>
      <span class="chip">Python</span>
      <span class="chip">C#</span>
      <span class="chip">OpenTelemetry</span>
      <span class="chip">DDD</span>
      <span class="chip">~2 hours</span>
    </div>
    <div class="hero__cta">
      <a class="btn btn--primary" href="{{ '/docs/00-introduction-setup/' | relative_url }}">Start the workshop</a>
      <a class="btn btn--secondary" href="{{ '/docs/prerequisites/' | relative_url }}">Prerequisites</a>
    </div>
  </div>
</section>

<section class="section container">
  <h2 class="section-heading">Workshop Modules</h2>
  <p class="section-sub">Seven modules take you from domain discovery to production-grade observability, with hands-on exercises at every step.</p>

  <div class="cards">
    <a class="card" href="{{ '/docs/00-introduction-setup/' | relative_url }}">
      <div class="card__eyebrow">Module 0</div>
      <h3 class="card__title">Introduction and Setup</h3>
      <p class="card__desc">Meet the workshop domain, clone the repos, and verify your local stack is running.</p>
      <div class="card__meta">
        <span>15 min</span>
        <span>Read</span>
      </div>
    </a>

    <a class="card" href="{{ '/docs/01-domain-landscape/' | relative_url }}">
      <div class="card__eyebrow">Module 1</div>
      <h3 class="card__title">The Domain Landscape</h3>
      <p class="card__desc">Map bounded contexts and identify the aggregates, entities, and value objects in an order-management domain.</p>
      <div class="card__meta">
        <span>15 min</span>
        <span>Read</span>
      </div>
    </a>

    <a class="card" href="{{ '/docs/02-domain-events-spans/' | relative_url }}">
      <div class="card__eyebrow">Module 2</div>
      <h3 class="card__title">Domain Events as Spans</h3>
      <p class="card__desc">Model domain events, then represent each as an OpenTelemetry span with semantic attributes.</p>
      <div class="card__meta">
        <span>25 min</span>
        <span>Read</span>
      </div>
    </a>

    <a class="card" href="{{ '/docs/03-structured-observability/' | relative_url }}">
      <div class="card__eyebrow">Module 3</div>
      <h3 class="card__title">Structured Observability</h3>
      <p class="card__desc">Add structured logging with trace correlation, define domain-specific metrics, and connect logs/traces/metrics in Grafana.</p>
      <div class="card__meta">
        <span>20 min</span>
        <span>Read</span>
      </div>
    </a>

    <a class="card" href="{{ '/docs/04-cross-context-debugging/' | relative_url }}">
      <div class="card__eyebrow">Module 4</div>
      <h3 class="card__title">Cross-Context Debugging</h3>
      <p class="card__desc">Propagate trace context across bounded-context boundaries via HTTP and messaging, then debug a cross-service issue end-to-end.</p>
      <div class="card__meta">
        <span>20 min</span>
        <span>Read</span>
      </div>
    </a>

    <a class="card" href="{{ '/docs/05-observability-economics/' | relative_url }}">
      <div class="card__eyebrow">Module 5</div>
      <h3 class="card__title">Observability Economics</h3>
      <p class="card__desc">Control costs with tail sampling, cardinality limits, and OTel Collector pipelines tuned to your domain's signal-to-noise ratio.</p>
      <div class="card__meta">
        <span>15 min</span>
        <span>Read</span>
      </div>
    </a>

    <a class="card" href="{{ '/docs/06-wrap-up/' | relative_url }}">
      <div class="card__eyebrow">Module 6</div>
      <h3 class="card__title">Wrap-Up and Next Steps</h3>
      <p class="card__desc">Review what you built, discuss production readiness, and explore where to go from here.</p>
      <div class="card__meta">
        <span>10 min</span>
        <span>Read</span>
      </div>
    </a>
  </div>
</section>

<section class="section-tight container">
  <h2 class="section-heading">Addendums</h2>
  <p class="section-sub">Deep dives for after the workshop or for experienced practitioners.</p>

  <div class="cards">
    <a class="card" href="{{ '/docs/addendum-a-event-storming/' | relative_url }}">
      <div class="card__eyebrow">Addendum A</div>
      <h3 class="card__title">Event Storming for Observability</h3>
      <p class="card__desc">Run an Event Storming session that produces both a domain model and an observability plan in one pass.</p>
      <div class="card__meta">
        <span>Read</span>
      </div>
    </a>

    <a class="card" href="{{ '/docs/addendum-b-advanced-patterns/' | relative_url }}">
      <div class="card__eyebrow">Addendum B</div>
      <h3 class="card__title">Advanced Patterns</h3>
      <p class="card__desc">Sagas with distributed traces, CQRS read-model observability, and anti-corruption layer telemetry.</p>
      <div class="card__meta">
        <span>Read</span>
      </div>
    </a>
  </div>
</section>

<section class="section-tight container">
  <h2 class="section-heading">Quick Links</h2>

  <div class="cards">
    <a class="doc-card" href="{{ '/docs/prerequisites/' | relative_url }}">
      <div class="doc-card__icon">P</div>
      <div>
        <p class="doc-card__title">Prerequisites</p>
        <p class="doc-card__desc">Software and accounts you need before starting.</p>
      </div>
    </a>

    <a class="doc-card" href="{{ '/docs/architecture/' | relative_url }}">
      <div class="doc-card__icon">A</div>
      <div>
        <p class="doc-card__title">Architecture Overview</p>
        <p class="doc-card__desc">The workshop domain model and infrastructure stack.</p>
      </div>
    </a>

    <a class="doc-card" href="{{ '/docs/troubleshooting/' | relative_url }}">
      <div class="doc-card__icon">T</div>
      <div>
        <p class="doc-card__title">Troubleshooting</p>
        <p class="doc-card__desc">Common issues and fixes for the workshop environment.</p>
      </div>
    </a>
  </div>
</section>
