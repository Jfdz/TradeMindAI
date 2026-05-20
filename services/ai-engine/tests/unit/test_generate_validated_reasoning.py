"""Tests for GenerateValidatedReasoningUseCase — C5 orchestrator, retry budget 0 (C1.4)."""

from __future__ import annotations

from unittest.mock import MagicMock

from ai_engine.core.domain.reasoning_context import ContextResult
from ai_engine.core.domain.reasoning_output import (
    ReasoningOutcome,
    ReasoningPayload,
    ReasoningResult,
)
from ai_engine.core.domain.reasoning_validation import ReasoningValidator
from ai_engine.core.use_cases.generate_validated_reasoning import (
    GenerateValidatedReasoningUseCase,
)
from tests.unit._reasoning_factories import (
    build_news_item,
    build_reasoning_context,
    build_signal_input,
)


def _grounded_payload() -> ReasoningPayload:
    # Numbers and URL are all grounded in build_reasoning_context() defaults.
    return ReasoningPayload(
        text="Price 603.0 above sma_200 (510.0). Constructive trend.",
        price_refs=("sma_200",),
        news_refs=(),
    )


def _ungrounded_payload() -> ReasoningPayload:
    return ReasoningPayload(
        text="Price 900.0 will definitely rise to 1000.0.",
        price_refs=(),
        news_refs=(),
    )


def test_returns_first_result_when_validation_passes_immediately():
    ctx = build_reasoning_context()
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.available(ctx)
    llm = MagicMock()
    llm.generate.return_value = ReasoningResult.generated(_grounded_payload())

    result = GenerateValidatedReasoningUseCase(
        context_uc, llm, ReasoningValidator()
    ).execute(build_signal_input())

    assert result.outcome == ReasoningOutcome.GENERATED
    llm.generate.assert_called_once()  # no retry


def test_failed_first_validation_refuses_immediately_without_retry():
    # C1.4 — retry budget 0: a failed first validation is final, no
    # second LLM call. retry_count stays 0 on the persisted artifact.
    ctx = build_reasoning_context()
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.available(ctx)
    llm = MagicMock()
    llm.generate.return_value = ReasoningResult.generated(_ungrounded_payload())

    result = GenerateValidatedReasoningUseCase(
        context_uc, llm, ReasoningValidator()
    ).execute(build_signal_input())

    assert result.outcome == ReasoningOutcome.REFUSED_BY_VALIDATOR
    assert result.refusal_reason is not None
    assert "ungrounded_number" in result.refusal_reason
    assert result.retry_count == 0
    llm.generate.assert_called_once()  # no retry
    # Validator feedback is never forwarded — there is no second call.
    assert "validator_feedback" not in llm.generate.call_args.kwargs


def test_propagates_llm_refusal_on_first_call_without_retry():
    ctx = build_reasoning_context()
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.available(ctx)
    llm = MagicMock()
    llm.generate.return_value = ReasoningResult.refused_by_llm("insufficient_facts")

    result = GenerateValidatedReasoningUseCase(
        context_uc, llm, ReasoningValidator()
    ).execute(build_signal_input())

    assert result.outcome == ReasoningOutcome.REFUSED_BY_LLM
    llm.generate.assert_called_once()


def test_grounded_first_payload_generates_with_retry_count_zero():
    # Happy path explicitly pins retry_count == 0 on the artifact.
    ctx = build_reasoning_context()
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.available(ctx)
    llm = MagicMock()
    llm.generate.return_value = ReasoningResult.generated(_grounded_payload())

    result = GenerateValidatedReasoningUseCase(
        context_uc, llm, ReasoningValidator()
    ).execute(build_signal_input())

    assert result.outcome == ReasoningOutcome.GENERATED
    assert result.retry_count == 0
    llm.generate.assert_called_once()


def test_propagates_llm_error_without_retry():
    ctx = build_reasoning_context()
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.available(ctx)
    llm = MagicMock()
    llm.generate.return_value = ReasoningResult.error("anthropic_call_failed")

    result = GenerateValidatedReasoningUseCase(
        context_uc, llm, ReasoningValidator()
    ).execute(build_signal_input())

    assert result.outcome == ReasoningOutcome.ERROR
    llm.generate.assert_called_once()


def test_returns_refused_no_facts_when_context_unavailable():
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.not_tracked("UNKNOWN")
    llm = MagicMock()

    result = GenerateValidatedReasoningUseCase(
        context_uc, llm, ReasoningValidator()
    ).execute(build_signal_input(ticker="UNKNOWN"))

    assert result.outcome == ReasoningOutcome.REFUSED_NO_FACTS
    llm.generate.assert_not_called()


def test_retry_does_not_fire_when_context_has_no_news_but_payload_grounded():
    # Empty news but text grounds in price_facts only → no UNGROUNDED_NEWS_URL.
    ctx = build_reasoning_context(news=())
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.available(ctx)
    llm = MagicMock()
    llm.generate.return_value = ReasoningResult.generated(
        ReasoningPayload(
            text="No recent news. Price 603.0 sits above sma_200 (510.0).",
            price_refs=("sma_200",),
            news_refs=(),
        )
    )

    result = GenerateValidatedReasoningUseCase(
        context_uc, llm, ReasoningValidator()
    ).execute(build_signal_input())

    assert result.outcome == ReasoningOutcome.GENERATED
    llm.generate.assert_called_once()


def test_ungrounded_news_url_refuses_immediately_without_retry():
    news = (build_news_item(url="https://reuters.com/x"),)
    ctx = build_reasoning_context(news=news)
    context_uc = MagicMock()
    context_uc.execute.return_value = ContextResult.available(ctx)
    llm = MagicMock()
    llm.generate.return_value = ReasoningResult.generated(
        ReasoningPayload(
            text="Price 603.0 above sma_200 (510.0). Steady trend.",
            price_refs=(),
            news_refs=("https://hallucinated.example.com/article",),
        )
    )

    result = GenerateValidatedReasoningUseCase(
        context_uc, llm, ReasoningValidator()
    ).execute(build_signal_input())

    assert result.outcome == ReasoningOutcome.REFUSED_BY_VALIDATOR
    assert result.refusal_reason is not None
    assert "ungrounded_news_url" in result.refusal_reason
    assert result.retry_count == 0
    llm.generate.assert_called_once()
