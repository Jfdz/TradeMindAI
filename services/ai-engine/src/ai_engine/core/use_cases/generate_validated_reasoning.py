"""End-to-end orchestrator for C5: grounded context → LLM → validator → retry once.

Composition over `BuildReasoningContextUseCase` (C3c) + `LlmReasoningPort` (C4)
+ `ReasoningValidator` (C5). Behavior:

  1. Build the reasoning context. If outcome != AVAILABLE → REFUSED_NO_FACTS.
  2. Call the LLM. If outcome != GENERATED → propagate as-is.
  3. Validate the payload. If pass → GENERATED with retry=0.
  4. Otherwise call the LLM once more with the validator feedback. If
     this call's outcome != GENERATED → propagate that result.
  5. Validate the second payload. If pass → GENERATED with retry=1.
  6. Otherwise → REFUSED_BY_VALIDATOR carrying the second feedback.

Never raises. Every failure mode is a `ReasoningOutcome` variant.
Emits structured logs with `retry` and `outcome` tags so the C8 audit
pipeline can compute generation-success and retry-rate metrics from log
ingestion without a Prometheus dependency wired into ai-engine yet.
"""

from __future__ import annotations

import logging

from ai_engine.core.domain.reasoning_context import ContextOutcome
from ai_engine.core.domain.reasoning_output import (
    LlmReasoningPort,
    ReasoningOutcome,
    ReasoningResult,
    SignalInput,
)
from ai_engine.core.domain.reasoning_validation import ReasoningValidator
from ai_engine.core.use_cases.build_reasoning_context import (
    BuildReasoningContextUseCase,
)

logger = logging.getLogger(__name__)


class GenerateValidatedReasoningUseCase:
    """Generate → validate → retry-once → final outcome."""

    def __init__(
        self,
        context_use_case: BuildReasoningContextUseCase,
        llm_port: LlmReasoningPort,
        validator: ReasoningValidator,
    ):
        self._context_use_case = context_use_case
        self._llm = llm_port
        self._validator = validator

    def execute(self, signal: SignalInput) -> ReasoningResult:
        context_result = self._context_use_case.execute(signal.ticker)
        if context_result.outcome != ContextOutcome.AVAILABLE:
            logger.info(
                "event=validated_reasoning.refused_no_facts ticker=%s "
                "context_outcome=%s",
                signal.ticker,
                context_result.outcome.value,
            )
            return ReasoningResult.refused_no_facts(
                detail=f"context_outcome={context_result.outcome.value}"
            )

        assert context_result.context is not None  # invariant of AVAILABLE
        ctx = context_result.context

        first = self._llm.generate(signal, ctx)
        if first.outcome != ReasoningOutcome.GENERATED:
            logger.info(
                "event=validated_reasoning.refused ticker=%s outcome=%s retry=0",
                signal.ticker,
                first.outcome.value,
            )
            return first

        assert first.payload is not None
        validation = self._validator.validate(first.payload, signal, ctx)
        if validation.passed:
            logger.info(
                "event=validated_reasoning.generated ticker=%s retry=0",
                signal.ticker,
            )
            return first

        logger.info(
            "event=validated_reasoning.validation_failed ticker=%s "
            "violations=%d retry=0",
            signal.ticker,
            len(validation.violations),
        )
        second = self._llm.generate(
            signal, ctx, validator_feedback=validation.feedback
        )
        if second.outcome != ReasoningOutcome.GENERATED:
            logger.info(
                "event=validated_reasoning.retry_refused ticker=%s outcome=%s retry=1",
                signal.ticker,
                second.outcome.value,
            )
            return second

        assert second.payload is not None
        validation2 = self._validator.validate(second.payload, signal, ctx)
        if validation2.passed:
            logger.info(
                "event=validated_reasoning.generated ticker=%s retry=1",
                signal.ticker,
            )
            return second

        logger.warning(
            "event=validated_reasoning.refused_by_validator ticker=%s "
            "violations=%d retry=1",
            signal.ticker,
            len(validation2.violations),
        )
        return ReasoningResult.refused_by_validator(
            reason=validation2.feedback,
            raw_response=second.raw_response,
        )
