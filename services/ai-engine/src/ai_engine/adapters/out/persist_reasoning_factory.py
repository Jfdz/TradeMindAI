"""Factory that wires the full C7 persist-validated-reasoning pipeline.

Single place where the seven dependencies — TradingCoreClient (C3c) +
BuildReasoningContextUseCase + LlmReasoningPort factory (C4) +
ReasoningValidator (C5) + GenerateValidatedReasoningUseCase +
TradingCoreReasoningSink (C7) + PersistValidatedReasoningUseCase —
are instantiated from `Settings`. Keeps `main.py`'s lifespan tidy
and gives tests a single seam to replace.
"""

from __future__ import annotations

from ai_engine.adapters.out.llm_reasoning_factory import create_llm_reasoning_client
from ai_engine.adapters.out.trading_core_client import TradingCoreClient
from ai_engine.adapters.out.trading_core_reasoning_sink import (
    TradingCoreReasoningSink,
)
from ai_engine.config import Settings
from ai_engine.core.domain.reasoning_validation import ReasoningValidator
from ai_engine.core.use_cases.build_reasoning_context import (
    BuildReasoningContextUseCase,
)
from ai_engine.core.use_cases.generate_validated_reasoning import (
    GenerateValidatedReasoningUseCase,
)
from ai_engine.core.use_cases.persist_validated_reasoning import (
    PersistValidatedReasoningUseCase,
)


def create_persist_reasoning_use_case(
    settings: Settings,
) -> PersistValidatedReasoningUseCase:
    """Compose every layer from Settings. Pure function over Settings."""
    secret = settings.trading_core_internal_secret or settings.internal_secret

    trading_core_client = TradingCoreClient(
        base_url=settings.trading_core_service_url,
        internal_secret=secret,
    )
    context_use_case = BuildReasoningContextUseCase(trading_core_client)

    llm_port = create_llm_reasoning_client(settings)
    validator = ReasoningValidator()

    generator = GenerateValidatedReasoningUseCase(
        context_use_case=context_use_case,
        llm_port=llm_port,
        validator=validator,
    )

    sink = TradingCoreReasoningSink(
        base_url=settings.trading_core_service_url,
        internal_secret=secret,
    )

    # Record the model actually used so the C6 audit (`reasoning_model_version`)
    # is accurate per provider, not always the Anthropic default.
    if settings.llm_provider == "minimax_oauth":
        model_version = settings.minimax_model
    else:
        model_version = settings.anthropic_model

    return PersistValidatedReasoningUseCase(
        generator=generator,
        sink=sink,
        provider=settings.llm_provider,
        model_version=model_version,
    )
