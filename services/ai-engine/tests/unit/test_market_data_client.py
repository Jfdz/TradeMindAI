from datetime import date as real_date

import httpx
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
    monkeypatch.setattr(market_data_client_module, "date", _FakeDate)

    def fake_get(url, params, headers, timeout):
        assert url == "http://market-data-service:8081/api/v1/prices/AAPL/history"
        assert params == {"timeframe": "DAILY", "from": "2025-11-18", "to": "2026-05-17", "size": 2}
        assert headers == {"X-Internal-Secret": "secret-123"}
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

    client = MarketDataClient("http://market-data-service:8081", internal_secret="secret-123")
    frame = client.fetch_ohlcv("AAPL", size=2)

    assert isinstance(frame, pd.DataFrame)
    assert list(frame.index.strftime("%Y-%m-%d")) == ["2026-04-16", "2026-04-17"]
    assert list(frame["close"]) == [101.0, 103.0]


def test_fetch_ohlcv_raises_for_empty_history(monkeypatch):
    monkeypatch.setattr(market_data_client_module, "date", _FakeDate)

    def fake_get(url, params, headers, timeout):
        return _FakeResponse({"content": []})

    monkeypatch.setattr(market_data_client_module.httpx, "get", fake_get)

    with pytest.raises(ValueError, match=r"No OHLCV data returned for ticker 'AAPL'"):
        MarketDataClient("http://market-data-service:8081").fetch_ohlcv("AAPL")


@pytest.mark.parametrize("status_code", [400, 401])
def test_fetch_ohlcv_propagates_http_errors(monkeypatch, status_code):
    monkeypatch.setattr(market_data_client_module, "date", _FakeDate)

    def fake_get(url, params, headers, timeout):
        request = httpx.Request("GET", url, params=params)
        response = httpx.Response(status_code, request=request)

        class _ErrorResponse:
            def raise_for_status(self):
                raise httpx.HTTPStatusError("bad response", request=request, response=response)

            def json(self):
                return {}

        return _ErrorResponse()

    monkeypatch.setattr(market_data_client_module.httpx, "get", fake_get)

    client = MarketDataClient("http://market-data-service:8081", internal_secret="secret-123")
    with pytest.raises(httpx.HTTPStatusError):
        client.fetch_ohlcv("AAPL")


class _FakeDate:
    @staticmethod
    def today():
        return real_date(2026, 5, 17)
