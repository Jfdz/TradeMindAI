"""Internal deep-analysis endpoint: outcome->status mapping, serialization, auth."""

from __future__ import annotations

from datetime import datetime, timezone

from fastapi import FastAPI
from fastapi.testclient import TestClient

import ai_engine.config as config_module
from ai_engine.adapters.in_.auth import require_internal_secret
from ai_engine.adapters.in_.deep_analysis import router
from ai_engine.core.domain.deep_analysis import (
    DEEP_ANALYSIS_SCHEMA_VERSION,
    AnalysisRole,
    AnalysisSection,
    Conviction,
    DeepAnalysis,
    DeepAnalysisResult,
    VerdictDirection,
)

FIXED_NOW = datetime(2026, 5, 13, 12, 0, 0, tzinfo=timezone.utc)


def _analysis() -> DeepAnalysis:
    return DeepAnalysis(
        schema_version=DEEP_ANALYSIS_SCHEMA_VERSION,
        ticker="META",
        signal_type="BUY",
        generated_at=FIXED_NOW,
        sections=(
            AnalysisSection(role=AnalysisRole.BULL, text="bull holds", price_refs=("sma_200",)),
            AnalysisSection(
                role=AnalysisRole.BEAR,
                text="",
                refused=True,
                refusal_reason="failed_grounding_validation",
                validator_violations=({"type": "ungrounded_number", "detail": "x"},),
            ),
            AnalysisSection(role=AnalysisRole.RISK, text="risk note"),
        ),
        verdict=AnalysisSection(role=AnalysisRole.JUDGE, text="bull edge", price_refs=("close",)),
        verdict_direction=VerdictDirection.BULLISH,
        conviction=Conviction.AGREES,
        provider="minimax_oauth",
        model_version="MiniMax-M2.5-highspeed",
    )


class _FakeUseCase:
    def __init__(self, result: DeepAnalysisResult):
        self._result = result
        self.signal = None

    def execute(self, signal):
        self.signal = signal
        return self._result


def _app(use_case, *, override_auth: bool = True) -> FastAPI:
    app = FastAPI()
    app.include_router(router)
    if override_auth:
        app.dependency_overrides[require_internal_secret] = lambda: None
    app.state.deep_analysis_use_case = use_case
    return app


def _post(client, body=None):
    return client.post(
        "/api/v1/internal/deep-analysis/meta",
        json=body or {"signalType": "BUY", "confidence": 0.62, "entryPrice": 603.0},
    )


def test_generated_returns_200_with_camelcase_artifact():
    fake = _FakeUseCase(DeepAnalysisResult.generated(_analysis()))
    client = TestClient(_app(fake))

    response = _post(client)

    assert response.status_code == 200
    body = response.json()
    assert body["outcome"] == "GENERATED"
    assert body["ticker"] == "META"
    assert body["signalType"] == "BUY"
    assert body["verdictDirection"] == "BULLISH"
    assert body["conviction"] == "AGREES"
    assert body["modelVersion"] == "MiniMax-M2.5-highspeed"
    assert body["verdict"]["role"] == "JUDGE"
    assert body["verdict"]["priceRefs"] == ["close"]
    assert {s["role"] for s in body["sections"]} == {"BULL", "BEAR", "RISK"}
    bear = next(s for s in body["sections"] if s["role"] == "BEAR")
    assert bear["refused"] is True
    assert bear["validatorViolations"][0]["type"] == "ungrounded_number"


def test_endpoint_builds_signal_input_from_path_and_body():
    fake = _FakeUseCase(DeepAnalysisResult.generated(_analysis()))
    client = TestClient(_app(fake))

    _post(
        client,
        {
            "signalType": "SELL",
            "confidence": 0.41,
            "entryPrice": 600.0,
            "stopLoss": 615.0,
            "predictedChangePct": -3.0,
        },
    )

    assert fake.signal.ticker == "META"  # path lower-cased -> upper
    assert fake.signal.signal_type == "SELL"
    assert fake.signal.confidence == 0.41
    assert fake.signal.stop_loss == 615.0
    assert fake.signal.predicted_change_pct == -3.0


def test_partial_outcome_returns_200():
    fake = _FakeUseCase(DeepAnalysisResult.partial(_analysis()))
    client = TestClient(_app(fake))
    response = _post(client)
    assert response.status_code == 200
    assert response.json()["outcome"] == "PARTIAL"


def test_refused_no_facts_returns_422():
    fake = _FakeUseCase(DeepAnalysisResult.refused_no_facts("context_outcome=NOT_TRACKED"))
    client = TestClient(_app(fake))
    response = _post(client)
    assert response.status_code == 422


def test_error_returns_502():
    fake = _FakeUseCase(DeepAnalysisResult.error("judge_produced_no_verdict"))
    client = TestClient(_app(fake))
    response = _post(client)
    assert response.status_code == 502


def test_wrong_internal_secret_is_rejected(monkeypatch):
    monkeypatch.setenv("INTERNAL_SECRET", "right-secret")
    config_module._settings = None
    try:
        fake = _FakeUseCase(DeepAnalysisResult.generated(_analysis()))
        client = TestClient(_app(fake, override_auth=False))
        response = client.post(
            "/api/v1/internal/deep-analysis/META",
            headers={"X-Internal-Secret": "wrong-secret"},
            json={"signalType": "BUY", "confidence": 0.62, "entryPrice": 603.0},
        )
        assert response.status_code == 401
    finally:
        config_module._settings = None
