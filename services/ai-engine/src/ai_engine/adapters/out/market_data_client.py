"""HTTP client for fetching OHLCV data from market-data-service."""

import logging

import httpx
import pandas as pd

logger = logging.getLogger(__name__)


class MarketDataClient:
    """Fetches historical OHLCV bars from market-data-service REST API."""

    def __init__(self, base_url: str, timeout: float = 10.0):
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout

    def fetch_ohlcv(self, ticker: str, size: int = 100) -> pd.DataFrame:
        """Return a DataFrame with columns [date, open, high, low, close, volume].

        Rows are ordered oldest-first (ascending date) as required by the
        feature engineering pipeline.  The API returns newest-first, so the
        response content is reversed before building the DataFrame.
        """
        url = f"{self._base_url}/api/v1/prices/{ticker}/history"
        params = {"timeframe": "DAILY", "size": size}

        response = httpx.get(url, params=params, timeout=self._timeout)
        response.raise_for_status()

        bars = response.json().get("content", [])
        if not bars:
            raise ValueError(f"No OHLCV data returned for ticker '{ticker}'")

        records = [
            {
                "date": bar["date"],
                "open": float(bar["ohlcv"]["open"]),
                "high": float(bar["ohlcv"]["high"]),
                "low": float(bar["ohlcv"]["low"]),
                "close": float(bar["ohlcv"]["close"]),
                "volume": float(bar["ohlcv"]["volume"]),
            }
            for bar in reversed(bars)
        ]

        df = pd.DataFrame(records)
        df["date"] = pd.to_datetime(df["date"])
        df = df.set_index("date")
        return df
