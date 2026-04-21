import numpy as np


class MinMaxNormalizer:
    """Per-feature MinMax scaler that fits on training data only."""

    def __init__(self):
        self._min: np.ndarray | None = None
        self._max: np.ndarray | None = None

    def fit(self, x: np.ndarray) -> "MinMaxNormalizer":
        """Compute min/max from x with shape (n_samples, n_features)."""
        self._min = x.min(axis=0)
        self._max = x.max(axis=0)
        return self

    def transform(self, x: np.ndarray) -> np.ndarray:
        """Scale x to [0, 1]. Features with zero range are left as 0."""
        self._check_fitted()
        rng = self._max - self._min
        rng = np.where(rng == 0, 1, rng)
        return (x - self._min) / rng

    def fit_transform(self, x: np.ndarray) -> np.ndarray:
        return self.fit(x).transform(x)

    def inverse_transform(self, x: np.ndarray) -> np.ndarray:
        self._check_fitted()
        rng = self._max - self._min
        rng = np.where(rng == 0, 1, rng)
        return x * rng + self._min

    def _check_fitted(self) -> None:
        if self._min is None:
            raise RuntimeError("Normalizer has not been fitted yet.")
