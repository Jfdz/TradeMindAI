# ai-engine

Python FastAPI service hosting a 1D CNN model for stock price direction prediction (UP/NEUTRAL/DOWN) with 17-feature engineering pipeline.

## Overview

This service is the AI brain of the platform. It receives raw OHLCV data, computes 17 technical features, feeds them through a 3-block 1D CNN, and returns direction predictions with confidence scores. It also handles model training, versioning, and exposes batch prediction endpoints consumed via RabbitMQ.

## Tech Stack

- Python 3.11, FastAPI, uvicorn
- PyTorch (CNN model)
- pandas, scikit-learn (feature engineering)
- SQLAlchemy + Alembic (ai_engine schema)
- pydantic-settings (config)
- RabbitMQ (prediction requests + market data events)

## Getting Started

### Prerequisites

- Python 3.11+
- pip
- Docker (for local infra)

### Local development

```bash
make infra-up
cd services/ai-engine
pip install -e ".[dev]"
uvicorn ai_engine.main:app --reload --port 8000
```

### Environment variables

| Variable | Description | Default |
|---|---|---|
| `DATABASE_URL` | PostgreSQL connection string | — |
| `RABBITMQ_URL` | RabbitMQ AMQP URL | `amqp://guest:guest@localhost` |
| `MODEL_PATH` | Path to model artifacts | `./models/` |
| `ENABLE_GPU` | Use CUDA if available | `false` |

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/health` | No | Health check |
| `GET` | `/ready` | No | Readiness (model loaded) |
| `POST` | `/api/v1/predict` | No | Single prediction |
| `POST` | `/api/v1/predict/batch` | No | Batch predictions |
| `GET` | `/api/v1/models` | ADMIN | List model versions |
| `GET` | `/api/v1/models/active` | ADMIN | Active model info |
| `POST` | `/api/v1/models/train` | ADMIN | Trigger training run |
| `POST` | `/api/v1/models/{id}/activate` | ADMIN | Activate model version |

## Testing

```bash
pytest tests/ -v
```

## Deployment

```bash
docker build -t ai-engine .
```
