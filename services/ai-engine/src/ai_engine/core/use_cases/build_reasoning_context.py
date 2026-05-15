"""Use case: assemble a ReasoningContext for a single ticker.

Wraps `TradingCoreClient` with validation of completeness and exposes
a single `execute(ticker)` entrypoint that the C4 LLM step will call.

The use case never raises on degraded outcomes; it returns a
`ContextResult` whose `outcome` is the single source of truth.
"""

from __future__ import annotations

import logging

from ai_engine.adapters.out.trading_core_client import TradingCoreClient
from ai_engine.core.domain.reasoning_context import ContextOutcome, ContextResult

logger = logging.getLogger(__name__)


class BuildReasoningContextUseCase:
    """Assembles `ReasoningContext` for ai-engine's reasoning generation."""

    def __init__(
        self,
        trading_core_client: TradingCoreClient,
        news_hours: int = 24,
        news_limit: int = 4,
    ):
        self._client = trading_core_client
        self._news_hours = max(1, min(news_hours, 168))
        self._news_limit = max(1, min(news_limit, 25))

    def execute(self, ticker: str) -> ContextResult:
        if not ticker or not ticker.strip():
            return ContextResult.upstream_failed("", "blank_ticker")
        normalized = ticker.strip().upper()

        result = self._client.fetch_reasoning_context(
            normalized,
            news_hours=self._news_hours,
            news_limit=self._news_limit,
        )

        if result.outcome != ContextOutcome.AVAILABLE:
            logger.info(
                "event=reasoning_context.unavailable ticker=%s outcome=%s detail=%s",
                normalized,
                result.outcome.value,
                result.detail or "",
            )
            return result

        # Defensive validation in case the client parser changes: refuse
        # if the context is missing the bare minimum needed to ground.
        ctx = result.context
        if ctx is None or ctx.price_facts.close is None:
            logger.warning(
                "event=reasoning_context.invariant_violation ticker=%s",
                normalized,
            )
            return ContextResult.upstream_failed(normalized, "invariant_violation")

        if not ctx.news:
            logger.info(
                "event=reasoning_context.news_empty ticker=%s errors=%s",
                normalized,
                list(ctx.errors),
            )
        else:
            logger.info(
                "event=reasoning_context.available ticker=%s news_count=%d errors=%s",
                normalized,
                len(ctx.news),
                list(ctx.errors),
            )
        return result
