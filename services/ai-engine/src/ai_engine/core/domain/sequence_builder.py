import numpy as np


def build_sequences(features: np.ndarray, window: int = 60) -> np.ndarray:
    """Build sliding-window sequences for CNN input.

    Args:
        features: Array of shape (N_days, n_features).
        window:   Number of time steps per sequence (default 60).

    Returns:
        Array of shape (N_samples, n_features, window) where
        N_samples = N_days - window + 1.
    """
    n_days, n_features = features.shape
    if n_days < window:
        raise ValueError(f"Need at least {window} days, got {n_days}.")

    n_samples = n_days - window + 1
    sequences = np.empty((n_samples, n_features, window), dtype=features.dtype)
    for i in range(n_samples):
        sequences[i] = features[i : i + window].T
    return sequences
