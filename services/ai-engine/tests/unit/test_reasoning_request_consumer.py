"""Tests for the C9 RabbitMQ consumer."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from unittest.mock import MagicMock

import aio_pika
import pytest

from ai_engine.adapters.in_.reasoning_request_consumer import (
    REASONING_REQUEST_QUEUE,
    ReasoningRequestConsumer,
    _to_signal_input,
)
from ai_engine.core.domain.reasoning_output import (
    ReasoningOutcome,
    ReasoningPayload,
    ReasoningResult,
)
from ai_engine.core.domain.reasoning_sink import SinkResult

# -------- aio-pika fake chain --------


class _FakeMessage:
    def __init__(self, body: bytes):
        self.body = body

    def process(self, requeue: bool = True):
        class _Ctx:
            async def __aenter__(self):
                return None

            async def __aexit__(self, *_args):
                return False

        return _Ctx()


class _FakeQueue:
    def __init__(self, captured_callbacks: list):
        self._captured = captured_callbacks

    async def bind(self, *_args, **_kwargs):
        return None

    async def consume(self, callback):
        self._captured.append(callback)
        return None


class _FakeExchange:
    pass


class _FakeChannel:
    def __init__(self, captured_callbacks: list, declared_queue_names: list):
        self._captured = captured_callbacks
        self._declared = declared_queue_names

    async def set_qos(self, prefetch_count: int):
        return None

    async def declare_exchange(self, *_args, **_kwargs):
        return _FakeExchange()

    async def declare_queue(self, name: str = "", *_args, **_kwargs):
        self._declared.append(name)
        return _FakeQueue(self._captured)


class _FakeConnection:
    def __init__(self, captured_callbacks: list, declared_queue_names: list):
        self._captured = captured_callbacks
        self._declared = declared_queue_names
        self.closed = False

    async def channel(self):
        return _FakeChannel(self._captured, self._declared)

    async def close(self):
        self.closed = True


# -------- _to_signal_input --------


def test_to_signal_input_parses_full_payload():
    event = {
        "signalId": "abc",
        "ticker": "META",
        "signalType": "BUY",
        "confidence": 0.62,
        "predictedChangePct": 4.5,
        "entryPrice": 603.0,
        "generatedAt": "2026-05-13T12:00:00Z",
    }
    signal = _to_signal_input(event)
    assert signal.ticker == "META"
    assert signal.signal_type == "BUY"
    assert signal.confidence == 0.62
    assert signal.entry_price == 603.0
    assert signal.predicted_change_pct == 4.5
    assert signal.generated_at == datetime(2026, 5, 13, 12, 0, 0, tzinfo=timezone.utc)


def test_to_signal_input_defaults_generated_at_to_now_when_missing():
    before = datetime.now(tz=timezone.utc)
    signal = _to_signal_input(
        {"signalId": "x", "ticker": "AAPL", "signalType": "HOLD", "confidence": 0.55}
    )
    after = datetime.now(tz=timezone.utc)
    assert before <= signal.generated_at <= after
    # Optional fields fall back to safe defaults.
    assert signal.entry_price == 0.0
    assert signal.predicted_change_pct is None


def test_to_signal_input_tolerates_null_optional_fields():
    event = {
        "signalId": "x",
        "ticker": "META",
        "signalType": "HOLD",
        "confidence": 0.50,
        "predictedChangePct": None,
        "entryPrice": None,
        "generatedAt": None,
    }
    signal = _to_signal_input(event)
    assert signal.entry_price == 0.0
    assert signal.predicted_change_pct is None


# -------- consumer.start() wires the queue --------


@pytest.mark.asyncio
async def test_start_declares_queue_with_dlq_arguments_and_consumes(monkeypatch):
    captured: list = []
    declared: list = []
    use_case = MagicMock()

    async def fake_connect_robust(url: str):
        assert url == "amqp://test"
        return _FakeConnection(captured, declared)

    monkeypatch.setattr(aio_pika, "connect_robust", fake_connect_robust)

    consumer = ReasoningRequestConsumer(
        "amqp://test", use_case, queue_name="custom.queue"
    )
    await consumer.start()

    # Both DLQ and main queue declared; consume callback captured.
    assert "dlq.signal.reasoning.requested" in declared
    assert "custom.queue" in declared
    assert len(captured) == 1


@pytest.mark.asyncio
async def test_start_defaults_to_trading_core_queue_name(monkeypatch):
    captured: list = []
    declared: list = []
    use_case = MagicMock()

    async def fake_connect_robust(_url: str):
        return _FakeConnection(captured, declared)

    monkeypatch.setattr(aio_pika, "connect_robust", fake_connect_robust)

    await ReasoningRequestConsumer("amqp://test", use_case).start()

    assert REASONING_REQUEST_QUEUE in declared
    assert REASONING_REQUEST_QUEUE == "trading-core.signal.reasoning.requested"


@pytest.mark.asyncio
async def test_start_stop_closes_connection(monkeypatch):
    captured: list = []
    declared: list = []
    conn = _FakeConnection(captured, declared)

    async def fake_connect_robust(_url: str):
        return conn

    monkeypatch.setattr(aio_pika, "connect_robust", fake_connect_robust)

    consumer = ReasoningRequestConsumer("amqp://test", MagicMock())
    await consumer.start()
    await consumer.stop()

    assert conn.closed


# -------- on_message dispatches to the use case --------


@pytest.mark.asyncio
async def test_on_message_invokes_use_case_with_parsed_signal(monkeypatch):
    captured: list = []
    declared: list = []

    use_case = MagicMock()
    use_case.execute.return_value = (
        ReasoningResult.generated(
            ReasoningPayload(text="ok", price_refs=("sma_200",), news_refs=())
        ),
        SinkResult.persisted("sig-abc"),
    )

    async def fake_connect_robust(_url: str):
        return _FakeConnection(captured, declared)

    monkeypatch.setattr(aio_pika, "connect_robust", fake_connect_robust)

    consumer = ReasoningRequestConsumer("amqp://test", use_case)
    await consumer.start()

    callback = captured[0]
    payload = {
        "signalId": "sig-abc",
        "ticker": "META",
        "signalType": "BUY",
        "confidence": 0.62,
        "predictedChangePct": 4.5,
        "entryPrice": 603.0,
        "generatedAt": "2026-05-13T12:00:00Z",
    }
    await callback(_FakeMessage(json.dumps(payload).encode()))

    use_case.execute.assert_called_once()
    args = use_case.execute.call_args.args
    assert args[0] == "sig-abc"
    assert args[1].ticker == "META"
    assert args[1].entry_price == 603.0


@pytest.mark.asyncio
async def test_on_message_raises_when_signal_id_missing(monkeypatch):
    captured: list = []
    declared: list = []
    use_case = MagicMock()

    async def fake_connect_robust(_url: str):
        return _FakeConnection(captured, declared)

    monkeypatch.setattr(aio_pika, "connect_robust", fake_connect_robust)

    await ReasoningRequestConsumer("amqp://test", use_case).start()

    callback = captured[0]
    payload = {"ticker": "META", "signalType": "BUY", "confidence": 0.62}
    with pytest.raises(ValueError, match="signalId"):
        await callback(_FakeMessage(json.dumps(payload).encode()))

    use_case.execute.assert_not_called()


@pytest.mark.asyncio
async def test_on_message_propagates_use_case_outcomes(monkeypatch):
    captured: list = []
    declared: list = []

    use_case = MagicMock()
    # Pipeline returns REFUSED_BY_VALIDATOR + UPSTREAM_FAILED sink; consumer
    # must still ACK (no exception raised from on_message).
    use_case.execute.return_value = (
        ReasoningResult(
            outcome=ReasoningOutcome.REFUSED_BY_VALIDATOR,
            refusal_reason="- [ungrounded_number] 900.0 not in facts",
            retry_count=1,
        ),
        SinkResult.upstream_failed("sig-abc", "http_503"),
    )

    async def fake_connect_robust(_url: str):
        return _FakeConnection(captured, declared)

    monkeypatch.setattr(aio_pika, "connect_robust", fake_connect_robust)
    await ReasoningRequestConsumer("amqp://test", use_case).start()

    callback = captured[0]
    payload = {
        "signalId": "sig-abc",
        "ticker": "META",
        "signalType": "BUY",
        "confidence": 0.62,
        "entryPrice": 603.0,
        "generatedAt": "2026-05-13T12:00:00Z",
    }
    # Should not raise — pipeline never raises and the consumer surfaces
    # outcomes via structured logs.
    await callback(_FakeMessage(json.dumps(payload).encode()))
    use_case.execute.assert_called_once()
