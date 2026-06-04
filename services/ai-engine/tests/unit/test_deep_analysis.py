"""Domain logic for deep analysis: verdict parsing, conviction, result types."""

from __future__ import annotations

import pytest

from ai_engine.core.domain.deep_analysis import (
    Conviction,
    DeepAnalysisOutcome,
    DeepAnalysisResult,
    VerdictDirection,
    compute_conviction,
    parse_verdict_direction,
)


def test_parse_verdict_direction_accepts_known_values_case_insensitively():
    assert parse_verdict_direction("BULLISH") == VerdictDirection.BULLISH
    assert parse_verdict_direction("bearish") == VerdictDirection.BEARISH
    assert parse_verdict_direction("  Neutral  ") == VerdictDirection.NEUTRAL


def test_parse_verdict_direction_returns_none_for_unusable():
    assert parse_verdict_direction(None) is None
    assert parse_verdict_direction("") is None
    assert parse_verdict_direction("MAYBE") is None


@pytest.mark.parametrize(
    "signal_type,direction,expected",
    [
        ("BUY", VerdictDirection.BULLISH, Conviction.AGREES),
        ("BUY", VerdictDirection.BEARISH, Conviction.CONTRADICTS),
        ("SELL", VerdictDirection.BEARISH, Conviction.AGREES),
        ("SELL", VerdictDirection.BULLISH, Conviction.CONTRADICTS),
        ("BUY", VerdictDirection.NEUTRAL, Conviction.UNCERTAIN),
        ("HOLD", VerdictDirection.BULLISH, Conviction.UNCERTAIN),
        ("", VerdictDirection.BEARISH, Conviction.UNCERTAIN),
    ],
)
def test_compute_conviction(signal_type, direction, expected):
    assert compute_conviction(signal_type, direction) == expected


def test_result_classmethods_set_outcome_and_payload():
    refused = DeepAnalysisResult.refused_no_facts("context_outcome=NOT_TRACKED")
    assert refused.outcome == DeepAnalysisOutcome.REFUSED_NO_FACTS
    assert refused.analysis is None
    assert refused.detail == "context_outcome=NOT_TRACKED"

    errored = DeepAnalysisResult.error("judge_produced_no_verdict")
    assert errored.outcome == DeepAnalysisOutcome.ERROR
    assert errored.analysis is None
