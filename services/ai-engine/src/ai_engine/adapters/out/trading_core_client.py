"""HTTP client for the trading-core reasoning-context BFF.

Wraps `GET /api/v1/internal/reasoning-context/{ticker}` and maps the
four status outcomes documented in `trading-core-service.yaml` to a
typed `ContextResult`.

Design:
  - Sync httpx (matches `MarketDataClient`).
  - No retries: trading-core already returns typed outcomes; ai-engine
    reacts rather than retries. Transport noise → UPSTREAM_FAILED, and
    the LLM step treats that as a refusal signal.
  - Schema-version-aware: the parser only consumes the fields it knows
    about so additive changes upstream do not break this client.
"""

from __future__ import annotations

import logging
from datetime import datetime
from typing import Any

import httpx

from ai_engine.core.domain.reasoning_context import (
    SCHEMA_VERSION,
    AnalystConsensus,
    ContextResult,
    InsiderActivity,
    NewsItem,
    PriceFacts,
    ReasoningContext,
    RecentPerformance,
)

logger = logging.getLogger(__name__)


class TradingCoreClient:
    """Fetches the assembled reasoning context from trading-core."""

    def __init__(self, base_url: str, internal_secret: str = "", timeout: float = 10.0):
        self._base_url = base_url.rstrip("/")
        self._internal_secret = internal_secret
        self._timeout = timeout

    def fetch_reasoning_context(
        self,
        ticker: str,
        news_hours: int = 48,
        news_limit: int = 8,
    ) -> ContextResult:
        if not self._internal_secret:
            logger.error("event=trading_core.reasoning_context.no_secret ticker=%s", ticker)
            return ContextResult.upstream_failed(ticker, "internal_secret_not_configured")

        url = f"{self._base_url}/api/v1/internal/reasoning-context/{ticker}"
        params = {"newsHours": news_hours, "newsLimit": news_limit}
        headers = {"X-Internal-Secret": self._internal_secret}

        try:
            response = httpx.get(url, params=params, headers=headers, timeout=self._timeout)
        except httpx.RequestError as exc:
            logger.warning(
                "event=trading_core.reasoning_context.transport_failed ticker=%s detail=%s",
                ticker,
                str(exc),
            )
            return ContextResult.upstream_failed(ticker, f"transport: {exc!s}")

        status = response.status_code
        if status == 200:
            try:
                return self._parse_available(ticker, response.json())
            except (KeyError, TypeError, ValueError) as exc:
                logger.warning(
                    "event=trading_core.reasoning_context.parse_failed ticker=%s detail=%s",
                    ticker,
                    str(exc),
                )
                return ContextResult.upstream_failed(ticker, f"parse: {exc!s}")
        if status == 404:
            logger.info("event=trading_core.reasoning_context.not_tracked ticker=%s", ticker)
            return ContextResult.not_tracked(ticker)
        if status == 422:
            detail = _safe_error_field(response, "error") or "INSUFFICIENT_HISTORY"
            logger.info(
                "event=trading_core.reasoning_context.insufficient_history ticker=%s detail=%s",
                ticker,
                detail,
            )
            return ContextResult.insufficient_history(ticker, detail)
        if status in (503, 502, 504):
            logger.warning(
                "event=trading_core.reasoning_context.upstream_down ticker=%s status=%d",
                ticker,
                status,
            )
            return ContextResult.upstream_failed(ticker, f"http_{status}")
        if status == 401:
            logger.error("event=trading_core.reasoning_context.unauthorized ticker=%s", ticker)
            return ContextResult.upstream_failed(ticker, "unauthorized")

        logger.warning(
            "event=trading_core.reasoning_context.unexpected_status ticker=%s status=%d",
            ticker,
            status,
        )
        return ContextResult.upstream_failed(ticker, f"http_{status}")

    @staticmethod
    def _parse_available(ticker: str, payload: dict[str, Any]) -> ContextResult:
        price_facts_raw = payload.get("priceFacts")
        if not isinstance(price_facts_raw, dict):
            raise ValueError("priceFacts missing or not an object")
        if price_facts_raw.get("close") is None:
            raise ValueError("priceFacts.close is null — cannot ground a reasoning")

        price_facts = PriceFacts(
            ticker=str(price_facts_raw.get("ticker", ticker)),
            timeframe=str(price_facts_raw.get("timeframe", "DAILY")),
            snapshot_at=str(price_facts_raw["snapshotAt"]),
            bars_available=int(price_facts_raw.get("barsAvailable", 0)),
            close=float(price_facts_raw["close"]),
            previous_close=_opt_float(price_facts_raw.get("previousClose")),
            pct_change_1d=_opt_float(price_facts_raw.get("pctChange1d")),
            pct_change_5d=_opt_float(price_facts_raw.get("pctChange5d")),
            pct_change_30d=_opt_float(price_facts_raw.get("pctChange30d")),
            high_52w=_opt_float(price_facts_raw.get("high52w")),
            low_52w=_opt_float(price_facts_raw.get("low52w")),
            sma_20=_opt_float(price_facts_raw.get("sma20")),
            sma_50=_opt_float(price_facts_raw.get("sma50")),
            sma_200=_opt_float(price_facts_raw.get("sma200")),
            rsi_14=_opt_float(price_facts_raw.get("rsi14")),
            macd_histogram=_opt_float(price_facts_raw.get("macdHistogram")),
            volume=int(price_facts_raw.get("volume", 0)),
            volume_avg_20d=_opt_float(price_facts_raw.get("volumeAvg20d")),
            support=_opt_float(price_facts_raw.get("support")),
            resistance=_opt_float(price_facts_raw.get("resistance")),
        )

        news_raw = payload.get("news") or []
        news = tuple(
            NewsItem(
                id=int(item["id"]),
                headline=str(item["headline"]),
                published_at=str(item["publishedAt"]),
                url=str(item["url"]),
                source=item.get("source"),
                category=item.get("category"),
                summary=item.get("summary"),
                image=item.get("image"),
            )
            for item in news_raw
            if isinstance(item, dict) and item.get("headline") and item.get("url")
        )

        errors_raw = payload.get("errors") or []
        errors = tuple(str(err) for err in errors_raw if isinstance(err, str))

        # Additive enrichment: parse only when present so older trading-core
        # builds (no analystConsensus key) keep producing a valid context.
        analyst_raw = payload.get("analystConsensus")
        analyst_consensus = None
        if isinstance(analyst_raw, dict):
            analyst_consensus = AnalystConsensus(
                period=str(analyst_raw["period"]) if analyst_raw.get("period") else None,
                strong_buy=int(analyst_raw.get("strongBuy", 0)),
                buy=int(analyst_raw.get("buy", 0)),
                hold=int(analyst_raw.get("hold", 0)),
                sell=int(analyst_raw.get("sell", 0)),
                strong_sell=int(analyst_raw.get("strongSell", 0)),
                total=int(analyst_raw.get("total", 0)),
            )

        perf_raw = payload.get("recentPerformance")
        recent_performance = None
        if isinstance(perf_raw, dict):
            recent_performance = RecentPerformance(
                wins=int(perf_raw.get("wins", 0)),
                losses=int(perf_raw.get("losses", 0)),
                resolved_count=int(perf_raw.get("resolvedCount", 0)),
            )

        insider_raw = payload.get("insiderActivity")
        insider_activity = None
        if isinstance(insider_raw, dict):
            insider_activity = InsiderActivity(
                buy_count=int(insider_raw.get("buyCount", 0)),
                sell_count=int(insider_raw.get("sellCount", 0)),
                net_shares=int(insider_raw.get("netShares", 0)),
            )

        generated_at_raw = payload.get("generatedAt")
        if not generated_at_raw:
            raise ValueError("generatedAt is missing")
        generated_at = _parse_iso8601(str(generated_at_raw))

        ctx = ReasoningContext(
            schema_version=SCHEMA_VERSION,
            ticker=str(payload.get("ticker", ticker)).upper(),
            generated_at=generated_at,
            price_facts=price_facts,
            news=news,
            errors=errors,
            analyst_consensus=analyst_consensus,
            recent_performance=recent_performance,
            insider_activity=insider_activity,
        )
        return ContextResult.available(ctx)


def _opt_float(value: Any) -> float | None:
    if value is None:
        return None
    return float(value)


def _parse_iso8601(value: str) -> datetime:
    # Python's fromisoformat accepts the trailing "Z" only from 3.11+.
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    return datetime.fromisoformat(value)


def _safe_error_field(response: httpx.Response, key: str) -> str | None:
    try:
        body = response.json()
    except ValueError:
        return None
    if isinstance(body, dict) and key in body:
        return str(body[key])
    return None
