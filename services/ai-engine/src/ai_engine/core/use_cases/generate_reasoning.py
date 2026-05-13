"""Use case: generate a reasoning for a single signal.

Orchestrates BuildReasoningContextUseCase + LlmReasoningPort and returns
a typed ReasoningResult. Never raises — every failure mode is a
ReasoningOutcome variant the caller can branch on.
"""

from __future__ import annotations

import logging

from ai_engine.core.domain.reasoning_context import ContextOutcome
from ai_engine.core.domain.reasoning_output import (
    LlmReasoningPort,
    ReasoningResult,
    SignalInput,
)
from ai_engine.core.use_cases.build_reasoning_context import (
    BuildReasoningContextUseCase,
)

logger = logging.getLogger(__name__)


class GenerateReasoningUseCase:
    """End-to-end: signal → grounded context → LLM → ReasoningResult."""

    def __init__(
        self,
        context_use_case: BuildReasoningContextUseCase,
        llm_port: LlmReasoningPort,
    ):
        self._context_use_case = context_use_case
        self._llm = llm_port

    def execute(self, signal: SignalInput) -> ReasoningResult:
        context_result = self._context_use_case.execute(signal.ticker)
        if context_result.outcome != ContextOutcome.AVAILABLE:
            logger.info(
                "event=generate_reasoning.refused_no_facts ticker=%s context_outcome=%s detail=%s",
                signal.ticker,
                context_result.outcome.value,
                context_result.detail or "",
            )
            return ReasoningResult.refused_no_facts(
                detail=f"context_outcome={context_result.outcome.value}"
            )

        assert context_result.context is not None  # invariant of AVAILABLE
        return self._llm.generate(signal, context_result.context)
