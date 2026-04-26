from types import SimpleNamespace

import pandas as pd
import pytest

import ai_engine.adapters.out.market_data_client as market_data_client_module
import ai_engine.main as ai_main
from ai_engine.core.use_cases.prediction_service import PredictionResult


class _FakePredictionService:
    def __init__(self):
        self.calls: list[list[tuple[str, object]]] = []

    def predict_batch(self, pairs):
        self.calls.append(pairs)
        return [
            PredictionResult(
                ticker=ticker,
                direction="UP",
                confidence=0.91,
                predicted_change_pct=1.5,
                raw_logits=[0.1, 0.2, 0.7],
            )
            for ticker, _ in pairs
        ]


class _FakeMarketDataClient:
    def __init__(self, base_url):
        self.base_url = base_url

    def fetch_ohlcv(self, ticker):
        return pd.DataFrame({"ticker": [ticker], "close": [123.45]})


def test_make_sync_predict_returns_serialisable_predictions(monkeypatch):
    service = _FakePredictionService()
    ai_main.app.state.prediction_service = service
    ai_main.app.state.model_loaded = True
    monkeypatch.setattr(market_data_client_module, "MarketDataClient", _FakeMarketDataClient)

    result = ai_main._make_sync_predict(ai_main.app)(["AAPL", "MSFT"])

    assert len(result) == 2
    assert result[0]["ticker"] == "AAPL"
    assert result[0]["direction"] == "UP"
    assert service.calls[0][0][0] == "AAPL"


class _FakeExchange:
    def __init__(self, published_messages):
        self._published_messages = published_messages

    async def publish(self, message, routing_key=""):
        self._published_messages.append((message.body.decode(), message.content_type, routing_key))


class _FakeChannel:
    def __init__(self, published_messages):
        self._published_messages = published_messages

    async def set_qos(self, prefetch_count):
        return None

    async def declare_exchange(self, *args, **kwargs):
        return _FakeExchange(self._published_messages)

    async def declare_queue(self, *args, **kwargs):
        return object()


class _FakeConnection:
    def __init__(self, published_messages):
        self._published_messages = published_messages

    async def channel(self):
        return _FakeChannel(self._published_messages)

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb):
        return False


@pytest.mark.asyncio
async def test_make_market_data_trigger_fetches_predictions_and_publishes(monkeypatch):
    published_messages = []
    service = _FakePredictionService()
    settings = SimpleNamespace(
        rabbitmq_url="amqp://guest:guest@localhost",
        market_data_service_url="http://market-data-service:8081",
    )
    ai_main.app.state.prediction_service = service
    ai_main.app.state.model_loaded = True

    monkeypatch.setattr(market_data_client_module, "MarketDataClient", _FakeMarketDataClient)

    async def fake_connect_robust(url):
        assert url == settings.rabbitmq_url
        return _FakeConnection(published_messages)

    import aio_pika

    monkeypatch.setattr(aio_pika, "connect_robust", fake_connect_robust)

    trigger = ai_main._make_market_data_trigger(ai_main.app, settings)
    await trigger(["AAPL", "MSFT"])

    assert service.calls[0][0][0] == "AAPL"
    assert len(published_messages) == 1
    body, content_type, routing_key = published_messages[0]
    assert content_type == "application/json"
    assert routing_key == ""
    assert '"tickers": ["AAPL", "MSFT"]' in body
    assert '"direction": "UP"' in body
