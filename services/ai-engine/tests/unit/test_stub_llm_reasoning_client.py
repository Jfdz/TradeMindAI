"""Tests for StubLlmReasoningClient."""

from __future__ import annotations

from datetime import datetime, timezone

from ai_engine.adapters.out.stub_llm_reasoning_client import StubLlmReasoningClient
from ai_engine.core.domain.reasoning_output import ReasoningOutcome, SignalInput
from tests.unit._reasoning_factories import (
    build_reasoning_context,
    build_signal_input,
)


def test_stub_always_returns_refused_llm_disabled():
    result = StubLlmReasoningClient().generate(
        build_signal_input(), build_reasoning_context()
    )

    assert result.outcome == ReasoningOutcome.REFUSED_LLM_DISABLED
    assert result.payload is None
    assert result.refusal_reason == "llm_provider_is_stub"


def test_stub_does_not_inspect_signal_or_context():
    # Should not crash for an unusual signal — invariant for safe default.
    weird_signal = SignalInput(
        ticker="A",
        signal_type="SELL",
        confidence=0.0,
        entry_price=0.01,
        predicted_change_pct=None,
        generated_at=datetime(2020, 1, 1, tzinfo=timezone.utc),
    )
    result = StubLlmReasoningClient().generate(weird_signal, build_reasoning_context())
    assert result.outcome == ReasoningOutcome.REFUSED_LLM_DISABLED
