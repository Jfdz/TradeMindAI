"""Factory: pick the right LlmReasoningPort implementation for the runtime config.

This is the single switch between the three supported auth modes. The
downstream pipeline (use case, validator, persistence) sees only the
`LlmReasoningPort` interface and is independent of which client is wired.

Modes (via `settings.llm_provider`):

  - "stub" (default, dev-safe). Returns REFUSED_LLM_DISABLED for every
    call; no network egress. Use until a real key/token is configured.

  - "anthropic_oauth". Uses CLAUDE_CODE_OAUTH_TOKEN. TEMPORARY arrangement
    — the OAuth token is issued for Claude Code, not for general API
    backends, so Anthropic may suspend the token if usage patterns drift
    from interactive use. Emits a prominent WARN log on every factory
    init so the choice is auditable.

  - "anthropic_api_key". Uses a proper ANTHROPIC_API_KEY from the
    Anthropic console. Target end state — migrate here before exposing
    reasonings to real users.

If the selected provider's credential is missing, the factory falls back
to the stub and logs a warning rather than crashing — keeps the service
bootable and traceable.
"""

from __future__ import annotations

import logging

from ai_engine.adapters.out.stub_llm_reasoning_client import StubLlmReasoningClient
from ai_engine.config import Settings
from ai_engine.core.domain.reasoning_output import LlmReasoningPort

logger = logging.getLogger(__name__)


def create_llm_reasoning_client(settings: Settings) -> LlmReasoningPort:
    provider = (settings.llm_provider or "stub").strip().lower()

    if provider == "anthropic_oauth":
        if not settings.claude_code_oauth_token:
            logger.warning(
                "event=llm_factory.missing_credential provider=anthropic_oauth "
                "fallback=stub reason=claude_code_oauth_token_empty"
            )
            return StubLlmReasoningClient()
        return _build_anthropic_oauth(settings)

    if provider == "anthropic_api_key":
        if not settings.anthropic_api_key:
            logger.warning(
                "event=llm_factory.missing_credential provider=anthropic_api_key "
                "fallback=stub reason=anthropic_api_key_empty"
            )
            return StubLlmReasoningClient()
        return _build_anthropic_api_key(settings)

    if provider != "stub":
        logger.warning(
            "event=llm_factory.unknown_provider provider=%r fallback=stub",
            provider,
        )
    logger.info("event=llm_factory.using_stub")
    return StubLlmReasoningClient()


def _build_anthropic_oauth(settings: Settings) -> LlmReasoningPort:
    from anthropic import Anthropic  # imported lazily so stub mode doesn't require the SDK
    from ai_engine.adapters.out.anthropic_llm_reasoning_client import (
        AnthropicLlmReasoningClient,
    )

    logger.warning(
        "event=llm_factory.oauth_mode_active model=%s "
        "WARNING: using CLAUDE_CODE_OAUTH_TOKEN from a Claude Max subscription. "
        "This may violate Anthropic consumer terms and rate-limit the account. "
        "Migrate to llm_provider=anthropic_api_key before exposing reasonings to real users.",
        settings.anthropic_model,
    )
    client = Anthropic(auth_token=settings.claude_code_oauth_token)
    return AnthropicLlmReasoningClient(client, model=settings.anthropic_model)


def _build_anthropic_api_key(settings: Settings) -> LlmReasoningPort:
    from anthropic import Anthropic
    from ai_engine.adapters.out.anthropic_llm_reasoning_client import (
        AnthropicLlmReasoningClient,
    )

    logger.info(
        "event=llm_factory.api_key_mode_active model=%s",
        settings.anthropic_model,
    )
    client = Anthropic(api_key=settings.anthropic_api_key)
    return AnthropicLlmReasoningClient(client, model=settings.anthropic_model)
