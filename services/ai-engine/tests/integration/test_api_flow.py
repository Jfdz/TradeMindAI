from unittest.mock import AsyncMock

import pandas as pd
import pytest
from fastapi.testclient import TestClient

import ai_engine.adapters.in_.prediction as prediction_router
import ai_engine.adapters.in_.training as training_router
import ai_engine.main as ai_main
from ai_engine.core.use_cases.prediction_service import PredictionResult


class _PredictionService:
    def __init__(self):
        self.calls = []

    def predict_one(self, ticker, ohlcv_df):
        self.calls.append((ticker, ohlcv_df))
        return PredictionResult(
            ticker=ticker,
            direction="UP",
            confidence=0.93,
            predicted_change_pct=1.5,
            raw_logits=[0.05, 0.1, 0.85],
        )


@pytest.fixture(autouse=True)
def reset_app_state(monkeypatch):
    monkeypatch.setattr(ai_main, "_apply_migrations", AsyncMock(return_value=None))
    monkeypatch.setattr(ai_main, "_start_consumers", AsyncMock(return_value=None))
    ai_main.app.state.model_loaded = False
    ai_main.app.state.consumers_ready = True
    ai_main.app.state.prediction_service = None
    training_router._runs.clear()
    yield
    training_router._runs.clear()


@pytest.fixture
def client():
    with TestClient(ai_main.app) as test_client:
        yield test_client


def test_health_and_ready_endpoints(client):
    health = client.get("/health")
    assert health.status_code == 200
    assert health.json() == {"status": "ok"}

    not_ready = client.get("/ready")
    assert not_ready.status_code == 503
    assert not_ready.json()["status"] == "not ready"

    ai_main.app.state.model_loaded = True
    ready = client.get("/ready")
    assert ready.status_code == 200
    assert ready.json() == {"status": "ready"}


def test_predict_endpoint_returns_service_output(client, monkeypatch):
    ai_main.app.state.model_loaded = True
    ai_main.app.state.prediction_service = _PredictionService()
    monkeypatch.setattr(
        prediction_router,
        "_fetch_ohlcv",
        lambda ticker: pd.DataFrame({"close": [101.0, 102.0, 103.0]}),
    )

    response = client.post("/api/v1/predict", json={"ticker": "AAPL"})

    assert response.status_code == 200
    payload = response.json()
    assert payload["ticker"] == "AAPL"
    assert payload["direction"] == "UP"
    assert payload["confidence"] == 0.93
    assert ai_main.app.state.prediction_service.calls[0][0] == "AAPL"


def test_training_flow_completes(client, monkeypatch):
    import numpy as np
    import pandas as pd

    import ai_engine.adapters.out.db_adapter as db_adapter

    n = 200
    dates = pd.date_range("2023-01-01", periods=n, freq="D")
    rng = np.random.default_rng(42)

    def _make_ohlcv():
        close = rng.uniform(100, 200, size=n).astype(np.float32)
        return pd.DataFrame(
            {
                "open": close * 0.99,
                "high": close * 1.01,
                "low": close * 0.98,
                "close": close,
                "volume": rng.uniform(1e6, 1e7, size=n),
            },
            index=dates,
        )

    monkeypatch.setattr(
        db_adapter,
        "load_ohlcv",
        lambda symbols=None, min_rows=200: {"AAPL": _make_ohlcv(), "MSFT": _make_ohlcv()},
    )
    monkeypatch.setattr(db_adapter, "upsert_training_run", lambda *a, **kw: None)
    monkeypatch.setattr(db_adapter, "upsert_model_version", lambda *a, **kw: None)

    response = client.post(
        "/api/v1/models/train",
        json={
            "version_tag": "weekly",
            "max_epochs": 1,
            "lr": 0.001,
            "batch_size": 8,
            "patience": 1,
        },
    )

    assert response.status_code == 202
    run_id = response.json()["run_id"]

    status = None
    for _ in range(20):
        status = client.get(f"/api/v1/models/train/{run_id}")
        if status.json()["status"] == "COMPLETED":
            break

    assert status is not None
    assert status.status_code == 200
    assert status.json()["status"] == "COMPLETED"
    assert "finished_at" in status.json()
