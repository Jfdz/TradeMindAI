"""Tests for PersistValidatedReasoningUseCase — the C7 end-to-end orchestrator."""

from __future__ import annotations

from unittest.mock import MagicMock

from ai_engine.core.domain.reasoning_output import (
    ReasoningOutcome,
    ReasoningPayload,
    ReasoningResult,
)
from ai_engine.core.domain.reasoning_sink import SinkOutcome, SinkResult
from ai_engine.core.use_cases.generate_validated_reasoning import GenerationOutcome
from ai_engine.core.use_cases.persist_validated_reasoning import (
    PersistValidatedReasoningUseCase,
)
from tests.unit._reasoning_factories import (
    build_reasoning_context,
    build_signal_input,
)


def _grounded_payload() -> ReasoningPayload:
    return ReasoningPayload(
        text="Price 603.0 above sma_200 (510.0).",
        price_refs=("sma_200",),
        news_refs=(),
    )


def test_execute_calls_sink_with_serialized_payload_on_generated():
    ctx = build_reasoning_context()
    generator = MagicMock()
    generator.execute_with_context.return_value = GenerationOutcome(
        result=ReasoningResult.generated(_grounded_payload()),
        context=ctx,
    )
    sink = MagicMock()
    sink.persist.return_value = SinkResult.persisted("sig-123")

    use_case = PersistValidatedReasoningUseCase(
        generator, sink, provider="anthropic_oauth", model_version="claude-haiku-4-5"
    )

    reasoning_result, sink_result = use_case.execute("sig-123", build_signal_input())

    assert reasoning_result.outcome == ReasoningOutcome.GENERATED
    assert sink_result.outcome == SinkOutcome.PERSISTED
    # Sink received signal_id + serialized payload with the expected fields.
    args = sink.persist.call_args.args
    assert args[0] == "sig-123"
    body = args[1]
    assert body["outcome"] == "GENERATED"
    assert body["provider"] == "anthropic_oauth"
    assert body["modelVersion"] == "claude-haiku-4-5"
    assert body["factsSnapshot"]["ticker"] == "META"
    assert body["priceRefs"] == ["sma_200"]


def test_execute_persists_refused_no_facts_without_snapshot():
    generator = MagicMock()
    generator.execute_with_context.return_value = GenerationOutcome(
        result=ReasoningResult.refused_no_facts(detail="context_outcome=NOT_TRACKED"),
        context=None,
    )
    sink = MagicMock()
    sink.persist.return_value = SinkResult.persisted("sig-123")

    use_case = PersistValidatedReasoningUseCase(
        generator, sink, provider="stub", model_version="stub-v1"
    )

    reasoning_result, sink_result = use_case.execute(
        "sig-123", build_signal_input(ticker="UNKNOWN")
    )

    assert reasoning_result.outcome == ReasoningOutcome.REFUSED_NO_FACTS
    assert sink_result.outcome == SinkOutcome.PERSISTED
    body = sink.persist.call_args.args[1]
    assert body["outcome"] == "REFUSED_NO_FACTS"
    assert body["factsSnapshot"] is None


def test_execute_persists_refused_by_validator_with_violations():
    ctx = build_reasoning_context()
    violations = ({"type": "ungrounded_number", "detail": "900.0 not in facts"},)
    generator = MagicMock()
    generator.execute_with_context.return_value = GenerationOutcome(
        result=ReasoningResult.refused_by_validator(
            reason="- [ungrounded_number] 900.0 not in facts",
            violations=violations,
        ),
        context=ctx,
    )
    sink = MagicMock()
    sink.persist.return_value = SinkResult.persisted("sig-123")

    use_case = PersistValidatedReasoningUseCase(
        generator, sink, provider="anthropic_oauth", model_version="claude-haiku-4-5"
    )

    _, sink_result = use_case.execute("sig-123", build_signal_input())

    assert sink_result.outcome == SinkOutcome.PERSISTED
    body = sink.persist.call_args.args[1]
    assert body["outcome"] == "REFUSED_BY_VALIDATOR"
    assert body["validatorViolations"] == [
        {"type": "ungrounded_number", "detail": "900.0 not in facts"}
    ]


def test_execute_returns_sink_signal_not_found_when_trading_core_404s():
    ctx = build_reasoning_context()
    generator = MagicMock()
    generator.execute_with_context.return_value = GenerationOutcome(
        result=ReasoningResult.generated(_grounded_payload()),
        context=ctx,
    )
    sink = MagicMock()
    sink.persist.return_value = SinkResult.signal_not_found("sig-unknown")

    use_case = PersistValidatedReasoningUseCase(
        generator, sink, provider="stub", model_version="stub-v1"
    )

    reasoning_result, sink_result = use_case.execute("sig-unknown", build_signal_input())

    # The reasoning still generated cleanly even though persistence failed.
    assert reasoning_result.outcome == ReasoningOutcome.GENERATED
    assert sink_result.outcome == SinkOutcome.SIGNAL_NOT_FOUND


def test_execute_returns_sink_upstream_failed_when_trading_core_unreachable():
    ctx = build_reasoning_context()
    generator = MagicMock()
    generator.execute_with_context.return_value = GenerationOutcome(
        result=ReasoningResult.generated(_grounded_payload()),
        context=ctx,
    )
    sink = MagicMock()
    sink.persist.return_value = SinkResult.upstream_failed(
        "sig-123", "transport: ConnectError"
    )

    use_case = PersistValidatedReasoningUseCase(
        generator, sink, provider="stub", model_version="stub-v1"
    )

    reasoning_result, sink_result = use_case.execute("sig-123", build_signal_input())

    assert reasoning_result.outcome == ReasoningOutcome.GENERATED
    assert sink_result.outcome == SinkOutcome.UPSTREAM_FAILED


def test_execute_skips_sink_for_unactionable_error():
    generator = MagicMock()
    generator.execute_with_context.return_value = GenerationOutcome(
        result=ReasoningResult.error("transport: ConnectError"),
        context=None,
    )
    sink = MagicMock()

    use_case = PersistValidatedReasoningUseCase(
        generator, sink, provider="stub", model_version="stub-v1"
    )

    reasoning_result, sink_result = use_case.execute("sig-123", build_signal_input())

    assert reasoning_result.outcome == ReasoningOutcome.ERROR
    # The sink was NOT called because there's nothing audit-worthy to persist
    # (no facts, no LLM raw response, just a transport blip).
    sink.persist.assert_not_called()
    assert sink_result.outcome == SinkOutcome.UPSTREAM_FAILED


def test_execute_carries_retry_count_into_payload():
    import dataclasses

    ctx = build_reasoning_context()
    generated = ReasoningResult.generated(_grounded_payload())
    retried = dataclasses.replace(generated, retry_count=1)
    generator = MagicMock()
    generator.execute_with_context.return_value = GenerationOutcome(
        result=retried, context=ctx
    )
    sink = MagicMock()
    sink.persist.return_value = SinkResult.persisted("sig-123")

    use_case = PersistValidatedReasoningUseCase(
        generator, sink, provider="anthropic_oauth", model_version="claude-haiku-4-5"
    )

    use_case.execute("sig-123", build_signal_input())

    body = sink.persist.call_args.args[1]
    assert body["retryCount"] == 1
