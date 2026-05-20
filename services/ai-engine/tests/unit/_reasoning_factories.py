"""Shared builders for reasoning-pipeline tests.

Single source of truth for sample `PriceFacts`, `ReasoningContext`, and
`SignalInput` fixtures used across `test_stub_llm_reasoning_client`,
`test_anthropic_llm_reasoning_client`, `test_generate_reasoning`,
`test_build_reasoning_context`, and `test_reasoning_prompts`.

Centralizing keeps the test corpus DRY (Sonar duplication metric was
flagging ~55% on new-code lines before this module existed) and means
the canonical numeric values for a "META at 603" sample appear in one
place — so a future schema change only updates here.

Module is underscore-prefixed so pytest does not try to collect it as
a test file.
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from ai_engine.core.domain.reasoning_context import (
    SCHEMA_VERSION,
    NewsItem,
    PriceFacts,
    ReasoningContext,
)
from ai_engine.core.domain.reasoning_output import SignalInput

DEFAULT_GENERATED_AT = datetime(2026, 5, 13, 12, 0, 0, tzinfo=timezone.utc)


def build_price_facts(**overrides: Any) -> PriceFacts:
    """Sample PriceFacts for META at close=603.

    Pass keyword overrides to mutate individual fields without rebuilding
    the whole struct (e.g. `build_price_facts(close=None)` to simulate
    insufficient data, `build_price_facts(ticker="AAPL")` for a different
    ticker, etc.).
    """
    base: dict[str, Any] = dict(
        ticker="META",
        timeframe="DAILY",
        snapshot_at="2026-05-12",
        bars_available=252,
        close=603.0,
        previous_close=590.94,
        pct_change_1d=2.04,
        pct_change_5d=None,
        pct_change_30d=None,
        high_52w=638.0,
        low_52w=412.0,
        sma_20=595.10,
        sma_50=580.20,
        sma_200=510.0,
        rsi_14=58.3,
        macd_histogram=1.2,
        volume=12_400_000,
        volume_avg_20d=14_100_000.0,
        support=580.0,
        resistance=620.0,
    )
    base.update(overrides)
    return PriceFacts(**base)


def build_news_item(**overrides: Any) -> NewsItem:
    base: dict[str, Any] = dict(
        id=42,
        headline="META beats Q1 expectations",
        published_at="2026-05-12T10:00:00Z",
        url="https://reuters.com/x",
        source="Reuters",
    )
    base.update(overrides)
    return NewsItem(**base)


def build_reasoning_context(
    *,
    ticker: str = "META",
    price_facts: PriceFacts | None = None,
    news: tuple[NewsItem, ...] = (),
    errors: tuple[str, ...] = (),
    generated_at: datetime | None = None,
) -> ReasoningContext:
    return ReasoningContext(
        schema_version=SCHEMA_VERSION,
        ticker=ticker,
        generated_at=generated_at or DEFAULT_GENERATED_AT,
        price_facts=price_facts or build_price_facts(ticker=ticker),
        news=news,
        errors=errors,
    )


def build_signal_input(**overrides: Any) -> SignalInput:
    base: dict[str, Any] = dict(
        ticker="META",
        signal_type="BUY",
        confidence=0.62,
        entry_price=603.0,
        predicted_change_pct=4.5,
        generated_at=DEFAULT_GENERATED_AT,
    )
    base.update(overrides)
    return SignalInput(**base)
