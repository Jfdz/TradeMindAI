"""Tests for GenerateReasoningUseCase — the C4 end-to-end orchestrator."""

from __future__ import annotations

from unittest.mock import MagicMock

from ai_engine.core.domain.reasoning_context import ContextResult
from ai_engine.core.domain.reasoning_output import (
    ReasoningOutcome,
    ReasoningPayload,
    ReasoningResult,
)
from ai_engine.core.use_cases.generate_reasoning import GenerateReasoningUseCase
from tests.unit._reasoning_factories import (
    build_reasoning_context,
    build_signal_input,
)


def test_execute_passes_signal_and_context_to_llm_when_available():
    ctx = build_reasoning_context()
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.available(ctx)
    llm = MagicMock()
    llm.generate.return_value = ReasoningResult.generated(
        ReasoningPayload(text="bullish setup", price_refs=("sma_200",), news_refs=()),
    )

    result = GenerateReasoningUseCase(context_uc, llm).execute(build_signal_input())

    assert result.outcome == ReasoningOutcome.GENERATED
    context_uc.execute.assert_called_once_with("META")
    passed_sig, passed_ctx = llm.generate.call_args.args
    assert passed_sig.ticker == "META"
    assert passed_ctx is ctx


def test_execute_returns_refused_no_facts_when_ticker_not_tracked():
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.not_tracked("UNKNOWN")
    llm = MagicMock()

    result = GenerateReasoningUseCase(context_uc, llm).execute(
        build_signal_input(ticker="UNKNOWN", entry_price=10.0, predicted_change_pct=None)
    )

    assert result.outcome == ReasoningOutcome.REFUSED_NO_FACTS
    assert result.detail is not None and "NOT_TRACKED" in result.detail
    llm.generate.assert_not_called()


def test_execute_returns_refused_no_facts_when_insufficient_history():
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.insufficient_history(
        "NEWCO", "INSUFFICIENT_HISTORY"
    )
    llm = MagicMock()

    result = GenerateReasoningUseCase(context_uc, llm).execute(
        build_signal_input(ticker="NEWCO", entry_price=10.0, predicted_change_pct=None)
    )

    assert result.outcome == ReasoningOutcome.REFUSED_NO_FACTS
    assert "INSUFFICIENT_HISTORY" in (result.detail or "")
    llm.generate.assert_not_called()


def test_execute_returns_refused_no_facts_when_upstream_failed():
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.upstream_failed("META", "http_503")
    llm = MagicMock()

    result = GenerateReasoningUseCase(context_uc, llm).execute(build_signal_input())

    assert result.outcome == ReasoningOutcome.REFUSED_NO_FACTS
    llm.generate.assert_not_called()


def test_execute_propagates_refusal_by_llm():
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.available(build_reasoning_context())
    llm = MagicMock()
    llm.generate.return_value = ReasoningResult.refused_by_llm("insufficient_facts")

    result = GenerateReasoningUseCase(context_uc, llm).execute(build_signal_input())

    assert result.outcome == ReasoningOutcome.REFUSED_BY_LLM
    assert result.refusal_reason == "insufficient_facts"


def test_execute_propagates_llm_disabled_with_stub_port():
    from ai_engine.adapters.out.stub_llm_reasoning_client import StubLlmReasoningClient

    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.available(build_reasoning_context())

    result = GenerateReasoningUseCase(context_uc, StubLlmReasoningClient()).execute(
        build_signal_input()
    )

    assert result.outcome == ReasoningOutcome.REFUSED_LLM_DISABLED
