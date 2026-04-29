from unittest.mock import AsyncMock

import pandas as pd
import pytest
from fastapi.testclient import TestClient

import ai_engine.adapters.in_.prediction as prediction_router
import ai_engine.adapters.in_.training as training_router
import ai_engine.adapters.out.db_adapter as db_adapter
import ai_engine.config as config_module
import ai_engine.main as ai_main
from ai_engine.adapters.in_.auth import require_internal_secret
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

    async def _mock_start_consumers(app):
        app.state.consumers_ready = True

    monkeypatch.setattr(ai_main, "_start_consumers", _mock_start_consumers)
    monkeypatch.setattr(training_router, "upsert_training_run", lambda *a, **kw: None)

    ai_main.app.dependency_overrides[require_internal_secret] = lambda: None
    ai_main.app.state.model_loaded = False
    ai_main.app.state.prediction_service = None
    training_router._runs.clear()
    yield
    ai_main.app.dependency_overrides.pop(require_internal_secret, None)
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
    ai_main.app.state.consumers_ready = True
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

    monkeypatch.setenv("INTERNAL_SECRET", "test-secret")
    config_module._settings = None

    training_router.upsert_training_run = lambda *a, **kw: None

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

    def _mock_load_training_run(run_id):
        stored = training_router._runs.get(run_id)
        if stored is None:
            return None
        return {
            "run_id": run_id,
            "model_version_id": stored.get("version_id"),
            "status": stored.get("status", "PENDING"),
            "hyperparameters": stored.get("params", {}),
            "metrics": stored.get("metrics", {}),
            "started_at": stored.get("started_at"),
            "finished_at": stored.get("finished_at"),
            "created_at": stored.get("started_at"),
        }

    monkeypatch.setitem(
        db_adapter.__dict__,
        "load_ohlcv",
        lambda symbols=None, min_rows=200: {
            "AAPL": _make_ohlcv(),
            "MSFT": _make_ohlcv(),
        },
    )
    monkeypatch.setitem(db_adapter.__dict__, "upsert_model_version", lambda *a, **kw: None)
    monkeypatch.setitem(db_adapter.__dict__, "load_training_run", _mock_load_training_run)

    def _mock_load_training_run(run_id):
        run = training_router._runs.get(run_id)
        if run is None:
            return None
        return {
            "run_id": run_id,
            "model_version_id": run.get("version_id"),
            "status": run.get("status"),
            "hyperparameters": run.get("params", {}),
            "metrics": run.get("metrics", {}),
            "started_at": run.get("started_at"),
            "finished_at": run.get("finished_at"),
            "created_at": run.get("started_at"),
        }

    monkeypatch.setattr(db_adapter, "load_training_run", _mock_load_training_run)

    response = client.post(
        "/api/v1/models/train",
        headers={"X-Internal-Secret": "test-secret"},
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
    assert run_id is not None
