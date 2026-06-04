"""Reasoning context domain model — what the LLM consumes to ground a reasoning.

The structure must match the JSON returned by
`GET /api/v1/internal/reasoning-context/{ticker}` on trading-core
(see `shared/api-specs/trading-core-service.yaml`). Numeric fields are
nullable when the underlying provider could not compute them honestly;
callers must propagate nulls, not substitute zeros.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum

SCHEMA_VERSION = "v1.0"


class ContextOutcome(str, Enum):
    """Typed outcome returned by the context builder.

    The LLM step (C4) treats anything except `AVAILABLE` as a refusal
    signal and emits a deterministic fallback rather than fabricating
    facts.
    """

    AVAILABLE = "AVAILABLE"
    NOT_TRACKED = "NOT_TRACKED"
    INSUFFICIENT_HISTORY = "INSUFFICIENT_HISTORY"
    UPSTREAM_FAILED = "UPSTREAM_FAILED"


@dataclass(frozen=True, slots=True)
class PriceFacts:
    """Deterministic price + indicator snapshot used as ground truth."""

    ticker: str
    timeframe: str
    snapshot_at: str
    bars_available: int
    close: float
    previous_close: float | None
    pct_change_1d: float | None
    pct_change_5d: float | None
    pct_change_30d: float | None
    high_52w: float | None
    low_52w: float | None
    sma_20: float | None
    sma_50: float | None
    sma_200: float | None
    rsi_14: float | None
    macd_histogram: float | None
    volume: int
    volume_avg_20d: float | None
    support: float | None
    resistance: float | None


@dataclass(frozen=True, slots=True)
class NewsItem:
    """Single news entry — strictly headline + metadata, no article body."""

    id: int
    headline: str
    published_at: str
    url: str
    source: str | None = None
    category: str | None = None
    summary: str | None = None
    image: str | None = None


@dataclass(frozen=True, slots=True)
class AnalystConsensus:
    """Latest analyst-recommendation snapshot from the enrichment provider.

    All fields are integer counts, so the C5 validator — which only grounds
    decimal tokens — lets the LLM cite them verbatim without an
    `ungrounded_number` violation. `period` is the ISO date of the snapshot.
    Absent when the provider returned nothing or failed.
    """

    period: str | None
    strong_buy: int
    buy: int
    hold: int
    sell: int
    strong_sell: int
    total: int


@dataclass(frozen=True, slots=True)
class RecentPerformance:
    """The ticker's recent resolved win/loss track record (deterministic
    reflection injected so the reasoning learns from past same-ticker signals).

    Integer counts only → validator-safe, same as `AnalystConsensus`. Absent
    when there is no resolved history. `resolved_count` is `wins + losses`.
    """

    wins: int
    losses: int
    resolved_count: int


@dataclass(frozen=True, slots=True)
class ReasoningContext:
    """Full payload assembled for one reasoning generation."""

    schema_version: str
    ticker: str
    generated_at: datetime
    price_facts: PriceFacts
    news: tuple[NewsItem, ...]
    errors: tuple[str, ...]
    # Best-effort enrichment; None when the provider had no coverage. Defaulted
    # so existing constructions (and the JSON parser's additive evolution) stay
    # backward-compatible.
    analyst_consensus: AnalystConsensus | None = None
    recent_performance: RecentPerformance | None = None


@dataclass(frozen=True, slots=True)
class ContextResult:
    """Outcome wrapper: presence of `context` is determined by `outcome`.

    Invariants:
      - `outcome == AVAILABLE`  → `context` is non-None.
      - `outcome != AVAILABLE`  → `context` is None.
    """

    outcome: ContextOutcome
    context: ReasoningContext | None = None
    ticker: str | None = None
    detail: str | None = None
    degradation: tuple[str, ...] = field(default_factory=tuple)

    @classmethod
    def available(cls, context: ReasoningContext) -> ContextResult:
        return cls(
            outcome=ContextOutcome.AVAILABLE,
            context=context,
            ticker=context.ticker,
            degradation=context.errors,
        )

    @classmethod
    def not_tracked(cls, ticker: str) -> ContextResult:
        return cls(outcome=ContextOutcome.NOT_TRACKED, ticker=ticker)

    @classmethod
    def insufficient_history(cls, ticker: str, detail: str | None = None) -> ContextResult:
        return cls(outcome=ContextOutcome.INSUFFICIENT_HISTORY, ticker=ticker, detail=detail)

    @classmethod
    def upstream_failed(cls, ticker: str, detail: str) -> ContextResult:
        return cls(outcome=ContextOutcome.UPSTREAM_FAILED, ticker=ticker, detail=detail)
