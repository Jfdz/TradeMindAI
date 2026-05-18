"""Tests for BuildReasoningContextUseCase."""

from __future__ import annotations

from datetime import datetime, timezone
from unittest.mock import MagicMock

from ai_engine.core.domain.reasoning_context import (
    SCHEMA_VERSION,
    ContextOutcome,
    ContextResult,
    NewsItem,
    PriceFacts,
    ReasoningContext,
)
from ai_engine.core.use_cases.build_reasoning_context import (
    BuildReasoningContextUseCase,
)


def _sample_context() -> ReasoningContext:
    return ReasoningContext(
        schema_version=SCHEMA_VERSION,
        ticker="AAPL",
        generated_at=datetime(2026, 5, 13, 12, 0, 0, tzinfo=timezone.utc),
        price_facts=PriceFacts(
            ticker="AAPL", timeframe="DAILY", snapshot_at="2026-05-12",
            bars_available=252, close=173.45, previous_close=170.10,
            pct_change_1d=1.97, pct_change_5d=None, pct_change_30d=None,
            high_52w=180.0, low_52w=150.0, sma_20=172.0, sma_50=165.0,
            sma_200=158.4, rsi_14=58.3, macd_histogram=0.5,
            volume=12_400_000, volume_avg_20d=11_500_000.0,
            support=168.0, resistance=178.0,
        ),
        news=(
            NewsItem(
                id=42, headline="Apple Q1 beats", published_at="2026-05-12T10:00:00Z",
                url="https://example.com/a", source="Reuters",
            ),
        ),
        errors=(),
    )


def test_execute_normalizes_ticker_and_passes_clamped_params():
    client = MagicMock()
    client.fetch_reasoning_context.return_value = ContextResult.available(_sample_context())
    use_case = BuildReasoningContextUseCase(client, news_hours=99999, news_limit=9999)

    result = use_case.execute("  aapl  ")

    assert result.outcome == ContextOutcome.AVAILABLE
    client.fetch_reasoning_context.assert_called_once_with(
        "AAPL", news_hours=168, news_limit=25
    )


def test_execute_returns_upstream_failed_on_blank_ticker():
    client = MagicMock()
    use_case = BuildReasoningContextUseCase(client)

    result = use_case.execute("   ")

    assert result.outcome == ContextOutcome.UPSTREAM_FAILED
    assert result.detail == "blank_ticker"
    client.fetch_reasoning_context.assert_not_called()


def test_execute_propagates_not_tracked_outcome():
    client = MagicMock()
    client.fetch_reasoning_context.return_value = ContextResult.not_tracked("UNKNOWN")
    use_case = BuildReasoningContextUseCase(client)

    result = use_case.execute("UNKNOWN")

    assert result.outcome == ContextOutcome.NOT_TRACKED


def test_execute_propagates_insufficient_history_outcome():
    client = MagicMock()
    client.fetch_reasoning_context.return_value = ContextResult.insufficient_history(
        "NEWCO", "INSUFFICIENT_HISTORY"
    )
    use_case = BuildReasoningContextUseCase(client)

    result = use_case.execute("NEWCO")

    assert result.outcome == ContextOutcome.INSUFFICIENT_HISTORY


def test_execute_propagates_upstream_failed_outcome():
    client = MagicMock()
    client.fetch_reasoning_context.return_value = ContextResult.upstream_failed(
        "AAPL", "http_503"
    )
    use_case = BuildReasoningContextUseCase(client)

    result = use_case.execute("AAPL")

    assert result.outcome == ContextOutcome.UPSTREAM_FAILED


def test_execute_tolerates_empty_news_without_failing():
    ctx = _sample_context()
    empty_news_ctx = ReasoningContext(
        schema_version=ctx.schema_version,
        ticker=ctx.ticker,
        generated_at=ctx.generated_at,
        price_facts=ctx.price_facts,
        news=(),
        errors=("news_aggregator_empty",),
    )
    client = MagicMock()
    client.fetch_reasoning_context.return_value = ContextResult.available(empty_news_ctx)
    use_case = BuildReasoningContextUseCase(client)

    result = use_case.execute("AAPL")

    assert result.outcome == ContextOutcome.AVAILABLE
    assert result.context is not None
    assert len(result.context.news) == 0
    assert result.context.errors == ("news_aggregator_empty",)


def test_execute_news_hours_lower_bound_clamps_to_one():
    client = MagicMock()
    client.fetch_reasoning_context.return_value = ContextResult.available(_sample_context())
    use_case = BuildReasoningContextUseCase(client, news_hours=-5, news_limit=0)

    use_case.execute("AAPL")

    client.fetch_reasoning_context.assert_called_once_with(
        "AAPL", news_hours=1, news_limit=1
    )


def test_execute_uses_cost_efficient_defaults_when_no_overrides():
    client = MagicMock()
    client.fetch_reasoning_context.return_value = ContextResult.available(_sample_context())
    use_case = BuildReasoningContextUseCase(client)

    use_case.execute("AAPL")

    client.fetch_reasoning_context.assert_called_once_with(
        "AAPL", news_hours=24, news_limit=4
    )


def test_execute_warns_when_news_set_has_no_images(caplog):
    import logging

    # Sample context's news[0] has no image — perfect for this assertion.
    client = MagicMock()
    client.fetch_reasoning_context.return_value = ContextResult.available(_sample_context())
    use_case = BuildReasoningContextUseCase(client)

    with caplog.at_level(
        logging.WARNING, logger="ai_engine.core.use_cases.build_reasoning_context"
    ):
        use_case.execute("AAPL")

    matched = [
        r for r in caplog.records
        if "reasoning_context.news_no_images" in r.getMessage()
    ]
    assert matched, "expected reasoning_context.news_no_images warning"


def test_execute_does_not_warn_when_at_least_one_news_item_has_image(caplog):
    import logging

    base = _sample_context()
    news_with_image = (
        NewsItem(
            id=42, headline="Apple Q1 beats", published_at="2026-05-12T10:00:00Z",
            url="https://example.com/a", source="Reuters",
            image="https://img.example.com/a.png",
        ),
    )
    ctx = ReasoningContext(
        schema_version=base.schema_version,
        ticker=base.ticker,
        generated_at=base.generated_at,
        price_facts=base.price_facts,
        news=news_with_image,
        errors=base.errors,
    )
    client = MagicMock()
    client.fetch_reasoning_context.return_value = ContextResult.available(ctx)
    use_case = BuildReasoningContextUseCase(client)

    with caplog.at_level(
        logging.WARNING, logger="ai_engine.core.use_cases.build_reasoning_context"
    ):
        use_case.execute("AAPL")

    assert not any(
        "reasoning_context.news_no_images" in r.getMessage() for r in caplog.records
    )
