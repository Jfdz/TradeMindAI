import math
from dataclasses import dataclass

import torch

from ai_engine.core.domain.feature_engineering import compute_features
from ai_engine.core.domain.normalizer import MinMaxNormalizer
from ai_engine.core.models.cnn import StockCNN
from ai_engine.core.use_cases.model_registry import ModelRegistry

_DIRECTION_LABELS = {0: "DOWN", 1: "NEUTRAL", 2: "UP"}
_WINDOW = 60
_K = 1.0           # tunable scale factor: magnitude = sign × K × atr_pct × confidence
_MAX_MOVE_PCT = 15.0
_MIN_MOVE_PCT = 0.10  # floor so low-vol BUY/SELL isn't effectively zero
_FALLBACK_CHANGE_MAP = {0: -1.5, 1: 0.0, 2: 1.5}  # used when ATR unavailable


@dataclass
class PredictionResult:
    ticker: str
    direction: str
    confidence: float
    predicted_change_pct: float
    raw_logits: list[float]


class PredictionService:
    """Runs inference for one or many tickers using the active model.

    The service is intentionally stateless with respect to the model — it
    delegates version management to ModelRegistry and re-loads the active
    model on first call or after an explicit reload().
    """

    def __init__(self, registry: ModelRegistry, device: str = "cpu"):
        self._registry = registry
        self._device = torch.device(device)
        self._model: StockCNN | None = None
        self._normalizer = MinMaxNormalizer()

    # ── public API ────────────────────────────────────────────────────────────

    def predict_one(self, ticker: str, ohlcv_df) -> PredictionResult:
        """Run inference for a single ticker given its OHLCV DataFrame."""
        model = self._get_model()
        sequence, atr_pct = self._preprocess(ohlcv_df)
        return self._infer(ticker, sequence, model, atr_pct)

    def predict_batch(self, requests: list[tuple[str, object]]) -> list[PredictionResult]:
        """Run inference for multiple (ticker, ohlcv_df) pairs in one forward pass."""
        model = self._get_model()
        results = []
        tensors = []
        tickers = []
        atr_pcts = []

        for ticker, df in requests:
            seq, atr_pct = self._preprocess(df)
            tensors.append(seq)
            tickers.append(ticker)
            atr_pcts.append(atr_pct)

        batch = torch.cat(tensors, dim=0).to(self._device)
        model.eval()
        with torch.no_grad():
            logits = model(batch)

        for i, ticker in enumerate(tickers):
            results.append(self._logits_to_result(ticker, logits[i], atr_pcts[i]))

        return results

    def reload(self) -> None:
        """Force reload of the active model from the registry."""
        self._model = None
        self._get_model()

    # ── internal ─────────────────────────────────────────────────────────────

    def _get_model(self) -> StockCNN:
        if self._model is None:
            m = StockCNN()
            self._model = self._registry.load_active(m).to(self._device)
            self._model.eval()
        return self._model

    def _preprocess(self, ohlcv_df) -> tuple[torch.Tensor, float | None]:
        features = compute_features(ohlcv_df)
        if len(features) < _WINDOW:
            raise ValueError(f"Need at least {_WINDOW} rows after feature computation.")
        atr_pct: float | None = None
        try:
            atr_val = float(features["atr"].iloc[-1])
            close_val = float(features["close"].iloc[-1])
            if close_val > 0 and math.isfinite(atr_val) and math.isfinite(close_val):
                candidate = atr_val / close_val * 100
                if math.isfinite(candidate) and candidate > 0:
                    atr_pct = candidate
        except Exception:
            atr_pct = None
        x = features.to_numpy()
        x_scaled = self._normalizer.fit_transform(x)
        # Take the most recent window
        seq = x_scaled[-_WINDOW:].T  # shape: (n_features, window)
        tensor = torch.tensor(seq, dtype=torch.float32).unsqueeze(0)  # (1, n_features, window)
        return tensor, atr_pct

    def _infer(
        self, ticker: str, sequence: torch.Tensor, model: StockCNN, atr_pct: float | None = None
    ) -> PredictionResult:
        model.eval()
        with torch.no_grad():
            logits = model(sequence.to(self._device))[0]
        return self._logits_to_result(ticker, logits, atr_pct)

    def _logits_to_result(
        self, ticker: str, logits: torch.Tensor, atr_pct: float | None = None
    ) -> PredictionResult:
        probs = torch.softmax(logits, dim=0)
        class_idx = int(probs.argmax().item())
        confidence = round(float(probs[class_idx].item()), 4)
        sign = {0: -1.0, 1: 0.0, 2: 1.0}[class_idx]
        if sign == 0.0:
            magnitude = 0.0
        elif atr_pct is None:
            # Fallback when ATR is unavailable (seed model, missing market data)
            magnitude = _FALLBACK_CHANGE_MAP[class_idx]
        else:
            raw = sign * _K * atr_pct * confidence
            # Enforce non-zero floor so a low-vol directional signal is still actionable
            raw = max(raw, _MIN_MOVE_PCT) if raw > 0 else min(raw, -_MIN_MOVE_PCT)
            magnitude = max(-_MAX_MOVE_PCT, min(_MAX_MOVE_PCT, raw))
        return PredictionResult(
            ticker=ticker,
            direction=_DIRECTION_LABELS[class_idx],
            confidence=confidence,
            predicted_change_pct=round(magnitude, 4),
            raw_logits=[round(float(v), 4) for v in logits.tolist()],
        )
