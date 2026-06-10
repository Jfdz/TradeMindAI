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
        # C16 — single canonical secret field. k8s mounts
        # internal-service-secret/market-data as INTERNAL_API_SECRET, which
        # pydantic-settings hydrates into `internal_secret`. The per-service
        # *_internal_secret fields were removed to eliminate the two-Secret
        # drift that 401d the reasoning pipeline.
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


def test_factory_uses_internal_secret_for_trading_core_sink():
    # Single-secret contract: the factory forwards Settings.internal_secret
    # verbatim to the sink. Replaces the old "prefer TC, fall back to
    # generic" two-Secret logic that was the source of the prod 401s.
    use_case = create_persist_reasoning_use_case(_StubSettings(internal_secret="the-canonical-secret"))
    assert use_case._sink._internal_secret == "the-canonical-secret"


def test_factory_propagates_unset_internal_secret_to_sink():
    # If k8s forgot to mount INTERNAL_API_SECRET the sink ships with an
    # empty secret and the trading-core filter will reject with 401. The
    # factory should not silently invent a fallback value.
    use_case = create_persist_reasoning_use_case(_StubSettings(internal_secret=""))
    assert use_case._sink._internal_secret == ""


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
