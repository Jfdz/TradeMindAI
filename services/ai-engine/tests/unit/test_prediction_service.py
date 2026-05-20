"""Unit tests for PredictionService._logits_to_result ATR-based magnitude logic."""

import math

import pytest
import torch

from ai_engine.core.use_cases.prediction_service import (
    PredictionService,
    _FALLBACK_CHANGE_MAP,
    _MAX_MOVE_PCT,
    _MIN_MOVE_PCT,
)


def _make_logits(class_idx: int, *, gap: float = 5.0) -> torch.Tensor:
    """Return a logits tensor that argmax to class_idx with high confidence."""
    logits = torch.full((3,), -gap)
    logits[class_idx] = gap
    return logits


# --------------------------------------------------------------------------- #
# Helpers to reach _logits_to_result without a real model or registry
# --------------------------------------------------------------------------- #

class _FakeRegistry:
    def load_active(self, model):
        return model


def _service() -> PredictionService:
    from ai_engine.core.models.cnn import StockCNN
    svc = PredictionService.__new__(PredictionService)
    svc._registry = _FakeRegistry()
    svc._device = torch.device("cpu")
    svc._model = None
    from ai_engine.core.domain.normalizer import MinMaxNormalizer
    svc._normalizer = MinMaxNormalizer()
    return svc


# ─── direction × sign ────────────────────────────────────────────────────────

def test_neutral_class_always_returns_zero():
    svc = _service()
    result = svc._logits_to_result("AAPL", _make_logits(1), atr_pct=2.0)
    assert result.predicted_change_pct == 0.0
    assert result.direction == "NEUTRAL"


def test_up_class_returns_positive_with_atr():
    svc = _service()
    result = svc._logits_to_result("AAPL", _make_logits(2), atr_pct=2.0)
    assert result.predicted_change_pct > 0
    assert result.direction == "UP"


def test_down_class_returns_negative_with_atr():
    svc = _service()
    result = svc._logits_to_result("AAPL", _make_logits(0), atr_pct=2.0)
    assert result.predicted_change_pct < 0
    assert result.direction == "DOWN"


# ─── magnitude varies with atr_pct ───────────────────────────────────────────

def test_higher_atr_yields_larger_magnitude():
    svc = _service()
    logits = _make_logits(2)
    low = svc._logits_to_result("X", logits, atr_pct=0.5).predicted_change_pct
    high = svc._logits_to_result("X", logits, atr_pct=3.0).predicted_change_pct
    assert high > low


def test_different_tickers_with_different_atr_produce_different_pct():
    svc = _service()
    logits = _make_logits(2)
    a = svc._logits_to_result("AAPL", logits, atr_pct=1.0).predicted_change_pct
    b = svc._logits_to_result("TSLA", logits, atr_pct=4.0).predicted_change_pct
    assert a != b


# ─── clamp ───────────────────────────────────────────────────────────────────

def test_magnitude_clamped_at_max_move_pct_for_up():
    svc = _service()
    result = svc._logits_to_result("AAPL", _make_logits(2), atr_pct=500.0)
    assert result.predicted_change_pct == pytest.approx(_MAX_MOVE_PCT, abs=1e-4)


def test_magnitude_clamped_at_max_move_pct_for_down():
    svc = _service()
    result = svc._logits_to_result("AAPL", _make_logits(0), atr_pct=500.0)
    assert result.predicted_change_pct == pytest.approx(-_MAX_MOVE_PCT, abs=1e-4)


# ─── floor ───────────────────────────────────────────────────────────────────

def test_up_class_with_tiny_atr_still_meets_floor():
    svc = _service()
    result = svc._logits_to_result("AAPL", _make_logits(2), atr_pct=1e-6)
    assert result.predicted_change_pct >= _MIN_MOVE_PCT


def test_down_class_with_tiny_atr_still_meets_negative_floor():
    svc = _service()
    result = svc._logits_to_result("AAPL", _make_logits(0), atr_pct=1e-6)
    assert result.predicted_change_pct <= -_MIN_MOVE_PCT


# ─── fallback when atr_pct missing ───────────────────────────────────────────

def test_fallback_when_atr_pct_is_none_up():
    svc = _service()
    result = svc._logits_to_result("AAPL", _make_logits(2), atr_pct=None)
    assert result.predicted_change_pct == pytest.approx(_FALLBACK_CHANGE_MAP[2])


def test_fallback_when_atr_pct_is_none_down():
    svc = _service()
    result = svc._logits_to_result("AAPL", _make_logits(0), atr_pct=None)
    assert result.predicted_change_pct == pytest.approx(_FALLBACK_CHANGE_MAP[0])


def test_fallback_neutral_is_zero_regardless_of_atr():
    svc = _service()
    assert svc._logits_to_result("AAPL", _make_logits(1), atr_pct=None).predicted_change_pct == 0.0


# ─── rounding ────────────────────────────────────────────────────────────────

def test_predicted_change_pct_is_rounded_to_4_decimals():
    svc = _service()
    result = svc._logits_to_result("AAPL", _make_logits(2), atr_pct=1.23456789)
    str_val = str(abs(result.predicted_change_pct))
    _, _, decimals = str_val.partition(".")
    assert len(decimals) <= 4


# ─── raw_logits and direction unchanged ──────────────────────────────────────

def test_raw_logits_preserved():
    svc = _service()
    logits = _make_logits(2)
    result = svc._logits_to_result("AAPL", logits, atr_pct=2.0)
    assert len(result.raw_logits) == 3


def test_ticker_preserved():
    svc = _service()
    result = svc._logits_to_result("MSFT", _make_logits(2), atr_pct=2.0)
    assert result.ticker == "MSFT"
