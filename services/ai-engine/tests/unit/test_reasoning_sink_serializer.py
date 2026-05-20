"""Tests for build_wire_payload — the C7 serializer."""

from __future__ import annotations

import pytest

from ai_engine.core.domain.reasoning_output import (
    ReasoningOutcome,
    ReasoningPayload,
    ReasoningResult,
)
from ai_engine.core.domain.reasoning_sink import build_wire_payload, is_persistable
from tests.unit._reasoning_factories import (
    build_news_item,
    build_reasoning_context,
    build_signal_input,
)


def test_wire_payload_camel_cases_keys_matching_trading_core_dto():
    result = ReasoningResult.generated(
        ReasoningPayload(
            text="Price 603.0 above sma_200 (510.0).",
            price_refs=("sma_200",),
            news_refs=("https://reuters.com/x",),
        ),
        raw_response={"input_tokens": 100, "output_tokens": 50},
    )
    ctx = build_reasoning_context(news=(build_news_item(),))
    sig = build_signal_input()

    payload = build_wire_payload(
        result=result,
        context=ctx,
        signal=sig,
        provider="anthropic_oauth",
        model_version="claude-haiku-4-5",
    )

    # Top-level keys are exactly what UpdateReasoningRequest expects.
    assert set(payload.keys()) == {
        "outcome",
        "reasoning",
        "reasoningGeneratedAt",
        "provider",
        "modelVersion",
        "retryCount",
        "refusalReason",
        "factsSnapshot",
        "priceRefs",
        "newsRefs",
        "validatorViolations",
        "rawAudit",
    }
    assert payload["outcome"] == "GENERATED"
    assert payload["reasoning"] == "Price 603.0 above sma_200 (510.0)."
    assert payload["provider"] == "anthropic_oauth"
    assert payload["modelVersion"] == "claude-haiku-4-5"
    assert payload["retryCount"] == 0
    assert payload["priceRefs"] == ["sma_200"]
    assert payload["newsRefs"] == ["https://reuters.com/x"]
    assert payload["rawAudit"]["input_tokens"] == 100


def test_wire_payload_includes_full_facts_snapshot():
    result = ReasoningResult.generated(
        ReasoningPayload(text="ok", price_refs=(), news_refs=())
    )
    ctx = build_reasoning_context(news=(build_news_item(),))

    payload = build_wire_payload(
        result=result,
        context=ctx,
        signal=build_signal_input(),
        provider="stub",
        model_version="stub-v1",
    )

    snapshot = payload["factsSnapshot"]
    assert snapshot["schemaVersion"] == "v1.0"
    assert snapshot["ticker"] == "META"
    assert "priceFacts" in snapshot
    assert snapshot["priceFacts"]["close"] == pytest.approx(603.0)
    assert snapshot["priceFacts"]["sma_200"] == pytest.approx(510.0)
    assert len(snapshot["news"]) == 1
    assert snapshot["news"][0]["headline"] == "META beats Q1 expectations"
    # Signal facts ride along so audit can replay confidence/entry.
    assert snapshot["signal"]["signalType"] == "BUY"
    assert snapshot["signal"]["confidence"] == pytest.approx(0.62)


def test_wire_payload_drops_none_fields_from_price_facts_snapshot():
    result = ReasoningResult.generated(
        ReasoningPayload(text="ok", price_refs=(), news_refs=())
    )
    # pct_change_5d is None in the default factory.
    ctx = build_reasoning_context()

    payload = build_wire_payload(
        result=result,
        context=ctx,
        signal=build_signal_input(),
        provider="stub",
        model_version="stub-v1",
    )

    assert "pct_change_5d" not in payload["factsSnapshot"]["priceFacts"]
    # close is non-null and should be present.
    assert "close" in payload["factsSnapshot"]["priceFacts"]


def test_wire_payload_serializes_refused_by_validator_with_violations():
    violations = ({"type": "ungrounded_number", "detail": "900.0 not in facts"},)
    result = ReasoningResult.refused_by_validator(
        reason="- [ungrounded_number] 900.0 not in facts",
        raw_response={"stop_reason": "tool_use"},
        violations=violations,
    )

    payload = build_wire_payload(
        result=result,
        context=build_reasoning_context(),
        signal=build_signal_input(),
        provider="anthropic_oauth",
        model_version="claude-haiku-4-5",
    )

    assert payload["outcome"] == "REFUSED_BY_VALIDATOR"
    assert payload["reasoning"] is None
    assert payload["priceRefs"] is None
    assert payload["newsRefs"] is None
    assert payload["validatorViolations"] == [
        {"type": "ungrounded_number", "detail": "900.0 not in facts"}
    ]
    assert payload["refusalReason"] == "- [ungrounded_number] 900.0 not in facts"


def test_wire_payload_serializes_refused_no_facts_without_snapshot():
    result = ReasoningResult.refused_no_facts(detail="context_outcome=NOT_TRACKED")

    payload = build_wire_payload(
        result=result,
        context=None,
        signal=build_signal_input(ticker="UNKNOWN"),
        provider="stub",
        model_version="stub-v1",
    )

    assert payload["outcome"] == "REFUSED_NO_FACTS"
    assert payload["factsSnapshot"] is None
    assert payload["reasoning"] is None
    assert payload["validatorViolations"] is None


def test_wire_payload_carries_retry_count():
    result = ReasoningResult.generated(
        ReasoningPayload(text="ok", price_refs=("sma_200",), news_refs=())
    )
    # Simulate retry path via dataclasses.replace.
    import dataclasses

    result = dataclasses.replace(result, retry_count=1)

    payload = build_wire_payload(
        result=result,
        context=build_reasoning_context(),
        signal=build_signal_input(),
        provider="anthropic_oauth",
        model_version="claude-haiku-4-5",
    )

    assert payload["retryCount"] == 1


def test_is_persistable_skips_error_without_raw_response():
    err = ReasoningResult.error("transport: ConnectError")
    assert not is_persistable(err)


def test_is_persistable_persists_error_with_raw_response():
    err = ReasoningResult(
        outcome=ReasoningOutcome.ERROR,
        refusal_reason="provider_error",
        raw_response={"stop_reason": "max_tokens"},
        detail="anthropic_returned_no_tool_use",
    )
    assert is_persistable(err)


def test_is_persistable_persists_all_other_outcomes():
    assert is_persistable(ReasoningResult.refused_llm_disabled())
    assert is_persistable(ReasoningResult.refused_no_facts("no_ctx"))
    assert is_persistable(ReasoningResult.refused_by_llm("insufficient"))
    assert is_persistable(
        ReasoningResult.refused_by_validator("- [x] y", violations=())
    )
