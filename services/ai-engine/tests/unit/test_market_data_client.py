import pandas as pd
import pytest

import ai_engine.adapters.out.market_data_client as market_data_client_module
from ai_engine.adapters.out.market_data_client import MarketDataClient


class _FakeResponse:
    def __init__(self, payload: dict):
        self._payload = payload

    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict:
        return self._payload


def test_fetch_ohlcv_returns_oldest_rows_first(monkeypatch):
    def fake_get(url, params, timeout):
        assert url == "http://market-data-service:8081/api/v1/prices/AAPL/history"
        assert params == {"timeframe": "DAILY", "size": 2}
        assert timeout == 10
        return _FakeResponse(
            {
                "content": [
                    {
                        "date": "2026-04-17",
                        "ohlcv": {
                            "open": 101.0,
                            "high": 104.0,
                            "low": 99.5,
                            "close": 103.0,
                            "volume": 1000,
                        },
                    },
                    {
                        "date": "2026-04-16",
                        "ohlcv": {
                            "open": 100.0,
                            "high": 102.0,
                            "low": 98.5,
                            "close": 101.0,
                            "volume": 900,
                        },
                    },
                ]
            }
        )

    monkeypatch.setattr(market_data_client_module.httpx, "get", fake_get)

    frame = MarketDataClient("http://market-data-service:8081").fetch_ohlcv("AAPL", size=2)

    assert isinstance(frame, pd.DataFrame)
    assert list(frame["date"]) == ["2026-04-16", "2026-04-17"]
    assert list(frame["close"]) == [101.0, 103.0]


def test_fetch_ohlcv_raises_for_empty_history(monkeypatch):
    def fake_get(url, params, timeout):
        return _FakeResponse({"content": []})

    monkeypatch.setattr(market_data_client_module.httpx, "get", fake_get)

    with pytest.raises(ValueError, match="No OHLCV data for AAPL"):
        MarketDataClient("http://market-data-service:8081").fetch_ohlcv("AAPL")
