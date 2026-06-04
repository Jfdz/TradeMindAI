"""AnthropicChatClient — `ChatLlmPort` impl over the Anthropic Python SDK.

Auth-agnostic, exactly like `AnthropicLlmReasoningClient`: the factory hands it
a pre-built `anthropic.Anthropic` client (API key, OAuth, or pointed at
MiniMax's Anthropic-compatible `/anthropic` endpoint). One forced tool call per
turn, no free text.

Resilience contract: this never raises. A provider/transport failure, or a
model that narrates instead of calling the forced tool (the MiniMax M-series
quirk), both map to `ToolCallResult.failed(...)`. `max_tokens` defaults high
enough to clear the M-series hidden <think> pass before the tool call.
"""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING, Any

from ai_engine.core.domain.chat_llm import ToolCallResult

if TYPE_CHECKING:
    from anthropic import Anthropic

logger = logging.getLogger(__name__)


class AnthropicChatClient:
    """ChatLlmPort impl using the official Anthropic Python SDK."""

    PROVIDER_NAME = "anthropic"
    DEFAULT_MODEL = "claude-haiku-4-5"
    # Slightly warmer than the grounded path: bull/bear want distinct angles.
    # M-series ignore temperature, so this only affects the Anthropic fallback.
    TEMPERATURE = 0.4
    DEFAULT_MAX_TOKENS = 4096

    def __init__(
        self,
        client: "Anthropic",
        model: str = DEFAULT_MODEL,
        max_tokens: int = DEFAULT_MAX_TOKENS,
    ):
        self._client = client
        self._model = model
        self._max_tokens = max_tokens

    def complete_tool(
        self,
        system_prompt: str,
        user_prompt: str,
        tool: dict[str, Any],
    ) -> ToolCallResult:
        tool_name = tool.get("name", "")
        try:
            response = self._client.messages.create(
                model=self._model,
                max_tokens=self._max_tokens,
                temperature=self.TEMPERATURE,
                system=system_prompt,
                tools=[tool],
                tool_choice={"type": "tool", "name": tool_name},
                messages=[{"role": "user", "content": user_prompt}],
            )
        except Exception as exc:  # SDK raises a broad hierarchy; never leak it
            logger.warning(
                "event=chat_llm.provider_error tool=%s detail=%s",
                tool_name,
                str(exc),
            )
            return ToolCallResult.failed(f"provider_error: {exc!s}")

        raw_audit = _audit_blob(self._model, response)
        tool_use = next(
            (b for b in response.content if getattr(b, "type", None) == "tool_use"),
            None,
        )
        if tool_use is None:
            logger.warning(
                "event=chat_llm.no_tool_use tool=%s stop_reason=%s",
                tool_name,
                getattr(response, "stop_reason", "unknown"),
            )
            return ToolCallResult.failed("no_tool_use", raw=raw_audit)

        arguments = dict(getattr(tool_use, "input", {}) or {})
        return ToolCallResult.succeeded(arguments, raw=raw_audit)


def _audit_blob(model: str, response: Any) -> dict[str, Any]:
    usage = getattr(response, "usage", None)
    return {
        "model": model,
        "stop_reason": getattr(response, "stop_reason", None),
        "usage": (
            {
                "input_tokens": getattr(usage, "input_tokens", None),
                "output_tokens": getattr(usage, "output_tokens", None),
            }
            if usage is not None
            else None
        ),
    }
