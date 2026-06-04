"""Tests for create_llm_reasoning_client.

The factory is the kill switch between stub, OAuth-mode, and API-key
modes. These tests pin that behavior so a config typo never silently
disables LLM calls in production (or, inversely, never silently enables
OAuth mode when the key is meant to be used).
"""

from __future__ import annotations

from unittest.mock import MagicMock, patch

from ai_engine.adapters.out.llm_reasoning_factory import create_llm_reasoning_client
from ai_engine.adapters.out.stub_llm_reasoning_client import StubLlmReasoningClient


class _StubSettings:
    """Subset of Settings the factory reads."""

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


def test_default_stub_provider_returns_stub_client():
    client = create_llm_reasoning_client(_StubSettings())
    assert isinstance(client, StubLlmReasoningClient)


def test_unknown_provider_falls_back_to_stub():
    client = create_llm_reasoning_client(_StubSettings(llm_provider="claude_via_carrier_pigeon"))
    assert isinstance(client, StubLlmReasoningClient)


def test_oauth_provider_without_token_falls_back_to_stub():
    client = create_llm_reasoning_client(
        _StubSettings(llm_provider="anthropic_oauth", claude_code_oauth_token="")
    )
    assert isinstance(client, StubLlmReasoningClient)


def test_api_key_provider_without_key_falls_back_to_stub():
    client = create_llm_reasoning_client(
        _StubSettings(llm_provider="anthropic_api_key", anthropic_api_key="")
    )
    assert isinstance(client, StubLlmReasoningClient)


def test_oauth_provider_with_token_builds_anthropic_client_with_auth_token():
    fake_sdk = MagicMock()
    with patch("anthropic.Anthropic") as anthropic_ctor:
        anthropic_ctor.return_value = fake_sdk
        client = create_llm_reasoning_client(
            _StubSettings(
                llm_provider="anthropic_oauth",
                claude_code_oauth_token="oauth-token-xyz",
                anthropic_model="claude-haiku-4-5",
            )
        )

    anthropic_ctor.assert_called_once_with(auth_token="oauth-token-xyz")
    # Should NOT be the stub.
    assert not isinstance(client, StubLlmReasoningClient)
    # Should have wrapped our fake SDK.
    assert getattr(client, "_client", None) is fake_sdk
    assert getattr(client, "_model", None) == "claude-haiku-4-5"


def test_api_key_provider_with_key_builds_anthropic_client_with_api_key():
    fake_sdk = MagicMock()
    with patch("anthropic.Anthropic") as anthropic_ctor:
        anthropic_ctor.return_value = fake_sdk
        client = create_llm_reasoning_client(
            _StubSettings(
                llm_provider="anthropic_api_key",
                anthropic_api_key="sk-ant-abc",
                anthropic_model="claude-haiku-4-5",
            )
        )

    anthropic_ctor.assert_called_once_with(api_key="sk-ant-abc")
    assert not isinstance(client, StubLlmReasoningClient)
    assert getattr(client, "_client", None) is fake_sdk


def test_oauth_factory_emits_warning_log(caplog):
    import logging

    fake_sdk = MagicMock()
    factory_logger = "ai_engine.adapters.out.llm_reasoning_factory"
    with patch("anthropic.Anthropic", return_value=fake_sdk):
        with caplog.at_level(logging.WARNING, logger=factory_logger):
            create_llm_reasoning_client(
                _StubSettings(
                    llm_provider="anthropic_oauth",
                    claude_code_oauth_token="oauth-token",
                )
            )

    # The OAuth mode WARN log is part of the audit trail — must be present.
    assert any("oauth_mode_active" in rec.message for rec in caplog.records)


def test_minimax_oauth_without_token_falls_back_to_stub():
    client = create_llm_reasoning_client(
        _StubSettings(llm_provider="minimax_oauth", minimax_oauth_token="")
    )
    assert isinstance(client, StubLlmReasoningClient)


def test_minimax_oauth_with_token_builds_anthropic_client_pointed_at_minimax():
    fake_sdk = MagicMock()
    with patch("anthropic.Anthropic") as anthropic_ctor:
        anthropic_ctor.return_value = fake_sdk
        client = create_llm_reasoning_client(
            _StubSettings(
                llm_provider="minimax_oauth",
                minimax_oauth_token="sk-cp-minimax-xyz",
                minimax_base_url="https://api.minimax.io/anthropic",
                minimax_model="MiniMax-M2.5-highspeed",
                minimax_max_tokens=4096,
            )
        )

    # Route A: the Anthropic SDK is pointed at MiniMax's Anthropic-compatible
    # endpoint with the OAuth token supplied as a Bearer auth_token.
    anthropic_ctor.assert_called_once_with(
        auth_token="sk-cp-minimax-xyz",
        base_url="https://api.minimax.io/anthropic",
    )
    assert not isinstance(client, StubLlmReasoningClient)
    assert getattr(client, "_client", None) is fake_sdk
    assert getattr(client, "_model", None) == "MiniMax-M2.5-highspeed"
    # The raised cap is what unblocks M-series (the 350 default ERRORs them).
    assert getattr(client, "_max_tokens", None) == 4096


def test_minimax_oauth_factory_emits_warning_log(caplog):
    import logging

    fake_sdk = MagicMock()
    factory_logger = "ai_engine.adapters.out.llm_reasoning_factory"
    with patch("anthropic.Anthropic", return_value=fake_sdk):
        with caplog.at_level(logging.WARNING, logger=factory_logger):
            create_llm_reasoning_client(
                _StubSettings(
                    llm_provider="minimax_oauth",
                    minimax_oauth_token="sk-cp-minimax-xyz",
                )
            )

    # The MiniMax-OAuth ToS-risk WARN is part of the audit trail — must be present.
    assert any("minimax_oauth_mode_active" in rec.message for rec in caplog.records)
