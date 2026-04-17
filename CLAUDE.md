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

**PBI:** `E3-F10-PBI-04` - JWT login
**Feature:** FEAT-10: Authentication System
**Epic:** EPIC-3: Trading Core
**Sprint:** S5
**Jira:** SCRUM-243 -> `Listo`
**Branch:** `feature-E3-F10-authentication-system`
**Completed:** 2026-04-17

### What was built (FEAT-10 PBI-01 through PBI-04)

| PBI | Jira | What was built | Status |
|---|---|---|---|
| E3-F10-PBI-01 | SCRUM-240 | `domain/model/User.java`, `Subscription.java`, `SubscriptionPlan.java` — zero framework annotations, constructor validation | Listo |
| E3-F10-PBI-02 | SCRUM-241 | `V1__create_users_table.sql`, `V2__create_subscriptions_table.sql` in `trading_core` schema | Listo |
| E3-F10-PBI-03 | SCRUM-242 | `POST /api/v1/auth/register` — BCrypt(12), FREE subscription auto-created, 201 response | Listo |
| E3-F10-PBI-04 | SCRUM-243 | `POST /api/v1/auth/login` — JWT (15 min) + refresh token (7d HTTP-only cookie) in Redis | Listo |

---

## Next In Development

**PBI:** `E3-F10-PBI-05` - JWT authentication filter
**Feature:** FEAT-10: Authentication System
**Epic:** EPIC-3: Trading Core
**Sprint:** S5
**Jira:** SCRUM-244 -> `In Development`

### Acceptance criteria

- `JwtAuthenticationFilter` validates Bearer token on protected endpoints
- Valid JWT → SecurityContext populated; expired/invalid JWT → 401

### FEAT-10 PBI Tracker

| PBI | Jira | Title | Status |
|---|---|---|---|
| E3-F10-PBI-01 | SCRUM-240 | User domain model | Listo |
| E3-F10-PBI-02 | SCRUM-241 | Flyway migrations for users | Listo |
| E3-F10-PBI-03 | SCRUM-242 | User registration | Listo |
| E3-F10-PBI-04 | SCRUM-243 | JWT login | Listo |
| E3-F10-PBI-05 | SCRUM-244 | JWT authentication filter | In Development |
| E3-F10-PBI-06 | SCRUM-245 | Token refresh | To Do |
| E3-F10-PBI-07 | SCRUM-246 | Logout with Redis blacklist | To Do |
| E3-F10-PBI-08 | SCRUM-247 | Security configuration | To Do |

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

