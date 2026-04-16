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

---

## Last Completed Task

**PBI:** `E2-F08-PBI-05` - Training REST endpoint
**Feature:** FEAT-08: Training Pipeline
**Epic:** EPIC-2: AI Engine
**Sprint:** S4
**Jira:** SCRUM-232 -> `Listo`
**Branch:** `feature-E2-F08-training-pipeline`
**Completed:** 2026-04-16

### What was built (full FEAT-08)

| PBI | Jira | What was built | Status |
|---|---|---|---|
| E2-F08-PBI-01 | SCRUM-228 | `core/domain/data_loader.py` — temporal 70/15/15 split, no shuffle on val/test | Listo |
| E2-F08-PBI-02 | SCRUM-229 | `core/use_cases/trainer.py` — CrossEntropyLoss (weighted), AdamW, ReduceLROnPlateau, early stopping (patience=10) | Listo |
| E2-F08-PBI-03 | SCRUM-230 | `core/use_cases/evaluator.py` — accuracy, precision/recall/F1 per class, confusion matrix | Listo |
| E2-F08-PBI-04 | SCRUM-231 | `core/use_cases/model_registry.py` — save/load state_dict + metadata.json, activate version | Listo |
| E2-F08-PBI-05 | SCRUM-232 | `adapters/in_/training.py` — `POST /api/v1/models/train` returns 202+run_id, `GET /api/v1/models/train/{run_id}` | Listo |

---

## Next In Development

**PBI:** `E2-F09-PBI-01` - Single prediction endpoint
**Feature:** FEAT-09: Prediction API & Messaging
**Epic:** EPIC-2: AI Engine
**Sprint:** S4
**Jira:** TBD -> `To Do`

### Acceptance criteria

- `POST /api/v1/predict` with ticker returns `{direction, confidence, predicted_change_pct}`
- Active model must be loaded; returns 503 if not ready

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

