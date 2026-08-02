---
title: "Troubleshooting"
order: 22
description: "Common issues and fixes for the workshop environment."
label: "Reference"
---

Common issues and their fixes, organized by symptom.

---

## Docker daemon not running

**Symptom**: `Cannot connect to the Docker daemon` or `docker: command not found`

**Fix**:

- **macOS/Windows**: Start Docker Desktop from the Applications menu. Wait for the whale icon in the system tray to stop animating.
- **Linux**: Start the Docker service:
  ```bash
  sudo systemctl start docker
  ```
- **Codespaces**: Docker is pre-installed. If you see this error, the devcontainer may not have finished initializing. Wait 1-2 minutes and try again.

---

## Port already in use

**Symptom**: `Bind for 0.0.0.0:8080 failed: port is already allocated`

**Fix**: Check for existing containers or processes using the port:

```bash
# Check for existing workshop containers
docker compose ps

# If containers are running from a previous session, stop them
docker compose down

# If a non-Docker process is using the port
lsof -i :8080
# Kill the process, or change the port mapping in compose.yaml
```

---

## Services will not start in Codespaces

**Symptom**: Services fail to start or are not reachable after creating a Codespace.

**Fix**: The devcontainer's post-create script takes 2-3 minutes to complete. Check its status:

1. Open the VS Code terminal
2. Look for the "Setting up dev container..." notification
3. Wait for it to finish before running `docker compose up`

If services are running but not reachable from your browser, check that port forwarding is configured:

1. Open the **Ports** tab in VS Code (next to Terminal)
2. Verify that ports 3000 (Grafana), 8080-8084 (services) are listed
3. If a port is missing, click **Add Port** and enter the port number
4. Set visibility to **Public** if you need to share links

---

## Grafana shows no data

**Symptom**: Dashboards are empty, Explore shows no traces or logs.

**Fix**: This is almost always a timing issue. The observability stack needs 30-60 seconds to initialize after containers start.

1. **Wait 60 seconds** after `docker compose up` completes
2. Check that the OTel Collector is running and healthy:
   ```bash
   docker compose logs otel-collector | tail -20
   ```
3. Check that Tempo, Prometheus, and Loki are running:
   ```bash
   docker compose ps | grep -E "tempo|prometheus|loki"
   ```
4. Generate some traffic so there is data to display:
   ```bash
   newman run ../../tests/collections/01-checkout-happy-path.json \
     -e ../../tests/environments/local.json
   ```
5. In Grafana, verify datasources are configured: **Settings** (gear icon) then **Data Sources**. You should see Tempo, Prometheus, and Loki.

---

## Newman tests fail

**Symptom**: `connect ECONNREFUSED 127.0.0.1:8080` or test assertions fail.

**Fix**:

1. Verify all services are running and healthy:
   ```bash
   docker compose ps
   # All services should show "Up" and "(healthy)"
   ```
2. If services are starting but not yet healthy, wait 30 seconds. The health checks take time on first startup.
3. Check service logs for errors:
   ```bash
   docker compose logs order-service | tail -20
   ```
4. If using Codespaces, make sure you are using the correct environment file and that port forwarding is active. The `local.json` environment file assumes `localhost` -- in Codespaces, you may need to use the forwarded URL.

---

## Kafka consumer not receiving events

**Symptom**: The Notification service is running but no notifications appear in logs or metrics.

**Fix**:

1. Check that Kafka is running and the topic exists:
   ```bash
   docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list
   # Should include "order-events"
   ```
2. Check the consumer group status:
   ```bash
   docker compose exec kafka kafka-consumer-groups \
     --bootstrap-server localhost:9092 \
     --group notification-service-v2 \
     --describe
   ```
3. Check the Notification service logs:
   ```bash
   docker compose logs notification-service | tail -30
   ```
4. If the consumer group shows no members, restart the Notification service:
   ```bash
   docker compose restart notification-service
   ```

---

## Traces do not appear in Tempo

**Symptom**: Grafana Explore shows no traces, even after generating traffic.

**Fix**:

1. **Wait 30 seconds**. The OTel Collector batches spans before exporting (default batch timeout is 1 second, but Tempo needs time to index).
2. Check the Collector is receiving and exporting spans:
   ```bash
   docker compose logs otel-collector | grep -i "span"
   ```
3. Check the Collector's metrics for dropped spans:
   ```bash
   curl -s http://localhost:8888/metrics | grep otelcol_receiver_accepted_spans
   ```
4. Verify the Tempo datasource in Grafana points to `http://tempo:3200`.

---

## Structured logs missing domain fields

**Symptom**: Log lines in Loki do not contain `order.id`, `customer.id`, or `customer.tier`.

**Fix**: The domain fields only appear when the code uses `DomainContext` (or equivalent) to scope them. Check that:

1. The use case wraps its logic in a `DomainContext` block:

   - **Quarkus**: `try (var ctx = DomainContext.open(...))` in the saga or use case
   - **Python**: `with DomainContext(...)` in the saga or use case
   - **C#**: `using var domainContext = new DomainContext(...)` in the saga or use case

2. The logging framework is configured for structured JSON output. Check the service's configuration:
   - **Quarkus**: `quarkus.log.console.json=true` in `application.properties`
   - **Python**: structlog is configured with `JSONRenderer` in `otel_setup.py`
   - **C#**: `builder.Logging.AddOpenTelemetry()` in `Program.cs`

---

## Metrics show NaN or no data

**Symptom**: Dashboard panels show "No data" or `NaN` values.

**Fix**: Metrics need traffic to produce data points. A single checkout is often not enough for rate-based queries.

1. Generate sustained traffic:
   ```bash
   # Run the test collection multiple times
   for i in {1..5}; do
     newman run ../../tests/collections/01-checkout-happy-path.json \
       -e ../../tests/environments/local.json
   done
   ```
2. Wait 30-60 seconds for Prometheus to scrape the new data points.
3. Check that Prometheus is scraping the Collector:
   ```bash
   curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[].health'
   ```
4. If using `rate()` or `histogram_quantile()` in a query, you need at least two data points -- which means two scrape intervals (default 15 seconds each).

---

## Container runs out of memory

**Symptom**: A container exits with code 137 (OOM killed).

**Fix**: The workshop stack requires approximately 8 GB of RAM. If you are running on a constrained machine:

1. Increase Docker's memory allocation:
   - **Docker Desktop**: Settings then Resources then Memory, set to at least 8 GB
2. Stop other Docker containers that are not part of the workshop:
   ```bash
   docker ps  # check for unrelated containers
   ```
3. If still constrained, reduce the memory limits in `compose.yaml`. The infrastructure containers (Tempo, Loki) are the largest consumers.
