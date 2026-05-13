"""End-to-end orchestrator for C5: grounded context → LLM → validator → retry once.

Composition over `BuildReasoningContextUseCase` (C3c) + `LlmReasoningPort` (C4)
+ `ReasoningValidator` (C5). Behavior:

  1. Build the reasoning context. If outcome != AVAILABLE → REFUSED_NO_FACTS.
  2. Call the LLM. If outcome != GENERATED → propagate as-is.
  3. Validate the payload. If pass → GENERATED with retry=0.
  4. Otherwise call the LLM once more with the validator feedback. If
     this call's outcome != GENERATED → propagate that result.
  5. Validate the second payload. If pass → GENERATED with retry=1.
  6. Otherwise → REFUSED_BY_VALIDATOR carrying the second feedback and
     the structured violations (so C6 audit can replay which rules tripped).

Never raises. Every failure mode is a `ReasoningOutcome` variant.

C7 callers wanting to persist the artifact downstream use
`execute_with_context()` to also get the `ReasoningContext` the LLM saw —
that becomes the `factsSnapshot` JSONB sent to trading-core.
"""

from __future__ import annotations

import dataclasses
import logging
from dataclasses import dataclass

from ai_engine.core.domain.reasoning_context import ContextOutcome, ReasoningContext
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


@dataclass(frozen=True, slots=True)
class GenerationOutcome:
    """Bundle of (result, context) for callers that need the audit anchor.

    `context` is None when context_use_case never returned AVAILABLE (i.e.
    `result.outcome == REFUSED_NO_FACTS`). Otherwise it carries the exact
    `ReasoningContext` the LLM saw and the validator checked against.
    """

    result: ReasoningResult
    context: ReasoningContext | None


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
        """Backwards-compatible facade. Discards the context."""
        return self.execute_with_context(signal).result

    def execute_with_context(self, signal: SignalInput) -> GenerationOutcome:
        context_result = self._context_use_case.execute(signal.ticker)
        if context_result.outcome != ContextOutcome.AVAILABLE:
            logger.info(
                "event=validated_reasoning.refused_no_facts ticker=%s "
                "context_outcome=%s",
                signal.ticker,
                context_result.outcome.value,
            )
            return GenerationOutcome(
                result=ReasoningResult.refused_no_facts(
                    detail=f"context_outcome={context_result.outcome.value}"
                ),
                context=None,
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
            return GenerationOutcome(result=first, context=ctx)

        assert first.payload is not None
        validation = self._validator.validate(first.payload, signal, ctx)
        if validation.passed:
            logger.info(
                "event=validated_reasoning.generated ticker=%s retry=0",
                signal.ticker,
            )
            return GenerationOutcome(result=first, context=ctx)

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
            # Tag the result with retry=1 so the persisted artifact reflects
            # that the LLM was actually called twice.
            return GenerationOutcome(
                result=dataclasses.replace(second, retry_count=1),
                context=ctx,
            )

        assert second.payload is not None
        validation2 = self._validator.validate(second.payload, signal, ctx)
        if validation2.passed:
            logger.info(
                "event=validated_reasoning.generated ticker=%s retry=1",
                signal.ticker,
            )
            return GenerationOutcome(
                result=dataclasses.replace(second, retry_count=1),
                context=ctx,
            )

        logger.warning(
            "event=validated_reasoning.refused_by_validator ticker=%s "
            "violations=%d retry=1",
            signal.ticker,
            len(validation2.violations),
        )
        violations_payload = tuple(
            {"type": v.type.value, "detail": v.detail}
            for v in validation2.violations
        )
        refused_result = ReasoningResult.refused_by_validator(
            reason=validation2.feedback,
            raw_response=second.raw_response,
            violations=violations_payload,
        )
        return GenerationOutcome(
            result=dataclasses.replace(refused_result, retry_count=1),
            context=ctx,
        )
