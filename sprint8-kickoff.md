# Sprint 8 — Kickoff Checklist

> **Jul 6 - Jul 19** | **Goal**: Production-ready with CI/CD, monitoring, security, load-tested
> **Total**: 68 SP across 19 PBIs | **EPIC-6**: Production & DevOps

---

## Dependency check

```mermaid
graph LR
    E1["EPIC-1\nFoundation ✅"]
    E2["EPIC-2\nAI Engine ✅"]
    E3["EPIC-3\nTrading Core ✅"]
    E4["EPIC-4\nFrontend ✅"]
    E5["EPIC-5\nBacktesting ✅"]
    E6["EPIC-6\nProduction 🔜"]

    E1 --> E2 --> E3 --> E4 --> E5 --> E6

    style E1 fill:#10b981,color:#fff
    style E2 fill:#10b981,color:#fff
    style E3 fill:#10b981,color:#fff
    style E4 fill:#10b981,color:#fff
    style E5 fill:#10b981,color:#fff
    style E6 fill:#3b82f6,color:#fff
```

> Verify all prior features are `Listo` in `CLAUDE.md` before starting S8.

---

## Execution order

```mermaid
graph TB
    F20["FEAT-20\nProduction Docker\n3+3 SP"]
    F21["FEAT-21\nKubernetes\n5+3+3+3+3 SP"]
    F22["FEAT-22\nCI/CD Pipelines\n5+5+5 SP"]
    F23["FEAT-23\nObservability\n3+5+5+5 SP"]
    F24["FEAT-24\nSecurity & Load\n3+2+2+5 SP"]

    F20 --> F21
    F21 --> F22
    F22 --> F23
    F23 --> F24

    style F20 fill:#f59e0b,color:#1f2937
    style F21 fill:#f59e0b,color:#1f2937
    style F22 fill:#8b5cf6,color:#fff
    style F23 fill:#3b82f6,color:#fff
    style F24 fill:#ec4899,color:#fff
```

---

## Blocker infrastructure checklist

| # | What | Why | Status |
|---|---|---|---|
| 1 | Kubernetes cluster (staging + prod) | F21 — all K8s PBIs require `kubectl` | ☐ |
| 2 | Container registry (ECR / GCR / Docker Hub) | F22 — GitHub Actions needs a push target | ☐ |
| 3 | Domain + TLS certificate | F21-PBI-03 — Ingress + TLS termination | ☐ |
| 4 | Grafana instance | F23-PBI-03 — dashboards | ☐ |
| 5 | Production secrets (DB, JWT, Redis, RabbitMQ) | F21-PBI-05 — K8s Secrets | ☐ |
| 6 | `GITHUB_TOKEN` with registry + K8s permissions | F22 — CI pipelines | ☐ |
| 7 | Zipkin or Jaeger instance | F23-PBI-04 — distributed tracing | ☐ (week 2) |

---

## New environment variables to document in `.env.example`

```bash
# Registry & K8s
REGISTRY_URL=registry.example.com
K8S_NAMESPACE_STAGING=staging
K8S_NAMESPACE_PROD=production
KUBECONFIG=/path/to/kubeconfig

# Observability
GRAFANA_URL=https://grafana.example.com
PROMETHEUS_ENDPOINT=http://prometheus:9090
OTEL_EXPORTER_ENDPOINT=http://jaeger:14268/api/traces

# Load testing
K6_VUS=100
K6_DURATION=5m
```

---

## K8s secrets to create manually (never commit)

```bash
kubectl create secret generic trading-saas-secrets \
  --from-literal=DB_PASSWORD=... \
  --from-literal=JWT_SECRET=... \
  --from-literal=REDIS_PASSWORD=... \
  --from-literal=RABBITMQ_PASSWORD=... \
  --namespace=staging

# Repeat for production namespace
```

---

## CLAUDE.md updates required

### Feature Status table — add EPIC-6 rows

| Feature | Jira | Status |
|---|---|---|
| FEAT-20: Production Docker & Scanning | SCRUM-? | `To Do` |
| FEAT-21: Kubernetes | SCRUM-? | `To Do` |
| FEAT-22: CI/CD Pipelines | SCRUM-? | `To Do` |
| FEAT-23: Observability | SCRUM-? | `To Do` |
| FEAT-24: Security & Load Testing | SCRUM-? | `To Do` |

### Next In Development block

```markdown
**PBI:** `E6-F20-PBI-01` - Production Docker Compose
**Feature:** FEAT-20: Production Docker & Scanning
**Epic:** EPIC-6: Production & DevOps
**Sprint:** S8
**Jira:** SCRUM-XXX -> `To Do`
```

---

## Jira sync commands

```bash
# Preview
python scripts/jira-sync.py --sprint S8 --dry-run

# Create + start sprint S8
python scripts/jira-sync.py --sprint S8 --start-sprint S8
```

---

## Sprint timeline

```mermaid
gantt
    title Sprint 8 — Jul 6 to Jul 19
    dateFormat  YYYY-MM-DD
    section Week 1
    FEAT-20 Production Docker   :f20, 2026-07-06, 2d
    FEAT-21 Kubernetes          :f21, after f20, 4d
    section Week 2
    FEAT-22 CI/CD Pipelines     :f22, 2026-07-13, 3d
    FEAT-23 Observability       :f23, after f22, 3d
    FEAT-24 Security & Load     :f24, 2026-07-17, 2d
```

---

## Story point distribution

```mermaid
pie title Sprint 8 — 68 SP by Feature
    "FEAT-20 Docker (6 SP)" : 6
    "FEAT-21 Kubernetes (17 SP)" : 17
    "FEAT-22 CI/CD (15 SP)" : 15
    "FEAT-23 Observability (18 SP)" : 18
    "FEAT-24 Security & Load (12 SP)" : 12
```

---

## Definition of Done — Sprint 8

- [ ] `kubectl get pods -n production` → all 4 services `Running`
- [ ] Grafana dashboard shows HTTP latency + error rate per service
- [ ] GitHub Actions triggers on PR, deploys to staging on merge to `main`
- [ ] Manual approval gate required before production deploy
- [ ] k6 report: p95 latency < 500ms for signals, < 2s for backtest submit
- [ ] No CRITICAL/HIGH CVEs in `trivy` scan of any image
- [ ] All security headers present (`curl -I https://prod-url`)
- [ ] Distributed traces visible in Zipkin/Jaeger for cross-service requests
