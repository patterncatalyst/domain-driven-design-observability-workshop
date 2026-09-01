# Testing the Workshop in GitHub Codespaces (and Locally)

Captured 2026-09-01. How to test the workshop end-to-end via GitHub Codespaces
(the participant path) and locally via Docker Compose.

Repo: `github.com/patterncatalyst/domain-driven-design-observability-workshop`
Branches: `main` (full impl), `workshop/quarkus|python|dotnet` (starter branches =
main + root `.devcontainer/`), `cp-4-broken` (Module 4 debugging exercise: notification
consumers hardcode `customer.tier="unknown"` in all three languages).

---

## Do you need a second GitHub ID?

Depends on which layer you're testing:

| Goal | Second account? | How |
|---|---|---|
| **Environment** (devcontainer boots, stack runs, Modules 0–5 work) | **No** | On your own repo: **Code → Codespaces → Create codespace on `workshop/<lang>`**. You can Codespace any branch of a repo you own — no fork required. |
| **The fork instructions as a real participant** | **Effectively yes** | GitHub **won't let you fork your own repo into your own account** (Fork no-ops for the owner). Use a **free second GitHub account** (best — matches the "fork as a new user" QA step) **or fork into an org you control** (different owner namespace). |

You can validate the full technical experience solo; to validate the *fork step itself*
+ the free-tier participant experience, use a separate identity (a free throwaway
account is fine).

---

## ⚠️ Gotcha to fix BEFORE testing the fork path

Since ~2022, GitHub's **Fork dialog defaults to "Copy the `main` branch only" (checked).**
If a participant accepts the default, their fork has **only `main`** — NOT
`workshop/quarkus|python|dotnet` and NOT `cp-4-broken`. Module 0 then says "switch to
`workshop/<lang>`" and there's no such branch. Current Module 0 does not call this out.

Two fixes (either/both):
1. **Fix the fork step** — instruct participants to **uncheck "Copy the `main` branch
   only"** so all branches come across (and Module 4 gets `cp-4-broken`).
2. **Add a no-fork path** (simpler for a 2-hour workshop) — "Create a Codespace directly
   on `patterncatalyst/…@workshop/<lang>`." Hands-on edits (Modules 2/3/4) live inside the
   Codespace and never need pushing, so a fork is only needed if participants want to save
   work back.

**STATUS: not yet applied to `_docs/00-introduction-setup.md`.** Pending decision on which
option(s) to include. (Claude offered to update Module 0 with the uncheck note + the
no-fork option + machine/quota note.)

---

## Machine, quota, cost

- Each workshop branch's root `.devcontainer/devcontainer.json` requests
  **`cpus: 4, memory: 8gb, storage: 32gb`**. No 4-core/8GB Codespaces tier exists, so
  GitHub provisions the **4-core / 16 GB** machine — comfortable for 5 services + 7 infra
  containers + docker-in-docker.
- Devcontainer images: `mcr.microsoft.com/devcontainers/{java:3-21-bookworm,python:3-bookworm,dotnet:10.0-bookworm}`
  plus features: java 25 (temurin) / docker-in-docker / node 22 / common-utils.
- Ports forwarded: 3000 (Grafana, auto-opens), 9090, 3200, 3100, 4317/4318, 9092, 5432,
  8080–8084, 9001.
- Lifecycle: `postCreateCommand` → `.devcontainer/post-create.sh` (pull images + build);
  `postStartCommand` → `.devcontainer/post-start.sh` (`docker compose up`). First boot
  ~5–10 min (low end on 4-core).
- **Free tier: 120 core-hours/month** → ~30 h on a 4-core box; storage within the 15 GB-month
  free allowance for short-lived codespaces.
- **Billing:** codespaces you create on your repo bill to *your* quota; a participant's fork
  bills to *theirs*.

---

## Suggested test order

1. **Solo smoke test (no fork, your account):** Codespace on `workshop/quarkus` from your
   own repo. Confirm the stack: `docker compose ps` all healthy, Grafana auto-opens on
   :3000, and a checkout returns 201 CONFIRMED. Catches any Codespaces-specific issue
   (docker-in-docker, port forwarding) before involving a second account.
2. **Authentic participant run:** second free account (or org) → **fork with ALL branches**
   → follow Module 0 verbatim, once per language.
3. **Local Docker Compose walkthrough** (already validated end-to-end by Claude — should
   match): per language, from `exercises/<lang>/`:
   ```bash
   docker compose up --build -d
   ../../tests/verify.sh                 # expect 28/28
   # then follow Modules 0–5; hands-on edits need: docker compose up --build -d <svc>
   ```

---

## What was already validated locally (reference)

All three languages (Quarkus/Python/C#) passed Modules 0–5 via Docker Compose:
`tests/verify.sh` 28/28 and Newman `00`–`04` green. Module 4's full find→fix→verify loop
was run on all three languages against `cp-4-broken` (broken → `tier=unknown` on
notification spans; fix → `tier=GOLD`). Versions standardized on Python 3.14 / JDK 25 /
.NET 10. CI green on `main`. See the `walkthrough-findings` project memory for the detailed
list of fixes. Key operational notes when following the walkthrough:
- Metric queries use the `workshop_` namespace prefix (collector-applied).
- Code edits require `docker compose up --build -d <svc>` (not `restart`) — services run as
  built images. Exception: Module 5's otel-collector `restart` is correct (config is
  volume-mounted).
- Quarkus `CartId` enforces a `cart_` prefix (500 on violation); use `cart_`-prefixed cartIds.
- OTel metric export interval: Python 10s, Quarkus/C# 60s — dashboards (`rate[5m]`) still
  work; short rate windows lag.

---

## Open follow-ups
- [ ] Apply the Module 0 fork-branch fix (uncheck "Copy the main branch only") and/or add
      the no-fork Codespaces path + machine/quota note to `_docs/00-introduction-setup.md`.
- [ ] (Optional) Add a note to Module 0 about the 4-core/16 GB machine and free-tier hours.
