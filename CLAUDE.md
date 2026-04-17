# Trading SaaS - Development Progress

This file tracks Claude's progress through the PLAN_EXECUTION.md backlog.
Update after every completed PBI: record the last done task and the next in development.

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

**PBI:** `E4-F14-PBI-01` - Next.js project scaffold
**Feature:** FEAT-14: Frontend Scaffold & Auth
**Epic:** EPIC-4: Frontend Dashboard
**Sprint:** S5
**Jira:** SCRUM-265 -> `To Do`

### Acceptance criteria

- `create-next-app` with App Router, TypeScript, Tailwind, ESLint
- `npm run dev` serves at :3000
- Project structure: `src/app/`, `src/components/`, `src/lib/`, `src/hooks/`, `src/types/`

---

## Backlog Queue (Sprint 1)

| PBI | Title | Status |
|---|---|---|
| E1-F01-PBI-01 | Initialize monorepo structure | Done |
| E1-F01-PBI-02 | Create .gitignore and Makefile | Done |
| E1-F01-PBI-03 | Docker Compose for local dev | Done |
| E1-F01-PBI-04 | Database schema initialization | Done |
| E1-F01-PBI-05 | Environment variable documentation | Done |
| E1-F02-PBI-01 | Spring Boot project scaffold | Done |
| E1-F02-PBI-02 | Domain models | In Development |
| E1-F02-PBI-03 | Port interfaces | To Do |
| E1-F02-PBI-04 | Flyway migrations | To Do |
