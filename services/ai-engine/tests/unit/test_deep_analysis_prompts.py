"""Deep-analysis prompt + tool-schema shape and user-prompt builders."""

from __future__ import annotations

from ai_engine.core.domain.deep_analysis import AnalysisRole
from ai_engine.core.domain.deep_analysis_prompts import (
    BULL_SYSTEM_PROMPT,
    JUDGE_SYSTEM_PROMPT,
    OPINION_TOOL_SCHEMA,
    VERDICT_TOOL_SCHEMA,
    build_judge_prompt,
    build_role_prompt,
    role_system_prompt,
)
from tests.unit._reasoning_factories import (
    build_news_item,
    build_reasoning_context,
    build_signal_input,
)


def test_opinion_tool_schema_shape():
    assert OPINION_TOOL_SCHEMA["name"] == "emit_opinion"
    schema = OPINION_TOOL_SCHEMA["input_schema"]
    assert set(schema["required"]) == {"opinion", "refusal"}
    assert schema["additionalProperties"] is False
    assert schema["properties"]["opinion"]["maxLength"] == 600


def test_verdict_tool_schema_shape():
    assert VERDICT_TOOL_SCHEMA["name"] == "emit_verdict"
    schema = VERDICT_TOOL_SCHEMA["input_schema"]
    assert schema["properties"]["verdict"]["enum"] == ["BULLISH", "BEARISH", "NEUTRAL"]
    assert set(schema["required"]) == {"verdict", "rationale"}
    assert schema["additionalProperties"] is False


def test_role_system_prompts_are_distinct_and_role_named():
    assert "BULL" in role_system_prompt(AnalysisRole.BULL)
    assert "BEAR" in role_system_prompt(AnalysisRole.BEAR)
    assert "RISK" in role_system_prompt(AnalysisRole.RISK)
    assert role_system_prompt(AnalysisRole.BULL) != role_system_prompt(AnalysisRole.BEAR)


def test_system_prompts_carry_grounding_rules():
    for prompt in (BULL_SYSTEM_PROMPT, JUDGE_SYSTEM_PROMPT):
        assert "NUMBERS:" in prompt
        assert "NO ABSOLUTES:" in prompt
        assert "NO INVENTED EVENTS:" in prompt


def test_build_role_prompt_includes_shared_context_and_task():
    ctx = build_reasoning_context(news=(build_news_item(),))
    prompt = build_role_prompt(AnalysisRole.BULL, build_signal_input(), ctx)
    assert "<context>" in prompt
    assert "close: 603.0" in prompt
    assert "news_urls: [1] https://reuters.com/x" in prompt
    assert "emit_opinion" in prompt


def test_build_judge_prompt_includes_both_sides_and_verdict_call():
    ctx = build_reasoning_context(news=(build_news_item(),))
    prompt = build_judge_prompt(build_signal_input(), ctx, "bull says up", "bear says down")
    assert "<bull>\nbull says up\n</bull>" in prompt
    assert "<bear>\nbear says down\n</bear>" in prompt
    assert "emit_verdict" in prompt


def test_build_judge_prompt_uses_placeholders_for_missing_sides():
    prompt = build_judge_prompt(build_signal_input(), build_reasoning_context(), "", "   ")
    assert "(bull case unavailable)" in prompt
    assert "(bear case unavailable)" in prompt
