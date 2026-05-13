"""Tests for GenerateReasoningUseCase — the C4 end-to-end orchestrator."""

from __future__ import annotations

from datetime import datetime, timezone
from unittest.mock import MagicMock

from ai_engine.core.domain.reasoning_context import (
    SCHEMA_VERSION,
    ContextResult,
    PriceFacts,
    ReasoningContext,
)
from ai_engine.core.domain.reasoning_output import (
    ReasoningOutcome,
    ReasoningPayload,
    ReasoningResult,
    SignalInput,
)
from ai_engine.core.use_cases.generate_reasoning import GenerateReasoningUseCase


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


def _sig(ticker: str = "META") -> SignalInput:
    return SignalInput(
        ticker=ticker, signal_type="BUY", confidence=0.62,
        entry_price=603.0, predicted_change_pct=4.5,
        generated_at=datetime(2026, 5, 13, tzinfo=timezone.utc),
    )


def test_execute_passes_signal_and_context_to_llm_when_available():
    ctx = _ctx()
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.available(ctx)
    llm = MagicMock()
    llm.generate.return_value = ReasoningResult.generated(
        ReasoningPayload(text="bullish setup", price_refs=("sma_200",), news_refs=()),
    )

    result = GenerateReasoningUseCase(context_uc, llm).execute(_sig())

    assert result.outcome == ReasoningOutcome.GENERATED
    context_uc.execute.assert_called_once_with("META")
    passed_sig, passed_ctx = llm.generate.call_args.args
    assert passed_sig.ticker == "META"
    assert passed_ctx is ctx


def test_execute_returns_refused_no_facts_when_ticker_not_tracked():
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.not_tracked("UNKNOWN")
    llm = MagicMock()

    result = GenerateReasoningUseCase(context_uc, llm).execute(_sig("UNKNOWN"))

    assert result.outcome == ReasoningOutcome.REFUSED_NO_FACTS
    assert result.detail is not None and "NOT_TRACKED" in result.detail
    llm.generate.assert_not_called()


def test_execute_returns_refused_no_facts_when_insufficient_history():
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.insufficient_history(
        "NEWCO", "INSUFFICIENT_HISTORY"
    )
    llm = MagicMock()

    result = GenerateReasoningUseCase(context_uc, llm).execute(_sig("NEWCO"))

    assert result.outcome == ReasoningOutcome.REFUSED_NO_FACTS
    assert "INSUFFICIENT_HISTORY" in (result.detail or "")
    llm.generate.assert_not_called()


def test_execute_returns_refused_no_facts_when_upstream_failed():
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.upstream_failed("META", "http_503")
    llm = MagicMock()

    result = GenerateReasoningUseCase(context_uc, llm).execute(_sig())

    assert result.outcome == ReasoningOutcome.REFUSED_NO_FACTS
    llm.generate.assert_not_called()


def test_execute_propagates_refusal_by_llm():
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.available(_ctx())
    llm = MagicMock()
    llm.generate.return_value = ReasoningResult.refused_by_llm("insufficient_facts")

    result = GenerateReasoningUseCase(context_uc, llm).execute(_sig())

    assert result.outcome == ReasoningOutcome.REFUSED_BY_LLM
    assert result.refusal_reason == "insufficient_facts"


def test_execute_propagates_llm_disabled_with_stub_port():
    from ai_engine.adapters.out.stub_llm_reasoning_client import StubLlmReasoningClient

    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.available(_ctx())

    result = GenerateReasoningUseCase(context_uc, StubLlmReasoningClient()).execute(_sig())

    assert result.outcome == ReasoningOutcome.REFUSED_LLM_DISABLED
