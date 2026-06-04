"""Tests for TradingCoreClient — the ai-engine HTTP client for the
reasoning-context BFF on trading-core."""

from __future__ import annotations

import httpx
import pytest

import ai_engine.adapters.out.trading_core_client as module
from ai_engine.adapters.out.trading_core_client import TradingCoreClient
from ai_engine.core.domain.reasoning_context import (
    SCHEMA_VERSION,
    ContextOutcome,
)


class _FakeResponse:
    def __init__(self, status_code: int, payload: dict | None = None):
        self.status_code = status_code
        self._payload = payload or {}

    def json(self) -> dict:
        return self._payload


def _full_payload() -> dict:
    return {
        "ticker": "AAPL",
        "generatedAt": "2026-05-13T12:00:00Z",
        "priceFacts": {
            "ticker": "AAPL",
            "timeframe": "DAILY",
            "snapshotAt": "2026-05-12",
            "barsAvailable": 252,
            "close": 173.45,
            "previousClose": 170.10,
            "pctChange1d": 1.97,
            "sma20": 172.00,
            "sma50": 165.00,
            "sma200": 158.40,
            "rsi14": 58.3,
            "macdHistogram": 0.5,
            "volume": 12400000,
            "volumeAvg20d": 11500000.0,
            "high52w": 180.0,
            "low52w": 150.0,
            "support": 168.0,
            "resistance": 178.0,
        },
        "news": [
            {
                "id": 42,
                "headline": "Apple Q1 beats estimates",
                "publishedAt": "2026-05-12T10:00:00Z",
                "url": "https://example.com/a",
                "source": "Reuters",
                "image": "https://example.com/a.png",
            }
        ],
        "errors": [],
    }


def _make_client(secret: str = "secret-x") -> TradingCoreClient:
    return TradingCoreClient("https://trading-core:8082", internal_secret=secret)


def test_fetch_returns_available_outcome_on_200(monkeypatch):
    captured: dict = {}

    def fake_get(url, params, headers, timeout):
        captured["url"] = url
        captured["params"] = params
        captured["headers"] = headers
        captured["timeout"] = timeout
        return _FakeResponse(200, _full_payload())

    monkeypatch.setattr(module.httpx, "get", fake_get)

    result = _make_client().fetch_reasoning_context("AAPL", news_hours=48, news_limit=8)

    assert captured["url"] == "https://trading-core:8082/api/v1/internal/reasoning-context/AAPL"
    assert captured["params"] == {"newsHours": 48, "newsLimit": 8}
    assert captured["headers"] == {"X-Internal-Secret": "secret-x"}
    assert result.outcome == ContextOutcome.AVAILABLE
    assert result.context is not None
    assert result.context.schema_version == SCHEMA_VERSION
    assert result.context.ticker == "AAPL"
    assert result.context.price_facts.close == pytest.approx(173.45)
    assert result.context.price_facts.sma_200 == pytest.approx(158.40)
    assert result.context.price_facts.pct_change_5d is None
    assert len(result.context.news) == 1
    assert result.context.news[0].headline == "Apple Q1 beats estimates"


def test_fetch_returns_not_tracked_on_404(monkeypatch):
    monkeypatch.setattr(module.httpx, "get", lambda *a, **kw: _FakeResponse(404))

    result = _make_client().fetch_reasoning_context("UNKNOWN")

    assert result.outcome == ContextOutcome.NOT_TRACKED
    assert result.ticker == "UNKNOWN"
    assert result.context is None


def test_fetch_returns_insufficient_history_on_422(monkeypatch):
    monkeypatch.setattr(
        module.httpx,
        "get",
        lambda *a, **kw: _FakeResponse(422, {"error": "INSUFFICIENT_HISTORY", "ticker": "NEWCO"}),
    )

    result = _make_client().fetch_reasoning_context("NEWCO")

    assert result.outcome == ContextOutcome.INSUFFICIENT_HISTORY
    assert result.ticker == "NEWCO"
    assert result.detail == "INSUFFICIENT_HISTORY"


@pytest.mark.parametrize("status", [502, 503, 504])
def test_fetch_returns_upstream_failed_on_5xx(monkeypatch, status):
    monkeypatch.setattr(module.httpx, "get", lambda *a, **kw: _FakeResponse(status))

    result = _make_client().fetch_reasoning_context("AAPL")

    assert result.outcome == ContextOutcome.UPSTREAM_FAILED
    assert result.detail == f"http_{status}"


def test_fetch_returns_upstream_failed_on_401(monkeypatch):
    monkeypatch.setattr(module.httpx, "get", lambda *a, **kw: _FakeResponse(401))

    result = _make_client().fetch_reasoning_context("AAPL")

    assert result.outcome == ContextOutcome.UPSTREAM_FAILED
    assert result.detail == "unauthorized"


def test_fetch_returns_upstream_failed_on_transport_error(monkeypatch):
    def fake_get(*args, **kwargs):
        raise httpx.ConnectError("connection refused", request=None)

    monkeypatch.setattr(module.httpx, "get", fake_get)

    result = _make_client().fetch_reasoning_context("AAPL")

    assert result.outcome == ContextOutcome.UPSTREAM_FAILED
    assert "transport" in (result.detail or "")


def test_fetch_returns_upstream_failed_when_secret_missing(monkeypatch):
    client = TradingCoreClient("https://trading-core:8082", internal_secret="")
    result = client.fetch_reasoning_context("AAPL")

    assert result.outcome == ContextOutcome.UPSTREAM_FAILED
    assert result.detail == "internal_secret_not_configured"


def test_fetch_returns_upstream_failed_when_price_facts_close_is_null(monkeypatch):
    payload = _full_payload()
    payload["priceFacts"]["close"] = None
    monkeypatch.setattr(module.httpx, "get", lambda *a, **kw: _FakeResponse(200, payload))

    result = _make_client().fetch_reasoning_context("AAPL")

    assert result.outcome == ContextOutcome.UPSTREAM_FAILED
    assert "parse" in (result.detail or "")


def test_fetch_returns_upstream_failed_when_payload_missing_price_facts(monkeypatch):
    minimal_payload = {
        "ticker": "AAPL",
        "generatedAt": "2026-05-13T12:00:00Z",
        "news": [],
    }
    monkeypatch.setattr(
        module.httpx,
        "get",
        lambda *a, **kw: _FakeResponse(200, minimal_payload),
    )

    result = _make_client().fetch_reasoning_context("AAPL")

    assert result.outcome == ContextOutcome.UPSTREAM_FAILED


def test_fetch_skips_news_entries_without_url_or_headline(monkeypatch):
    payload = _full_payload()
    when = "2026-05-12T10:00:00Z"
    payload["news"] = [
        {"id": 1, "headline": "ok", "url": "https://example.com/ok", "publishedAt": when},
        {"id": 2, "headline": "", "url": "https://example.com/empty", "publishedAt": when},
        {"id": 3, "headline": "no-url", "url": "", "publishedAt": when},
    ]
    monkeypatch.setattr(module.httpx, "get", lambda *a, **kw: _FakeResponse(200, payload))

    result = _make_client().fetch_reasoning_context("AAPL")

    assert result.outcome == ContextOutcome.AVAILABLE
    assert result.context is not None
    assert len(result.context.news) == 1
    assert result.context.news[0].headline == "ok"


def test_fetch_propagates_errors_field_as_degradation(monkeypatch):
    payload = _full_payload()
    payload["news"] = []
    payload["errors"] = ["news_aggregator_unavailable"]
    monkeypatch.setattr(module.httpx, "get", lambda *a, **kw: _FakeResponse(200, payload))

    result = _make_client().fetch_reasoning_context("AAPL")

    assert result.outcome == ContextOutcome.AVAILABLE
    assert result.degradation == ("news_aggregator_unavailable",)


def test_fetch_parses_analyst_consensus_when_present(monkeypatch):
    payload = _full_payload()
    payload["analystConsensus"] = {
        "period": "2026-05-01",
        "strongBuy": 12,
        "buy": 9,
        "hold": 3,
        "sell": 1,
        "strongSell": 1,
        "total": 26,
    }
    monkeypatch.setattr(module.httpx, "get", lambda *a, **kw: _FakeResponse(200, payload))

    result = _make_client().fetch_reasoning_context("AAPL")

    ac = result.context.analyst_consensus
    assert ac is not None
    assert ac.period == "2026-05-01"
    assert ac.strong_buy == 12
    assert ac.sell == 1
    assert ac.total == 26


def test_fetch_analyst_consensus_is_none_when_absent(monkeypatch):
    # _full_payload() carries no analystConsensus key — older trading-core
    # builds and the additive parser must yield None, not raise.
    monkeypatch.setattr(module.httpx, "get", lambda *a, **kw: _FakeResponse(200, _full_payload()))

    result = _make_client().fetch_reasoning_context("AAPL")

    assert result.context is not None
    assert result.context.analyst_consensus is None


def test_fetch_parses_recent_performance_when_present(monkeypatch):
    payload = _full_payload()
    payload["recentPerformance"] = {"wins": 7, "losses": 3, "resolvedCount": 10}
    monkeypatch.setattr(module.httpx, "get", lambda *a, **kw: _FakeResponse(200, payload))

    result = _make_client().fetch_reasoning_context("AAPL")

    rp = result.context.recent_performance
    assert rp is not None
    assert rp.wins == 7
    assert rp.losses == 3
    assert rp.resolved_count == 10


def test_fetch_recent_performance_is_none_when_absent(monkeypatch):
    monkeypatch.setattr(module.httpx, "get", lambda *a, **kw: _FakeResponse(200, _full_payload()))

    result = _make_client().fetch_reasoning_context("AAPL")

    assert result.context is not None
    assert result.context.recent_performance is None
