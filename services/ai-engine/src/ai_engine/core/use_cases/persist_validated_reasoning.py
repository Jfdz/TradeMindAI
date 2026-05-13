"""End-to-end C7 orchestrator: generate-validated-reasoning + persist to trading-core.

Composes:
  - `GenerateValidatedReasoningUseCase` (C3c + C4 + C5 pipeline)
  - `ReasoningSinkPort` (C7 HTTP client to the trading-core PUT endpoint)

Returns a `(ReasoningResult, SinkResult)` tuple so the caller (e.g. a
RabbitMQ consumer) can react to both the reasoning outcome and the
persistence outcome independently — a reasoning that generated cleanly
but failed to persist (transport blip, signal id unknown) is a
different operational signal than a refusal that persisted fine.
"""

from __future__ import annotations

import logging

from ai_engine.core.domain.reasoning_output import ReasoningResult, SignalInput
from ai_engine.core.domain.reasoning_sink import (
    ReasoningSinkPort,
    SinkResult,
    build_wire_payload,
    is_persistable,
)
from ai_engine.core.use_cases.generate_validated_reasoning import (
    GenerateValidatedReasoningUseCase,
)

logger = logging.getLogger(__name__)


class PersistValidatedReasoningUseCase:
    """generate-validated → serialize → POST → return both outcomes."""

    def __init__(
        self,
        generator: GenerateValidatedReasoningUseCase,
        sink: ReasoningSinkPort,
        provider: str,
        model_version: str,
    ):
        self._generator = generator
        self._sink = sink
        self._provider = provider
        self._model_version = model_version

    def execute(
        self, signal_id: str, signal: SignalInput
    ) -> tuple[ReasoningResult, SinkResult]:
        outcome = self._generator.execute_with_context(signal)
        result = outcome.result

        if not is_persistable(result):
            logger.info(
                "event=persist_validated_reasoning.skipped signal_id=%s outcome=%s",
                signal_id,
                result.outcome.value,
            )
            return result, SinkResult.upstream_failed(signal_id, "non_persistable_outcome")

        payload = build_wire_payload(
            result=result,
            context=outcome.context,
            signal=signal,
            provider=self._provider,
            model_version=self._model_version,
        )
        sink_result = self._sink.persist(signal_id, payload)

        logger.info(
            "event=persist_validated_reasoning.completed signal_id=%s "
            "reasoning_outcome=%s sink_outcome=%s retry=%d",
            signal_id,
            result.outcome.value,
            sink_result.outcome.value,
            result.retry_count,
        )
        return result, sink_result
