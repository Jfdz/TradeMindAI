"""Tests for AnthropicLlmReasoningClient — SDK mocked via MagicMock."""

from __future__ import annotations

from unittest.mock import MagicMock

from ai_engine.adapters.out.anthropic_llm_reasoning_client import (
    AnthropicLlmReasoningClient,
)
from ai_engine.core.domain.reasoning_output import ReasoningOutcome
from tests.unit._reasoning_factories import (
    build_reasoning_context,
    build_signal_input,
)


def _tool_use_block(input_dict: dict) -> MagicMock:
    block = MagicMock()
    block.type = "tool_use"
    block.input = input_dict
    return block


def _response(blocks: list, stop_reason: str = "tool_use") -> MagicMock:
    response = MagicMock()
    response.content = blocks
    response.stop_reason = stop_reason
    response.usage = MagicMock(
        input_tokens=100,
        output_tokens=50,
        cache_read_input_tokens=0,
        cache_creation_input_tokens=0,
    )
    return response


def test_generate_returns_generated_on_successful_tool_use():
    fake_sdk = MagicMock()
    fake_sdk.messages.create.return_value = _response([
        _tool_use_block({
            "reasoning": "Price above SMA200 (510.00) with RSI14 at 58.3 suggests bullish setup.",
            "price_refs": ["sma_200", "rsi_14"],
            "news_refs": [],
            "refusal": False,
            "refusal_reason": None,
        }),
    ])

    result = AnthropicLlmReasoningClient(fake_sdk).generate(
        build_signal_input(), build_reasoning_context()
    )

    assert result.outcome == ReasoningOutcome.GENERATED
    assert result.payload is not None
    assert "510.00" in result.payload.text
    assert result.payload.price_refs == ("sma_200", "rsi_14")
    assert result.raw_response is not None
    assert result.raw_response["stop_reason"] == "tool_use"


def test_generate_passes_pinned_request_shape_to_sdk():
    fake_sdk = MagicMock()
    fake_sdk.messages.create.return_value = _response([
        _tool_use_block({
            "reasoning": "ok", "price_refs": [], "news_refs": [],
            "refusal": False, "refusal_reason": None,
        }),
    ])

    AnthropicLlmReasoningClient(fake_sdk).generate(
        build_signal_input(), build_reasoning_context()
    )

    call = fake_sdk.messages.create.call_args.kwargs
    assert call["model"] == "claude-haiku-4-5"
    assert call["temperature"] == 0.2
    assert call["max_tokens"] == 350
    assert call["tool_choice"] == {"type": "tool", "name": "emit_reasoning"}
    # Exactly one tool, and it is our emit_reasoning schema with the
    # cache_control breakpoint wired in.
    assert len(call["tools"]) == 1
    assert call["tools"][0]["name"] == "emit_reasoning"
    assert call["tools"][0]["cache_control"] == {"type": "ephemeral"}
    # System prompt is sent as a list of blocks with cache_control on the
    # only block so the cacheable prefix is well-defined.
    assert isinstance(call["system"], list)
    assert call["system"][0]["type"] == "text"
    assert call["system"][0]["cache_control"] == {"type": "ephemeral"}


def test_generate_returns_refused_by_llm_when_refusal_true():
    fake_sdk = MagicMock()
    fake_sdk.messages.create.return_value = _response([
        _tool_use_block({
            "reasoning": "",
            "refusal": True,
            "refusal_reason": "insufficient_facts",
            "price_refs": [],
            "news_refs": [],
        }),
    ])

    result = AnthropicLlmReasoningClient(fake_sdk).generate(
        build_signal_input(), build_reasoning_context()
    )

    assert result.outcome == ReasoningOutcome.REFUSED_BY_LLM
    assert result.refusal_reason == "insufficient_facts"


def test_generate_returns_refused_by_llm_with_unspecified_when_reason_missing():
    fake_sdk = MagicMock()
    fake_sdk.messages.create.return_value = _response([
        _tool_use_block({
            "reasoning": "", "refusal": True, "price_refs": [], "news_refs": [],
        }),
    ])

    result = AnthropicLlmReasoningClient(fake_sdk).generate(
        build_signal_input(), build_reasoning_context()
    )

    assert result.outcome == ReasoningOutcome.REFUSED_BY_LLM
    assert result.refusal_reason == "unspecified"


def test_generate_returns_error_on_sdk_exception():
    fake_sdk = MagicMock()
    fake_sdk.messages.create.side_effect = RuntimeError("connection refused")

    result = AnthropicLlmReasoningClient(fake_sdk).generate(
        build_signal_input(), build_reasoning_context()
    )

    assert result.outcome == ReasoningOutcome.ERROR
    assert "connection refused" in (result.detail or "")


def test_generate_returns_error_when_no_tool_use_block():
    text_block = MagicMock()
    text_block.type = "text"
    fake_sdk = MagicMock()
    fake_sdk.messages.create.return_value = _response([text_block], stop_reason="end_turn")

    result = AnthropicLlmReasoningClient(fake_sdk).generate(
        build_signal_input(), build_reasoning_context()
    )

    assert result.outcome == ReasoningOutcome.ERROR
    assert result.detail == "anthropic_returned_no_tool_use"


def test_generate_returns_error_on_empty_reasoning_text():
    fake_sdk = MagicMock()
    fake_sdk.messages.create.return_value = _response([
        _tool_use_block({
            "reasoning": "   ", "refusal": False,
            "price_refs": [], "news_refs": [],
        }),
    ])

    result = AnthropicLlmReasoningClient(fake_sdk).generate(
        build_signal_input(), build_reasoning_context()
    )

    assert result.outcome == ReasoningOutcome.ERROR


def test_generate_uses_custom_model_when_provided():
    fake_sdk = MagicMock()
    fake_sdk.messages.create.return_value = _response([
        _tool_use_block({
            "reasoning": "ok", "price_refs": [], "news_refs": [],
            "refusal": False, "refusal_reason": None,
        }),
    ])

    AnthropicLlmReasoningClient(fake_sdk, model="claude-sonnet-4-6").generate(
        build_signal_input(), build_reasoning_context()
    )

    call = fake_sdk.messages.create.call_args.kwargs
    assert call["model"] == "claude-sonnet-4-6"


def test_generate_records_usage_in_raw_response():
    fake_sdk = MagicMock()
    fake_sdk.messages.create.return_value = _response([
        _tool_use_block({
            "reasoning": "ok", "price_refs": [], "news_refs": [],
            "refusal": False, "refusal_reason": None,
        }),
    ])

    result = AnthropicLlmReasoningClient(fake_sdk).generate(
        build_signal_input(), build_reasoning_context()
    )

    assert result.raw_response is not None
    usage = result.raw_response["usage"]
    assert usage["input_tokens"] == 100
    assert usage["output_tokens"] == 50
    # Cache telemetry surfaced so cost dashboards can compute the hit ratio
    # the day the cacheable prefix grows past Haiku 4.5's 4096-token minimum.
    assert "cache_read_input_tokens" in usage
    assert "cache_creation_input_tokens" in usage
