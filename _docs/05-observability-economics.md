---
title: "Observability Economics"
order: 5
part: "The Workshop"
description: "Control costs with tail sampling, cardinality limits, and OTel Collector pipelines tuned to your domain's signal-to-noise ratio."
duration: "15 min"
label: "Module 5"
---

## Learning objectives

By the end of this module you will be able to:

- Understand the cost implications of full-fidelity tracing in production
- Compare head sampling vs tail sampling and know when to use each
- Apply cardinality discipline to keep metrics affordable
- Use the Observability Cost dashboard to measure overhead

---

## Step 1: Understand the cost

Before we start optimizing, let's understand the scale of the problem.

Every span, metric series, and log line costs storage and compute. Consider the math for our workshop:

- A single checkout generates ~15 spans across 5 services
- At 1,000 checkouts/second, that is 15,000 spans/second
- Over a day: **1.3 billion spans**
- Each span carries attributes, events, and links -- typically 500 bytes to 2 KB

Full-fidelity retention of every trace is expensive. The question is not *whether* to sample, but *how* to sample without losing the traces that matter.

---

## Step 2: Browse the OTel Collector configuration

### Head sampling vs tail sampling

Before we look at the config, understand the two sampling approaches:

**Head sampling** decides at the start of a trace whether to keep it. The decision is made probabilistically (e.g., "keep 10% of traces") and propagated to all downstream services via the `traceparent` header's sampled bit.

**Advantages**: simple, cheap, no buffering required. Every service knows immediately whether to record spans.

**Disadvantage**: the decision is random. A 10% sample rate means you lose 90% of error traces too. For high-volume happy paths this is fine. For low-volume failures, this is unacceptable.

**Tail sampling** waits for the entire trace to complete before deciding. The OpenTelemetry Collector buffers all spans, sees the full picture, and then applies policies like "keep all error traces, keep all slow traces, sample 10% of the rest."

**Advantages**: keeps every error trace and every slow trace. The debugging experience for failures is unchanged.

**Disadvantage**: the Collector must buffer all in-flight traces for `decision_wait` seconds. That is RAM proportional to throughput multiplied by decision wait multiplied by average trace size. The `num_traces: 50000` setting caps the buffer -- plan capacity for production accordingly.

### Review the tail sampling processor

Open `infrastructure/otel-collector/config.yaml`.

Find the `tail_sampling` processor definition (around line 54). Review the three policies:

1. **`errors`** -- always keep traces with errors
2. **`slow`** -- always keep traces with latency > 1 second
3. **`random_sample`** -- keep 10% of everything else

```yaml
# infrastructure/otel-collector/config.yaml
processors:
  tail_sampling:
    decision_wait: 10s
    num_traces: 50000
    expected_new_traces_per_sec: 100
    policies:
      # Always keep traces with errors
      - name: errors
        type: status_code
        status_code:
          status_codes: [ERROR]
      # Always keep slow traces (> 1 second)
      - name: slow
        type: latency
        latency:
          threshold_ms: 1000
      # Sample 10% of everything else
      - name: random_sample
        type: probabilistic
        probabilistic:
          sampling_percentage: 10
```

### Find the active pipeline

Now find the `traces` pipeline under `service: > pipelines:` (around line 109). Notice that the active processors line is `[memory_limiter, resource, batch]` -- tail sampling is **not** wired in yet.

```yaml
service:
  pipelines:
    traces:
      receivers: [otlp]
      # Default: no tail sampling
      processors: [memory_limiter, resource, batch]
      # Module 5: swap to this line to enable tail sampling
      # processors: [memory_limiter, resource, tail_sampling, batch]
      exporters: [otlp/tempo]
```

---

## Step 3: Activate tail sampling

To activate tail sampling, edit `infrastructure/otel-collector/config.yaml`.

Find the `traces` pipeline (around line 109-114). Comment out the current processors line and uncomment the tail sampling line:

```yaml
# BEFORE:
    processors: [memory_limiter, resource, batch]
    # processors: [memory_limiter, resource, tail_sampling, batch]

# AFTER:
    # processors: [memory_limiter, resource, batch]
    processors: [memory_limiter, resource, tail_sampling, batch]
```

Then restart the OTel Collector to pick up the change:

```bash
docker compose restart otel-collector
```

---

## Step 4: Review cardinality discipline

**Cardinality** is the number of unique time series a metric produces. Every unique combination of label values creates a new time series. Unbounded labels create unbounded series, which exhausts your metrics backend.

### What safe cardinality looks like

Our workshop metrics keep cardinality bounded by design:

| Metric | Labels | Max cardinality |
|---|---|---|
| `checkout_outcomes_total` | `outcome` (4 values), `tier` (5 values) | 20 series |
| `inventory_reservations_total` | `status` (3 values), `tier` (5 values) | 15 series |
| `notifications_sent_total` | `kind` (3 values), `tier` (5 values) | 15 series |
| `acl_drift_total` | `context` (1), `transport` (2), `type` (small set) | ~10 series |

### The cardinality landmines to avoid

| Landmine | Why it explodes | The fix |
|---|---|---|
| `order_id` as a label | Unbounded -- one time series per order | Put it on a span attribute, not a metric label |
| Full URL `path` as a label | Includes IDs -- `/api/orders/abc-123` | Use route templates: `/api/orders/{id}` |
| `error_message` as a label | Free-form strings | Use `error_class` (the exception type) instead |
| `customer_id` as a label | Unbounded -- one time series per customer | Use `customer.tier` or `customer.segment` |
| `user_email` as a label | Unbounded and PII | Do not use |

**The mental check**: before adding a label, ask "how many distinct values can this take?" If the answer is "depends on traffic," it is not a label -- it is a span attribute.

---

## Step 5: Run traffic and observe the cost dashboard

**Try it:** Generate some traffic to see the cost difference:

```bash
newman run tests/collections/01-checkout-happy-path.json \
  -e tests/environments/local.json
```

Run it several times to build up enough data.

Open the **Observability Cost** dashboard in Grafana (`http://localhost:3000` > **Dashboards** > **Observability Cost**).

Look at three panels:

1. **Span volume through Collector** -- `otelcol_receiver_accepted_spans_total` rate, plus any dropped spans
2. **Collector memory** -- shows the tail sampling buffer's memory impact
3. **Export rate by signal** -- compare the export rate now (with tail sampling) vs before

A subtlety: some of these numbers include **observability about observability**. The Collector scrapes its own internal metrics on `:8888`, and Tempo's metrics generator emits service-graph and span-metrics back into the pipeline. Part of the load you are watching is the system observing itself. That is normal -- and it is a real production cost too.

---

## Step 6: Key takeaways

**Sample intelligently**. Keep error traces and slow traces at 100%. Sample the happy path aggressively. This gives you the best cost-to-fidelity ratio.

**Metrics are cheap, traces are expensive**. Use metrics for dashboards and alerting. Use traces for debugging specific incidents. Do not try to answer aggregate questions with traces.

**Cardinality discipline is a design decision, not an afterthought**. The bounded label sets in Module 3 (`outcome`, `tier`, `kind`) were chosen deliberately. Adding an unbounded label is a production incident waiting to happen.

**The coupling dimension from Khononov applies here**. High-volatility data (individual requests, specific order IDs) should be sampled or placed on spans. Low-volatility data (aggregate counts, success rates) should be kept as metrics at full fidelity. The observability strategy mirrors the domain's coupling characteristics.

### The sampling decision matrix

| Volume profile | Recommended starting point |
|---|---|
| Low volume (< 100 RPS), errors are rare | 100% head sampling, no tail sampling |
| Medium volume, errors matter most | 5-10% head sampling, plus tail sampling for errors and slow traces |
| High volume, mostly happy path | 1% head sampling, tail sampling for errors and slow traces |
| Very high volume with budget pressure | Aggressive tail sampling; drop happy-path traces entirely |

Sampling configurations are operational decisions, not architecture decisions. You can change them later without modifying application code.

---

## Checkpoint

You are ready for Module 6 when:

- You understand the difference between head sampling and tail sampling
- You can read the Observability Cost dashboard and explain what drives each number
- You know why cardinality matters and can identify an unbounded label
