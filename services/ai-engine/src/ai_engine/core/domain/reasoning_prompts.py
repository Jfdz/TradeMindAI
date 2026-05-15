"""System prompt + tool schema + user-prompt builder for reasoning generation.

The system prompt is the contract between this service and the LLM:
hard rules, output shape, refusal conditions. C5 validator enforces a
subset of these rules deterministically after generation.

All text is English (per the C4 decision).

NOTE on prompt caching: at ~400 tokens, the system prompt is below
Haiku 4.5's 4096-token minimum cacheable prefix. Any `cache_control`
marker would be silently ignored. When the prompt grows past 4096 tokens
(e.g. after adding few-shot examples from the C8 eval set), revisit
caching and add a breakpoint after the last frozen block.
"""

from __future__ import annotations

from ai_engine.core.domain.reasoning_context import ReasoningContext
from ai_engine.core.domain.reasoning_output import SignalInput

# Built with implicit string concatenation so each source line stays
# under the 100-char lint limit. The compiled string content is byte-for-byte
# stable — do NOT inline this back into a triple-quoted block without
# auditing line length, because the prompt is part of the C6 reasoning
# snapshot and any silent character drift makes audits noisy.
SYSTEM_PROMPT = (
    "You are a constrained writer of trading-signal reasonings for retail investors.\n"
    "\n"
    "HARD RULES — violating any returns refusal=true with an appropriate refusal_reason:\n"
    "\n"
    "1. NUMBERS: Use ONLY numbers present in <price_facts> or the deterministic "
    "fields of <signal> (entry_price, target_price, stop_loss, expected_move_pct, "
    "predicted_change_pct). Never invent prices, support/resistance levels, "
    "percentage changes, or volume figures. Every number you cite must appear in "
    "one of those two blocks verbatim.\n"
    "\n"
    "2. NEWS: Reference news ONLY from <news>. You may quote a headline verbatim; "
    "do not paraphrase facts that are not in the headline. If <news> is empty, do "
    "not mention specific events.\n"
    "\n"
    "3. CONFIDENCE: If signal.confidence < 0.50, you MUST describe this as a "
    "LOW-CONFIDENCE or TENTATIVE setup. Never call it \"balanced\", \"strong\", or "
    "\"clear\".\n"
    "\n"
    "4. NO INVENTED EVENTS: Do NOT mention acquisitions, partnerships, earnings "
    "beats, lawsuits, regulatory actions, or any corporate event that is absent "
    "from <news>.\n"
    "\n"
    "5. NO ABSOLUTES: Do NOT use words like \"definitely\", \"guaranteed\", "
    "\"certain\", \"sure thing\", \"will rise\", \"will fall\". This is a "
    "probabilistic forecast — use hedged language (\"suggests\", \"indicates\", "
    "\"may\", \"could\").\n"
    "\n"
    "6. LENGTH: Reasoning text is at most 400 characters. Be concise.\n"
    "\n"
    "7. CITATIONS: In price_refs, list the snake_case field names you used. "
    "Names may come from <price_facts> (e.g. \"sma_200\", \"rsi_14\") OR from "
    "the deterministic fields of <signal> (\"entry_price\", \"target_price\", "
    "\"stop_loss\", \"expected_move_pct\", \"predicted_change_pct\"). In news_refs, "
    "list the exact URLs of news items you referenced.\n"
    "\n"
    "8. INSUFFICIENT FACTS: If <price_facts> is missing essential fields (close or "
    "sma_200 is null), return refusal=true with refusal_reason=\"insufficient_facts\".\n"
    "\n"
    "OUTPUT FORMAT: Call the emit_reasoning tool exactly once. Never write free "
    "text outside the tool call. The tool call is the only valid response shape."
)


REASONING_TOOL_SCHEMA = {
    "name": "emit_reasoning",
    "description": (
        "Emit the grounded trading-signal reasoning. Must be called exactly once. "
        "Every number in `reasoning` must come from <price_facts> or from the "
        "deterministic numeric fields of <signal> (entry_price, target_price, "
        "stop_loss, expected_move_pct, predicted_change_pct); every event from "
        "<news>. Set refusal=true with refusal_reason if the context cannot "
        "ground a faithful reasoning."
    ),
    "input_schema": {
        "type": "object",
        "properties": {
            "reasoning": {
                "type": "string",
                "maxLength": 400,
                "description": "Plain-English reasoning. Empty string when refusal=true.",
            },
            "price_refs": {
                "type": "array",
                "items": {"type": "string"},
                "description": (
                    "Snake_case field names cited in `reasoning`. Names may come "
                    "from <price_facts> or from the deterministic numeric fields "
                    "of <signal> (entry_price, target_price, stop_loss, "
                    "expected_move_pct, predicted_change_pct)."
                ),
            },
            "news_refs": {
                "type": "array",
                "items": {"type": "string"},
                "description": "URLs of news items from <news> cited in `reasoning`.",
            },
            "refusal": {"type": "boolean"},
            "refusal_reason": {
                "anyOf": [{"type": "string"}, {"type": "null"}],
                "description": (
                    "Required when refusal=true. Examples: 'insufficient_facts', "
                    "'no_news_for_borderline_confidence', 'cannot_ground'."
                ),
            },
        },
        "required": ["reasoning", "refusal"],
        "additionalProperties": False,
    },
}


def build_user_prompt(signal: SignalInput, context: ReasoningContext) -> str:
    """Render the per-request user message containing the grounded context.

    Volatile content — never cache this. The order and labels here must
    stay aligned with the field references the system prompt uses.
    """
    pf = context.price_facts
    if context.news:
        news_lines = "\n".join(
            f"- {n.published_at} | {n.source or 'unknown'} | {n.headline} | {n.url}"
            for n in context.news
        )
    else:
        news_lines = "(no recent news in window)"

    pct = signal.predicted_change_pct
    pct_str = "null" if pct is None else f"{pct}"
    target_str = "null" if signal.target_price is None else f"{signal.target_price}"
    stop_str = "null" if signal.stop_loss is None else f"{signal.stop_loss}"
    move_str = "null" if signal.expected_move_pct is None else f"{signal.expected_move_pct}"

    return (
        "<context>\n"
        "<signal>\n"
        f"ticker: {signal.ticker}\n"
        f"type: {signal.signal_type}\n"
        f"confidence: {signal.confidence:.2f}\n"
        f"entry_price: {signal.entry_price}\n"
        f"target_price: {target_str}\n"
        f"stop_loss: {stop_str}\n"
        f"expected_move_pct: {move_str}\n"
        f"predicted_change_pct: {pct_str}\n"
        f"generated_at: {signal.generated_at.isoformat()}\n"
        "</signal>\n\n"
        "<price_facts>\n"
        f"close: {pf.close}\n"
        f"previous_close: {pf.previous_close}\n"
        f"pct_change_1d: {pf.pct_change_1d}\n"
        f"pct_change_5d: {pf.pct_change_5d}\n"
        f"pct_change_30d: {pf.pct_change_30d}\n"
        f"sma_20: {pf.sma_20}\n"
        f"sma_50: {pf.sma_50}\n"
        f"sma_200: {pf.sma_200}\n"
        f"rsi_14: {pf.rsi_14}\n"
        f"macd_histogram: {pf.macd_histogram}\n"
        f"high_52w: {pf.high_52w}\n"
        f"low_52w: {pf.low_52w}\n"
        f"support: {pf.support}\n"
        f"resistance: {pf.resistance}\n"
        f"volume: {pf.volume}\n"
        f"volume_avg_20d: {pf.volume_avg_20d}\n"
        "</price_facts>\n\n"
        f"<news>\n{news_lines}\n</news>\n"
        "</context>\n\n"
        "Generate the reasoning by calling emit_reasoning exactly once."
    )
