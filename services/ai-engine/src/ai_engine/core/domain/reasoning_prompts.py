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
    "Trading-signal reasoning writer for retail investors. Tool-only output.\n"
    "\n"
    "HARD RULES — any violation: refusal=true + refusal_reason.\n"
    "1 NUMBERS: only digits from <price_facts> or <signal> deterministic fields "
    "(confidence, entry_price, target_price, stop_loss, expected_move_pct, "
    "predicted_change_pct). Verbatim. No round/approximate/restate. No match -> "
    "omit, use words.\n"
    "2 NEWS: only from <news>. Quote headlines verbatim. Empty -> no events.\n"
    "3 CONFIDENCE: confidence<0.50 -> text must contain literal 'low confidence', "
    "'low-confidence', 'tentative', or 'speculative'. No paraphrase. Never "
    "'balanced'/'strong'/'clear'.\n"
    "4 NO INVENTED EVENTS: no acquisition/partnership/earnings/lawsuit/regulatory "
    "absent from <news>.\n"
    "5 NO ABSOLUTES: no 'definitely', 'guaranteed', 'certain', 'sure thing', "
    "'will rise', 'will fall'. Hedge ('suggests', 'may', 'could').\n"
    "6 LENGTH: <=400 chars.\n"
    "7 CITATIONS: price_refs = snake_case field names used (e.g. sma_200, "
    "rsi_14, confidence). news_refs = exact URLs from the <news> news_urls list.\n"
    "8 INSUFFICIENT FACTS: close or sma_200 null -> refusal=true, "
    "refusal_reason='insufficient_facts'.\n"
    "9 ANALYST: integer counts in <analyst> (strong_buy/buy/hold/sell/"
    "strong_sell/total) may be cited verbatim; never invent analyst numbers.\n"
    "\n"
    "Call emit_reasoning exactly once. No free text."
)


REASONING_TOOL_SCHEMA = {
    "name": "emit_reasoning",
    "description": "Emit grounded trading-signal reasoning. Call exactly once.",
    "input_schema": {
        "type": "object",
        "properties": {
            "reasoning": {
                "type": "string",
                "maxLength": 400,
                "description": "Reasoning text. Empty when refusal=true.",
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
        items = "\n".join(
            f"[{i}] {n.headline} ({n.source or 'unknown'}, {n.published_at[:10]})"
            for i, n in enumerate(context.news, start=1)
        )
        urls = "  ".join(f"[{i}] {n.url}" for i, n in enumerate(context.news, start=1))
        news_lines = f"{items}\nnews_urls: {urls}"
    else:
        news_lines = "(no recent news in window)"

    ac = context.analyst_consensus
    if ac is not None:
        analyst_lines = (
            f"period: {ac.period}\n"
            f"strong_buy: {ac.strong_buy}  buy: {ac.buy}  hold: {ac.hold}  "
            f"sell: {ac.sell}  strong_sell: {ac.strong_sell}  total: {ac.total}"
        )
    else:
        analyst_lines = "(no analyst coverage)"

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
        f"confidence: {signal.confidence:.4f}\n"
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
        f"<analyst>\n{analyst_lines}\n</analyst>\n"
        "</context>\n\n"
        "Generate the reasoning by calling emit_reasoning exactly once."
    )
