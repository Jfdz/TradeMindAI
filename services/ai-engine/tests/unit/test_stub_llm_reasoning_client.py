"""Tests for StubLlmReasoningClient."""

from __future__ import annotations

from datetime import datetime, timezone

from ai_engine.adapters.out.stub_llm_reasoning_client import StubLlmReasoningClient
from ai_engine.core.domain.reasoning_context import (
    SCHEMA_VERSION,
    PriceFacts,
    ReasoningContext,
)
from ai_engine.core.domain.reasoning_output import ReasoningOutcome, SignalInput


def _ctx() -> ReasoningContext:
    return ReasoningContext(
        schema_version=SCHEMA_VERSION,
        ticker="META",
        generated_at=datetime(2026, 5, 13, tzinfo=timezone.utc),
        price_facts=PriceFacts(
            ticker="META", timeframe="DAILY", snapshot_at="2026-05-12",
            bars_available=252, close=603.0, previous_close=590.94,
            pct_change_1d=2.04, pct_change_5d=None, pct_change_30d=None,
            high_52w=638.0, low_52w=412.0, sma_20=595.10, sma_50=580.20,
            sma_200=510.0, rsi_14=58.3, macd_histogram=1.2,
            volume=12_400_000, volume_avg_20d=14_100_000.0,
            support=580.0, resistance=620.0,
        ),
        news=(),
        errors=(),
    )


def _sig() -> SignalInput:
    return SignalInput(
        ticker="META", signal_type="BUY", confidence=0.62,
        entry_price=603.0, predicted_change_pct=4.5,
        generated_at=datetime(2026, 5, 13, tzinfo=timezone.utc),
    )


def test_stub_always_returns_refused_llm_disabled():
    result = StubLlmReasoningClient().generate(_sig(), _ctx())

    assert result.outcome == ReasoningOutcome.REFUSED_LLM_DISABLED
    assert result.payload is None
    assert result.refusal_reason == "llm_provider_is_stub"


def test_stub_does_not_inspect_signal_or_context():
    # Should not crash for an unusual signal — invariant for safe default.
    weird_signal = SignalInput(
        ticker="A", signal_type="SELL", confidence=0.0,
        entry_price=0.01, predicted_change_pct=None,
        generated_at=datetime(2020, 1, 1, tzinfo=timezone.utc),
    )
    result = StubLlmReasoningClient().generate(weird_signal, _ctx())
    assert result.outcome == ReasoningOutcome.REFUSED_LLM_DISABLED
