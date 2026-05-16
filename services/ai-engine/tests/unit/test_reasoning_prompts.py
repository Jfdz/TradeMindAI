"""Verify SYSTEM_PROMPT shape, tool schema, and user prompt builder."""

from __future__ import annotations

import json
from datetime import datetime, timezone

from ai_engine.core.domain.reasoning_context import ReasoningContext
from ai_engine.core.domain.reasoning_output import SignalInput
from ai_engine.core.domain.reasoning_prompts import (
    REASONING_TOOL_SCHEMA,
    SYSTEM_PROMPT,
    build_user_prompt,
)
from tests.unit._reasoning_factories import (
    build_news_item,
    build_reasoning_context,
    build_signal_input,
)


def _context_with_news() -> ReasoningContext:
    return build_reasoning_context(news=(build_news_item(),))


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


def test_refusal_reason_uses_type_union_not_anyof():
    # C1.2 — anyOf[{string},{null}] compacted to type:["string","null"].
    rr = REASONING_TOOL_SCHEMA["input_schema"]["properties"]["refusal_reason"]
    assert rr["type"] == ["string", "null"]
    assert "anyOf" not in rr


def test_prompt_and_schema_stay_within_token_budget():
    # C1.7 — drift guard. Caveman-terse prompt + compacted schema must
    # not creep back toward the verbose originals (~88 / ~488 tokens).
    assert len(SYSTEM_PROMPT) < 1500
    assert len(json.dumps(REASONING_TOOL_SCHEMA)) < 1200


def test_user_prompt_news_block_is_indexed_with_url_map():
    # C1.3 — compact indexed list + a separate news_urls map keeps the
    # validator's URL grounding intact while dropping per-line verbosity.
    prompt = build_user_prompt(build_signal_input(), _context_with_news())
    assert "[1] META beats Q1 expectations (Reuters, 2026-05-12)" in prompt
    assert "news_urls: [1] https://reuters.com/x" in prompt


def test_user_prompt_includes_all_signal_fields():
    prompt = build_user_prompt(build_signal_input(), _context_with_news())
    assert "ticker: META" in prompt
    assert "type: BUY" in prompt
    assert "confidence: 0.62" in prompt
    assert "entry_price: 603.0" in prompt


def test_user_prompt_includes_price_facts():
    prompt = build_user_prompt(build_signal_input(), _context_with_news())
    assert "close: 603.0" in prompt
    assert "sma_200: 510.0" in prompt
    assert "rsi_14: 58.3" in prompt


def test_user_prompt_includes_news_block_with_url():
    prompt = build_user_prompt(build_signal_input(), _context_with_news())
    assert "META beats Q1 expectations" in prompt
    assert "https://reuters.com/x" in prompt
    assert "Reuters" in prompt


def test_user_prompt_handles_empty_news_with_placeholder():
    prompt = build_user_prompt(build_signal_input(), build_reasoning_context())
    assert "(no recent news in window)" in prompt


def test_user_prompt_renders_null_predicted_change():
    sig = SignalInput(
        ticker="META",
        signal_type="HOLD",
        confidence=0.45,
        entry_price=600.0,
        predicted_change_pct=None,
        generated_at=datetime(2026, 5, 13, tzinfo=timezone.utc),
    )
    prompt = build_user_prompt(sig, _context_with_news())
    assert "predicted_change_pct: null" in prompt


def test_user_prompt_renders_derived_prices_when_present():
    sig = build_signal_input(
        target_price=627.12,
        stop_loss=590.94,
        expected_move_pct=4.0,
    )
    prompt = build_user_prompt(sig, _context_with_news())
    assert "target_price: 627.12" in prompt
    assert "stop_loss: 590.94" in prompt
    assert "expected_move_pct: 4.0" in prompt


def test_user_prompt_renders_null_derived_prices_when_absent():
    prompt = build_user_prompt(build_signal_input(), _context_with_news())
    assert "target_price: null" in prompt
    assert "stop_loss: null" in prompt
    assert "expected_move_pct: null" in prompt
