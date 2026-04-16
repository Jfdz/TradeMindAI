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

---

## Last Completed Task

**PBI:** `E2-F07-PBI-06` - Label generator
**Feature:** FEAT-07: CNN Model Implementation
**Epic:** EPIC-2: AI Engine
**Sprint:** S3
**Jira:** SCRUM-226 -> `Listo`
**Branch:** `feature-E2-F07-CNN-model-implementation`
**Completed:** 2026-04-16

### What was built (full FEAT-07)

| PBI | Jira | What was built | Status |
|---|---|---|---|
| E2-F07-PBI-01 | SCRUM-221 | `core/ports/predictor.py` — `BasePredictor` ABC with `load_model`, `preprocess`, `predict` | Listo |
| E2-F07-PBI-02 | SCRUM-222 | `core/models/cnn.py` — `StockCNN` 3-block Conv1d(17→64→128→256), AdaptiveAvgPool, FC(256→128→3), Dropout(0.5) | Listo |
| E2-F07-PBI-03 | SCRUM-223 | `core/domain/feature_engineering.py` — 17 features: OHLCV + RSI, MACD×3, SMA×2, EMA×2, BB×2, ATR, OBV | Listo |
| E2-F07-PBI-04 | SCRUM-224 | `core/domain/normalizer.py` — `MinMaxNormalizer` fit/transform/inverse_transform | Listo |
| E2-F07-PBI-05 | SCRUM-225 | `core/domain/sequence_builder.py` — sliding window → (N_samples, 17, 60) | Listo |
| E2-F07-PBI-06 | SCRUM-226 | `core/domain/label_generator.py` — 3-class labels UP/NEUTRAL/DOWN at t+5 | Listo |

---

## Next In Development

**PBI:** `E2-F08-PBI-01` - Data loader
**Feature:** FEAT-08: Training Pipeline
**Epic:** EPIC-2: AI Engine
**Sprint:** S3
**Jira:** TBD -> `To Do`

### Acceptance criteria

- PyTorch DataLoader with train/val/test split (70/15/15, temporal)
- No temporal data leakage (train < val < test)

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

