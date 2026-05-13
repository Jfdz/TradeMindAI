"""Domain model for LLM-generated reasonings.

Contract between the C4 LLM step and C5 validator / C6 persistence.
`ReasoningOutcome` is a typed enum so downstream code branches on a
known set of states without inspecting exceptions.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import Enum
from typing import Protocol

from ai_engine.core.domain.reasoning_context import ReasoningContext

REASONING_SCHEMA_VERSION = "v1.0"


class ReasoningOutcome(str, Enum):
    """Typed outcome returned by the LLM step.

    Downstream (C5 validator, C6 persistence) reacts to this enum and
    never to exceptions raised during generation.
    """

    GENERATED = "GENERATED"
    REFUSED_BY_LLM = "REFUSED_BY_LLM"
    REFUSED_BY_VALIDATOR = "REFUSED_BY_VALIDATOR"
    REFUSED_LLM_DISABLED = "REFUSED_LLM_DISABLED"
    REFUSED_NO_FACTS = "REFUSED_NO_FACTS"
    ERROR = "ERROR"


@dataclass(frozen=True, slots=True)
class ReasoningPayload:
    """Validated, citable reasoning output.

    `price_refs` and `news_refs` exist so the validator (C5) can confirm
    that every number / event in `text` traces back to the grounded
    context, and so the UI can render footnotes.
    """

    text: str
    price_refs: tuple[str, ...]
    news_refs: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class SignalInput:
    """Caller-supplied signal facts.

    Joined with `ReasoningContext` (price + news) at use-case entry to
    form the full LLM context.
    """

    ticker: str
    signal_type: str
    confidence: float
    entry_price: float
    predicted_change_pct: float | None
    generated_at: datetime


@dataclass(frozen=True, slots=True)
class ReasoningResult:
    """Container returned from the LLM step. Always non-throwing.

    Invariants:
      - outcome == GENERATED → payload is non-None.
      - outcome != GENERATED → payload is None; refusal_reason or detail set.

    `retry_count` tracks how many times the C5 validator forced a retry
    before the final outcome. `validator_violations` carries the
    structured violations on REFUSED_BY_VALIDATOR so C6 audit can replay
    which rules tripped without re-parsing the feedback string.
    """

    outcome: ReasoningOutcome
    payload: ReasoningPayload | None = None
    refusal_reason: str | None = None
    raw_response: dict | None = None
    detail: str | None = None
    retry_count: int = 0
    validator_violations: tuple[dict, ...] | None = None

    @classmethod
    def generated(
        cls, payload: ReasoningPayload, raw_response: dict | None = None
    ) -> ReasoningResult:
        return cls(
            outcome=ReasoningOutcome.GENERATED,
            payload=payload,
            raw_response=raw_response,
        )

    @classmethod
    def refused_by_llm(
        cls, reason: str, raw_response: dict | None = None
    ) -> ReasoningResult:
        return cls(
            outcome=ReasoningOutcome.REFUSED_BY_LLM,
            refusal_reason=reason,
            raw_response=raw_response,
        )

    @classmethod
    def refused_by_validator(
        cls,
        reason: str,
        raw_response: dict | None = None,
        violations: tuple[dict, ...] | None = None,
    ) -> ReasoningResult:
        return cls(
            outcome=ReasoningOutcome.REFUSED_BY_VALIDATOR,
            refusal_reason=reason,
            raw_response=raw_response,
            validator_violations=violations,
        )

    @classmethod
    def refused_llm_disabled(cls) -> ReasoningResult:
        return cls(
            outcome=ReasoningOutcome.REFUSED_LLM_DISABLED,
            refusal_reason="llm_provider_is_stub",
        )

    @classmethod
    def refused_no_facts(cls, detail: str) -> ReasoningResult:
        return cls(
            outcome=ReasoningOutcome.REFUSED_NO_FACTS,
            refusal_reason="upstream_context_unavailable",
            detail=detail,
        )

    @classmethod
    def error(cls, detail: str) -> ReasoningResult:
        return cls(
            outcome=ReasoningOutcome.ERROR,
            refusal_reason="provider_error",
            detail=detail,
        )


class LlmReasoningPort(Protocol):
    """Outbound port: generate a reasoning given a signal + grounded context.

    Implementations must never raise. Every failure mode maps to a
    `ReasoningResult` variant so the orchestrator can react with a
    single match statement.

    `validator_feedback`: when provided (C5 retry path), the adapter
    must inject the feedback text into the user prompt so the LLM
    knows what its previous attempt did wrong. Stubs ignore it.
    """

    def generate(
        self,
        signal: SignalInput,
        context: ReasoningContext,
        validator_feedback: str | None = None,
    ) -> ReasoningResult: ...
