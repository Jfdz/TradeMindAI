"""Factory: pick the `ChatLlmPort` impl for the runtime config (Fase 3).

Mirrors `llm_reasoning_factory` and reads the *same* provider settings, so the
deep-analysis debate runs on whatever LLM the grounded path uses. The
downstream use case sees only the `ChatLlmPort` interface.

Modes (via `settings.llm_provider`):

  - "stub" (default): `StubChatClient`, fails closed, no egress.
  - "minimax_oauth": Anthropic SDK pointed at MiniMax's `/anthropic` endpoint
    with the subscription OAuth token. Same ToS / rate-limit caveat as the
    grounded path — WARN-logged. This is the cost-justified home for the
    multi-call debate.
  - "anthropic_api_key" / "anthropic_oauth": Anthropic-hosted fallback.

Missing credential => fall back to the stub with a warning, never crash.
"""

from __future__ import annotations

import logging

from ai_engine.adapters.out.stub_chat_client import StubChatClient
from ai_engine.config import Settings
from ai_engine.core.domain.chat_llm import ChatLlmPort

logger = logging.getLogger(__name__)


def create_chat_llm_client(settings: Settings) -> ChatLlmPort:
    provider = (settings.llm_provider or "stub").strip().lower()

    if provider == "minimax_oauth":
        if not settings.minimax_oauth_token:
            logger.warning(
                "event=chat_llm_factory.missing_credential provider=minimax_oauth "
                "fallback=stub reason=minimax_oauth_token_empty"
            )
            return StubChatClient()
        return _build_minimax_oauth(settings)

    if provider == "anthropic_api_key":
        if not settings.anthropic_api_key:
            logger.warning(
                "event=chat_llm_factory.missing_credential provider=anthropic_api_key "
                "fallback=stub reason=anthropic_api_key_empty"
            )
            return StubChatClient()
        return _build_anthropic(api_key=settings.anthropic_api_key, model=settings.anthropic_model)

    if provider == "anthropic_oauth":
        if not settings.claude_code_oauth_token:
            logger.warning(
                "event=chat_llm_factory.missing_credential provider=anthropic_oauth "
                "fallback=stub reason=claude_code_oauth_token_empty"
            )
            return StubChatClient()
        return _build_anthropic(
            auth_token=settings.claude_code_oauth_token, model=settings.anthropic_model
        )

    if provider != "stub":
        logger.warning(
            "event=chat_llm_factory.unknown_provider provider=%r fallback=stub",
            provider,
        )
    logger.info("event=chat_llm_factory.using_stub")
    return StubChatClient()


def _build_minimax_oauth(settings: Settings) -> ChatLlmPort:
    from anthropic import Anthropic

    from ai_engine.adapters.out.anthropic_chat_client import AnthropicChatClient

    logger.warning(
        "event=chat_llm_factory.minimax_oauth_mode_active model=%s base_url=%s "
        "WARNING: using a MiniMax subscription OAuth token as an API backend for "
        "the deep-analysis debate. This may violate MiniMax consumer terms and "
        "rate-limit the account. Obtain a platform MINIMAX_API_KEY before "
        "exposing deep analysis to real users.",
        settings.minimax_model,
        settings.minimax_base_url,
    )
    client = Anthropic(
        auth_token=settings.minimax_oauth_token,
        base_url=settings.minimax_base_url,
    )
    return AnthropicChatClient(
        client,
        model=settings.minimax_model,
        max_tokens=settings.minimax_max_tokens,
    )


def _build_anthropic(
    *,
    model: str,
    api_key: str | None = None,
    auth_token: str | None = None,
) -> ChatLlmPort:
    from anthropic import Anthropic

    from ai_engine.adapters.out.anthropic_chat_client import AnthropicChatClient

    if auth_token is not None:
        logger.warning(
            "event=chat_llm_factory.oauth_mode_active model=%s "
            "WARNING: using CLAUDE_CODE_OAUTH_TOKEN for deep analysis. Migrate to "
            "anthropic_api_key before exposing to real users.",
            model,
        )
        client = Anthropic(auth_token=auth_token)
    else:
        logger.info("event=chat_llm_factory.api_key_mode_active model=%s", model)
        client = Anthropic(api_key=api_key)
    return AnthropicChatClient(client, model=model)
