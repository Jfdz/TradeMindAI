"""AnthropicLlmReasoningClient — production implementation.

Auth-agnostic: receives a pre-instantiated `anthropic.Anthropic` client.
The factory layer decides whether to build that client with an OAuth
Bearer token (Claude Max subscription, temporary) or with an API key
(proper paid tier, target end state).

Design pins:
  - Model: claude-haiku-4-5 (Jan 2026, $1/$5 per 1M tokens).
  - temperature: 0.2 (low variance for production reliability).
  - max_tokens: 350 (tight cap; reasonings are <=400 chars so 350 output
    tokens is plenty and cuts per-call cost roughly in half vs the C4
    initial 600).
  - tool_choice forces exactly one emit_reasoning call — no free text.
  - Prompt caching: NOT wired. An earlier attempt sent `system` as a
    cache_control block list; in production that request shape triggered
    Anthropic 400 "messages: text content blocks must be non-empty" and
    broke reasoning generation. Caching was a no-op anyway — the
    system+tool prefix (~700 tokens) is far below Haiku 4.5's 4096-token
    minimum cacheable prefix, so the marker was always silently ignored.
    `system` is therefore sent as a plain string (the long-stable C4
    shape). Revisit caching only once the prefix genuinely exceeds 4096
    tokens, and validate the request shape against the live API first.
    Cache usage counters are still captured in the audit blob so the
    metric is ready if/when caching is correctly re-introduced.
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING

from ai_engine.core.domain.reasoning_context import ReasoningContext
from ai_engine.core.domain.reasoning_output import (
    ReasoningPayload,
    ReasoningResult,
    SignalInput,
)
from ai_engine.core.domain.reasoning_prompts import (
    REASONING_TOOL_SCHEMA,
    SYSTEM_PROMPT,
    build_user_prompt,
)

if TYPE_CHECKING:
    from anthropic import Anthropic

logger = logging.getLogger(__name__)


class AnthropicLlmReasoningClient:
    """LlmReasoningPort impl using the official Anthropic Python SDK."""

    PROVIDER_NAME = "anthropic"
    DEFAULT_MODEL = "claude-haiku-4-5"
    TEMPERATURE = 0.2
    MAX_TOKENS = 350

    def __init__(self, client: "Anthropic", model: str = DEFAULT_MODEL):
        self._client = client
        self._model = model

    def generate(
        self,
        signal: SignalInput,
        context: ReasoningContext,
        validator_feedback: str | None = None,
    ) -> ReasoningResult:
        user_prompt = build_user_prompt(signal, context)
        if validator_feedback:
            user_prompt = (
                "PREVIOUS ATTEMPT FAILED VALIDATION. Fix the violations below and "
                "emit a fresh emit_reasoning call. Your previous output is not "
                "available to you — write the reasoning from scratch using only "
                "values from <price_facts> and URLs from <news>.\n\n"
                f"Violations:\n{validator_feedback}\n\n"
                f"{user_prompt}"
            )
        try:
            # `system` as a plain string and tools without cache_control —
            # the long-stable C4 request shape. The cache_control block-list
            # form caused Anthropic 400 "messages: text content blocks must
            # be non-empty" in production while delivering zero benefit
            # (prefix below Haiku's 4096-token cache minimum). Do not
            # reintroduce caching here without validating the exact request
            # shape against the live API.
            response = self._client.messages.create(
                model=self._model,
                max_tokens=self.MAX_TOKENS,
                temperature=self.TEMPERATURE,
                system=SYSTEM_PROMPT,
                tools=[REASONING_TOOL_SCHEMA],
                tool_choice={"type": "tool", "name": "emit_reasoning"},
                messages=[{"role": "user", "content": user_prompt}],
            )
        except Exception as exc:
            logger.warning(
                "event=llm_reasoning.anthropic_error ticker=%s detail=%s",
                signal.ticker,
                str(exc),
            )
            return ReasoningResult.error(f"anthropic_call_failed: {exc!s}")

        tool_use = next(
            (b for b in response.content if getattr(b, "type", None) == "tool_use"),
            None,
        )
        if tool_use is None:
            logger.warning(
                "event=llm_reasoning.no_tool_use ticker=%s stop_reason=%s",
                signal.ticker,
                getattr(response, "stop_reason", "unknown"),
            )
            return ReasoningResult.error("anthropic_returned_no_tool_use")

        payload_raw = dict(getattr(tool_use, "input", {}) or {})
        usage = getattr(response, "usage", None)
        raw_audit = {
            "model": self._model,
            "stop_reason": getattr(response, "stop_reason", None),
            "usage": (
                {
                    "input_tokens": getattr(usage, "input_tokens", None),
                    "output_tokens": getattr(usage, "output_tokens", None),
                    "cache_read_input_tokens": getattr(
                        usage, "cache_read_input_tokens", None
                    ),
                    "cache_creation_input_tokens": getattr(
                        usage, "cache_creation_input_tokens", None
                    ),
                }
                if usage is not None
                else None
            ),
            "tool_use_input": payload_raw,
        }

        if payload_raw.get("refusal") is True:
            reason = payload_raw.get("refusal_reason") or "unspecified"
            logger.info(
                "event=llm_reasoning.refused_by_llm ticker=%s reason=%s",
                signal.ticker,
                reason,
            )
            return ReasoningResult.refused_by_llm(reason, raw_response=raw_audit)

        text = (payload_raw.get("reasoning") or "").strip()
        if not text:
            logger.warning(
                "event=llm_reasoning.empty_text ticker=%s",
                signal.ticker,
            )
            return ReasoningResult.error("anthropic_returned_empty_reasoning")

        price_refs = tuple(payload_raw.get("price_refs") or ())
        news_refs = tuple(payload_raw.get("news_refs") or ())
        payload = ReasoningPayload(
            text=text, price_refs=price_refs, news_refs=news_refs
        )
        logger.info(
            "event=llm_reasoning.generated ticker=%s len=%d price_refs=%d news_refs=%d",
            signal.ticker,
            len(text),
            len(price_refs),
            len(news_refs),
        )
        return ReasoningResult.generated(payload, raw_response=raw_audit)
