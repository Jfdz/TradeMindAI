"""System prompts + tool schemas + user-prompt builders for deep analysis.

Four roles share the *same* grounded `<context>` block (rendered by
`reasoning_prompts.render_context_block`) so every section debates identical
facts and the C5 validator checks each against the same numbers. Bull, bear
and risk all emit through one `emit_opinion` tool; the judge emits a structured
verdict through `emit_verdict`. Keeping two schemas (not four) shrinks the
validation surface.

The hard rules mirror the grounded `SYSTEM_PROMPT` anti-hallucination rules
(numbers/news grounded, no invented events, no absolutes, integer enrichment
counts citable) minus the low-confidence-label rule, which is a UX rule for the
single grounded reasoning and does not fit an advocacy section.
"""

from __future__ import annotations

from ai_engine.core.domain.deep_analysis import AnalysisRole
from ai_engine.core.domain.reasoning_context import ReasoningContext
from ai_engine.core.domain.reasoning_output import SignalInput
from ai_engine.core.domain.reasoning_prompts import render_context_block

_SHARED_RULES = (
    "HARD RULES — any violation: refusal=true + refusal_reason.\n"
    "1 NUMBERS: cite only digits present verbatim in <price_facts> or <signal> "
    "deterministic fields (confidence, entry_price, target_price, stop_loss, "
    "expected_move_pct, predicted_change_pct). No round/approximate/invent. No "
    "match -> use words.\n"
    "2 NEWS: reference only <news> items; quote headlines verbatim. news_refs = "
    "exact URLs from the news_urls list. Empty -> no events.\n"
    "3 NO INVENTED EVENTS: no acquisition/partnership/earnings/lawsuit/regulatory "
    "absent from <news>.\n"
    "4 NO ABSOLUTES: no 'definitely', 'guaranteed', 'certain', 'sure thing', "
    "'will rise', 'will fall'. Hedge ('suggests', 'may', 'could').\n"
    "5 ENRICHMENT: integer counts in <analyst> and <track_record> may be cited "
    "verbatim; never invent them.\n"
    "6 LENGTH: <=600 chars.\n"
)

BULL_SYSTEM_PROMPT = (
    "You are the BULL analyst in a grounded trading debate. Argue the strongest "
    "evidence-based BULLISH case for this signal using only the grounded facts. "
    "Persuasive but honest — invent nothing.\n\n"
    + _SHARED_RULES
    + "\nCall emit_opinion exactly once. No free text."
)

BEAR_SYSTEM_PROMPT = (
    "You are the BEAR analyst in a grounded trading debate. Argue the strongest "
    "evidence-based BEARISH case against this signal using only the grounded "
    "facts. Persuasive but honest — invent nothing.\n\n"
    + _SHARED_RULES
    + "\nCall emit_opinion exactly once. No free text."
)

RISK_SYSTEM_PROMPT = (
    "You are the RISK analyst in a grounded trading debate. Name the key "
    "downside risks and the conditions that would invalidate this signal, using "
    "only the grounded facts. Invent nothing.\n\n"
    + _SHARED_RULES
    + "\nCall emit_opinion exactly once. No free text."
)

JUDGE_SYSTEM_PROMPT = (
    "You are the JUDGE in a grounded trading debate. Weigh the BULL and BEAR "
    "arguments against the grounded facts and deliver a verdict: BULLISH, "
    "BEARISH, or NEUTRAL. Favor the side better supported by <price_facts> and "
    "<news>; when the evidence is balanced, return NEUTRAL. The verdict is "
    "analysis, not an order — never claim certainty.\n\n"
    + _SHARED_RULES
    + "\nCall emit_verdict exactly once. No free text."
)

_ROLE_SYSTEM_PROMPTS = {
    AnalysisRole.BULL: BULL_SYSTEM_PROMPT,
    AnalysisRole.BEAR: BEAR_SYSTEM_PROMPT,
    AnalysisRole.RISK: RISK_SYSTEM_PROMPT,
}

_ROLE_INSTRUCTIONS = {
    AnalysisRole.BULL: "Argue the bullish case now. Call emit_opinion exactly once.",
    AnalysisRole.BEAR: "Argue the bearish case now. Call emit_opinion exactly once.",
    AnalysisRole.RISK: (
        "List the key risks and signal invalidators now. " "Call emit_opinion exactly once."
    ),
}


OPINION_TOOL_SCHEMA = {
    "name": "emit_opinion",
    "description": "Emit one grounded debate opinion. Call exactly once.",
    "input_schema": {
        "type": "object",
        "properties": {
            "opinion": {
                "type": "string",
                "maxLength": 600,
                "description": "The grounded argument. Empty when refusal=true.",
            },
            "price_refs": {
                "type": "array",
                "items": {"type": "string"},
                "description": "snake_case field names cited.",
            },
            "news_refs": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Exact <news> URLs cited.",
            },
            "refusal": {"type": "boolean"},
            "refusal_reason": {
                "type": ["string", "null"],
                "description": "Required when refusal=true.",
            },
        },
        "required": ["opinion", "refusal"],
        "additionalProperties": False,
    },
}


VERDICT_TOOL_SCHEMA = {
    "name": "emit_verdict",
    "description": "Emit the judge's grounded verdict. Call exactly once.",
    "input_schema": {
        "type": "object",
        "properties": {
            "verdict": {
                "type": "string",
                "enum": ["BULLISH", "BEARISH", "NEUTRAL"],
                "description": "Direction the evidence best supports.",
            },
            "rationale": {
                "type": "string",
                "maxLength": 600,
                "description": "Grounded justification weighing bull vs bear.",
            },
            "price_refs": {
                "type": "array",
                "items": {"type": "string"},
                "description": "snake_case field names cited.",
            },
            "news_refs": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Exact <news> URLs cited.",
            },
        },
        "required": ["verdict", "rationale"],
        "additionalProperties": False,
    },
}


def role_system_prompt(role: AnalysisRole) -> str:
    """System prompt for an advocacy role (bull / bear / risk)."""
    return _ROLE_SYSTEM_PROMPTS[role]


def build_role_prompt(role: AnalysisRole, signal: SignalInput, context: ReasoningContext) -> str:
    """User message for an advocacy role: shared grounded context + task line."""
    return render_context_block(signal, context) + _ROLE_INSTRUCTIONS[role]


def build_judge_prompt(
    signal: SignalInput,
    context: ReasoningContext,
    bull_text: str,
    bear_text: str,
) -> str:
    """User message for the judge: shared context + both debate sides.

    A withheld/refused side is passed as an explicit placeholder so the judge
    never silently treats an absent argument as evidence.
    """
    bull = bull_text.strip() or "(bull case unavailable)"
    bear = bear_text.strip() or "(bear case unavailable)"
    return (
        render_context_block(signal, context)
        + f"<bull>\n{bull}\n</bull>\n"
        + f"<bear>\n{bear}\n</bear>\n\n"
        + "Weigh both sides against the facts and deliver your verdict. "
        + "Call emit_verdict exactly once."
    )
