"""GenerateDeepAnalysisUseCase: orchestration, grounding, conviction, outcomes.

A fake ChatLlmPort dispatches by the role named in the system prompt, so tests
script each debate participant independently. Grounded sample strings only use
numbers present in the META context (close 603.0, sma_200 510.0, rsi_14 58.3),
so the real ReasoningValidator passes them.
"""

from __future__ import annotations

from datetime import datetime, timezone

from ai_engine.core.domain.chat_llm import ToolCallResult
from ai_engine.core.domain.deep_analysis import (
    Conviction,
    DeepAnalysisOutcome,
    VerdictDirection,
)
from ai_engine.core.domain.reasoning_context import ContextResult
from ai_engine.core.domain.reasoning_validation import ReasoningValidator
from ai_engine.core.use_cases.generate_deep_analysis import GenerateDeepAnalysisUseCase
from tests.unit._reasoning_factories import (
    build_news_item,
    build_reasoning_context,
    build_signal_input,
)

FIXED_NOW = datetime(2026, 5, 13, 12, 0, 0, tzinfo=timezone.utc)

GROUNDED_BULL = "Close 603.0 holds above sma_200 510.0 and rsi_14 58.3 suggests room."
GROUNDED_BEAR = "Near resistance; with rsi_14 58.3 the close 603.0 may stall."
GROUNDED_RISK = "A close back below sma_200 510.0 would invalidate the setup."
GROUNDED_JUDGE = "Bull edge: close 603.0 sits over sma_200 510.0; momentum may persist."


class _FakeContextUseCase:
    def __init__(self, result: ContextResult):
        self._result = result

    def execute(self, ticker: str) -> ContextResult:  # noqa: ARG002
        return self._result


class _RoleChat:
    """Dispatches complete_tool by the role named in the system prompt."""

    def __init__(self, *, bull, bear, judge, risk):
        self._by_role = {"BULL": bull, "BEAR": bear, "JUDGE": judge, "RISK": risk}
        self.roles_called: list[str] = []

    def complete_tool(self, system_prompt, user_prompt, tool):  # noqa: ARG002
        for role, result in self._by_role.items():
            if f"You are the {role}" in system_prompt:
                self.roles_called.append(role)
                return result
        raise AssertionError(f"no role matched system prompt: {system_prompt[:40]!r}")


def _opinion(text, *, news_refs=(), refusal=False, refusal_reason=None) -> ToolCallResult:
    return ToolCallResult.succeeded(
        {
            "opinion": text,
            "price_refs": [],
            "news_refs": list(news_refs),
            "refusal": refusal,
            "refusal_reason": refusal_reason,
        }
    )


def _verdict(direction, rationale="", *, news_refs=()) -> ToolCallResult:
    return ToolCallResult.succeeded(
        {
            "verdict": direction,
            "rationale": rationale,
            "price_refs": [],
            "news_refs": list(news_refs),
        }
    )


def _make_use_case(chat, *, context_result=None) -> GenerateDeepAnalysisUseCase:
    ctx = build_reasoning_context(news=(build_news_item(),))
    return GenerateDeepAnalysisUseCase(
        context_use_case=_FakeContextUseCase(context_result or ContextResult.available(ctx)),
        chat_llm=chat,
        validator=ReasoningValidator(),
        provider="minimax_oauth",
        model_version="MiniMax-M2.5-highspeed",
        clock=lambda: FIXED_NOW,
    )


def _all_grounded_chat(verdict_direction="BULLISH") -> _RoleChat:
    return _RoleChat(
        bull=_opinion(GROUNDED_BULL),
        bear=_opinion(GROUNDED_BEAR),
        judge=_verdict(verdict_direction, GROUNDED_JUDGE),
        risk=_opinion(GROUNDED_RISK),
    )


def test_refused_no_facts_when_context_unavailable():
    use_case = _make_use_case(
        _all_grounded_chat(), context_result=ContextResult.not_tracked("META")
    )
    result = use_case.execute(build_signal_input())
    assert result.outcome == DeepAnalysisOutcome.REFUSED_NO_FACTS
    assert result.analysis is None


def test_happy_path_generates_and_agrees_with_buy_signal():
    chat = _all_grounded_chat("BULLISH")
    result = _make_use_case(chat).execute(build_signal_input(signal_type="BUY"))

    assert result.outcome == DeepAnalysisOutcome.GENERATED
    analysis = result.analysis
    assert analysis is not None
    assert analysis.verdict_direction == VerdictDirection.BULLISH
    assert analysis.conviction == Conviction.AGREES
    assert analysis.generated_at == FIXED_NOW
    assert analysis.provider == "minimax_oauth"
    assert analysis.model_version == "MiniMax-M2.5-highspeed"
    assert {s.role.value for s in analysis.sections} == {"BULL", "BEAR", "RISK"}
    assert all(not s.refused for s in analysis.sections)
    assert analysis.verdict.text == GROUNDED_JUDGE
    assert chat.roles_called == ["BULL", "BEAR", "JUDGE", "RISK"]


def test_contradicting_verdict_flags_low_conviction():
    chat = _all_grounded_chat("BEARISH")
    result = _make_use_case(chat).execute(build_signal_input(signal_type="BUY"))
    assert result.analysis.conviction == Conviction.CONTRADICTS


def test_neutral_verdict_is_uncertain():
    chat = _all_grounded_chat("NEUTRAL")
    result = _make_use_case(chat).execute(build_signal_input(signal_type="BUY"))
    assert result.analysis.conviction == Conviction.UNCERTAIN


def test_judge_failure_yields_error_no_artifact():
    chat = _RoleChat(
        bull=_opinion(GROUNDED_BULL),
        bear=_opinion(GROUNDED_BEAR),
        judge=ToolCallResult.failed("no_tool_use"),
        risk=_opinion(GROUNDED_RISK),
    )
    result = _make_use_case(chat).execute(build_signal_input())
    assert result.outcome == DeepAnalysisOutcome.ERROR
    assert result.analysis is None


def test_model_refusal_in_one_section_yields_partial():
    chat = _RoleChat(
        bull=_opinion("", refusal=True, refusal_reason="no clear view"),
        bear=_opinion(GROUNDED_BEAR),
        judge=_verdict("NEUTRAL", GROUNDED_JUDGE),
        risk=_opinion(GROUNDED_RISK),
    )
    result = _make_use_case(chat).execute(build_signal_input())
    assert result.outcome == DeepAnalysisOutcome.PARTIAL
    bull = next(s for s in result.analysis.sections if s.role.value == "BULL")
    assert bull.refused
    assert bull.text == ""
    assert bull.refusal_reason == "no clear view"


def test_ungrounded_number_withholds_section_text_but_keeps_verdict():
    chat = _RoleChat(
        bull=_opinion("Price will reach 999.9 shortly."),
        bear=_opinion(GROUNDED_BEAR),
        judge=_verdict("NEUTRAL", GROUNDED_JUDGE),
        risk=_opinion(GROUNDED_RISK),
    )
    result = _make_use_case(chat).execute(build_signal_input())
    assert result.outcome == DeepAnalysisOutcome.PARTIAL
    bull = next(s for s in result.analysis.sections if s.role.value == "BULL")
    assert bull.refused
    assert bull.text == ""
    assert bull.refusal_reason == "failed_grounding_validation"
    assert any(v["type"] == "ungrounded_number" for v in bull.validator_violations)


def test_ungrounded_verdict_rationale_withheld_but_direction_survives():
    chat = _RoleChat(
        bull=_opinion(GROUNDED_BULL),
        bear=_opinion(GROUNDED_BEAR),
        judge=_verdict("BULLISH", "Target 1234.5 is in reach."),
        risk=_opinion(GROUNDED_RISK),
    )
    result = _make_use_case(chat).execute(build_signal_input(signal_type="BUY"))
    assert result.outcome == DeepAnalysisOutcome.PARTIAL
    assert result.analysis.verdict_direction == VerdictDirection.BULLISH
    assert result.analysis.conviction == Conviction.AGREES
    assert result.analysis.verdict.refused
    assert result.analysis.verdict.text == ""


def test_low_confidence_label_rule_is_not_applied_to_advocacy_sections():
    # confidence 0.40 (<0.50): grounded path would demand a 'tentative' label,
    # but advocacy sections must not be withheld for lacking it.
    chat = _all_grounded_chat("BULLISH")
    result = _make_use_case(chat).execute(build_signal_input(confidence=0.40))
    assert result.outcome == DeepAnalysisOutcome.GENERATED
    assert all(not s.refused for s in result.analysis.sections)
