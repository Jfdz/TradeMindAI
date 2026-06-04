"""Factory that wires the Fase 3 deep-analysis use case from Settings.

Parallel to `persist_reasoning_factory` but for the multi-agent debate: it
reuses the same grounded context chain (`TradingCoreClient` →
`BuildReasoningContextUseCase`) and the same `ReasoningValidator`, swapping the
single `LlmReasoningPort` for the multi-call `ChatLlmPort`. Pure function over
`Settings`; one seam for tests and `main.py` to replace.
"""

from __future__ import annotations

from ai_engine.adapters.out.chat_llm_factory import create_chat_llm_client
from ai_engine.adapters.out.trading_core_client import TradingCoreClient
from ai_engine.config import Settings
from ai_engine.core.domain.reasoning_validation import ReasoningValidator
from ai_engine.core.use_cases.build_reasoning_context import (
    BuildReasoningContextUseCase,
)
from ai_engine.core.use_cases.generate_deep_analysis import (
    GenerateDeepAnalysisUseCase,
)


def create_deep_analysis_use_case(settings: Settings) -> GenerateDeepAnalysisUseCase:
    secret = settings.trading_core_internal_secret or settings.internal_secret

    trading_core_client = TradingCoreClient(
        base_url=settings.trading_core_service_url,
        internal_secret=secret,
    )
    context_use_case = BuildReasoningContextUseCase(trading_core_client)
    chat_llm = create_chat_llm_client(settings)

    # Audit the model actually used per provider, not the Anthropic default.
    if settings.llm_provider == "minimax_oauth":
        model_version = settings.minimax_model
    else:
        model_version = settings.anthropic_model

    return GenerateDeepAnalysisUseCase(
        context_use_case=context_use_case,
        chat_llm=chat_llm,
        validator=ReasoningValidator(),
        provider=settings.llm_provider,
        model_version=model_version,
    )
