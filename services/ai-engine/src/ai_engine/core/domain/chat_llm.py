"""Generic tool-calling LLM port for multi-section generation (Fase 3).

The grounded reasoning path (`LlmReasoningPort`) is single-purpose: one
`emit_reasoning` call bound to a `SignalInput` + `ReasoningContext`. The deep
analysis mode needs several independent tool calls with different system
prompts and tool schemas (bull / bear / judge / risk), so it talks to the LLM
through this lower-level port instead of overloading the reasoning port.

Like every outbound port in this service, implementations MUST never raise:
every failure maps to a `ToolCallResult` with ``ok=False`` so the use case
reacts with a single check. In particular, a model that emits free text
instead of the forced tool call maps to ``ok=False, error="no_tool_use"`` —
not an exception. That keeps the multi-agent orchestration resilient to the
MiniMax M-series quirk where a reasoning model occasionally narrates instead
of calling the tool.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol


@dataclass(frozen=True, slots=True)
class ToolCallResult:
    """Outcome of a single forced tool call. Never carries an exception."""

    ok: bool
    arguments: dict[str, Any] | None = None
    raw: dict[str, Any] | None = None
    error: str | None = None

    @classmethod
    def succeeded(
        cls, arguments: dict[str, Any], raw: dict[str, Any] | None = None
    ) -> "ToolCallResult":
        return cls(ok=True, arguments=arguments, raw=raw)

    @classmethod
    def failed(cls, error: str, raw: dict[str, Any] | None = None) -> "ToolCallResult":
        return cls(ok=False, error=error, raw=raw)


class ChatLlmPort(Protocol):
    """Outbound port: run one system+user turn that forces a single tool call.

    Implementations must never raise; map provider/transport/no-tool-use
    failures to ``ToolCallResult.failed(...)``.
    """

    def complete_tool(
        self,
        system_prompt: str,
        user_prompt: str,
        tool: dict[str, Any],
    ) -> ToolCallResult: ...
