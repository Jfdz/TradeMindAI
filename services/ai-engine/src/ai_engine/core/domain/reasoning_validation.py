"""Deterministic validator over LLM-generated reasonings (C5).

The LLM's `emit_reasoning` tool schema already constrains the output
shape, but it cannot enforce that the *content* references only real
grounded facts. This module is the second pass: pure-Python rule checks
against the same `PriceFacts` + `news` + `SignalInput` the LLM saw.

Four rules, all deterministic and side-effect free:

  1. **Ungrounded number** — every decimal token in `payload.text` must
     fall within 0.5% of some non-null numeric field in `price_facts`
     or `signal.entry_price` / `signal.predicted_change_pct`. Integer
     tokens are ignored (they are typically counts like "5 days", not
     prices).

  2. **Ungrounded news URL** — every URL in `payload.news_refs` must
     match one of `context.news[*].url` byte-for-byte.

  3. **Missing low-confidence label** — when `signal.confidence < 0.50`,
     the text must contain at least one of
     ``"low confidence"``, ``"low-confidence"``, ``"tentative"``,
     ``"speculative"`` (case-insensitive).

  4. **Forbidden absolute word** — the text must not contain any of
     ``"definitely"``, ``"guaranteed"``, ``"certain"``, ``"sure thing"``,
     ``"will rise"``, ``"will fall"``.

The validator is the source of truth for ``feedback`` text sent back to
the LLM on retry — see ``GenerateValidatedReasoningUseCase``.
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from enum import Enum
from typing import Iterable

from ai_engine.core.domain.reasoning_context import ReasoningContext
from ai_engine.core.domain.reasoning_output import ReasoningPayload, SignalInput

logger = logging.getLogger(__name__)


class ValidationViolationType(str, Enum):
    UNGROUNDED_NUMBER = "ungrounded_number"
    UNGROUNDED_NEWS_URL = "ungrounded_news_url"
    MISSING_LOW_CONFIDENCE_LABEL = "missing_low_confidence_label"
    FORBIDDEN_ABSOLUTE_WORD = "forbidden_absolute_word"


@dataclass(frozen=True, slots=True)
class ValidationViolation:
    type: ValidationViolationType
    detail: str


@dataclass(frozen=True, slots=True)
class ValidationResult:
    passed: bool
    violations: tuple[ValidationViolation, ...]

    @property
    def feedback(self) -> str:
        """Human-readable summary fed back to the LLM on retry."""
        if self.passed:
            return ""
        return "\n".join(f"- [{v.type.value}] {v.detail}" for v in self.violations)


class ReasoningValidator:
    """Pure-function validator. Same input always yields the same result."""

    NUMERIC_TOLERANCE = 0.01  # 1% relative tolerance for price/indicator matching
    ABSOLUTE_TOLERANCE_NEAR_ZERO = 0.001  # used when the reference value is ~0
    # Backend-derived execution levels (target_price, stop_loss, expected_move_pct)
    # are computed deterministically by SignalMathService and must round-trip
    # exactly. Allow a tight cents-level band to tolerate display rounding
    # ("135.25" vs stored 135.252000) but nothing wider — that's the whole point
    # of having the math live in the backend.
    DERIVED_PRICE_TOLERANCE = 0.0005  # 0.05% relative
    DERIVED_PRICE_ABS_TOLERANCE = 0.005  # 0.005 absolute fallback near zero
    LOW_CONFIDENCE_THRESHOLD = 0.50

    # Decimal numbers only (e.g. "510.00", "58.3", "2.04"). Integer-only tokens
    # are skipped because they typically denote counts, periods, or rounded
    # mentions ("over the last 5 days", "3 quarters"), not price values that
    # need to ground in <price_facts>.
    _NUMBER_RE = re.compile(r"\b\d+\.\d+\b")

    _LOW_CONFIDENCE_TOKENS = (
        "low confidence",
        "low-confidence",
        "tentative",
        "speculative",
    )

    _ABSOLUTE_WORDS_RE = re.compile(
        r"\b(definitely|guaranteed|certain|sure thing|will rise|will fall)\b",
        re.IGNORECASE,
    )

    def validate(
        self,
        payload: ReasoningPayload,
        signal: SignalInput,
        context: ReasoningContext,
    ) -> ValidationResult:
        violations: list[ValidationViolation] = []

        violations.extend(self._check_absolute_words(payload.text))
        violations.extend(self._check_low_confidence_label(payload.text, signal))
        violations.extend(self._check_numeric_grounding(payload.text, signal, context))
        violations.extend(self._check_news_url_grounding(payload.news_refs, context))

        return ValidationResult(
            passed=(len(violations) == 0),
            violations=tuple(violations),
        )

    def _check_absolute_words(self, text: str) -> Iterable[ValidationViolation]:
        seen: set[str] = set()
        for match in self._ABSOLUTE_WORDS_RE.finditer(text):
            word = match.group().lower()
            if word in seen:
                continue
            seen.add(word)
            yield ValidationViolation(
                type=ValidationViolationType.FORBIDDEN_ABSOLUTE_WORD,
                detail=f"contains forbidden word '{match.group()}'",
            )

    def _check_low_confidence_label(
        self, text: str, signal: SignalInput
    ) -> Iterable[ValidationViolation]:
        if signal.confidence >= self.LOW_CONFIDENCE_THRESHOLD:
            return
        lower_text = text.lower()
        if any(token in lower_text for token in self._LOW_CONFIDENCE_TOKENS):
            return
        yield ValidationViolation(
            type=ValidationViolationType.MISSING_LOW_CONFIDENCE_LABEL,
            detail=(
                f"signal.confidence is {signal.confidence:.2f} (below "
                f"{self.LOW_CONFIDENCE_THRESHOLD:.2f}) but the reasoning text "
                f"does not contain any of: "
                f"{', '.join(repr(t) for t in self._LOW_CONFIDENCE_TOKENS)}"
            ),
        )

    def _check_numeric_grounding(
        self,
        text: str,
        signal: SignalInput,
        context: ReasoningContext,
    ) -> Iterable[ValidationViolation]:
        indicator_values = list(self._collect_indicator_facts(signal, context))
        derived_values = list(self._collect_derived_prices(signal))
        all_values = indicator_values + derived_values
        reported: set[float] = set()
        for match in self._NUMBER_RE.finditer(text):
            try:
                number = float(match.group())
            except ValueError:
                continue
            if number in reported:
                continue
            # Two-tier match: backend-derived levels must round-trip near
            # exactly; indicator/price facts get the wider 1% band.
            if self._matches_derived(number, derived_values):
                continue
            if self._matches_any(number, indicator_values):
                continue
            reported.add(number)
            nearest = self._nearest(number, all_values)
            logger.warning(
                "event=reasoning_validator.ungrounded_price ticker=%s "
                "mentioned=%s nearest=%s",
                signal.ticker,
                match.group(),
                "none" if nearest is None else f"{nearest}",
            )
            yield ValidationViolation(
                type=ValidationViolationType.UNGROUNDED_NUMBER,
                detail=(
                    f"number {match.group()} is not within "
                    f"{self.NUMERIC_TOLERANCE * 100:.1f}% of any indicator or "
                    f"within {self.DERIVED_PRICE_TOLERANCE * 100:.2f}% of any "
                    f"backend-derived target/stop value"
                ),
            )

    def _check_news_url_grounding(
        self,
        news_refs: tuple[str, ...],
        context: ReasoningContext,
    ) -> Iterable[ValidationViolation]:
        known_urls = {n.url for n in context.news}
        for url in news_refs:
            if url in known_urls:
                continue
            yield ValidationViolation(
                type=ValidationViolationType.UNGROUNDED_NEWS_URL,
                detail=f"news_refs contains URL not in <news>: {url}",
            )

    @staticmethod
    def _collect_indicator_facts(
        signal: SignalInput, context: ReasoningContext
    ) -> Iterable[float]:
        """Market indicators + the noisy/probabilistic signal fields.

        Eligible for the wider 1% tolerance band — these come from upstream
        with inherent rounding (sma_200 truncated to two decimals, rsi_14
        floored to one, model-predicted pct quantized at training time).
        """
        pf = context.price_facts
        for value in (
            pf.close,
            pf.previous_close,
            pf.pct_change_1d,
            pf.pct_change_5d,
            pf.pct_change_30d,
            pf.high_52w,
            pf.low_52w,
            pf.sma_20,
            pf.sma_50,
            pf.sma_200,
            pf.rsi_14,
            pf.macd_histogram,
            pf.volume_avg_20d,
            pf.support,
            pf.resistance,
            signal.entry_price,
            signal.predicted_change_pct,
        ):
            if value is not None:
                yield float(value)
        yield float(pf.volume)

    @staticmethod
    def _collect_derived_prices(signal: SignalInput) -> Iterable[float]:
        """Backend-derived execution levels.

        SignalMathService produces these deterministically; the validator
        accepts only a near-exact (0.05%) match to a stored value so the
        LLM cannot drift the cited target/stop without being caught.
        """
        for value in (signal.target_price, signal.stop_loss, signal.expected_move_pct):
            if value is not None:
                yield float(value)

    def _matches_any(self, number: float, known_values: list[float]) -> bool:
        for reference in known_values:
            if abs(reference) < 1e-9:
                if abs(number - reference) <= self.ABSOLUTE_TOLERANCE_NEAR_ZERO:
                    return True
            elif abs(number - reference) / abs(reference) <= self.NUMERIC_TOLERANCE:
                return True
        return False

    def _matches_derived(self, number: float, derived_values: list[float]) -> bool:
        for reference in derived_values:
            if abs(reference) < 1e-9:
                if abs(number - reference) <= self.DERIVED_PRICE_ABS_TOLERANCE:
                    return True
            elif abs(number - reference) / abs(reference) <= self.DERIVED_PRICE_TOLERANCE:
                return True
        return False

    @staticmethod
    def _nearest(number: float, known_values: list[float]) -> float | None:
        if not known_values:
            return None
        return min(known_values, key=lambda v: abs(v - number))
