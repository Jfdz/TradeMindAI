# Trading SaaS - Development Progress

This file tracks Claude's progress through the PLAN_EXECUTION.md backlog.
Update after every completed PBI: record the last done task and the next in development.

---

## Environment Notes

- Maven launcher: `C:\Users\fakdu\tools\apache-maven-3.9.10\bin\mvn.cmd`
- JDK 21 home: `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`
- Use `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot` when running Maven in this shell.

---

## Feature Status

| Feature | Jira | Status |
|---|---|---|
| FEAT-04: Technical Indicators | SCRUM-204 | `Listo` |
| FEAT-05: Market Data REST API | SCRUM-208 | `Listo` |
| FEAT-06: AI Service Scaffold | SCRUM-213 | `Listo` |
| FEAT-07: CNN Model Implementation | SCRUM-220 | `Listo` |
| FEAT-08: Training Pipeline | SCRUM-227 | `Listo` |
| FEAT-09: Prediction API & Messaging | SCRUM-233 | `Listo` |
| FEAT-10: Authentication System | SCRUM-240 | `Listo` |
| FEAT-11: Subscription & Rate Limiting | SCRUM-249 | `Listo` |
| FEAT-12: Signal Generation Engine | SCRUM-252 | `Listo` |
| FEAT-13: Strategy & Risk Management | SCRUM-259 | `Listo` |
| FEAT-14: Frontend Scaffold & Auth | SCRUM-264 | `Listo` |
| FEAT-15: Landing & Marketing Pages | SCRUM-271 | `Listo` |
| FEAT-16: Dashboard Core | SCRUM-274 | `Listo` |
| FEAT-17: Charts & Visualization | SCRUM-281 | `Listo` |
| FEAT-18: Backtesting Engine Core | SCRUM-285 | `Listo` |
| FEAT-19: Backtest Configuration Form | SCRUM-290 | `Listo` |
| FEAT-20: Docker & Container Optimization | SCRUM-298 | `Listo` |
| FEAT-21: Kubernetes Deployment | SCRUM-301 | `Listo` |
| FEAT-22: CI/CD Pipelines | SCRUM-307 | `Listo` |
| FEAT-23: Observability | SCRUM-311 | `Listo` |
| FEAT-24: Security Hardening | SCRUM-316 | `Listo` |

---

## Sprint 8 — COMPLETE

All Sprint 8 PBIs delivered. EPIC-6 (Production & DevOps) is fully done.

**Last completed PBI:** `E6-F22-PBI-03` - Production deploy pipeline
**Jira:** `SCRUM-310` -> `Listo`
**Branch:** `claude/review-deployment-plan-MFCwx`
**Completed:** 2026-04-24

---

## Last Completed Task

**PBI:** `E6-F22-PBI-03` - Production deploy pipeline
**Feature:** FEAT-22: CI/CD Pipelines
**Epic:** EPIC-6: Production & DevOps
**Sprint:** S8
**Jira:** SCRUM-310 -> `Listo`
**Branch:** `claude/review-deployment-plan-MFCwx`
**Completed:** 2026-04-24

### What was built (full FEAT-22 PBI-03)

| Acceptance criterion | Status |
|---|---|
| Manual approval gate via `environment: production` in GitHub Actions | Listo |
| `verify-images` job checks all 4 images at exact DEPLOY_SHA before any deploy | Listo |
| Rolling deploy steps for market-data-service, trading-core-service, ai-engine, web-app | Listo |
| Smoke tests: frontend 200, `/actuator/health` 200, auth endpoint 401 | Listo |
| Automatic `kubectl rollout undo` for all services on any failure | Listo |
| Release git tag `prod-YYYYMMDD-HHMM-SHA` on success | Listo |

### Also completed in this epic cycle

| PBI | Jira | What was built | Status |
|---|---|---|---|
| E6-F21-PBI-01 | SCRUM-302 | Deployment + Service for all 4 services with liveness/readiness probes, resource limits, non-root securityContext | Listo |
| E6-F21-PBI-02 | SCRUM-303 | HPA for trading-core and ai-engine — CPU 70%, min 2, max 10, scale-up/down behavior tuned | Listo |
| E6-F21-PBI-03 | SCRUM-304 | Ingress with TLS, path-based routing to microservices, cert-manager ClusterIssuer for Let's Encrypt | Listo |
| E6-F21-PBI-04 | SCRUM-305 | NetworkPolicies — default deny-all, explicit allowlist per service, web-app blocked from ai-engine | Listo |
| E6-F21-PBI-05 | SCRUM-306 | ConfigMaps per service, secrets-template.yml with REPLACE_ME placeholders, kustomization.yml | Listo |
| E6-F22-PBI-01 | SCRUM-308 | GitHub Actions CI per service — build, test, OWASP, Trivy, GHCR push | Listo |
| E6-F22-PBI-02 | SCRUM-309 | Staging deploy pipeline — image verify, rolling deploy, smoke tests | Listo |
| In-cluster infra | — | postgres.yml, redis.yml, rabbitmq.yml StatefulSets/Deployments for Oracle Always Free K3s | Listo |

---

## Next In Development

**Sprint 9 planning required.** All Sprint 8 items are complete. EPIC-6 is delivered.


## Backlog Queue (Sprint 8)

| PBI | Title | Status |
|---|---|---|
| E6-F20-PBI-01 | Production Docker Compose | Listo |
| E6-F20-PBI-02 | Docker image scanning | Listo |
| E6-F21-PBI-01 | K8s Deployments for all services | Listo |
| E6-F21-PBI-02 | HPA for auto-scaling | Listo |
| E6-F21-PBI-03 | Ingress + TLS | Listo |
| E6-F21-PBI-04 | NetworkPolicies | Listo |
| E6-F21-PBI-05 | ConfigMaps and Secrets | Listo |
| E6-F22-PBI-01 | GitHub Actions per service | Listo |
| E6-F22-PBI-02 | Staging deploy pipeline | Listo |
| E6-F22-PBI-03 | Production deploy pipeline | Listo |
| E6-F23-PBI-01 | Structured JSON logging | Listo |
| E6-F23-PBI-02 | Prometheus metrics | Listo |
| E6-F23-PBI-03 | Grafana dashboards | Listo |
| E6-F23-PBI-04 | Distributed tracing | Listo |
| E6-F24-PBI-01 | OWASP dependency check in CI | Listo |
| E6-F24-PBI-02 | Security headers | Listo |
| E6-F24-PBI-03 | CORS configuration | Listo |
| E6-F24-PBI-04 | Load testing with k6 | Listo |
