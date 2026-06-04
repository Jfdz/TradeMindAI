"""chat_llm_factory: provider selection, MiniMax wiring, fail-closed fallback."""

from __future__ import annotations

from unittest.mock import MagicMock, patch

from ai_engine.adapters.out.chat_llm_factory import create_chat_llm_client
from ai_engine.adapters.out.stub_chat_client import StubChatClient


class _StubSettings:
    """Subset of Settings the chat factory reads (same fields as the reasoning one)."""

    def __init__(
        self,
        *,
        llm_provider: str = "stub",
        claude_code_oauth_token: str = "",
        anthropic_api_key: str = "",
        anthropic_model: str = "claude-haiku-4-5",
        minimax_oauth_token: str = "",
        minimax_base_url: str = "https://api.minimax.io/anthropic",
        minimax_model: str = "MiniMax-M2.5-highspeed",
        minimax_max_tokens: int = 4096,
    ):
        self.llm_provider = llm_provider
        self.claude_code_oauth_token = claude_code_oauth_token
        self.anthropic_api_key = anthropic_api_key
        self.anthropic_model = anthropic_model
        self.minimax_oauth_token = minimax_oauth_token
        self.minimax_base_url = minimax_base_url
        self.minimax_model = minimax_model
        self.minimax_max_tokens = minimax_max_tokens


def test_default_provider_returns_stub():
    assert isinstance(create_chat_llm_client(_StubSettings()), StubChatClient)


def test_unknown_provider_falls_back_to_stub():
    assert isinstance(
        create_chat_llm_client(_StubSettings(llm_provider="carrier_pigeon")), StubChatClient
    )


def test_minimax_without_token_falls_back_to_stub():
    assert isinstance(
        create_chat_llm_client(_StubSettings(llm_provider="minimax_oauth")), StubChatClient
    )


def test_minimax_builds_chat_client_pointed_at_minimax_with_raised_cap():
    fake_sdk = MagicMock()
    with patch("anthropic.Anthropic") as anthropic_ctor:
        anthropic_ctor.return_value = fake_sdk
        client = create_chat_llm_client(
            _StubSettings(
                llm_provider="minimax_oauth",
                minimax_oauth_token="sk-cp-minimax-xyz",
                minimax_model="MiniMax-M2.5-highspeed",
                minimax_max_tokens=4096,
            )
        )

    anthropic_ctor.assert_called_once_with(
        auth_token="sk-cp-minimax-xyz",
        base_url="https://api.minimax.io/anthropic",
    )
    assert not isinstance(client, StubChatClient)
    assert getattr(client, "_client", None) is fake_sdk
    assert getattr(client, "_model", None) == "MiniMax-M2.5-highspeed"
    assert getattr(client, "_max_tokens", None) == 4096


def test_minimax_oauth_emits_tos_warning(caplog):
    with caplog.at_level("WARNING"):
        with patch("anthropic.Anthropic"):
            create_chat_llm_client(
                _StubSettings(llm_provider="minimax_oauth", minimax_oauth_token="tok")
            )
    assert any("minimax_oauth_mode_active" in rec.message for rec in caplog.records)
