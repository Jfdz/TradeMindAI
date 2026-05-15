"""Tests for ReasoningValidator — the C5 deterministic safety net.

Each rule is exercised in isolation (happy path + at least one
violation), then a combo test ensures multiple simultaneous violations
all surface in `result.violations` (the feedback string sent back to
the LLM on retry depends on all of them being present).
"""

from __future__ import annotations

from ai_engine.core.domain.reasoning_output import ReasoningPayload
from ai_engine.core.domain.reasoning_validation import (
    ReasoningValidator,
    ValidationViolationType,
)
from tests.unit._reasoning_factories import (
    build_news_item,
    build_reasoning_context,
    build_signal_input,
)

_VALIDATOR = ReasoningValidator()


def _payload(text: str, news_refs: tuple[str, ...] = ()) -> ReasoningPayload:
    return ReasoningPayload(text=text, price_refs=(), news_refs=news_refs)


# ---------- Rule 1: numeric grounding ----------


def test_pass_when_every_decimal_matches_price_facts_within_tolerance():
    ctx = build_reasoning_context()  # close=603.0, sma_200=510.0, rsi_14=58.3
    sig = build_signal_input()
    payload = _payload(
        "Price 603.0 sits above sma_200 (510.0) with rsi_14 at 58.3. Bullish."
    )

    result = _VALIDATOR.validate(payload, sig, ctx)

    assert result.passed
    assert result.violations == ()


def test_pass_when_decimal_is_within_relative_tolerance():
    # close=603.0; 605.99 is +0.495% — inside the 1% band.
    ctx = build_reasoning_context()
    payload = _payload("Price hovered near 605.99 during the session.")

    result = _VALIDATOR.validate(payload, build_signal_input(), ctx)

    assert result.passed, f"expected pass, got {result.feedback}"


def test_flag_ungrounded_decimal_outside_tolerance():
    # close=603.0; 700.0 is +16% — outside the 1% band.
    ctx = build_reasoning_context()
    payload = _payload("Resistance at 700.0 looks far away.")

    result = _VALIDATOR.validate(payload, build_signal_input(), ctx)

    assert not result.passed
    assert any(
        v.type == ValidationViolationType.UNGROUNDED_NUMBER for v in result.violations
    )


def test_integer_only_tokens_are_skipped_to_avoid_false_positives():
    # "5 days", "200 bars" — counts that should not need to ground.
    ctx = build_reasoning_context()
    payload = _payload("Over the last 5 days with 200 bars of history, trend holds.")

    result = _VALIDATOR.validate(payload, build_signal_input(), ctx)

    # No UNGROUNDED_NUMBER from integers. (Other rules may add violations.)
    assert not any(
        v.type == ValidationViolationType.UNGROUNDED_NUMBER for v in result.violations
    )


def test_predicted_change_pct_counts_as_grounded_value():
    # signal.predicted_change_pct=4.5 — citing 4.5 in the text must pass.
    ctx = build_reasoning_context()
    sig = build_signal_input(predicted_change_pct=4.5)
    payload = _payload(
        "The model projects +4.5 over the next session, well above sma_20 trend at 595.10."
    )

    result = _VALIDATOR.validate(payload, sig, ctx)

    assert result.passed, f"expected pass, got {result.feedback}"


def test_entry_price_counts_as_grounded_value():
    ctx = build_reasoning_context()
    sig = build_signal_input(entry_price=603.0)
    payload = _payload("Entry at 603.0 sits at the close.")

    result = _VALIDATOR.validate(payload, sig, ctx)

    assert result.passed


# ---------- Rule 2: news URL grounding ----------


def test_news_ref_url_must_match_one_in_context_exactly():
    news = (build_news_item(url="https://reuters.com/x"),)
    ctx = build_reasoning_context(news=news)
    payload = _payload(
        "Earnings beat suggests upside.",
        news_refs=("https://reuters.com/x",),
    )

    result = _VALIDATOR.validate(payload, build_signal_input(), ctx)

    assert result.passed


def test_flag_news_ref_url_not_in_context():
    news = (build_news_item(url="https://reuters.com/x"),)
    ctx = build_reasoning_context(news=news)
    payload = _payload(
        "Earnings beat suggests upside.",
        news_refs=("https://fabricated.example.com/article",),
    )

    result = _VALIDATOR.validate(payload, build_signal_input(), ctx)

    assert not result.passed
    assert any(
        v.type == ValidationViolationType.UNGROUNDED_NEWS_URL for v in result.violations
    )


# ---------- Rule 3: low-confidence label ----------


def test_low_confidence_signal_passes_when_text_says_tentative():
    ctx = build_reasoning_context()
    sig = build_signal_input(confidence=0.42)
    payload = _payload("Tentative setup; SMA200 at 510.0 still below price.")

    result = _VALIDATOR.validate(payload, sig, ctx)

    assert result.passed, f"expected pass, got {result.feedback}"


def test_low_confidence_signal_flagged_when_text_lacks_low_confidence_token():
    ctx = build_reasoning_context()
    sig = build_signal_input(confidence=0.42)
    payload = _payload("Strong setup with price at 603.0 well above sma_200 at 510.0.")

    result = _VALIDATOR.validate(payload, sig, ctx)

    assert not result.passed
    assert any(
        v.type == ValidationViolationType.MISSING_LOW_CONFIDENCE_LABEL
        for v in result.violations
    )


def test_normal_confidence_does_not_require_low_confidence_label():
    ctx = build_reasoning_context()
    sig = build_signal_input(confidence=0.75)
    payload = _payload("Solid trend: price 603.0 above sma_200 (510.0).")

    result = _VALIDATOR.validate(payload, sig, ctx)

    assert result.passed


def test_low_confidence_token_matches_case_insensitively():
    ctx = build_reasoning_context()
    sig = build_signal_input(confidence=0.30)
    payload = _payload("SPECULATIVE play: sma_200 at 510.0 still pressing down.")

    result = _VALIDATOR.validate(payload, sig, ctx)

    # No MISSING_LOW_CONFIDENCE_LABEL violation (case-insensitive match worked).
    assert not any(
        v.type == ValidationViolationType.MISSING_LOW_CONFIDENCE_LABEL
        for v in result.violations
    )


# ---------- Rule 4: forbidden absolute words ----------


def test_flag_definitely():
    ctx = build_reasoning_context()
    payload = _payload("Price 603.0 definitely above sma_200 (510.0). Up trend.")

    result = _VALIDATOR.validate(payload, build_signal_input(), ctx)

    assert not result.passed
    assert any(
        v.type == ValidationViolationType.FORBIDDEN_ABSOLUTE_WORD
        for v in result.violations
    )


def test_flag_guaranteed_and_will_rise_in_same_text():
    ctx = build_reasoning_context()
    payload = _payload(
        "Price 603.0 above sma_200 (510.0) — guaranteed it will rise further."
    )

    result = _VALIDATOR.validate(payload, build_signal_input(), ctx)

    forbidden = [
        v for v in result.violations if v.type == ValidationViolationType.FORBIDDEN_ABSOLUTE_WORD
    ]
    assert len(forbidden) == 2


def test_absolute_word_detection_is_case_insensitive():
    ctx = build_reasoning_context()
    payload = _payload("Price 603.0 above sma_200 (510.0) — DEFINITELY a buy.")

    result = _VALIDATOR.validate(payload, build_signal_input(), ctx)

    assert any(
        v.type == ValidationViolationType.FORBIDDEN_ABSOLUTE_WORD
        for v in result.violations
    )


def test_words_containing_certain_as_substring_do_not_false_positive():
    # 'uncertainty' contains 'certain' but the regex uses word boundaries.
    ctx = build_reasoning_context()
    sig = build_signal_input(confidence=0.40)  # low-conf so label rule wants tentative
    payload = _payload(
        "Tentative read: market uncertainty around 603.0 keeps sma_200 (510.0) in focus."
    )

    result = _VALIDATOR.validate(payload, sig, ctx)

    assert not any(
        v.type == ValidationViolationType.FORBIDDEN_ABSOLUTE_WORD
        for v in result.violations
    )


# ---------- Combinations + feedback shape ----------


def test_multiple_violations_all_surface_in_feedback():
    ctx = build_reasoning_context()
    sig = build_signal_input(confidence=0.30)
    payload = _payload(
        "Price 900.0 will definitely rise to 1000.0.",
        news_refs=("https://nope.example.com/fake",),
    )

    result = _VALIDATOR.validate(payload, sig, ctx)

    assert not result.passed
    types = {v.type for v in result.violations}
    assert ValidationViolationType.UNGROUNDED_NUMBER in types
    assert ValidationViolationType.FORBIDDEN_ABSOLUTE_WORD in types
    assert ValidationViolationType.MISSING_LOW_CONFIDENCE_LABEL in types
    assert ValidationViolationType.UNGROUNDED_NEWS_URL in types
    # Feedback string lists each violation on its own line for the LLM retry.
    assert result.feedback.count("\n") >= 3
    assert "ungrounded_number" in result.feedback
    assert "missing_low_confidence_label" in result.feedback
    assert "forbidden_absolute_word" in result.feedback
    assert "ungrounded_news_url" in result.feedback


def test_pass_result_has_empty_feedback():
    ctx = build_reasoning_context()
    payload = _payload("Price 603.0 above sma_200 (510.0). Constructive trend.")

    result = _VALIDATOR.validate(payload, build_signal_input(), ctx)

    assert result.passed
    assert result.feedback == ""


# ---------- Derived-price grounding (target_price / stop_loss) ----------


def test_target_price_counts_as_grounded_value():
    ctx = build_reasoning_context()
    sig = build_signal_input(target_price=627.12)
    payload = _payload("Target sits near 627.12 above sma_200 (510.0).")

    result = _VALIDATOR.validate(payload, sig, ctx)

    assert result.passed, f"expected pass, got {result.feedback}"


def test_stop_loss_counts_as_grounded_value():
    ctx = build_reasoning_context()
    sig = build_signal_input(stop_loss=590.94)
    payload = _payload("Stop placed at 590.94 below recent support of 580.0.")

    result = _VALIDATOR.validate(payload, sig, ctx)

    assert result.passed, f"expected pass, got {result.feedback}"


def test_target_price_within_one_percent_but_outside_tight_band_is_rejected():
    """Backend-derived prices must round-trip near-exactly; the 1% indicator
    band does NOT apply to target_price / stop_loss / expected_move_pct.
    """
    ctx = build_reasoning_context()
    sig = build_signal_input(target_price=627.12)
    # 630.45 is +0.53% off target_price — outside the 0.05% derived-price band.
    # Picked to also sit >1% away from every <price_facts> indicator
    # (closest is high_52w=638 at -1.18%) so this is a pure
    # derived-price tolerance test.
    payload = _payload("Target around 630.45.")

    result = _VALIDATOR.validate(payload, sig, ctx)

    assert not result.passed
    assert any(
        v.type == ValidationViolationType.UNGROUNDED_NUMBER for v in result.violations
    )


def test_ungrounded_price_emits_structured_log(caplog):
    import logging

    ctx = build_reasoning_context()
    sig = build_signal_input(entry_price=130.05, target_price=135.252, stop_loss=127.45)
    payload = _payload("Price 13.35 looks off versus entry 130.05.")

    with caplog.at_level(logging.WARNING, logger="ai_engine.core.domain.reasoning_validation"):
        result = _VALIDATOR.validate(payload, sig, ctx)

    assert not result.passed
    assert any(
        v.type == ValidationViolationType.UNGROUNDED_NUMBER for v in result.violations
    )
    matched = [r for r in caplog.records if "reasoning_validator.ungrounded_price" in r.getMessage()]
    assert matched, "expected ungrounded_price log line not emitted"
    msg = matched[0].getMessage()
    assert "mentioned=13.35" in msg
