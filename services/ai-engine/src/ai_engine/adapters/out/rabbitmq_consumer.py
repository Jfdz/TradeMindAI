"""RabbitMQ consumers for prediction requests and market-data events."""

import json
import logging
from typing import Callable

import aio_pika
from aio_pika import ExchangeType, IncomingMessage, Message

logger = logging.getLogger(__name__)

PREDICTION_REQUEST_QUEUE = "ai-engine.prediction.requests"
PREDICTION_RESULT_EXCHANGE = "prediction.result.completed"

MARKET_DATA_QUEUE = "ai-engine.market-data.prices"
MARKET_DATA_EVENT = "market-data.prices.updated"


class PredictionRequestConsumer:
    """Consume batch prediction requests from RabbitMQ.

    Listens on `ai-engine.prediction.requests`, calls the provided
    *predict_fn* callback, and publishes results to the
    `prediction.result.completed` exchange.
    """

    def __init__(self, amqp_url: str, predict_fn: Callable[[list[str]], list[dict]]):
        self._amqp_url = amqp_url
        self._predict_fn = predict_fn
        self._connection: aio_pika.abc.AbstractConnection | None = None

    async def start(self) -> None:
        self._connection = await aio_pika.connect_robust(self._amqp_url)
        channel = await self._connection.channel()
        await channel.set_qos(prefetch_count=10)

        # Dead-letter exchange for failed messages
        dlx = await channel.declare_exchange(
            "dlx.prediction.requests",
            ExchangeType.DIRECT,
            durable=True,
        )
        dlq = await channel.declare_queue("dlq.prediction.requests", durable=True)
        await dlq.bind(dlx, routing_key="dead")

        queue = await channel.declare_queue(
            PREDICTION_REQUEST_QUEUE,
            durable=True,
            arguments={
                "x-dead-letter-exchange": "dlx.prediction.requests",
                "x-dead-letter-routing-key": "dead",
            },
        )
        result_exchange = await channel.declare_exchange(
            PREDICTION_RESULT_EXCHANGE,
            ExchangeType.FANOUT,
            durable=True,
        )

        async def on_message(msg: IncomingMessage) -> None:
            async with msg.process(requeue=False):
                try:
                    body = json.loads(msg.body)
                    tickers: list[str] = body.get("tickers", [])
                    predictions = self._predict_fn(tickers)
                    payload = json.dumps({"tickers": tickers, "predictions": predictions}).encode()
                    await result_exchange.publish(
                        Message(body=payload, content_type="application/json"),
                        routing_key="",
                    )
                except Exception:
                    logger.exception("Failed to process prediction request")
                    raise

        await queue.consume(on_message)
        logger.info("PredictionRequestConsumer started on %s", PREDICTION_REQUEST_QUEUE)

    async def stop(self) -> None:
        if self._connection:
            await self._connection.close()


class MarketDataEventConsumer:
    """Consume market-data price update events and trigger predictions.

    Listens on `ai-engine.market-data.prices`, and for each
    `market-data.prices.updated` event calls *trigger_fn* with the
    list of updated symbols.
    """

    def __init__(self, amqp_url: str, trigger_fn: Callable[[list[str]], None]):
        self._amqp_url = amqp_url
        self._trigger_fn = trigger_fn
        self._connection: aio_pika.abc.AbstractConnection | None = None

    async def start(self) -> None:
        self._connection = await aio_pika.connect_robust(self._amqp_url)
        channel = await self._connection.channel()
        await channel.set_qos(prefetch_count=20)

        dlx = await channel.declare_exchange(
            "dlx.market-data.prices",
            ExchangeType.DIRECT,
            durable=True,
        )
        dlq = await channel.declare_queue("dlq.market-data.prices", durable=True)
        await dlq.bind(dlx, routing_key="dead")

        queue = await channel.declare_queue(
            MARKET_DATA_QUEUE,
            durable=True,
            arguments={
                "x-dead-letter-exchange": "dlx.market-data.prices",
                "x-dead-letter-routing-key": "dead",
            },
        )

        async def on_message(msg: IncomingMessage) -> None:
            async with msg.process(requeue=False):
                try:
                    body = json.loads(msg.body)
                    if body.get("event") == MARKET_DATA_EVENT:
                        symbols: list[str] = body.get("symbols", [])
                        self._trigger_fn(symbols)
                except Exception:
                    logger.exception("Failed to process market-data event")
                    raise

        await queue.consume(on_message)
        logger.info("MarketDataEventConsumer started on %s", MARKET_DATA_QUEUE)

    async def stop(self) -> None:
        if self._connection:
            await self._connection.close()
