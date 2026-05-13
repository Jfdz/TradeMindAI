"""Verify SYSTEM_PROMPT shape, tool schema, and user prompt builder."""

from __future__ import annotations

from datetime import datetime, timezone

from ai_engine.core.domain.reasoning_context import (
    SCHEMA_VERSION,
    NewsItem,
    PriceFacts,
    ReasoningContext,
)
from ai_engine.core.domain.reasoning_output import SignalInput
from ai_engine.core.domain.reasoning_prompts import (
    REASONING_TOOL_SCHEMA,
    SYSTEM_PROMPT,
    build_user_prompt,
)


def _sample_context() -> ReasoningContext:
    return ReasoningContext(
        schema_version=SCHEMA_VERSION,
        ticker="META",
        generated_at=datetime(2026, 5, 13, 12, 0, 0, tzinfo=timezone.utc),
        price_facts=PriceFacts(
            ticker="META", timeframe="DAILY", snapshot_at="2026-05-12",
            bars_available=252, close=603.0, previous_close=590.94,
            pct_change_1d=2.04, pct_change_5d=None, pct_change_30d=None,
            high_52w=638.0, low_52w=412.0, sma_20=595.10, sma_50=580.20,
            sma_200=510.0, rsi_14=58.3, macd_histogram=1.2,
            volume=12_400_000, volume_avg_20d=14_100_000.0,
            support=580.0, resistance=620.0,
        ),
        news=(
            NewsItem(
                id=1, headline="META beats Q1 expectations",
                published_at="2026-05-12T10:00:00Z",
                url="https://reuters.com/x", source="Reuters",
            ),
        ),
        errors=(),
    )


def _sample_signal() -> SignalInput:
    return SignalInput(
        ticker="META", signal_type="BUY", confidence=0.62,
        entry_price=603.0, predicted_change_pct=4.5,
        generated_at=datetime(2026, 5, 13, 12, 0, 0, tzinfo=timezone.utc),
    )


def test_system_prompt_contains_hard_rule_anchors():
    # These anchors are the contract: the C5 validator enforces a subset
    # of these rules deterministically. If they drift here, the validator
    # will catch it — but failing this test is faster feedback.
    for anchor in ("HARD RULES", "NUMBERS:", "NEWS:", "CONFIDENCE:", "INSUFFICIENT FACTS:"):
        assert anchor in SYSTEM_PROMPT, f"missing rule anchor: {anchor}"


def test_system_prompt_forbids_absolute_words():
    # Rule 5 — these must appear in the rule text so the LLM is warned.
    for word in ("definitely", "guaranteed", "certain"):
        assert word in SYSTEM_PROMPT


def test_tool_schema_requires_reasoning_and_refusal():
    schema = REASONING_TOOL_SCHEMA["input_schema"]
    assert set(schema["required"]) == {"reasoning", "refusal"}


def test_tool_schema_disallows_additional_properties():
    assert REASONING_TOOL_SCHEMA["input_schema"]["additionalProperties"] is False


def test_tool_schema_caps_reasoning_at_400_chars():
    assert REASONING_TOOL_SCHEMA["input_schema"]["properties"]["reasoning"]["maxLength"] == 400


def test_user_prompt_includes_all_signal_fields():
    prompt = build_user_prompt(_sample_signal(), _sample_context())
    assert "ticker: META" in prompt
    assert "type: BUY" in prompt
    assert "confidence: 0.62" in prompt
    assert "entry_price: 603.0" in prompt


def test_user_prompt_includes_price_facts():
    prompt = build_user_prompt(_sample_signal(), _sample_context())
    assert "close: 603.0" in prompt
    assert "sma_200: 510.0" in prompt
    assert "rsi_14: 58.3" in prompt


def test_user_prompt_includes_news_block_with_url():
    prompt = build_user_prompt(_sample_signal(), _sample_context())
    assert "META beats Q1 expectations" in prompt
    assert "https://reuters.com/x" in prompt
    assert "Reuters" in prompt


def test_user_prompt_handles_empty_news_with_placeholder():
    ctx = _sample_context()
    empty_news_ctx = ReasoningContext(
        schema_version=ctx.schema_version, ticker=ctx.ticker,
        generated_at=ctx.generated_at, price_facts=ctx.price_facts,
        news=(), errors=(),
    )
    prompt = build_user_prompt(_sample_signal(), empty_news_ctx)
    assert "(no recent news in window)" in prompt


def test_user_prompt_renders_null_predicted_change():
    sig = SignalInput(
        ticker="META", signal_type="HOLD", confidence=0.45,
        entry_price=600.0, predicted_change_pct=None,
        generated_at=datetime(2026, 5, 13, tzinfo=timezone.utc),
    )
    prompt = build_user_prompt(sig, _sample_context())
    assert "predicted_change_pct: null" in prompt
