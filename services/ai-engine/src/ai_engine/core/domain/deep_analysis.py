"""Domain model for the Fase 3 multi-agent deep-analysis mode.

Deep analysis is a premium, on-demand, deliberately NON-deterministic second
opinion: a bull / bear / judge / risk debate grounded in the same
`ReasoningContext` the deterministic reasoning path uses. It is explicitly
tagged *analysis, not an authoritative signal* and never overwrites the CNN
signal. Its only feedback onto the signal is a soft `Conviction` flag —
`CONTRADICTS` marks the signal for human review when the debate's verdict
opposes it; the signal value is never mutated (invariant #4).

Every number / event in any section still traces to `<price_facts>` / `<news>`
via the same C5 validator; only the low-confidence-label rule is dropped for
advocacy sections (it is a grounded-reasoning UX rule, not an anti-hallucination
rule). Determinism is *not* asserted for this mode — that is by design.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum

DEEP_ANALYSIS_SCHEMA_VERSION = "v1.0"


class AnalysisRole(str, Enum):
    BULL = "BULL"
    BEAR = "BEAR"
    JUDGE = "JUDGE"
    RISK = "RISK"


class VerdictDirection(str, Enum):
    BULLISH = "BULLISH"
    BEARISH = "BEARISH"
    NEUTRAL = "NEUTRAL"


class Conviction(str, Enum):
    """How the judge's verdict relates to the deterministic CNN signal."""

    AGREES = "AGREES"
    CONTRADICTS = "CONTRADICTS"  # -> soft low-conviction flag, surfaced for review
    UNCERTAIN = "UNCERTAIN"  # neutral verdict, or a non-directional (HOLD) signal


class DeepAnalysisOutcome(str, Enum):
    GENERATED = "GENERATED"
    PARTIAL = "PARTIAL"  # a verdict stands but >=1 section refused/failed/ungrounded
    REFUSED_NO_FACTS = "REFUSED_NO_FACTS"  # upstream context unavailable
    ERROR = "ERROR"  # no usable verdict — analysis has no headline


@dataclass(frozen=True, slots=True)
class AnalysisSection:
    """One role's contribution. `text` is blanked when the section is refused
    (by the model) or withheld (failed grounding) so ungrounded content never
    surfaces; `validator_violations` keeps the audit trail of why."""

    role: AnalysisRole
    text: str
    price_refs: tuple[str, ...] = ()
    news_refs: tuple[str, ...] = ()
    refused: bool = False
    refusal_reason: str | None = None
    validator_violations: tuple[dict, ...] = field(default_factory=tuple)


@dataclass(frozen=True, slots=True)
class DeepAnalysis:
    schema_version: str
    ticker: str
    signal_type: str
    generated_at: datetime
    sections: tuple[AnalysisSection, ...]  # bull, bear, risk
    verdict: AnalysisSection  # role == JUDGE
    verdict_direction: VerdictDirection
    conviction: Conviction
    provider: str
    model_version: str


@dataclass(frozen=True, slots=True)
class DeepAnalysisResult:
    """Non-throwing container returned by the use case."""

    outcome: DeepAnalysisOutcome
    analysis: DeepAnalysis | None = None
    detail: str | None = None

    @classmethod
    def generated(cls, analysis: DeepAnalysis) -> "DeepAnalysisResult":
        return cls(outcome=DeepAnalysisOutcome.GENERATED, analysis=analysis)

    @classmethod
    def partial(cls, analysis: DeepAnalysis) -> "DeepAnalysisResult":
        return cls(outcome=DeepAnalysisOutcome.PARTIAL, analysis=analysis)

    @classmethod
    def refused_no_facts(cls, detail: str) -> "DeepAnalysisResult":
        return cls(outcome=DeepAnalysisOutcome.REFUSED_NO_FACTS, detail=detail)

    @classmethod
    def error(cls, detail: str) -> "DeepAnalysisResult":
        return cls(outcome=DeepAnalysisOutcome.ERROR, detail=detail)


def parse_verdict_direction(raw: str | None) -> VerdictDirection | None:
    """Map the judge tool's `verdict` string to the enum; None if unusable."""
    if not raw:
        return None
    try:
        return VerdictDirection(str(raw).strip().upper())
    except ValueError:
        return None


def compute_conviction(signal_type: str, direction: VerdictDirection) -> Conviction:
    """Compare the debate verdict to the deterministic signal.

    A directional signal (BUY/SELL) that the verdict opposes is `CONTRADICTS`
    — the soft low-conviction flag. A neutral verdict or a non-directional
    signal (HOLD / unknown) is `UNCERTAIN`. Never raises.
    """
    st = (signal_type or "").upper()
    if direction == VerdictDirection.NEUTRAL or st not in ("BUY", "SELL"):
        return Conviction.UNCERTAIN
    signal_is_bullish = st == "BUY"
    verdict_is_bullish = direction == VerdictDirection.BULLISH
    return Conviction.AGREES if signal_is_bullish == verdict_is_bullish else Conviction.CONTRADICTS
