"""Tests for AnthropicLlmReasoningClient — SDK mocked via MagicMock."""

from __future__ import annotations

from datetime import datetime, timezone
from unittest.mock import MagicMock

from ai_engine.adapters.out.anthropic_llm_reasoning_client import (
    AnthropicLlmReasoningClient,
)
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
        input_tokens=100, output_tokens=50, cache_read_input_tokens=0
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

    result = AnthropicLlmReasoningClient(fake_sdk).generate(_sig(), _ctx())

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

    AnthropicLlmReasoningClient(fake_sdk).generate(_sig(), _ctx())

    call = fake_sdk.messages.create.call_args.kwargs
    assert call["model"] == "claude-haiku-4-5"
    assert call["temperature"] == 0.2
    assert call["max_tokens"] == 600
    assert call["tool_choice"] == {"type": "tool", "name": "emit_reasoning"}
    # Exactly one tool, and it is our emit_reasoning schema.
    assert len(call["tools"]) == 1
    assert call["tools"][0]["name"] == "emit_reasoning"
    # System prompt is sent as a plain string (no cache_control).
    assert isinstance(call["system"], str)


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

    result = AnthropicLlmReasoningClient(fake_sdk).generate(_sig(), _ctx())

    assert result.outcome == ReasoningOutcome.REFUSED_BY_LLM
    assert result.refusal_reason == "insufficient_facts"


def test_generate_returns_refused_by_llm_with_unspecified_when_reason_missing():
    fake_sdk = MagicMock()
    fake_sdk.messages.create.return_value = _response([
        _tool_use_block({
            "reasoning": "", "refusal": True, "price_refs": [], "news_refs": [],
        }),
    ])

    result = AnthropicLlmReasoningClient(fake_sdk).generate(_sig(), _ctx())

    assert result.outcome == ReasoningOutcome.REFUSED_BY_LLM
    assert result.refusal_reason == "unspecified"


def test_generate_returns_error_on_sdk_exception():
    fake_sdk = MagicMock()
    fake_sdk.messages.create.side_effect = RuntimeError("connection refused")

    result = AnthropicLlmReasoningClient(fake_sdk).generate(_sig(), _ctx())

    assert result.outcome == ReasoningOutcome.ERROR
    assert "connection refused" in (result.detail or "")


def test_generate_returns_error_when_no_tool_use_block():
    text_block = MagicMock()
    text_block.type = "text"
    fake_sdk = MagicMock()
    fake_sdk.messages.create.return_value = _response([text_block], stop_reason="end_turn")

    result = AnthropicLlmReasoningClient(fake_sdk).generate(_sig(), _ctx())

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

    result = AnthropicLlmReasoningClient(fake_sdk).generate(_sig(), _ctx())

    assert result.outcome == ReasoningOutcome.ERROR


def test_generate_uses_custom_model_when_provided():
    fake_sdk = MagicMock()
    fake_sdk.messages.create.return_value = _response([
        _tool_use_block({
            "reasoning": "ok", "price_refs": [], "news_refs": [],
            "refusal": False, "refusal_reason": None,
        }),
    ])

    AnthropicLlmReasoningClient(fake_sdk, model="claude-sonnet-4-6").generate(_sig(), _ctx())

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

    result = AnthropicLlmReasoningClient(fake_sdk).generate(_sig(), _ctx())

    assert result.raw_response is not None
    usage = result.raw_response["usage"]
    assert usage["input_tokens"] == 100
    assert usage["output_tokens"] == 50
