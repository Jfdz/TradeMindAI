"""Tests for the C8 eval harness (scripts/run_reasoning_eval.py).

Two layers covered here:

1. The harness against the live corpus shipped in tests/eval — pins
   the current quality bar so a validator regression or a corpus typo
   shows up immediately (correctness >=95%, false-positive rate <=5%).

2. Pure unit tests for the helpers (load_cases, jaccard_token_overlap,
   evaluate) on synthetic inputs.

Importing the harness as a module rather than spawning a subprocess
keeps the test fast and lets pytest see coverage.
"""

from __future__ import annotations

from pathlib import Path

import pytest

from scripts.run_reasoning_eval import (
    CaseOutcome,
    EvalCase,
    EvalReport,
    _accumulate,
    evaluate,
    jaccard_token_overlap,
    load_cases,
)

CORPUS_PATH = (
    Path(__file__).resolve().parent.parent / "eval" / "reasonings_eval.jsonl"
)


# ---------- Live corpus quality gate ----------


@pytest.fixture(scope="module")
def live_cases() -> list[EvalCase]:
    return load_cases(CORPUS_PATH)


def test_corpus_loads_at_least_fifteen_cases(live_cases: list[EvalCase]) -> None:
    assert len(live_cases) >= 15


def test_corpus_validator_correctness_meets_gate(live_cases: list[EvalCase]) -> None:
    report, _ = evaluate(live_cases)
    assert report.validator_correctness >= 0.95, (
        f"validator correctness dropped to {report.validator_correctness:.2%}; "
        f"failing cases: {[f.case_id for f in report.failures]}"
    )


def test_corpus_false_positive_rate_within_gate(live_cases: list[EvalCase]) -> None:
    report, _ = evaluate(live_cases)
    assert report.false_positive_rate <= 0.05, (
        f"false-positive rate climbed to {report.false_positive_rate:.2%}"
    )


def test_corpus_hallucination_detection_rate_is_one(
    live_cases: list[EvalCase],
) -> None:
    # Every hallucinated case in the corpus must be caught by at least
    # one expected violation type. Drops here mean the validator regressed
    # on a real rule (or the case was relabelled by mistake).
    report, _ = evaluate(live_cases)
    assert report.hallucination_detection_rate == 1.0, (
        f"hallucination detection dropped to "
        f"{report.hallucination_detection_rate:.2%}"
    )


def test_corpus_exercises_all_four_violation_types(
    live_cases: list[EvalCase],
) -> None:
    types = {t for c in live_cases for t in c.expected_violation_types}
    assert "ungrounded_number" in types
    assert "ungrounded_news_url" in types
    assert "missing_low_confidence_label" in types
    assert "forbidden_absolute_word" in types


# ---------- Pure unit tests ----------


def test_jaccard_token_overlap_identical_returns_one():
    assert jaccard_token_overlap("foo bar baz", "foo bar baz") == 1.0


def test_jaccard_token_overlap_disjoint_returns_zero():
    assert jaccard_token_overlap("foo bar", "alpha beta") == 0.0


def test_jaccard_token_overlap_is_lowercase_and_punctuation_robust():
    # Punctuation is stripped by \w+; case is normalized.
    assert jaccard_token_overlap("META at 603.0", "meta AT 603 0") == 1.0


def test_jaccard_token_overlap_partial_match():
    # candidate {a, b, c}; reference {b, c, d}: intersection 2, union 4 → 0.5
    score = jaccard_token_overlap("a b c", "b c d")
    assert score == pytest.approx(0.5)


def test_load_cases_skips_comments_and_blank_lines(tmp_path: Path):
    contents = (
        "// comment 1\n"
        "\n"
        "# also a comment\n"
        '{"id": "x", "description": "d", '
        '"signal": {"ticker": "META", "signal_type": "BUY", "confidence": 0.85, '
        '"entry_price": 603.0, "predicted_change_pct": 1.0, '
        '"generated_at": "2026-05-13T12:00:00Z"}, '
        '"context": {"schema_version": "v1.0", "ticker": "META", '
        '"generated_at": "2026-05-13T12:00:00Z", "price_facts": '
        '{"ticker": "META", "snapshot_at": "2026-05-12", "bars_available": 252, '
        '"close": 603.0, "volume": 0}, "news": [], "errors": []}, '
        '"candidate_payload": {"text": "ok", "price_refs": [], "news_refs": []}, '
        '"expected": {"validator_passes": true, "violation_types": []}}\n'
    )
    p = tmp_path / "tiny.jsonl"
    p.write_text(contents)

    cases = load_cases(p)

    assert len(cases) == 1
    assert cases[0].id == "x"


def test_load_cases_raises_on_malformed_json(tmp_path: Path):
    p = tmp_path / "bad.jsonl"
    p.write_text("{this is not json\n")
    with pytest.raises(ValueError, match="line 1"):
        load_cases(p)


def test_load_cases_raises_when_required_field_missing(tmp_path: Path):
    p = tmp_path / "incomplete.jsonl"
    p.write_text('{"id": "x"}\n')
    with pytest.raises(ValueError, match="line 1: malformed eval case"):
        load_cases(p)


def test_accumulate_counts_correct_verdict():
    report = EvalReport()
    outcome = CaseOutcome(
        case_id="x",
        description="d",
        validator_passed=True,
        expected_passes=True,
        expected_violation_types=(),
        actual_violation_types=(),
        token_overlap=0.5,
    )
    _accumulate(report, outcome)
    assert report.total == 1
    assert report.correct_verdicts == 1
    assert report.expected_pass_cases == 1
    assert report.expected_pass_false_positives == 0
    assert report.token_overlap_count == 1
    assert report.token_overlap_sum == 0.5


def test_accumulate_counts_false_positive():
    report = EvalReport()
    outcome = CaseOutcome(
        case_id="x",
        description="d",
        validator_passed=False,
        expected_passes=True,
        expected_violation_types=(),
        actual_violation_types=("ungrounded_number",),
        token_overlap=None,
    )
    _accumulate(report, outcome)
    assert report.correct_verdicts == 0
    assert report.expected_pass_false_positives == 1
    assert outcome in report.failures


def test_accumulate_counts_caught_violation_only_when_types_match():
    report = EvalReport()
    # Expected ungrounded_number, but actual is forbidden_absolute_word —
    # validator rejected but on the wrong rule; should NOT count as caught.
    outcome = CaseOutcome(
        case_id="x",
        description="d",
        validator_passed=False,
        expected_passes=False,
        expected_violation_types=("ungrounded_number",),
        actual_violation_types=("forbidden_absolute_word",),
        token_overlap=None,
    )
    _accumulate(report, outcome)
    assert report.expected_violation_cases == 1
    assert report.expected_violation_caught == 0


def test_accumulate_counts_caught_when_expected_subset_of_actual():
    report = EvalReport()
    outcome = CaseOutcome(
        case_id="x",
        description="d",
        validator_passed=False,
        expected_passes=False,
        expected_violation_types=("ungrounded_number",),
        actual_violation_types=("ungrounded_number", "forbidden_absolute_word"),
        token_overlap=None,
    )
    _accumulate(report, outcome)
    assert report.expected_violation_caught == 1


def test_eval_report_metrics_with_empty_input():
    report = EvalReport()
    assert report.validator_correctness == 0.0
    assert report.hallucination_detection_rate == 1.0
    assert report.false_positive_rate == 0.0
    assert report.token_overlap_mean is None
