import numpy as np
import pandas as pd

UP = 2
NEUTRAL = 1
DOWN = 0

_UP_THRESHOLD = 0.01
_DOWN_THRESHOLD = -0.01


def generate_labels(close: pd.Series, horizon: int = 5) -> np.ndarray:
    """Generate 3-class direction labels without look-ahead bias.

    For each day t the label is based on close[t+horizon] vs close[t]:
      - UP      (2): change > +1 %
      - NEUTRAL (1): -1 % <= change <= +1 %
      - DOWN    (0): change < -1 %

    The last *horizon* rows have no future data and are dropped, so the
    returned array has length len(close) - horizon.

    Args:
        close:   Series of closing prices aligned with the feature matrix.
        horizon: Forward-looking window in days (default 5).

    Returns:
        Integer ndarray of shape (len(close) - horizon,).
    """
    close_arr = close.to_numpy(dtype=float)
    future = close_arr[horizon:]
    current = close_arr[: len(close_arr) - horizon]
    pct_change = (future - current) / current

    labels = np.full(len(pct_change), NEUTRAL, dtype=np.int64)
    labels[pct_change > _UP_THRESHOLD] = UP
    labels[pct_change < _DOWN_THRESHOLD] = DOWN
    return labels
