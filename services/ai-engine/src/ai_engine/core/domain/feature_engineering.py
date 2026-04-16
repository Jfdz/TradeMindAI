import numpy as np
import pandas as pd

FEATURE_COLUMNS = [
    "open", "high", "low", "close", "volume",
    "rsi", "macd", "macd_signal", "macd_hist",
    "sma_20", "sma_50", "ema_12", "ema_26",
    "bb_upper", "bb_lower", "atr", "obv",
]


def compute_features(df: pd.DataFrame) -> pd.DataFrame:
    """Compute all 17 features from a raw OHLCV DataFrame.

    Args:
        df: DataFrame with columns [open, high, low, close, volume] (lowercase).

    Returns:
        DataFrame with exactly the 17 columns in FEATURE_COLUMNS.
        Rows with NaN (warm-up period for SMA_50) are dropped.
    """
    out = pd.DataFrame(index=df.index)

    out["open"] = df["open"]
    out["high"] = df["high"]
    out["low"] = df["low"]
    out["close"] = df["close"]
    out["volume"] = df["volume"]

    out["rsi"] = _rsi(df["close"], period=14)

    macd, signal, hist = _macd(df["close"])
    out["macd"] = macd
    out["macd_signal"] = signal
    out["macd_hist"] = hist

    out["sma_20"] = df["close"].rolling(20).mean()
    out["sma_50"] = df["close"].rolling(50).mean()
    out["ema_12"] = df["close"].ewm(span=12, adjust=False).mean()
    out["ema_26"] = df["close"].ewm(span=26, adjust=False).mean()

    bb_upper, bb_lower = _bollinger_bands(df["close"], period=20)
    out["bb_upper"] = bb_upper
    out["bb_lower"] = bb_lower

    out["atr"] = _atr(df["high"], df["low"], df["close"], period=14)
    out["obv"] = _obv(df["close"], df["volume"])

    return out[FEATURE_COLUMNS].dropna()


# ── indicator helpers ─────────────────────────────────────────────────────────

def _rsi(close: pd.Series, period: int = 14) -> pd.Series:
    delta = close.diff()
    gain = delta.clip(lower=0).rolling(period).mean()
    loss = (-delta.clip(upper=0)).rolling(period).mean()
    rs = gain / loss.replace(0, np.nan)
    return 100 - (100 / (1 + rs))


def _macd(close: pd.Series) -> tuple[pd.Series, pd.Series, pd.Series]:
    ema12 = close.ewm(span=12, adjust=False).mean()
    ema26 = close.ewm(span=26, adjust=False).mean()
    macd = ema12 - ema26
    signal = macd.ewm(span=9, adjust=False).mean()
    return macd, signal, macd - signal


def _bollinger_bands(close: pd.Series, period: int = 20) -> tuple[pd.Series, pd.Series]:
    sma = close.rolling(period).mean()
    std = close.rolling(period).std()
    return sma + 2 * std, sma - 2 * std


def _atr(high: pd.Series, low: pd.Series, close: pd.Series, period: int = 14) -> pd.Series:
    prev_close = close.shift(1)
    tr = pd.concat([
        high - low,
        (high - prev_close).abs(),
        (low - prev_close).abs(),
    ], axis=1).max(axis=1)
    return tr.rolling(period).mean()


def _obv(close: pd.Series, volume: pd.Series) -> pd.Series:
    direction = np.sign(close.diff()).fillna(0)
    return (direction * volume).cumsum()
