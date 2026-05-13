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
    ):
        self.llm_provider = llm_provider
        self.claude_code_oauth_token = claude_code_oauth_token
        self.anthropic_api_key = anthropic_api_key
        self.anthropic_model = anthropic_model


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
    with patch("anthropic.Anthropic", return_value=fake_sdk):
        with caplog.at_level(logging.WARNING, logger="ai_engine.adapters.out.llm_reasoning_factory"):
            create_llm_reasoning_client(
                _StubSettings(
                    llm_provider="anthropic_oauth",
                    claude_code_oauth_token="oauth-token",
                )
            )

    # The OAuth mode WARN log is part of the audit trail — must be present.
    assert any("oauth_mode_active" in rec.message for rec in caplog.records)
