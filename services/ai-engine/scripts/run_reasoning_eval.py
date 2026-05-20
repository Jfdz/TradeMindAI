"""Reasoning eval harness (C8).

Replays a JSONL corpus of (signal, context, candidate_payload, expected)
tuples through `ReasoningValidator` (C5) and reports correctness metrics:

  - **validator_correctness** — fraction of cases where the validator's
    pass/fail verdict matched the case's `expected.validator_passes`.
  - **hallucination_detection_rate** — of cases tagged with at least one
    expected violation type, the fraction the validator caught.
  - **false_positive_rate** — of cases expected to pass cleanly, the
    fraction the validator mistakenly rejected.
  - **token_overlap_mean** — Jaccard token overlap between
    `candidate_payload.text` and `ground_truth_text` averaged over the
    cases that supply a ground-truth string. This is forward-looking
    infrastructure: while the corpus is hand-written, the metric only
    reflects the consistency between paired hand-written strings; once
    real LLM outputs are captured into the corpus, it becomes a
    coherence proxy versus the human reference.

Use as a CLI:

    python -m ai_engine.scripts.run_reasoning_eval \\
        --jsonl tests/eval/reasonings_eval.jsonl \\
        --min-correctness 0.95 \\
        --max-false-positive-rate 0.05

Exit code is 0 when both thresholds hold; 1 otherwise. CI gates merges
against this so a regression in the validator's coverage shows up before
the change reaches production.
"""

from __future__ import annotations

import argparse
import json
import logging
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any

from ai_engine.core.domain.reasoning_context import (
    NewsItem,
    PriceFacts,
    ReasoningContext,
)
from ai_engine.core.domain.reasoning_output import ReasoningPayload, SignalInput
from ai_engine.core.domain.reasoning_validation import (
    ReasoningValidator,
    ValidationResult,
)

logger = logging.getLogger("reasoning_eval")

_TOKEN_RE = re.compile(r"\w+")


# ---------- Domain types for the harness ----------


@dataclass(frozen=True, slots=True)
class EvalCase:
    """One row of the JSONL corpus."""

    id: str
    description: str
    signal: SignalInput
    context: ReasoningContext
    candidate_payload: ReasoningPayload
    expected_passes: bool
    expected_violation_types: tuple[str, ...]
    ground_truth_text: str | None


@dataclass(frozen=True, slots=True)
class CaseOutcome:
    case_id: str
    description: str
    validator_passed: bool
    expected_passes: bool
    expected_violation_types: tuple[str, ...]
    actual_violation_types: tuple[str, ...]
    token_overlap: float | None


@dataclass(slots=True)
class EvalReport:
    total: int = 0
    correct_verdicts: int = 0
    expected_violation_cases: int = 0
    expected_violation_caught: int = 0
    expected_pass_cases: int = 0
    expected_pass_false_positives: int = 0
    token_overlap_sum: float = 0.0
    token_overlap_count: int = 0
    failures: list[CaseOutcome] = field(default_factory=list)

    @property
    def validator_correctness(self) -> float:
        return self.correct_verdicts / self.total if self.total else 0.0

    @property
    def hallucination_detection_rate(self) -> float:
        if not self.expected_violation_cases:
            return 1.0
        return self.expected_violation_caught / self.expected_violation_cases

    @property
    def false_positive_rate(self) -> float:
        if not self.expected_pass_cases:
            return 0.0
        return self.expected_pass_false_positives / self.expected_pass_cases

    @property
    def token_overlap_mean(self) -> float | None:
        if not self.token_overlap_count:
            return None
        return self.token_overlap_sum / self.token_overlap_count


# ---------- Loading ----------


def load_cases(jsonl_path: Path) -> list[EvalCase]:
    """Parse every non-empty, non-comment line of the JSONL into an EvalCase."""
    cases: list[EvalCase] = []
    with jsonl_path.open("r", encoding="utf-8") as f:
        for line_number, raw in enumerate(f, start=1):
            line = raw.strip()
            if not line or line.startswith("//") or line.startswith("#"):
                continue
            try:
                payload = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(
                    f"line {line_number} is not valid JSON: {exc.msg}"
                ) from exc
            cases.append(_parse_case(payload, line_number))
    return cases


def _parse_case(raw: dict[str, Any], line_number: int) -> EvalCase:
    try:
        return EvalCase(
            id=str(raw["id"]),
            description=str(raw.get("description", "")),
            signal=_parse_signal(raw["signal"]),
            context=_parse_context(raw["context"]),
            candidate_payload=_parse_payload(raw["candidate_payload"]),
            expected_passes=bool(raw["expected"]["validator_passes"]),
            expected_violation_types=tuple(
                str(t) for t in raw["expected"].get("violation_types", [])
            ),
            ground_truth_text=raw.get("ground_truth_text"),
        )
    except (KeyError, TypeError, ValueError) as exc:
        raise ValueError(f"line {line_number}: malformed eval case ({exc})") from exc


def _parse_signal(raw: dict[str, Any]) -> SignalInput:
    return SignalInput(
        ticker=str(raw["ticker"]),
        signal_type=str(raw["signal_type"]),
        confidence=float(raw["confidence"]),
        entry_price=float(raw["entry_price"]),
        predicted_change_pct=(
            float(raw["predicted_change_pct"])
            if raw.get("predicted_change_pct") is not None
            else None
        ),
        generated_at=_parse_iso(raw["generated_at"]),
    )


def _parse_context(raw: dict[str, Any]) -> ReasoningContext:
    return ReasoningContext(
        schema_version=str(raw.get("schema_version", "v1.0")),
        ticker=str(raw["ticker"]),
        generated_at=_parse_iso(raw["generated_at"]),
        price_facts=_parse_price_facts(raw["price_facts"]),
        news=tuple(_parse_news_item(n) for n in raw.get("news", [])),
        errors=tuple(str(e) for e in raw.get("errors", [])),
    )


def _parse_price_facts(raw: dict[str, Any]) -> PriceFacts:
    return PriceFacts(
        ticker=str(raw["ticker"]),
        timeframe=str(raw.get("timeframe", "DAILY")),
        snapshot_at=str(raw["snapshot_at"]),
        bars_available=int(raw["bars_available"]),
        close=float(raw["close"]),
        previous_close=_opt_float(raw.get("previous_close")),
        pct_change_1d=_opt_float(raw.get("pct_change_1d")),
        pct_change_5d=_opt_float(raw.get("pct_change_5d")),
        pct_change_30d=_opt_float(raw.get("pct_change_30d")),
        high_52w=_opt_float(raw.get("high_52w")),
        low_52w=_opt_float(raw.get("low_52w")),
        sma_20=_opt_float(raw.get("sma_20")),
        sma_50=_opt_float(raw.get("sma_50")),
        sma_200=_opt_float(raw.get("sma_200")),
        rsi_14=_opt_float(raw.get("rsi_14")),
        macd_histogram=_opt_float(raw.get("macd_histogram")),
        volume=int(raw.get("volume", 0)),
        volume_avg_20d=_opt_float(raw.get("volume_avg_20d")),
        support=_opt_float(raw.get("support")),
        resistance=_opt_float(raw.get("resistance")),
    )


def _parse_news_item(raw: dict[str, Any]) -> NewsItem:
    return NewsItem(
        id=int(raw["id"]),
        headline=str(raw["headline"]),
        published_at=str(raw["published_at"]),
        url=str(raw["url"]),
        source=raw.get("source"),
        category=raw.get("category"),
        summary=raw.get("summary"),
        image=raw.get("image"),
    )


def _parse_payload(raw: dict[str, Any]) -> ReasoningPayload:
    return ReasoningPayload(
        text=str(raw["text"]),
        price_refs=tuple(str(r) for r in raw.get("price_refs", [])),
        news_refs=tuple(str(r) for r in raw.get("news_refs", [])),
    )


def _opt_float(value: Any) -> float | None:
    if value is None:
        return None
    return float(value)


def _parse_iso(value: str) -> datetime:
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    return datetime.fromisoformat(value)


# ---------- Metrics ----------


def evaluate(
    cases: list[EvalCase], validator: ReasoningValidator | None = None
) -> tuple[EvalReport, list[CaseOutcome]]:
    """Run every case through the validator and return aggregate + per-case outcomes."""
    validator = validator or ReasoningValidator()
    report = EvalReport()
    outcomes: list[CaseOutcome] = []
    for case in cases:
        validation = validator.validate(case.candidate_payload, case.signal, case.context)
        outcome = _build_outcome(case, validation)
        outcomes.append(outcome)
        _accumulate(report, outcome)
    return report, outcomes


def _build_outcome(case: EvalCase, validation: ValidationResult) -> CaseOutcome:
    actual_types = tuple(v.type.value for v in validation.violations)
    token_overlap = None
    if (
        validation.passed
        and case.expected_passes
        and case.ground_truth_text
    ):
        token_overlap = jaccard_token_overlap(
            case.candidate_payload.text, case.ground_truth_text
        )
    return CaseOutcome(
        case_id=case.id,
        description=case.description,
        validator_passed=validation.passed,
        expected_passes=case.expected_passes,
        expected_violation_types=case.expected_violation_types,
        actual_violation_types=actual_types,
        token_overlap=token_overlap,
    )


def _accumulate(report: EvalReport, outcome: CaseOutcome) -> None:
    report.total += 1
    if outcome.validator_passed == outcome.expected_passes:
        report.correct_verdicts += 1
    else:
        report.failures.append(outcome)
    if outcome.expected_passes:
        report.expected_pass_cases += 1
        if not outcome.validator_passed:
            report.expected_pass_false_positives += 1
    else:
        report.expected_violation_cases += 1
        # "caught" means the validator flagged it at all — type-set agreement
        # is a strict subset check, see hallucination_detection_rate.
        if not outcome.validator_passed:
            # We further require at least one expected type in actual to count
            # as caught (lazy detection on a wrong rule is not "caught").
            actual = set(outcome.actual_violation_types)
            expected = set(outcome.expected_violation_types)
            if expected.issubset(actual):
                report.expected_violation_caught += 1
    if outcome.token_overlap is not None:
        report.token_overlap_sum += outcome.token_overlap
        report.token_overlap_count += 1


def jaccard_token_overlap(candidate: str, reference: str) -> float:
    """Lower-case word-token Jaccard similarity. Returns 0..1."""
    a = set(t.lower() for t in _TOKEN_RE.findall(candidate))
    b = set(t.lower() for t in _TOKEN_RE.findall(reference))
    if not a and not b:
        return 1.0
    if not a or not b:
        return 0.0
    intersection = len(a & b)
    union = len(a | b)
    return intersection / union if union else 0.0


# ---------- Reporting + CLI ----------


def format_report(report: EvalReport, outcomes: list[CaseOutcome]) -> str:
    lines = [
        "Reasoning eval — C8 harness",
        "=" * 48,
        f"Cases:                        {report.total}",
        f"Validator correctness:        {report.validator_correctness:.2%}",
        f"Hallucination detection rate: {report.hallucination_detection_rate:.2%}",
        f"False-positive rate:          {report.false_positive_rate:.2%}",
    ]
    if report.token_overlap_mean is not None:
        lines.append(
            f"Token overlap mean (passing): {report.token_overlap_mean:.3f}"
        )
    if report.failures:
        lines.append("")
        lines.append(f"FAILURES ({len(report.failures)}):")
        for f in report.failures:
            expected = (
                "pass"
                if f.expected_passes
                else f"fail ({', '.join(f.expected_violation_types) or 'any'})"
            )
            actual = (
                "pass"
                if f.validator_passed
                else f"fail ({', '.join(f.actual_violation_types) or 'any'})"
            )
            lines.append(f"  - {f.case_id}: expected {expected}, got {actual}")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Reasoning eval harness")
    parser.add_argument(
        "--jsonl",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "tests" / "eval" / "reasonings_eval.jsonl",
        help="Path to the JSONL eval corpus",
    )
    parser.add_argument(
        "--min-correctness",
        type=float,
        default=0.95,
        help="Fail the run when validator_correctness drops below this",
    )
    parser.add_argument(
        "--max-false-positive-rate",
        type=float,
        default=0.05,
        help="Fail the run when false_positive_rate exceeds this",
    )
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.WARNING, format="%(message)s")

    if not args.jsonl.exists():
        print(f"ERROR: eval corpus not found: {args.jsonl}", file=sys.stderr)
        return 2

    cases = load_cases(args.jsonl)
    if not cases:
        print(f"ERROR: eval corpus is empty: {args.jsonl}", file=sys.stderr)
        return 2

    report, outcomes = evaluate(cases)
    print(format_report(report, outcomes))

    threshold_breached = (
        report.validator_correctness < args.min_correctness
        or report.false_positive_rate > args.max_false_positive_rate
    )
    if threshold_breached:
        print(
            "\nFAIL: thresholds breached "
            f"(min_correctness={args.min_correctness:.2%}, "
            f"max_false_positive_rate={args.max_false_positive_rate:.2%})",
            file=sys.stderr,
        )
        return 1
    print("\nOK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
