---
title: "Prerequisites"
order: 20
description: "Software and accounts you need before starting the workshop."
label: "Reference"
---

## What you need

| Requirement | For Codespaces | For local |
|---|---|---|
| GitHub account (free tier) | Required | Required |
| Modern web browser | Required | Required |
| Docker Desktop (or Docker Engine + Compose v2) | Included | Required |
| 8 GB RAM available for containers | Included | Required |
| Language SDK (see below) | Included | Required |
| Newman (optional) | Included | Optional |

---

## Option A: GitHub Codespaces (recommended)

The fastest way to get started. Everything is pre-configured in the devcontainer:

1. Navigate to the [workshop repository](https://github.com/patterncatalyst/domain-driven-design-observability-workshop)
2. Click **Code** then **Codespaces** then **Create codespace on main**
3. Wait for the post-create script to finish (2-3 minutes)
4. The devcontainer installs all language SDKs, Docker, Newman, and VS Code extensions automatically

No additional setup is required. Skip to Module 0.

---

## Option B: local setup

If you prefer to run locally, you need Docker and one language SDK.

### Docker

Install [Docker Desktop](https://www.docker.com/products/docker-desktop/) (macOS/Windows) or Docker Engine with the Compose v2 plugin (Linux). Verify:

```bash
docker compose version
# Docker Compose version v2.x.x
```

Allocate at least **8 GB of RAM** to Docker. The workshop runs 5 application services plus 7 infrastructure containers (Kafka, Postgres, OTel Collector, Tempo, Prometheus, Loki, Grafana).

### Language SDK

You only need the SDK for **one** language -- pick the one you are most comfortable with.

#### Quarkus (Java)

- **JDK 25** -- download from [Adoptium](https://adoptium.net/) or use [SDKMAN](https://sdkman.io/): `sdk install java 25-tem`
- **Maven 3.9+** -- or use the included Maven wrapper (`./mvnw`)

Verify:

```bash
java -version
# openjdk version "25"
mvn -version  # or ./mvnw -version
# Apache Maven 3.9.x
```

#### Python

- **Python 3.14** -- download from [python.org](https://www.python.org/) or use [pyenv](https://github.com/pyenv/pyenv): `pyenv install 3.14`
- **pip** -- included with Python

Verify:

```bash
python3 --version
# Python 3.14.x
pip3 --version
```

#### C# (.NET)

- **.NET 10 SDK** -- download from [dotnet.microsoft.com](https://dotnet.microsoft.com/download)

Verify:

```bash
dotnet --version
# 10.0.100
```

### Newman (optional)

Newman is the CLI runner for Postman collections. The workshop uses it to run pre-built test scenarios against the services.

```bash
npm install -g newman
newman --version
```

If you prefer, you can use `curl` instead -- the test collections are just HTTP requests.

---

## Verify your setup

Clone the repository and start the stack for your chosen language:

```bash
git clone https://github.com/patterncatalyst/domain-driven-design-observability-workshop.git
cd domain-driven-design-observability-workshop/exercises/python  # or quarkus, dotnet
docker compose up --build -d
```

Wait 30-60 seconds for all services to start, then verify:

```bash
# Check all containers are running
docker compose ps

# Check Grafana is accessible
curl -s http://localhost:3000/api/health | jq .

# Run the smoke test
newman run ../../tests/collections/01-checkout-happy-path.json \
  -e ../../tests/environments/local.json
```

If the smoke test passes, you are ready for Module 0.
