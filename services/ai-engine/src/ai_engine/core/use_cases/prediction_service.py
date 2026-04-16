from dataclasses import dataclass

import numpy as np
import torch

from ai_engine.core.domain.feature_engineering import compute_features
from ai_engine.core.domain.normalizer import MinMaxNormalizer
from ai_engine.core.domain.sequence_builder import build_sequences
from ai_engine.core.models.cnn import StockCNN
from ai_engine.core.use_cases.model_registry import ModelRegistry

_DIRECTION_LABELS = {0: "DOWN", 1: "NEUTRAL", 2: "UP"}
_WINDOW = 60


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
        sequence = self._preprocess(ohlcv_df)
        return self._infer(ticker, sequence, model)

    def predict_batch(self, requests: list[tuple[str, object]]) -> list[PredictionResult]:
        """Run inference for multiple (ticker, ohlcv_df) pairs in one forward pass."""
        model = self._get_model()
        results = []
        tensors = []
        tickers = []

        for ticker, df in requests:
            seq = self._preprocess(df)
            tensors.append(seq)
            tickers.append(ticker)

        batch = torch.cat(tensors, dim=0).to(self._device)
        model.eval()
        with torch.no_grad():
            logits = model(batch)

        for i, ticker in enumerate(tickers):
            results.append(self._logits_to_result(ticker, logits[i]))

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

    def _preprocess(self, ohlcv_df) -> torch.Tensor:
        features = compute_features(ohlcv_df)
        if len(features) < _WINDOW:
            raise ValueError(f"Need at least {_WINDOW} rows after feature computation.")
        X = features.to_numpy()
        X_scaled = self._normalizer.fit_transform(X)
        # Take the most recent window
        seq = X_scaled[-_WINDOW:].T  # shape: (n_features, window)
        return torch.tensor(seq, dtype=torch.float32).unsqueeze(0)  # (1, n_features, window)

    def _infer(self, ticker: str, sequence: torch.Tensor, model: StockCNN) -> PredictionResult:
        model.eval()
        with torch.no_grad():
            logits = model(sequence.to(self._device))[0]
        return self._logits_to_result(ticker, logits)

    def _logits_to_result(self, ticker: str, logits: torch.Tensor) -> PredictionResult:
        probs = torch.softmax(logits, dim=0)
        class_idx = int(probs.argmax().item())
        confidence = round(float(probs[class_idx].item()), 4)
        # Approximate predicted change: UP→+1.5%, NEUTRAL→0%, DOWN→-1.5%
        change_map = {0: -1.5, 1: 0.0, 2: 1.5}
        return PredictionResult(
            ticker=ticker,
            direction=_DIRECTION_LABELS[class_idx],
            confidence=confidence,
            predicted_change_pct=change_map[class_idx],
            raw_logits=[round(float(v), 4) for v in logits.tolist()],
        )
