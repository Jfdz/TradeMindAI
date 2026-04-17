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
| FEAT-10: Authentication System | SCRUM-240 | `In Progress` |

---

## Last Completed Task

**PBI:** `E3-F10-PBI-08` - Security configuration
**Feature:** FEAT-10: Authentication System
**Epic:** EPIC-3: Trading Core
**Sprint:** S5
**Jira:** SCRUM-247 -> `Listo`
**Branch:** `feature-E3-F10-authentication-system`
**Completed:** 2026-04-17

### What was built (full FEAT-10)

| PBI | Jira | What was built | Status |
|---|---|---|---|
| E3-F10-PBI-01 | SCRUM-240 | `domain/model/User.java`, `Subscription.java`, `SubscriptionPlan.java` — zero framework annotations, constructor validation | Listo |
| E3-F10-PBI-02 | SCRUM-241 | `V1__create_users_table.sql`, `V2__create_subscriptions_table.sql` in `trading_core` schema | Listo |
| E3-F10-PBI-03 | SCRUM-242 | `POST /api/v1/auth/register` — BCrypt(12), FREE subscription auto-created, 201 response | Listo |
| E3-F10-PBI-04 | SCRUM-243 | `POST /api/v1/auth/login` — JWT (15 min) + refresh token (7d HTTP-only cookie) in Redis | Listo |
| E3-F10-PBI-05 | SCRUM-244 | `JwtAuthenticationFilter` — Bearer validation, blacklist check, SecurityContext population | Listo |
| E3-F10-PBI-06 | SCRUM-245 | `POST /api/v1/auth/refresh` — refresh token rotation (invalidate old, issue new) | Listo |
| E3-F10-PBI-07 | SCRUM-246 | `POST /api/v1/auth/logout` — refresh token deleted + access token blacklisted in Redis | Listo |
| E3-F10-PBI-08 | SCRUM-247 | `SecurityConfig` — STATELESS, endpoint rules (public/admin/auth), JSON 401/403 handlers | Listo |

---

## Next In Development

**Feature:** FEAT-11: Subscription Management
**Epic:** EPIC-3: Trading Core
**Sprint:** S5
**PBI:** `E3-F11-PBI-01` → `To Do`

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

