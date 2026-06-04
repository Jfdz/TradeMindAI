"""AnthropicChatClient: forced tool_choice, text-only -> no_tool_use, never raises."""

from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import MagicMock

from ai_engine.adapters.out.anthropic_chat_client import AnthropicChatClient
from ai_engine.core.domain.deep_analysis_prompts import OPINION_TOOL_SCHEMA


def _response(content, stop_reason="tool_use", usage=None):
    return SimpleNamespace(content=content, stop_reason=stop_reason, usage=usage)


def _tool_use(name, payload):
    return SimpleNamespace(type="tool_use", name=name, input=payload)


def test_complete_tool_returns_arguments_and_forces_tool_choice():
    sdk = MagicMock()
    sdk.messages.create.return_value = _response(
        [_tool_use("emit_opinion", {"opinion": "grounded", "refusal": False})],
        usage=SimpleNamespace(input_tokens=11, output_tokens=22),
    )
    client = AnthropicChatClient(sdk, model="MiniMax-M2.5-highspeed", max_tokens=1234)

    result = client.complete_tool("system", "user", OPINION_TOOL_SCHEMA)

    assert result.ok
    assert result.arguments["opinion"] == "grounded"
    assert result.raw["usage"]["output_tokens"] == 22
    _, kwargs = sdk.messages.create.call_args
    assert kwargs["tool_choice"] == {"type": "tool", "name": "emit_opinion"}
    assert kwargs["max_tokens"] == 1234
    assert kwargs["model"] == "MiniMax-M2.5-highspeed"
    assert kwargs["tools"] == [OPINION_TOOL_SCHEMA]


def test_complete_tool_maps_text_only_response_to_no_tool_use():
    sdk = MagicMock()
    text_block = SimpleNamespace(type="text", text="Let me think out loud...")
    sdk.messages.create.return_value = _response([text_block], stop_reason="end_turn")
    client = AnthropicChatClient(sdk)

    result = client.complete_tool("system", "user", OPINION_TOOL_SCHEMA)

    assert not result.ok
    assert result.error == "no_tool_use"
    assert result.raw["stop_reason"] == "end_turn"


def test_complete_tool_maps_sdk_exception_to_failed_without_raising():
    sdk = MagicMock()
    sdk.messages.create.side_effect = RuntimeError("429 rate limited")
    client = AnthropicChatClient(sdk)

    result = client.complete_tool("system", "user", OPINION_TOOL_SCHEMA)

    assert not result.ok
    assert "provider_error" in result.error
    assert "429" in result.error
