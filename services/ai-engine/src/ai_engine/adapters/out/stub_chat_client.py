"""StubChatClient — dev-safe `ChatLlmPort` with no network egress.

Returned by `chat_llm_factory` when `llm_provider="stub"` or the selected
provider's credential is missing. Every call fails with `chat_llm_disabled`,
so the deep-analysis use case yields a typed ERROR/REFUSED outcome instead of
crashing or reaching the network.
"""

from __future__ import annotations

from typing import Any

from ai_engine.core.domain.chat_llm import ToolCallResult


class StubChatClient:
    """No-op ChatLlmPort. Always fails closed, never touches the network."""

    def complete_tool(
        self,
        system_prompt: str,
        user_prompt: str,
        tool: dict[str, Any],
    ) -> ToolCallResult:
        return ToolCallResult.failed("chat_llm_disabled")
