#!/usr/bin/env python3
"""Generate all architecture diagrams for the DDD + OTel workshop."""
import sys
sys.path.insert(0, "/home/rsedor/.claude/skills/lgtm-diagram-generator/scripts")
import generate_diagram as g

# Recolor accent from amber to teal to match site theme
g.STYLES["accent"] = ("#ecfeff", "#0891b2")
g.AMBER = "#0e7490"
# Also patch the SVG marker for amber-colored arrows
_orig_svg = g._svg
def _patched_svg(width, height, bands, nodes, edges, notes):
    svg = _orig_svg(width, height, bands, nodes, edges, notes)
    return svg.replace("#b8650a", "#0e7490").replace("'Red Hat Text'", "'Inter'")
g._svg = _patched_svg

g.OUT = "/home/rsedor/Dev/domain-driven-design-observability-workshop/assets/diagrams"

# ============================================================================
# 1. Bounded Context Map
# ============================================================================
g.emit("bounded-context-map", 880, 420,
    bands=[
        {"x": 10, "y": 10, "w": 860, "h": 400, "label": "E-Commerce Checkout Domain", "fill": "#f8fafa"},
    ],
    nodes=[
        {"x": 330, "y": 140, "w": 200, "h": 70, "style": "accent", "lines": ["Order", "Core Subdomain", "Saga Orchestrator"]},
        {"x": 640, "y": 80,  "w": 200, "h": 70, "style": "box",    "lines": ["Inventory", "Supporting Subdomain"]},
        {"x": 640, "y": 250, "w": 200, "h": 70, "style": "sub",    "lines": ["Payment", "Generic Subdomain"]},
        {"x": 330, "y": 310, "w": 200, "h": 70, "style": "box",    "lines": ["Shipping", "Supporting Subdomain"]},
        {"x": 40,  "y": 140, "w": 200, "h": 70, "style": "box",    "lines": ["Notification", "Supporting Subdomain"]},
        {"x": 140, "y": 280, "w": 100, "h": 40, "style": "ghost",  "lines": ["Kafka"]},
    ],
    edges=[
        {"x1": 530, "y1": 160, "x2": 640, "y2": 115, "amber": True, "label": "Customer-Supplier + ACL", "lx": 0, "ly": -12},
        {"x1": 530, "y1": 195, "x2": 640, "y2": 275, "label": "Customer-Supplier", "lx": 0, "ly": 14},
        {"x1": 430, "y1": 210, "x2": 430, "y2": 310, "label": "Customer-Supplier", "lx": 60, "ly": 0},
        {"x1": 330, "y1": 175, "x2": 240, "y2": 175, "label": "Published Language", "lx": 0, "ly": -12},
        {"x1": 190, "y1": 210, "x2": 190, "y2": 280, "dashed": True, "label": "async", "lx": 30, "ly": 0},
    ],
    notes=[
        {"x": 440, "y": 35, "text": "Bounded Context Map", "bold": True, "size": 14, "anchor": "middle", "color": "#333"},
    ],
)

# ============================================================================
# 2. Checkout Saga Flow
# ============================================================================
g.emit("checkout-saga-flow", 900, 340,
    bands=[
        {"x": 10, "y": 50, "w": 880, "h": 120, "label": "Happy Path (synchronous)", "fill": "#ecfeff"},
        {"x": 10, "y": 200, "w": 880, "h": 60, "label": "Async (Kafka)", "fill": "#f0fdf4"},
        {"x": 10, "y": 270, "w": 880, "h": 55, "label": "Cancel Path (on any failure)", "fill": "#fef2f2"},
    ],
    nodes=[
        {"x": 20,  "y": 75, "w": 140, "h": 60, "style": "accent", "lines": ["Order.Checkout", ":8080"]},
        {"x": 200, "y": 75, "w": 160, "h": 60, "style": "box",    "lines": ["Inventory.Reserve", ":8081"]},
        {"x": 400, "y": 75, "w": 160, "h": 60, "style": "box",    "lines": ["Payment.Authorize", ":8082"]},
        {"x": 600, "y": 75, "w": 160, "h": 60, "style": "box",    "lines": ["Shipping.Schedule", ":8083"]},
        {"x": 200, "y": 210, "w": 180, "h": 40, "style": "box",   "lines": ["Order.Events.Publish"]},
        {"x": 530, "y": 210, "w": 180, "h": 40, "style": "box",   "lines": ["Notification.Consume"]},
        {"x": 200, "y": 278, "w": 160, "h": 40, "style": "ghost",  "lines": ["OrderCancelled"]},
    ],
    edges=[
        {"x1": 160, "y1": 105, "x2": 200, "y2": 105, "amber": True, "label": "1"},
        {"x1": 360, "y1": 105, "x2": 400, "y2": 105, "amber": True, "label": "2"},
        {"x1": 560, "y1": 105, "x2": 600, "y2": 105, "amber": True, "label": "3"},
        {"x1": 90,  "y1": 135, "x2": 90,  "y2": 210, "label": "publish", "lx": 40, "ly": 0},
        {"x1": 90, "y1": 210, "x2": 200, "y2": 230},
        {"x1": 380, "y1": 230, "x2": 530, "y2": 230, "label": "Kafka", "lx": 0, "ly": -12},
        {"x1": 90,  "y1": 135, "x2": 200, "y2": 298, "dashed": True, "label": "on failure", "lx": -20, "ly": 0},
    ],
    notes=[
        {"x": 450, "y": 30, "text": "Checkout Saga — Orchestration Pattern", "bold": True, "size": 14, "anchor": "middle", "color": "#333"},
    ],
)

# ============================================================================
# 3. OTel Pipeline
# ============================================================================
g.emit("otel-pipeline", 900, 380,
    bands=[
        {"x": 10,  "y": 50, "w": 180, "h": 310, "label": "Application Services", "fill": "#ecfeff"},
        {"x": 260, "y": 50, "w": 190, "h": 310, "label": "Collection", "fill": "#fafafa"},
        {"x": 510, "y": 50, "w": 190, "h": 310, "label": "Storage", "fill": "#f0fdf4"},
        {"x": 740, "y": 50, "w": 150, "h": 310, "label": "Visualization", "fill": "#fff8ef"},
    ],
    nodes=[
        {"x": 30,  "y": 80,  "w": 140, "h": 40, "style": "accent", "lines": ["Order :8080"]},
        {"x": 30,  "y": 130, "w": 140, "h": 40, "style": "box",    "lines": ["Inventory :8081"]},
        {"x": 30,  "y": 180, "w": 140, "h": 40, "style": "box",    "lines": ["Payment :8082"]},
        {"x": 30,  "y": 230, "w": 140, "h": 40, "style": "box",    "lines": ["Shipping :8083"]},
        {"x": 30,  "y": 280, "w": 140, "h": 40, "style": "box",    "lines": ["Notification :8084"]},
        {"x": 280, "y": 140, "w": 150, "h": 80, "style": "ink",    "lines": ["OTel Collector", ":4317 gRPC", ":4318 HTTP"]},
        {"x": 530, "y": 80,  "w": 150, "h": 50, "style": "box",    "lines": ["Tempo", "traces :3200"]},
        {"x": 530, "y": 170, "w": 150, "h": 50, "style": "box",    "lines": ["Prometheus", "metrics :9090"]},
        {"x": 530, "y": 260, "w": 150, "h": 50, "style": "box",    "lines": ["Loki", "logs :3100"]},
        {"x": 760, "y": 160, "w": 110, "h": 60, "style": "accent", "lines": ["Grafana", ":3000"]},
    ],
    edges=[
        {"x1": 170, "y1": 100, "x2": 280, "y2": 165, "amber": True, "label": "OTLP", "lx": 0, "ly": -12},
        {"x1": 170, "y1": 150, "x2": 280, "y2": 170},
        {"x1": 170, "y1": 200, "x2": 280, "y2": 180},
        {"x1": 170, "y1": 250, "x2": 280, "y2": 190},
        {"x1": 170, "y1": 300, "x2": 280, "y2": 200},
        {"x1": 430, "y1": 160, "x2": 530, "y2": 105},
        {"x1": 430, "y1": 180, "x2": 530, "y2": 195},
        {"x1": 430, "y1": 200, "x2": 530, "y2": 285},
        {"x1": 680, "y1": 105, "x2": 760, "y2": 175},
        {"x1": 680, "y1": 195, "x2": 760, "y2": 190},
        {"x1": 680, "y1": 285, "x2": 760, "y2": 205},
    ],
    notes=[
        {"x": 450, "y": 30, "text": "OpenTelemetry Pipeline", "bold": True, "size": 14, "anchor": "middle", "color": "#333"},
    ],
)

# ============================================================================
# 4. ACL Translation
# ============================================================================
g.emit("acl-translation", 880, 300,
    bands=[
        {"x": 10,  "y": 50, "w": 250, "h": 230, "label": "Order Context", "fill": "#ecfeff"},
        {"x": 300, "y": 50, "w": 260, "h": 230, "label": "Anti-Corruption Layer", "fill": "#fff8ef"},
        {"x": 600, "y": 50, "w": 270, "h": 230, "label": "Inventory Context", "fill": "#f0fdf4"},
    ],
    nodes=[
        {"x": 30,  "y": 90,  "w": 210, "h": 40, "style": "accent", "lines": ["Sku", "SKU-LAPTOP-PRO"]},
        {"x": 30,  "y": 145, "w": 210, "h": 40, "style": "accent", "lines": ["LineItem", "sku + quantity + unitPrice"]},
        {"x": 30,  "y": 200, "w": 210, "h": 40, "style": "accent", "lines": ["OrderId", "ord_xxx"]},
        {"x": 330, "y": 110, "w": 200, "h": 80, "style": "ink",    "lines": ["ACL Adapter", "Order.Acl.", "InventoryReserve"]},
        {"x": 620, "y": 90,  "w": 230, "h": 40, "style": "box",    "lines": ["ProductCode", "PROD-LAPTOP-PRO"]},
        {"x": 620, "y": 145, "w": 230, "h": 40, "style": "box",    "lines": ["ReservationLine", "productCode + quantity"]},
        {"x": 620, "y": 200, "w": 230, "h": 40, "style": "box",    "lines": ["ReservationId", "res_xxx"]},
    ],
    edges=[
        {"x1": 240, "y1": 110, "x2": 330, "y2": 140, "amber": True},
        {"x1": 240, "y1": 165, "x2": 330, "y2": 155},
        {"x1": 240, "y1": 220, "x2": 330, "y2": 170},
        {"x1": 530, "y1": 135, "x2": 620, "y2": 110, "amber": True},
        {"x1": 530, "y1": 150, "x2": 620, "y2": 165},
        {"x1": 530, "y1": 170, "x2": 620, "y2": 220},
    ],
    notes=[
        {"x": 440, "y": 30, "text": "Anti-Corruption Layer — Vocabulary Translation", "bold": True, "size": 14, "anchor": "middle", "color": "#333"},
        {"x": 430, "y": 210, "text": "translates", "size": 11, "anchor": "middle", "color": "#888"},
    ],
)

# ============================================================================
# 5. Trace Comparison (Before vs After)
# ============================================================================
g.emit("trace-comparison", 900, 360,
    bands=[
        {"x": 10,  "y": 40,  "w": 880, "h": 120, "label": "Before: Auto-instrumented (generic)", "fill": "#fef2f2"},
        {"x": 10,  "y": 190, "w": 880, "h": 150, "label": "After: Domain-named spans", "fill": "#f0fdf4"},
    ],
    nodes=[
        # Before - generic spans
        {"x": 30,  "y": 75,  "w": 190, "h": 35, "style": "sub", "lines": ["HTTP POST /api/orders/checkout"]},
        {"x": 240, "y": 75,  "w": 190, "h": 35, "style": "sub", "lines": ["HTTP POST /api/inventory/reserve"]},
        {"x": 450, "y": 75,  "w": 190, "h": 35, "style": "sub", "lines": ["HTTP POST /api/payments/authorize"]},
        {"x": 660, "y": 75,  "w": 190, "h": 35, "style": "sub", "lines": ["HTTP POST /api/shipments/schedule"]},
        # After - domain spans
        {"x": 30,  "y": 215, "w": 140, "h": 50, "style": "accent", "lines": ["Order.Checkout", "order.id, tier"]},
        {"x": 190, "y": 215, "w": 140, "h": 50, "style": "box",    "lines": ["Acl.Inventory", "Reserve"]},
        {"x": 350, "y": 215, "w": 140, "h": 50, "style": "box",    "lines": ["Inventory.Reserve", "reservation.id"]},
        {"x": 510, "y": 215, "w": 140, "h": 50, "style": "box",    "lines": ["Payment.Authorize", "authorization.id"]},
        {"x": 670, "y": 215, "w": 140, "h": 50, "style": "box",    "lines": ["Shipping.Schedule", "shipment.id"]},
        {"x": 350, "y": 290, "w": 140, "h": 35, "style": "box",    "lines": ["Events.Publish"]},
        {"x": 560, "y": 290, "w": 160, "h": 35, "style": "box",    "lines": ["Notification.Consume"]},
    ],
    edges=[
        # Before arrows
        {"x1": 220, "y1": 92, "x2": 240, "y2": 92},
        {"x1": 430, "y1": 92, "x2": 450, "y2": 92},
        {"x1": 640, "y1": 92, "x2": 660, "y2": 92},
        # After arrows
        {"x1": 170, "y1": 240, "x2": 190, "y2": 240, "amber": True},
        {"x1": 330, "y1": 240, "x2": 350, "y2": 240, "amber": True},
        {"x1": 490, "y1": 240, "x2": 510, "y2": 240, "amber": True},
        {"x1": 650, "y1": 240, "x2": 670, "y2": 240, "amber": True},
        {"x1": 100, "y1": 265, "x2": 350, "y2": 300, "dashed": True, "label": "Kafka"},
        {"x1": 490, "y1": 307, "x2": 560, "y2": 307},
    ],
    notes=[
        {"x": 450, "y": 20, "text": "Trace Comparison — Generic vs Domain-Named", "bold": True, "size": 14, "anchor": "middle", "color": "#333"},
        {"x": 820, "y": 130, "text": "No business context", "size": 10, "color": "#cc3333"},
        {"x": 820, "y": 280, "text": "Domain semantics", "size": 10, "color": "#16a34a"},
    ],
)

# ============================================================================
# 6. DDD Three-Layer Architecture
# ============================================================================
g.emit("ddd-three-layers", 700, 420,
    bands=[
        {"x": 10,  "y": 40,  "w": 680, "h": 370, "label": "Infrastructure (outermost)", "fill": "#f4f4f4"},
        {"x": 60,  "y": 100, "w": 580, "h": 250, "label": "Application (middle)", "fill": "#ecfeff"},
        {"x": 130, "y": 160, "w": 440, "h": 130, "label": "Domain (innermost — pure, no dependencies)", "fill": "#ffffff"},
    ],
    nodes=[
        # Domain layer (center)
        {"x": 150, "y": 195, "w": 120, "h": 40, "style": "accent", "lines": ["Aggregates"]},
        {"x": 285, "y": 195, "w": 120, "h": 40, "style": "accent", "lines": ["Value Objects"]},
        {"x": 420, "y": 195, "w": 120, "h": 40, "style": "accent", "lines": ["Domain Events"]},
        {"x": 215, "y": 245, "w": 120, "h": 30, "style": "accent", "lines": ["Ports (interfaces)"]},
        {"x": 355, "y": 245, "w": 120, "h": 30, "style": "accent", "lines": ["Identifiers"]},
        # Application layer
        {"x": 80,  "y": 120, "w": 130, "h": 40, "style": "box",   "lines": ["Use Cases"]},
        {"x": 250, "y": 120, "w": 130, "h": 40, "style": "box",   "lines": ["Commands"]},
        {"x": 420, "y": 120, "w": 130, "h": 40, "style": "box",   "lines": ["Saga / Orchestrator"]},
        # Infrastructure layer
        {"x": 20,  "y": 60,  "w": 120, "h": 40, "style": "kernel", "lines": ["REST / gRPC"]},
        {"x": 160, "y": 60,  "w": 100, "h": 40, "style": "kernel", "lines": ["Kafka"]},
        {"x": 280, "y": 60,  "w": 130, "h": 40, "style": "kernel", "lines": ["OTel Spans"]},
        {"x": 430, "y": 60,  "w": 130, "h": 40, "style": "kernel", "lines": ["REST Clients"]},
        {"x": 580, "y": 60,  "w": 100, "h": 40, "style": "kernel", "lines": ["Database"]},
        # Bottom infra
        {"x": 80,  "y": 360, "w": 150, "h": 40, "style": "kernel", "lines": ["Adapters (outbound)"]},
        {"x": 280, "y": 360, "w": 150, "h": 40, "style": "kernel", "lines": ["Kafka Publisher"]},
        {"x": 470, "y": 360, "w": 150, "h": 40, "style": "kernel", "lines": ["Metrics Registry"]},
    ],
    edges=[
        {"x1": 145, "y1": 100, "x2": 145, "y2": 120, "label": "depends", "lx": 35, "ly": 0},
        {"x1": 315, "y1": 100, "x2": 315, "y2": 120},
        {"x1": 485, "y1": 100, "x2": 485, "y2": 120},
        {"x1": 145, "y1": 160, "x2": 210, "y2": 195, "amber": True},
        {"x1": 315, "y1": 160, "x2": 345, "y2": 195, "amber": True},
        {"x1": 485, "y1": 160, "x2": 480, "y2": 195, "amber": True},
    ],
    notes=[
        {"x": 350, "y": 20, "text": "Hexagonal Architecture — Dependencies Point Inward", "bold": True, "size": 14, "anchor": "middle", "color": "#333"},
        {"x": 350, "y": 310, "text": "↑ Infrastructure depends on Domain (never the reverse) ↑", "size": 11, "anchor": "middle", "color": "#0e7490", "bold": True},
    ],
)

print("All 6 diagrams generated.")
