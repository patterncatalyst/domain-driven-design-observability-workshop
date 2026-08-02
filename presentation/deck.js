"use strict";

const H = require("./deck-helpers.js");
const {
  COLOR, FONT, W,
  newDeck, addFooter, addContentTitle, addBullets, addTwoColBullets,
  addThreeColBullets, addTable, addCaption, addSectionDivider, addNotes,
} = H;

const OUT = "./building-observable-domains.pptx";

const pres = newDeck();
pres.title = "Building Observable Domains — A DDD + OpenTelemetry Workshop";
pres.author = "Rob Sedor, Jeremy Davis";
pres.subject = "DDD + OpenTelemetry Workshop Introduction";

let pageNum = 0;

function S() {
  const s = pres.addSlide(); pageNum += 1; addFooter(s, pageNum); return s;
}

// ---- Slide 1: Title ---------------------------------------------------------
{
  const s = pres.addSlide();
  pageNum += 1;
  s.background = { color: COLOR.white };

  // Teal accent bar across top
  s.addShape("rect", {
    x: 0, y: 0, w: W, h: 0.08,
    fill: { color: COLOR.teal },
  });

  // Large teal accent block on left
  s.addShape("rect", {
    x: 0, y: 0.08, w: 0.12, h: 7.42,
    fill: { color: COLOR.teal },
  });

  // Workshop label
  s.addText("HANDS-ON WORKSHOP", {
    x: 1.20, y: 2.00, w: 11.13, h: 0.34,
    fontFace: FONT.title, fontSize: 14, bold: true, color: COLOR.teal,
    charSpacing: 6, align: "left", valign: "middle",
  });

  // Main title
  s.addText("Building Observable Domains", {
    x: 1.15, y: 2.44, w: 11.18, h: 1.20,
    fontFace: FONT.title, fontSize: 48, bold: true, color: COLOR.ink,
    align: "left", valign: "top",
  });

  // Subtitle
  s.addText("A Hands-on DDD + OpenTelemetry Workshop", {
    x: 1.20, y: 3.70, w: 11.13, h: 0.60,
    fontFace: FONT.body, fontSize: 22, italic: true, color: COLOR.caption,
    align: "left", valign: "top",
  });

  // Thin rule
  s.addShape("line", {
    x: 1.20, y: 4.55, w: 4.00, h: 0,
    line: { color: COLOR.teal, width: 1.5 },
  });

  // Speaker 1
  s.addText("Rob Sedor", {
    x: 1.20, y: 4.80, w: 11.13, h: 0.36,
    fontFace: FONT.body, fontSize: 18, bold: true, color: COLOR.ink,
    align: "left", valign: "middle",
  });
  s.addText("Senior Principal Chief Architect, Application Development", {
    x: 1.20, y: 5.14, w: 11.13, h: 0.32,
    fontFace: FONT.body, fontSize: 14, color: COLOR.caption,
    align: "left", valign: "middle",
  });

  // Speaker 2
  s.addText("Jeremy Davis", {
    x: 1.20, y: 5.62, w: 11.13, h: 0.36,
    fontFace: FONT.body, fontSize: 18, bold: true, color: COLOR.ink,
    align: "left", valign: "middle",
  });
  s.addText("Principal Specialist Solutions Architect", {
    x: 1.20, y: 5.96, w: 11.13, h: 0.32,
    fontFace: FONT.body, fontSize: 14, color: COLOR.caption,
    align: "left", valign: "middle",
  });

  // Bottom accent bar
  s.addShape("rect", {
    x: 0, y: 7.42, w: W, h: 0.08,
    fill: { color: COLOR.teal },
  });

  addNotes(s, "Welcome to Building Observable Domains. This workshop brings together two disciplines that are stronger together: Domain-Driven Design and OpenTelemetry. Over the next two hours, you will build microservices where observability speaks the language of the domain — not generic HTTP traces, but spans named after domain operations, metrics that answer business questions, and structured logs that carry domain context. I'm Rob Sedor, and I'm joined by Jeremy Davis. Let's get started.");
}

// ---- Slide 2: The Problem ---------------------------------------------------
{
  const s = S();
  addContentTitle(s, "THE PROBLEM", "Observability as an afterthought");
  addBullets(s, [
    "Domain logic ships first, telemetry comes later — if it comes at all.",
    "Generic traces tell you what happened, not why it mattered to the business.",
    'Business questions go unanswered: "How many orders are stuck in payment verification?"',
    "Cross-context failures are detective work — stitching together logs from five different services.",
    "When observability is bolted on, it reflects infrastructure topology, not domain structure.",
  ], { fontSize: 18 });

  addNotes(s, "This is the problem we see repeatedly in production systems. Teams ship domain logic first, and observability is treated as an infrastructure concern — something to add later, if there is time. The result is telemetry that tells you an HTTP 500 occurred on /api/orders, but not that a CheckoutSaga failed because inventory reservation timed out during a flash sale. The business team asks 'how many orders are stuck in payment verification right now?' and the engineering team has to write a database query because the observability system speaks in HTTP status codes, not domain concepts. Cross-context failures are the worst: an order fails, and you have to manually correlate traces, logs, and metrics across five services to understand why.");
}

// ---- Slide 3: Workshop Goals ------------------------------------------------
{
  const s = S();
  addContentTitle(s, "WORKSHOP GOALS", "What you'll build today");
  addBullets(s, [
    "5 microservices with domain-driven observability baked in from the start.",
    "Domain-named traces: spans like order.checkout.saga and inventory.reserve, not POST /api.",
    "Business metrics that answer domain questions: orders_pending_payment, shipments_by_carrier.",
    "Structured logs carrying domain context: orderId, customerId, saga state.",
    "Cross-signal correlation: traces <-> logs <-> metrics linked by domain identifiers.",
    "Debug a real production failure using only the observability tools you built.",
  ], { fontSize: 17 });

  addNotes(s, "By the end of this workshop you will have built five microservices where every trace span, every metric, and every log entry speaks the ubiquitous language of the domain. When you look at a trace in Grafana Tempo, you will see span names like order.checkout.saga, inventory.reserve.stock, and payment.authorize — not POST /api/orders. When you look at metrics in Grafana, you will see counters like orders_pending_payment and histograms like payment_authorization_duration — not http_server_request_duration. And you will use these tools to debug a real failure: a checkout that silently drops because of a race condition between inventory reservation and payment authorization.");
}

// ---- Slide 4: The E-Commerce Scenario ---------------------------------------
{
  const s = S();
  addContentTitle(s, "THE SCENARIO", "A checkout spanning 5 bounded contexts");

  // Draw bounded context boxes and arrows using shapes and text
  const boxH = 1.10;
  const boxW = 2.20;
  const boxY = 2.60;
  const arrowColor = COLOR.caption;

  // Order context (leftmost, slightly higher for visual hierarchy)
  const orderX = 0.80;
  const orderY = 3.20;
  s.addShape("roundRect", {
    x: orderX, y: orderY, w: boxW, h: boxH,
    fill: { color: COLOR.tealLight },
    line: { color: COLOR.teal, width: 2 },
    rectRadius: 0.08,
  });
  s.addText([
    { text: "Order", options: { fontSize: 18, bold: true, color: COLOR.ink, breakLine: true } },
    { text: "(Saga Orchestrator)", options: { fontSize: 11, color: COLOR.caption } },
  ], { x: orderX, y: orderY, w: boxW, h: boxH, align: "center", valign: "middle", fontFace: FONT.body });

  // Inventory context
  const invX = 4.20;
  const invY = 2.30;
  s.addShape("roundRect", {
    x: invX, y: invY, w: boxW, h: boxH,
    fill: { color: "F5F3FF" }, // violet-50
    line: { color: "7C3AED", width: 2 },
    rectRadius: 0.08,
  });
  s.addText([
    { text: "Inventory", options: { fontSize: 18, bold: true, color: COLOR.ink, breakLine: true } },
    { text: "(Stock Management)", options: { fontSize: 11, color: COLOR.caption } },
  ], { x: invX, y: invY, w: boxW, h: boxH, align: "center", valign: "middle", fontFace: FONT.body });

  // Payment context
  const payX = 7.20;
  const payY = 2.30;
  s.addShape("roundRect", {
    x: payX, y: payY, w: boxW, h: boxH,
    fill: { color: "ECFDF5" }, // emerald-50
    line: { color: "059669", width: 2 },
    rectRadius: 0.08,
  });
  s.addText([
    { text: "Payment", options: { fontSize: 18, bold: true, color: COLOR.ink, breakLine: true } },
    { text: "(Authorization)", options: { fontSize: 11, color: COLOR.caption } },
  ], { x: payX, y: payY, w: boxW, h: boxH, align: "center", valign: "middle", fontFace: FONT.body });

  // Shipping context
  const shipX = 10.20;
  const shipY = 2.30;
  s.addShape("roundRect", {
    x: shipX, y: shipY, w: boxW, h: boxH,
    fill: { color: "FFFBEB" }, // amber-50
    line: { color: "D97706", width: 2 },
    rectRadius: 0.08,
  });
  s.addText([
    { text: "Shipping", options: { fontSize: 18, bold: true, color: COLOR.ink, breakLine: true } },
    { text: "(Fulfillment)", options: { fontSize: 11, color: COLOR.caption } },
  ], { x: shipX, y: shipY, w: boxW, h: boxH, align: "center", valign: "middle", fontFace: FONT.body });

  // Notification context (below, connected via Kafka)
  const notifX = 4.20;
  const notifY = 4.60;
  s.addShape("roundRect", {
    x: notifX, y: notifY, w: boxW, h: boxH,
    fill: { color: "FEF2F2" }, // red-50
    line: { color: "DC2626", width: 2 },
    rectRadius: 0.08,
  });
  s.addText([
    { text: "Notification", options: { fontSize: 18, bold: true, color: COLOR.ink, breakLine: true } },
    { text: "(via Kafka Events)", options: { fontSize: 11, color: COLOR.caption } },
  ], { x: notifX, y: notifY, w: boxW, h: boxH, align: "center", valign: "middle", fontFace: FONT.body });

  // Arrows: Order -> Inventory (right arrow, from order box right edge to inventory left edge)
  // Using text arrows since pptxgenjs shape arrows are limited
  s.addText("→", {
    x: orderX + boxW, y: orderY - 0.20, w: invX - orderX - boxW, h: 0.50,
    fontFace: FONT.body, fontSize: 28, color: arrowColor,
    align: "center", valign: "middle",
  });
  // Inventory -> Payment
  s.addText("→", {
    x: invX + boxW, y: invY, w: payX - invX - boxW, h: boxH,
    fontFace: FONT.body, fontSize: 28, color: arrowColor,
    align: "center", valign: "middle",
  });
  // Payment -> Shipping
  s.addText("→", {
    x: payX + boxW, y: payY, w: shipX - payX - boxW, h: boxH,
    fontFace: FONT.body, fontSize: 28, color: arrowColor,
    align: "center", valign: "middle",
  });
  // Order -> Notification (down arrow)
  s.addText("↓", {
    x: orderX + 0.40, y: orderY + boxH, w: 1.40, h: notifY - orderY - boxH,
    fontFace: FONT.body, fontSize: 28, color: arrowColor,
    align: "center", valign: "middle",
  });

  // Flow labels
  s.addText("REST", {
    x: invX + boxW + 0.05, y: invY - 0.30, w: 0.80, h: 0.25,
    fontFace: FONT.mono, fontSize: 9, color: COLOR.caption, align: "center", valign: "middle",
  });
  s.addText("Kafka", {
    x: orderX - 0.10, y: orderY + boxH + 0.05, w: 1.00, h: 0.25,
    fontFace: FONT.mono, fontSize: 9, color: COLOR.caption, align: "center", valign: "middle",
  });

  addCaption(s, "The Saga pattern orchestrates a checkout across five bounded contexts, each with its own observability.");

  addNotes(s, "This is the domain we will instrument. An e-commerce checkout spanning five bounded contexts. The Order context runs a Saga that orchestrates the entire checkout flow. It calls Inventory to reserve stock, Payment to authorize the charge, and Shipping to schedule fulfillment. Notification receives domain events via Kafka — it is eventually consistent and decoupled from the synchronous flow. Each bounded context has its own microservice, its own data store, and its own observability. The challenge is making the end-to-end flow visible and debuggable when things go wrong — and things will go wrong in Module 4 when we inject a real failure.");
}

// ---- Slide 5: DDD Key Concepts ----------------------------------------------
{
  const s = S();
  addContentTitle(s, "DOMAIN-DRIVEN DESIGN", "Key concepts for this workshop");
  addTwoColBullets(s,
    [
      { text: "Strategic Design", options: { bullet: false, bold: true, fontSize: 20, color: COLOR.teal } },
      "Bounded Contexts: explicit boundaries with clear ownership",
      "Ubiquitous Language: shared vocabulary within each context",
      "Context Mapping: how bounded contexts integrate and communicate",
      "Subdomain Classification: core, supporting, and generic",
    ],
    [
      { text: "Tactical Design", options: { bullet: false, bold: true, fontSize: 20, color: COLOR.teal } },
      "Aggregates: consistency boundaries for domain operations",
      "Value Objects: immutable domain concepts (Money, Address)",
      "Domain Events: facts about what happened in the domain",
      "Anti-Corruption Layer: translation at context boundaries",
    ],
    { fontSize: 16 });

  addNotes(s, "A quick DDD refresher focused on the concepts that matter most for observability. Strategic design gives us the architectural boundaries — bounded contexts define where one model ends and another begins, and that is exactly where we need instrumentation. The ubiquitous language gives us naming conventions for spans, metrics, and log fields. Context mapping tells us where integration happens, and that is where cross-context trace propagation matters most. On the tactical side, aggregates define consistency boundaries — and those boundaries are natural span boundaries. Domain events are facts about what happened, and they map directly to span events and log entries. The anti-corruption layer is the translation point between contexts, and it is the ideal place to add instrumentation that captures the mapping between external and internal models.");
}

// ---- Slide 6: OpenTelemetry Key Concepts ------------------------------------
{
  const s = S();
  addContentTitle(s, "OPENTELEMETRY", "The three pillars and the ecosystem");
  addThreeColBullets(s,
    [
      { text: "Signals", options: { bullet: false, bold: true, fontSize: 20, color: COLOR.teal } },
      "Traces: distributed request flows across services",
      "Metrics: counters, histograms, gauges for quantitative data",
      "Logs: structured event records with context",
      "Baggage: cross-cutting key-value pairs propagated in-band",
    ],
    [
      { text: "Components", options: { bullet: false, bold: true, fontSize: 20, color: COLOR.teal } },
      "API: vendor-neutral interfaces for instrumentation",
      "SDK: configurable implementation of the API",
      "Auto-instrumentation: out-of-box library support",
      "Manual instrumentation: custom domain spans and metrics",
    ],
    [
      { text: "Infrastructure", options: { bullet: false, bold: true, fontSize: 20, color: COLOR.teal } },
      "Collector: receive, process, and export telemetry",
      "OTLP: the standard export protocol",
      "Sampling: control volume and cost at scale",
      "Grafana LGTM: Loki, Grafana, Tempo, Mimir",
    ],
    { fontSize: 14 });

  addNotes(s, "OpenTelemetry in three columns. Signals are what you produce: traces give you distributed request flows, metrics give you quantitative measurements, logs give you event records, and baggage lets you propagate key-value pairs across service boundaries without adding them to every span. Components are how you produce signals: the API is the vendor-neutral interface you code against, the SDK is the configurable implementation, auto-instrumentation handles libraries like HTTP clients and database drivers automatically, and manual instrumentation is where you add domain-specific spans and metrics. Infrastructure is where signals go: the Collector receives, processes, and exports telemetry, OTLP is the wire protocol, sampling controls volume and cost, and we use the Grafana LGTM stack — Loki for logs, Grafana for visualization, Tempo for traces, Mimir for metrics.");
}

// ---- Slide 7: Where DDD Meets Observability ---------------------------------
{
  const s = S();
  addContentTitle(s, "THE INTERSECTION", "Where DDD meets observability");

  // Teal highlight panel behind the content
  s.addShape("roundRect", {
    x: 0.50, y: 1.70, w: 12.33, h: 5.00,
    fill: { color: COLOR.tealLight },
    line: { color: COLOR.teal, width: 1 },
    rectRadius: 0.10,
  });

  addBullets(s, [
    "Span names should speak the ubiquitous language: order.checkout.saga, not POST /api/orders.",
    "Domain events are natural span boundaries — each event starts or ends a unit of domain work.",
    "Anti-corruption layer boundaries are instrumentation points — capture the translation happening at context edges.",
    "Business metrics answer domain questions, not infrastructure questions: orders_by_status, not http_requests_total.",
    "Context propagation mirrors identifier propagation — traceId flows the same path as orderId.",
  ], { fontSize: 17, x: 0.80, y: 1.90, w: 11.73, h: 4.60 });

  addNotes(s, "This is the central thesis of the workshop. DDD and observability are not separate concerns — they are the same concern viewed from different angles. When you name a span order.checkout.saga instead of POST /api/orders, you are applying the ubiquitous language to your telemetry. When you start a new span at a domain event boundary, you are using the domain model to structure your traces. When you instrument the anti-corruption layer, you are capturing the translation between bounded contexts — which is exactly where integration failures happen. When you create a metric called orders_pending_payment instead of http_requests_total, you are answering the question the business actually asks. And when you propagate traceId alongside orderId, you are aligning infrastructure context with domain context. This is what domain-driven observability means.");
}

// ---- Slide 8: Workshop Structure --------------------------------------------
{
  const s = S();
  addContentTitle(s, "WORKSHOP STRUCTURE", "Your path through the modules");

  const headerRow = [
    { text: "Module", bold: true, color: COLOR.white, fontSize: 13, options: { fill: { color: COLOR.teal } } },
    { text: "Topic", bold: true, color: COLOR.white, fontSize: 13, options: { fill: { color: COLOR.teal } } },
    { text: "Duration", bold: true, color: COLOR.white, fontSize: 13, options: { fill: { color: COLOR.teal } } },
  ];

  const dataRows = [
    [
      { text: "Module 0", bold: true, color: COLOR.teal, mono: true, fontSize: 13 },
      { text: "Introduction & Setup", fontSize: 14 },
      { text: "15 min", fontSize: 14, align: "center" },
    ],
    [
      { text: "Module 1", bold: true, color: COLOR.teal, mono: true, fontSize: 13 },
      { text: "The Domain Landscape", fontSize: 14 },
      { text: "15 min", fontSize: 14, align: "center" },
    ],
    [
      { text: "Module 2", bold: true, color: COLOR.teal, mono: true, fontSize: 13 },
      { text: "Domain Events & Spans", fontSize: 14 },
      { text: "25 min", fontSize: 14, align: "center" },
    ],
    [
      { text: "Module 3", bold: true, color: COLOR.teal, mono: true, fontSize: 13 },
      { text: "Structured Observability", fontSize: 14 },
      { text: "20 min", fontSize: 14, align: "center" },
    ],
    [
      { text: "Module 4", bold: true, color: COLOR.teal, mono: true, fontSize: 13 },
      { text: "Cross-Context Debugging", fontSize: 14 },
      { text: "20 min", fontSize: 14, align: "center" },
    ],
    [
      { text: "Module 5", bold: true, color: COLOR.teal, mono: true, fontSize: 13 },
      { text: "Observability Economics", fontSize: 14 },
      { text: "15 min", fontSize: 14, align: "center" },
    ],
    [
      { text: "Module 6", bold: true, color: COLOR.teal, mono: true, fontSize: 13 },
      { text: "Wrap-up & Discussion", fontSize: 14 },
      { text: "10 min", fontSize: 14, align: "center" },
    ],
  ];

  // Header row
  const tableData = [headerRow, ...dataRows];

  s.addTable(
    tableData.map((row, rowIdx) =>
      row.map((cell) => ({
        text: cell.text,
        options: {
          bold: cell.bold ?? false,
          color: cell.color || COLOR.body,
          fontFace: cell.mono ? FONT.mono : FONT.body,
          fontSize: cell.fontSize ?? 14,
          align: cell.align || "left",
          valign: "middle",
          fill: rowIdx === 0 ? { color: COLOR.teal } : (rowIdx % 2 === 0 ? { color: COLOR.tealLight } : undefined),
        },
      }))
    ),
    {
      x: 1.50, y: 1.85, w: 10.33, h: 4.50,
      border: { type: "solid", color: COLOR.grid, pt: 0.5 },
      colW: [2.00, 6.33, 2.00],
      rowH: [0.50, 0.52, 0.52, 0.52, 0.52, 0.52, 0.52, 0.52],
      valign: "middle",
    }
  );

  addCaption(s, "Total workshop time: approximately 2 hours");

  addNotes(s, "Here is the module breakdown. Module 0 is where you are now — introduction and getting your environment set up. Module 1 explores the domain landscape: the bounded contexts, the ubiquitous language, and the context map. Module 2 is the core hands-on module — you will instrument domain events as OpenTelemetry spans and see them flow through Grafana Tempo. Module 3 adds structured observability: business metrics with Mimir and structured logs with Loki. Module 4 is the debugging challenge — we inject a real failure and you use only the observability tools to find the root cause. Module 5 covers observability economics: sampling strategies, cardinality control, and cost management. Module 6 wraps up with discussion and next steps.");
}

// ---- Slide 9: Getting Started -----------------------------------------------
{
  const s = S();
  addContentTitle(s, "GETTING STARTED", "Set up your environment");

  // Step boxes
  const steps = [
    { num: "1", text: "Go to the repository", detail: "github.com/patterncatalyst/\ndomain-driven-design-observability-workshop" },
    { num: "2", text: "Fork the repo", detail: 'Click "Fork" to create\nyour own copy' },
    { num: "3", text: "Create a Codespace", detail: "Choose your language branch:\nworkshop/quarkus, workshop/python,\nor workshop/dotnet" },
    { num: "4", text: "Open the tutorial site", detail: "Keep it open in a\nsecond browser tab" },
  ];

  const stepW = 2.75;
  const stepGap = 0.20;
  const startX = 0.72;
  const stepY = 1.90;
  const stepH = 2.80;

  steps.forEach((step, i) => {
    const x = startX + i * (stepW + stepGap);

    // Step box
    s.addShape("roundRect", {
      x, y: stepY, w: stepW, h: stepH,
      fill: { color: COLOR.tealLight },
      line: { color: COLOR.teal, width: 1.5 },
      rectRadius: 0.08,
    });

    // Step number circle
    s.addShape("ellipse", {
      x: x + stepW / 2 - 0.25, y: stepY + 0.20, w: 0.50, h: 0.50,
      fill: { color: COLOR.teal },
    });
    s.addText(step.num, {
      x: x + stepW / 2 - 0.25, y: stepY + 0.20, w: 0.50, h: 0.50,
      fontFace: FONT.title, fontSize: 18, bold: true, color: COLOR.white,
      align: "center", valign: "middle",
    });

    // Step title
    s.addText(step.text, {
      x: x + 0.10, y: stepY + 0.85, w: stepW - 0.20, h: 0.40,
      fontFace: FONT.body, fontSize: 15, bold: true, color: COLOR.ink,
      align: "center", valign: "middle",
    });

    // Step detail
    s.addText(step.detail, {
      x: x + 0.10, y: stepY + 1.30, w: stepW - 0.20, h: 1.30,
      fontFace: FONT.body, fontSize: 12, color: COLOR.caption,
      align: "center", valign: "top",
    });

    // Arrow between steps
    if (i < steps.length - 1) {
      s.addText("▶", {
        x: x + stepW, y: stepY + stepH / 2 - 0.20, w: stepGap, h: 0.40,
        fontFace: FONT.body, fontSize: 14, color: COLOR.teal,
        align: "center", valign: "middle",
      });
    }
  });

  // Available languages section
  s.addShape("roundRect", {
    x: 0.72, y: 5.10, w: 11.89, h: 1.10,
    fill: { color: COLOR.panel },
    line: { color: COLOR.grid, width: 0.5 },
    rectRadius: 0.06,
  });

  s.addText("AVAILABLE LANGUAGES", {
    x: 0.95, y: 5.15, w: 3.00, h: 0.30,
    fontFace: FONT.title, fontSize: 11, bold: true, color: COLOR.teal,
    charSpacing: 3, align: "left", valign: "middle",
  });

  const langs = [
    { name: "Quarkus (Java)", branch: "workshop/quarkus" },
    { name: "Python (FastAPI)", branch: "workshop/python" },
    { name: "C# (.NET 10)", branch: "workshop/dotnet" },
  ];

  langs.forEach((lang, i) => {
    const lx = 1.00 + i * 3.90;
    s.addText([
      { text: lang.name, options: { bold: true, fontSize: 14, color: COLOR.ink } },
      { text: "  →  ", options: { fontSize: 12, color: COLOR.caption } },
      { text: lang.branch, options: { fontSize: 12, color: COLOR.teal, fontFace: FONT.mono } },
    ], {
      x: lx, y: 5.50, w: 3.70, h: 0.50,
      fontFace: FONT.body, align: "left", valign: "middle",
    });
  });

  addCaption(s, "[QR code to repo URL]    github.com/patterncatalyst/domain-driven-design-observability-workshop", 6.50);

  addNotes(s, "Four steps to get started. Step 1: navigate to the GitHub repository. Step 2: fork it to your own account — you will be pushing code changes during the exercises. Step 3: create a GitHub Codespace on your language branch. We support three languages: Quarkus for Java developers, FastAPI for Python developers, and .NET 10 for C# developers. The Codespace comes pre-configured with all dependencies, the Grafana LGTM observability stack, Kafka, and PostgreSQL. Step 4: open the tutorial site in a second browser tab — you will follow the step-by-step instructions there while coding in the Codespace. If you prefer to work locally, the README has instructions for running with podman compose.");
}

// ---- Slide 10: Let's Build! -------------------------------------------------
{
  const s = pres.addSlide();
  pageNum += 1;
  s.background = { color: COLOR.white };

  // Top accent bar
  s.addShape("rect", {
    x: 0, y: 0, w: W, h: 0.08,
    fill: { color: COLOR.teal },
  });

  // Left accent bar
  s.addShape("rect", {
    x: 0, y: 0.08, w: 0.12, h: 7.42,
    fill: { color: COLOR.teal },
  });

  // Main call-to-action
  s.addText("Let's build observable\ndomains together.", {
    x: 1.20, y: 1.80, w: 11.13, h: 2.00,
    fontFace: FONT.title, fontSize: 44, bold: true, color: COLOR.ink,
    align: "left", valign: "top",
  });

  // Thin rule
  s.addShape("line", {
    x: 1.20, y: 4.10, w: 5.00, h: 0,
    line: { color: COLOR.teal, width: 1.5 },
  });

  // Tutorial site
  s.addText("TUTORIAL SITE", {
    x: 1.20, y: 4.40, w: 11.13, h: 0.30,
    fontFace: FONT.title, fontSize: 11, bold: true, color: COLOR.teal,
    charSpacing: 4, align: "left", valign: "middle",
  });
  s.addText("patterncatalyst.github.io/domain-driven-design-observability-workshop", {
    x: 1.20, y: 4.70, w: 11.13, h: 0.40,
    fontFace: FONT.mono, fontSize: 16, color: COLOR.ink,
    align: "left", valign: "middle",
  });

  // GitHub repo
  s.addText("GITHUB REPOSITORY", {
    x: 1.20, y: 5.30, w: 11.13, h: 0.30,
    fontFace: FONT.title, fontSize: 11, bold: true, color: COLOR.teal,
    charSpacing: 4, align: "left", valign: "middle",
  });
  s.addText("github.com/patterncatalyst/domain-driven-design-observability-workshop", {
    x: 1.20, y: 5.60, w: 11.13, h: 0.40,
    fontFace: FONT.mono, fontSize: 16, color: COLOR.ink,
    align: "left", valign: "middle",
  });

  // Bottom accent bar
  s.addShape("rect", {
    x: 0, y: 7.42, w: W, h: 0.08,
    fill: { color: COLOR.teal },
  });

  addNotes(s, "Time to start building. Open your Codespace, navigate to the tutorial site, and begin with Module 0. If you get stuck at any point, raise your hand — Rob and Jeremy are here to help. The tutorial site has complete step-by-step instructions, code snippets, and explanations for every module. The exercises build on each other, so work through them in order. By Module 4, you will have a fully observable system and a real failure to debug. Let's go.");
}

// ---- Write ------------------------------------------------------------------
pres.writeFile({ fileName: OUT })
  .then((p) => console.log("WROTE", p))
  .catch((e) => { console.error(e); process.exit(1); });
