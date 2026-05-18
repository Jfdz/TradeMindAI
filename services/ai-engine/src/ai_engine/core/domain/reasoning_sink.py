"""Outbound port + serializer for posting reasoning artifacts to trading-core (C7).

After `GenerateValidatedReasoningUseCase` finishes, the artifact must be
persisted on `trading_signals` (via the PUT endpoint added in C6) so the
admin audit view, the user-facing UI, and the C8 eval pipeline all see
the same row.

Design:
  - `ReasoningSinkPort` is the abstraction. The production impl is the
    `TradingCoreReasoningSink` HTTP client; tests pass a fake.
  - `build_wire_payload()` converts the domain types into the exact dict
    the trading-core controller expects (camelCase keys matching the
    Java `UpdateReasoningRequest` DTO).
  - Returns a typed `SinkResult` — never raises.
"""

from __future__ import annotations

import dataclasses
from dataclasses import dataclass
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Protocol

from ai_engine.core.domain.reasoning_context import ReasoningContext
from ai_engine.core.domain.reasoning_output import (
    ReasoningOutcome,
    ReasoningResult,
    SignalInput,
)


class SinkOutcome(str, Enum):
    PERSISTED = "PERSISTED"
    SIGNAL_NOT_FOUND = "SIGNAL_NOT_FOUND"
    UPSTREAM_FAILED = "UPSTREAM_FAILED"


@dataclass(frozen=True, slots=True)
class SinkResult:
    outcome: SinkOutcome
    signal_id: str
    detail: str | None = None

    @classmethod
    def persisted(cls, signal_id: str) -> SinkResult:
        return cls(outcome=SinkOutcome.PERSISTED, signal_id=signal_id)

    @classmethod
    def signal_not_found(cls, signal_id: str) -> SinkResult:
        return cls(outcome=SinkOutcome.SIGNAL_NOT_FOUND, signal_id=signal_id)

    @classmethod
    def upstream_failed(cls, signal_id: str, detail: str) -> SinkResult:
        return cls(
            outcome=SinkOutcome.UPSTREAM_FAILED, signal_id=signal_id, detail=detail
        )


class ReasoningSinkPort(Protocol):
    """Outbound port: persist a reasoning artifact on a specific signal.

    Implementations must never raise. Every failure mode maps to a
    `SinkResult` variant.
    """

    def persist(self, signal_id: str, payload: dict[str, Any]) -> SinkResult: ...


def build_wire_payload(
    result: ReasoningResult,
    context: ReasoningContext | None,
    signal: SignalInput,
    provider: str,
    model_version: str,
) -> dict[str, Any]:
    """Convert the domain types into the wire shape trading-core expects.

    The output keys are camelCase to match the Java `UpdateReasoningRequest`
    record. Optional fields are omitted (set to None) so the JSONB columns
    on trading-core store NULL rather than ``[]`` / ``{}`` sentinels.
    """
    text = result.payload.text if result.payload is not None else None
    price_refs = (
        list(result.payload.price_refs) if result.payload is not None else None
    )
    news_refs = (
        list(result.payload.news_refs) if result.payload is not None else None
    )
    validator_violations = (
        list(result.validator_violations) if result.validator_violations else None
    )

    facts_snapshot = _serialize_context(context, signal) if context else None

    generated_at = datetime.now(tz=timezone.utc).isoformat()

    return {
        "outcome": result.outcome.value,
        "reasoning": text,
        "reasoningGeneratedAt": generated_at,
        "provider": provider,
        "modelVersion": model_version,
        "retryCount": result.retry_count,
        "refusalReason": result.refusal_reason,
        "factsSnapshot": facts_snapshot,
        "priceRefs": price_refs,
        "newsRefs": news_refs,
        "validatorViolations": validator_violations,
        "rawAudit": result.raw_response,
    }


def _serialize_context(
    context: ReasoningContext, signal: SignalInput
) -> dict[str, Any]:
    """Render the audit anchor: full ReasoningContext + signal facts.

    Stored on trading-core as `reasoning_facts_snapshot JSONB`. The shape
    is internal-only (consumed by admin audit + C8 eval); we keep it as a
    plain dict instead of a versioned schema so additive fields on
    ReasoningContext do not require a migration.
    """
    return {
        "schemaVersion": context.schema_version,
        "ticker": context.ticker,
        "generatedAt": context.generated_at.isoformat(),
        "priceFacts": _asdict_skip_none(context.price_facts),
        "news": [_asdict_skip_none(n) for n in context.news],
        "errors": list(context.errors),
        "signal": {
            "signalType": signal.signal_type,
            "confidence": signal.confidence,
            "entryPrice": signal.entry_price,
            "predictedChangePct": signal.predicted_change_pct,
            "generatedAt": signal.generated_at.isoformat(),
        },
    }


def _asdict_skip_none(item: Any) -> dict[str, Any]:
    """Convert a frozen dataclass to dict, dropping None values.

    Keeps the JSONB blob compact and prevents Hibernate from storing
    explicit nulls for unused price-fact fields.
    """
    raw = dataclasses.asdict(item)
    return {k: v for k, v in raw.items() if v is not None}


def is_persistable(result: ReasoningResult) -> bool:
    """Decide whether the orchestrator should call the sink.

    For C7 we persist every outcome — REFUSED_NO_FACTS included — so the
    admin can see that ai-engine considered the signal and refused
    upstream. Returns False only if outcome is ERROR with no context,
    where the artifact carries no audit value (no facts, no LLM call).
    """
    return result.outcome != ReasoningOutcome.ERROR or result.raw_response is not None
