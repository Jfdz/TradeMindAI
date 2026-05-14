"""Tests for TradingCoreReasoningSink — the C7 HTTP client."""

from __future__ import annotations

import httpx
import pytest

import ai_engine.adapters.out.trading_core_reasoning_sink as module
from ai_engine.adapters.out.trading_core_reasoning_sink import TradingCoreReasoningSink
from ai_engine.core.domain.reasoning_sink import SinkOutcome


class _FakeResponse:
    def __init__(self, status_code: int, text: str = ""):
        self.status_code = status_code
        self.text = text


def _make_sink(secret: str = "secret-x") -> TradingCoreReasoningSink:
    return TradingCoreReasoningSink(
        "https://trading-core:8082", internal_secret=secret
    )


def _payload() -> dict:
    return {
        "outcome": "GENERATED",
        "reasoning": "Price 603.0 above sma_200.",
        "provider": "anthropic_oauth",
        "modelVersion": "claude-haiku-4-5",
        "retryCount": 0,
    }


def test_persist_returns_persisted_on_204(monkeypatch):
    captured: dict = {}

    def fake_put(url, content, headers, timeout):
        captured["url"] = url
        captured["headers"] = headers
        captured["timeout"] = timeout
        captured["content"] = content
        return _FakeResponse(204)

    monkeypatch.setattr(module.httpx, "put", fake_put)

    signal_id = "sig-123"
    result = _make_sink().persist(signal_id, _payload())

    assert (
        captured["url"]
        == "https://trading-core:8082/api/v1/internal/signals/sig-123/reasoning"
    )
    assert captured["headers"]["X-Internal-Secret"] == "secret-x"
    assert captured["headers"]["Content-Type"] == "application/json"
    # Body is the JSON-serialized payload.
    assert "GENERATED" in captured["content"]
    assert result.outcome == SinkOutcome.PERSISTED
    assert result.signal_id == signal_id


def test_persist_returns_signal_not_found_on_404(monkeypatch):
    monkeypatch.setattr(module.httpx, "put", lambda *a, **kw: _FakeResponse(404))

    result = _make_sink().persist("missing-id", _payload())

    assert result.outcome == SinkOutcome.SIGNAL_NOT_FOUND


def test_persist_returns_upstream_failed_on_401(monkeypatch):
    monkeypatch.setattr(module.httpx, "put", lambda *a, **kw: _FakeResponse(401))

    result = _make_sink().persist("sig-123", _payload())

    assert result.outcome == SinkOutcome.UPSTREAM_FAILED
    assert result.detail == "unauthorized"


def test_persist_returns_upstream_failed_on_400_with_body(monkeypatch):
    monkeypatch.setattr(
        module.httpx,
        "put",
        lambda *a, **kw: _FakeResponse(400, text="{\"error\":\"outcome is required\"}"),
    )

    result = _make_sink().persist("sig-123", _payload())

    assert result.outcome == SinkOutcome.UPSTREAM_FAILED
    assert "bad_request" in (result.detail or "")
    assert "outcome is required" in (result.detail or "")


@pytest.mark.parametrize("status", [500, 502, 503, 504])
def test_persist_returns_upstream_failed_on_5xx(monkeypatch, status):
    monkeypatch.setattr(module.httpx, "put", lambda *a, **kw: _FakeResponse(status))

    result = _make_sink().persist("sig-123", _payload())

    assert result.outcome == SinkOutcome.UPSTREAM_FAILED
    assert result.detail == f"http_{status}"


def test_persist_returns_upstream_failed_on_transport_error(monkeypatch):
    def fake_put(*args, **kwargs):
        raise httpx.ConnectError("connection refused", request=None)

    monkeypatch.setattr(module.httpx, "put", fake_put)

    result = _make_sink().persist("sig-123", _payload())

    assert result.outcome == SinkOutcome.UPSTREAM_FAILED
    assert "transport" in (result.detail or "")


def test_persist_returns_upstream_failed_when_secret_missing():
    sink = TradingCoreReasoningSink("https://trading-core:8082", internal_secret="")
    result = sink.persist("sig-123", _payload())

    assert result.outcome == SinkOutcome.UPSTREAM_FAILED
    assert result.detail == "internal_secret_not_configured"


def test_persist_returns_upstream_failed_on_blank_signal_id():
    result = _make_sink().persist("   ", _payload())

    assert result.outcome == SinkOutcome.UPSTREAM_FAILED
    assert result.detail == "blank_signal_id"
