"""RabbitMQ consumer for C9 — signal reasoning requests.

Listens on `trading-core.signal.reasoning.requested` (the queue
trading-core's `SignalGenerationService` and `PendingReasoningBackfillRunner`
publish to). For each message, parses the signal facts, runs them
through `PersistValidatedReasoningUseCase`, and ACKs.

The pipeline takes care of:
  - Building the grounded context via trading-core BFF (C3c)
  - Calling the LLM (C4) and validating (C5) with one retry
  - Posting the artifact back via PUT (C6/C7)

This consumer is the trigger boundary; it does not do persistence
itself. Failures during the pipeline still ACK (the pipeline always
returns a typed outcome, never raises) — transport blips against
trading-core surface as `SinkOutcome.UPSTREAM_FAILED` in logs, and the
existing `PendingReasoningBackfillRunner` cron re-publishes any signal
that stayed in `PENDING` status.

Protocol-level failures (malformed JSON, missing signalId) route to
the DLQ via `requeue=False` and the exchange-level DLX binding.
"""

from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from typing import Any

import aio_pika
from aio_pika import ExchangeType, IncomingMessage

from ai_engine.core.domain.reasoning_output import SignalInput
from ai_engine.core.use_cases.persist_validated_reasoning import (
    PersistValidatedReasoningUseCase,
)

logger = logging.getLogger(__name__)

REASONING_REQUEST_QUEUE = "trading-core.signal.reasoning.requested"


class ReasoningRequestConsumer:
    """Trigger boundary for the Track C reasoning pipeline."""

    def __init__(
        self,
        amqp_url: str,
        use_case: PersistValidatedReasoningUseCase,
        queue_name: str = REASONING_REQUEST_QUEUE,
    ):
        self._amqp_url = amqp_url
        self._use_case = use_case
        self._queue_name = queue_name
        self._connection: aio_pika.abc.AbstractConnection | None = None

    async def start(self) -> None:
        self._connection = await aio_pika.connect_robust(self._amqp_url)
        channel = await self._connection.channel()
        await channel.set_qos(prefetch_count=4)

        # Dead-letter exchange/queue — symmetric to the trading-core
        # publisher's existing DLX binding so unparseable messages do
        # not block the queue.
        dlx = await channel.declare_exchange(
            "dlx.signal.reasoning.requested",
            ExchangeType.DIRECT,
            durable=True,
        )
        dlq = await channel.declare_queue(
            "dlq.signal.reasoning.requested", durable=True
        )
        await dlq.bind(dlx, routing_key="dead")

        queue = await channel.declare_queue(
            self._queue_name,
            durable=True,
            arguments={
                "x-dead-letter-exchange": "dlx.signal.reasoning.requested",
                "x-dead-letter-routing-key": "dead",
            },
        )

        async def on_message(msg: IncomingMessage) -> None:
            # `requeue=False` so a malformed message dead-letters instead
            # of looping; the pipeline itself never raises, so any error
            # caught here is genuinely a protocol-level failure.
            async with msg.process(requeue=False):
                try:
                    self._handle(msg.body)
                except Exception:
                    logger.exception(
                        "event=reasoning_consumer.unhandled_error body_preview=%s",
                        msg.body[:200] if msg.body else b"",
                    )
                    raise

        await queue.consume(on_message)
        logger.info(
            "event=reasoning_consumer.started queue=%s", self._queue_name
        )

    async def stop(self) -> None:
        if self._connection:
            await self._connection.close()

    # --- parsing + dispatch ---

    def _handle(self, body: bytes) -> None:
        event = json.loads(body)
        signal_id = event.get("signalId")
        if not signal_id:
            raise ValueError("event missing required field signalId")

        signal = _to_signal_input(event)
        logger.info(
            "event=reasoning_consumer.dispatch signal_id=%s ticker=%s",
            signal_id,
            signal.ticker,
        )
        result, sink_result = self._use_case.execute(signal_id, signal)
        logger.info(
            "event=reasoning_consumer.processed signal_id=%s "
            "reasoning_outcome=%s sink_outcome=%s retry=%d",
            signal_id,
            result.outcome.value,
            sink_result.outcome.value,
            result.retry_count,
        )


def _to_signal_input(event: dict[str, Any]) -> SignalInput:
    """Convert the trading-core event payload into a SignalInput.

    Tolerates missing optional fields (predictedChangePct, entryPrice
    nulled out by trading-core on HOLD signals) and stamps
    `generated_at` with message-receive time when older publishes
    predate the C9 publisher change.
    """
    generated_at_raw = event.get("generatedAt")
    if generated_at_raw:
        generated_at = _parse_iso(generated_at_raw)
    else:
        # Backward-compat with messages published before C9 added
        # generatedAt to the wire shape. Receive time is close enough
        # for context lookup which uses current market data anyway.
        generated_at = datetime.now(tz=timezone.utc)

    confidence_raw = event.get("confidence")
    confidence = float(confidence_raw) if confidence_raw is not None else 0.0

    entry_price_raw = event.get("entryPrice")
    entry_price = float(entry_price_raw) if entry_price_raw is not None else 0.0

    pct_raw = event.get("predictedChangePct")
    predicted_pct = float(pct_raw) if pct_raw is not None else None

    target_raw = event.get("targetPrice")
    target_price = float(target_raw) if target_raw is not None else None

    stop_raw = event.get("stopLoss")
    stop_loss = float(stop_raw) if stop_raw is not None else None

    move_raw = event.get("expectedMovePct")
    expected_move_pct = float(move_raw) if move_raw is not None else None

    return SignalInput(
        ticker=str(event.get("ticker") or ""),
        signal_type=str(event.get("signalType") or "HOLD"),
        confidence=confidence,
        entry_price=entry_price,
        predicted_change_pct=predicted_pct,
        generated_at=generated_at,
        target_price=target_price,
        stop_loss=stop_loss,
        expected_move_pct=expected_move_pct,
    )


def _parse_iso(value: str) -> datetime:
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    return datetime.fromisoformat(value)
