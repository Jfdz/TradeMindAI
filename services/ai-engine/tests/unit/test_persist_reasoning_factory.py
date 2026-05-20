"""Tests for the C9 wiring factory."""

from __future__ import annotations

from unittest.mock import patch

from ai_engine.adapters.out.persist_reasoning_factory import (
    create_persist_reasoning_use_case,
)
from ai_engine.adapters.out.stub_llm_reasoning_client import StubLlmReasoningClient
from ai_engine.adapters.out.trading_core_reasoning_sink import (
    TradingCoreReasoningSink,
)


class _StubSettings:
    def __init__(self, **overrides):
        self.trading_core_service_url = "https://trading-core:8082"
        self.trading_core_internal_secret = "tc-secret"
        self.internal_secret = "shared-secret"
        self.llm_provider = "stub"
        self.claude_code_oauth_token = ""
        self.anthropic_api_key = ""
        self.anthropic_model = "claude-haiku-4-5"
        for k, v in overrides.items():
            setattr(self, k, v)


def test_factory_builds_with_stub_provider_and_sink_pointing_at_trading_core():
    use_case = create_persist_reasoning_use_case(_StubSettings())
    # The internal LLM port is the stub when llm_provider="stub".
    assert isinstance(use_case._generator._llm, StubLlmReasoningClient)
    # The sink is the trading-core HTTP client.
    assert isinstance(use_case._sink, TradingCoreReasoningSink)
    assert use_case._provider == "stub"
    assert use_case._model_version == "claude-haiku-4-5"


def test_factory_prefers_trading_core_internal_secret_over_generic():
    settings = _StubSettings(
        trading_core_internal_secret="specific-tc-secret",
        internal_secret="other-secret",
    )
    use_case = create_persist_reasoning_use_case(settings)
    # The sink ends up with the specific secret, not the generic one.
    assert use_case._sink._internal_secret == "specific-tc-secret"


def test_factory_falls_back_to_internal_secret_when_specific_missing():
    settings = _StubSettings(
        trading_core_internal_secret="",
        internal_secret="shared-secret",
    )
    use_case = create_persist_reasoning_use_case(settings)
    assert use_case._sink._internal_secret == "shared-secret"


def test_factory_wires_anthropic_oauth_when_provider_is_anthropic_oauth():
    settings = _StubSettings(
        llm_provider="anthropic_oauth",
        claude_code_oauth_token="oauth-token-xyz",
    )
    with patch("anthropic.Anthropic") as anthropic_ctor:
        use_case = create_persist_reasoning_use_case(settings)
    anthropic_ctor.assert_called_once_with(auth_token="oauth-token-xyz")
    assert not isinstance(use_case._generator._llm, StubLlmReasoningClient)
    assert use_case._provider == "anthropic_oauth"


def test_factory_propagates_anthropic_model_setting():
    settings = _StubSettings(anthropic_model="claude-sonnet-4-6")
    use_case = create_persist_reasoning_use_case(settings)
    assert use_case._model_version == "claude-sonnet-4-6"
