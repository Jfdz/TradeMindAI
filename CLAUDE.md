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
| FEAT-20: Docker & Container Optimization | SCRUM-298 | `In Development` |
| FEAT-21: Kubernetes Deployment | SCRUM-301 | `To Do` |
| FEAT-22: CI/CD Pipelines | SCRUM-307 | `To Do` |
| FEAT-23: Observability | SCRUM-311 | `To Do` |
| FEAT-24: Security Hardening | SCRUM-316 | `To Do` |

---

## Current Sprint 8 Snapshot

**Last completed PBI:** `E5-F19-PBI-03` - Strategy vs benchmark chart
**Jira:** `SCRUM-297` -> `Listo`
**Next PBI:** `E6-F20-PBI-01` - Production Docker Compose
**Jira:** `SCRUM-299` -> `In Development`
**Branch:** `feature-E6-F20-docker-optimization`

---

## Last Completed Task

**PBI:** `E3-F11-PBI-03` - Rate limiting with bucket4j + Redis
**Feature:** FEAT-11: Subscription & Rate Limiting
**Epic:** EPIC-3: Trading Core
**Sprint:** S5
**Jira:** SCRUM-251 -> `Listo`
**Branch:** `feature-E3-F10-authentication-system`
**Completed:** 2026-04-17

### What was built (full FEAT-11)

| PBI | Jira | What was built | Status |
|---|---|---|---|
| E3-F11-PBI-01 | SCRUM-249 | `GET /api/v1/subscriptions/plans` — public endpoint returns FREE/BASIC/PREMIUM details | Listo |
| E3-F11-PBI-02 | SCRUM-250 | `@RequiresSubscription(plan)` annotation + `RequiresSubscriptionAspect` — 403 "Upgrade required" | Listo |
| E3-F11-PBI-03 | SCRUM-251 | `RateLimitFilter` with `LettuceBasedProxyManager` — FREE=5/day, BASIC=50/day, PREMIUM=unlimited, X-RateLimit-* headers | Listo |

---

## Next In Development

**PBI:** `E6-F20-PBI-01` - Production Docker Compose
**Feature:** FEAT-20: Docker & Container Optimization
**Epic:** EPIC-6: Production & DevOps
**Sprint:** S8
**Jira:** SCRUM-299 -> `In Development`

### Acceptance criteria

- `docker-compose.prod.yml` with resource limits (CPU/memory) for all 4 services
- Restart policies set (`restart: unless-stopped`)
- Read-only filesystems with tmpfs for writable dirs where possible
- `docker compose -f docker-compose.prod.yml up` → all services start and pass health checks

---

## Backlog Queue (Sprint 8)

| PBI | Title | Status |
|---|---|---|
| E6-F20-PBI-01 | Production Docker Compose | In Development |
| E6-F20-PBI-02 | Docker image scanning | To Do |
| E6-F21-PBI-01 | K8s Deployments for all services | To Do |
| E6-F21-PBI-02 | HPA for auto-scaling | To Do |
| E6-F21-PBI-03 | Ingress + TLS | To Do |
| E6-F21-PBI-04 | NetworkPolicies | To Do |
| E6-F21-PBI-05 | ConfigMaps and Secrets | To Do |
| E6-F22-PBI-01 | GitHub Actions per service | To Do |
| E6-F22-PBI-02 | Staging deploy pipeline | To Do |
| E6-F22-PBI-03 | Production deploy pipeline | To Do |
| E6-F23-PBI-01 | Structured JSON logging | To Do |
| E6-F23-PBI-02 | Prometheus metrics | To Do |
| E6-F23-PBI-03 | Grafana dashboards | To Do |
| E6-F23-PBI-04 | Distributed tracing | To Do |
| E6-F24-PBI-01 | OWASP dependency check in CI | To Do |
| E6-F24-PBI-02 | Security headers | To Do |
| E6-F24-PBI-03 | CORS configuration | To Do |
| E6-F24-PBI-04 | Load testing with k6 | To Do |
