import numpy as np


class MinMaxNormalizer:
    """Per-feature MinMax scaler that fits on training data only."""

    def __init__(self):
        self._min: np.ndarray | None = None
        self._max: np.ndarray | None = None

    def fit(self, X: np.ndarray) -> "MinMaxNormalizer":
        """Compute min/max from X with shape (n_samples, n_features)."""
        self._min = X.min(axis=0)
        self._max = X.max(axis=0)
        return self

    def transform(self, X: np.ndarray) -> np.ndarray:
        """Scale X to [0, 1]. Features with zero range are left as 0."""
        self._check_fitted()
        rng = self._max - self._min
        rng = np.where(rng == 0, 1, rng)
        return (X - self._min) / rng

    def fit_transform(self, X: np.ndarray) -> np.ndarray:
        return self.fit(X).transform(X)

    def inverse_transform(self, X: np.ndarray) -> np.ndarray:
        self._check_fitted()
        rng = self._max - self._min
        rng = np.where(rng == 0, 1, rng)
        return X * rng + self._min

    def _check_fitted(self) -> None:
        if self._min is None:
            raise RuntimeError("Normalizer has not been fitted yet.")
