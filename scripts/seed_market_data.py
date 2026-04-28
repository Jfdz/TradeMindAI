#!/usr/bin/env python3
"""
Seed script: fetches real market data from Yahoo Finance and populates TradeMindAI DB.

Usage (from Ubuntu server):
  # Start port-forward in another terminal first:
  #   kubectl port-forward svc/postgres --address 0.0.0.0 5433:5432 -n trading-saas
  pip3 install yfinance psycopg2-binary pandas numpy
  python3 seed_market_data.py
"""

import json
import sys

try:
    import psycopg2
    import yfinance as yf
    import pandas as pd
    import numpy as np
except ImportError as e:
    print(f"Missing dependency: {e}")
    print("Run: pip3 install yfinance psycopg2-binary pandas numpy")
    sys.exit(1)

DB_CONFIG = {
    "host": "localhost",
    "port": 5433,
    "database": "trading_saas",
    "user": "tradinguser",
    "password": "",
}

SYMBOLS = [
    ("AAPL",  "Apple Inc.",                  "NASDAQ", "Technology"),
    ("MSFT",  "Microsoft Corporation",       "NASDAQ", "Technology"),
    ("GOOGL", "Alphabet Inc.",               "NASDAQ", "Technology"),
    ("AMZN",  "Amazon.com Inc.",             "NASDAQ", "Consumer Cyclical"),
    ("NVDA",  "NVIDIA Corporation",          "NASDAQ", "Technology"),
    ("TSLA",  "Tesla Inc.",                  "NASDAQ", "Consumer Cyclical"),
    ("META",  "Meta Platforms Inc.",         "NASDAQ", "Technology"),
    ("JPM",   "JPMorgan Chase & Co.",        "NYSE",   "Financial Services"),
    ("V",     "Visa Inc.",                   "NYSE",   "Financial Services"),
    ("NFLX",  "Netflix Inc.",               "NASDAQ", "Communication Services"),
    ("AMD",   "Advanced Micro Devices",      "NASDAQ", "Technology"),
    ("INTC",  "Intel Corporation",           "NASDAQ", "Technology"),
    ("BA",    "Boeing Company",              "NYSE",   "Industrials"),
    ("DIS",   "Walt Disney Company",         "NYSE",   "Communication Services"),
    ("WMT",   "Walmart Inc.",               "NYSE",   "Consumer Defensive"),
    ("PYPL",  "PayPal Holdings Inc.",        "NASDAQ", "Financial Services"),
    ("COIN",  "Coinbase Global Inc.",        "NASDAQ", "Financial Services"),
    ("UBER",  "Uber Technologies Inc.",      "NYSE",   "Technology"),
    ("SPOT",  "Spotify Technology",          "NYSE",   "Communication Services"),
    ("PLTR",  "Palantir Technologies Inc.",  "NYSE",   "Technology"),
]


def calculate_rsi(close, period=14):
    delta = close.diff()
    gain = delta.where(delta > 0, 0.0)
    loss = -delta.where(delta < 0, 0.0)
    avg_gain = gain.ewm(com=period - 1, min_periods=period).mean()
    avg_loss = loss.ewm(com=period - 1, min_periods=period).mean()
    rs = avg_gain / avg_loss
    return 100 - (100 / (1 + rs))


def calculate_indicators(df):
    """Returns list of (date, indicator_type, value, metadata_dict) tuples."""
    rows = []
    close = df["Close"].squeeze()

    # SMA 20 / 50 / 200
    for period in [20, 50, 200]:
        sma = close.rolling(window=period).mean()
        for dt, val in sma.dropna().items():
            rows.append((dt.date(), f"SMA_{period}", float(val), {}))

    # EMA 12 / 26
    for period in [12, 26]:
        ema = close.ewm(span=period, adjust=False).mean()
        for dt, val in ema.dropna().items():
            rows.append((dt.date(), f"EMA_{period}", float(val), {}))

    # RSI 14
    rsi = calculate_rsi(close, 14)
    for dt, val in rsi.dropna().items():
        rows.append((dt.date(), "RSI_14", float(val), {}))

    # MACD (12, 26, 9)
    ema12 = close.ewm(span=12, adjust=False).mean()
    ema26 = close.ewm(span=26, adjust=False).mean()
    macd   = ema12 - ema26
    signal = macd.ewm(span=9, adjust=False).mean()
    hist   = macd - signal
    for dt, val in macd.dropna().items():
        rows.append((dt.date(), "MACD", float(val), {
            "signal":    float(signal[dt]),
            "histogram": float(hist[dt]),
        }))

    # Bollinger Bands (20, 2)
    sma20 = close.rolling(window=20).mean()
    std20 = close.rolling(window=20).std()
    upper = sma20 + std20 * 2
    lower = sma20 - std20 * 2
    for dt, val in sma20.dropna().items():
        rows.append((dt.date(), "BOLLINGER_UPPER", float(upper[dt]), {
            "middle": float(val),
            "lower":  float(lower[dt]),
        }))

    return rows


def seed_symbols(cur):
    print(f"\n[1/3] Inserting {len(SYMBOLS)} symbols...")
    for ticker, name, exchange, sector in SYMBOLS:
        cur.execute("""
            INSERT INTO market_data.symbols (ticker, name, exchange, sector, active)
            VALUES (%s, %s, %s, %s, TRUE)
            ON CONFLICT (ticker) DO UPDATE
                SET name = EXCLUDED.name, updated_at = NOW()
        """, (ticker, name, exchange, sector))
    print(f"      Done.")


def seed_prices_and_indicators(cur, ticker):
    df = yf.download(ticker, period="2y", interval="1d", progress=False, auto_adjust=False)
    if df.empty:
        print(f"      No data returned, skipping.")
        return

    # Flatten MultiIndex columns (yfinance >= 0.2.x)
    if isinstance(df.columns, pd.MultiIndex):
        df.columns = df.columns.get_level_values(0)

    adj_col = "Adj Close" if "Adj Close" in df.columns else "Close"

    price_count = 0
    for dt, row in df.iterrows():
        try:
            cur.execute("""
                INSERT INTO market_data.stock_prices
                    (symbol_ticker, trade_date, time_frame,
                     open, high, low, close, adjusted_close, volume)
                VALUES (%s, %s, 'DAILY', %s, %s, %s, %s, %s, %s)
                ON CONFLICT (symbol_ticker, trade_date, time_frame) DO NOTHING
            """, (
                ticker,
                dt.date(),
                float(row["Open"]),
                float(row["High"]),
                float(row["Low"]),
                float(row["Close"]),
                float(row[adj_col]),
                int(row["Volume"]),
            ))
            price_count += 1
        except Exception as e:
            print(f"      Price row error ({dt.date()}): {e}")

    indicators = calculate_indicators(df)
    ind_count = 0
    for ind_date, ind_type, value, metadata in indicators:
        try:
            cur.execute("""
                INSERT INTO market_data.technical_indicators
                    (symbol_ticker, indicator_date, indicator_type, value, metadata)
                VALUES (%s, %s, %s, %s, %s)
                ON CONFLICT (symbol_ticker, indicator_date, indicator_type) DO NOTHING
            """, (ticker, ind_date, ind_type, value, json.dumps(metadata)))
            ind_count += 1
        except Exception as e:
            print(f"      Indicator row error ({ind_date}, {ind_type}): {e}")

    print(f"      {price_count} prices  |  {ind_count} indicators")


def seed_trading_core(cur):
    print("\n[3/3] Seeding trading_core sample data...")

    # Demo user
    cur.execute("""
        INSERT INTO trading_core.users
            (email, password_hash, first_name, last_name, active, timezone)
        VALUES
            ('demo@trademind.ai',
             '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewFVsn2/K0fLZ/NO',
             'Demo', 'User', TRUE, 'America/New_York')
        ON CONFLICT (email) DO NOTHING
    """)

    cur.execute("SELECT id FROM trading_core.users WHERE email = 'demo@trademind.ai'")
    row = cur.fetchone()
    if not row:
        print("      Could not retrieve demo user id.")
        return
    user_id = row[0]

    # PREMIUM subscription
    cur.execute("""
        INSERT INTO trading_core.subscriptions (user_id, plan, expires_at)
        VALUES (%s, 'PREMIUM', NULL)
        ON CONFLICT DO NOTHING
    """, (user_id,))

    # Default strategy
    cur.execute("""
        INSERT INTO trading_core.strategies
            (id, user_id, name, description, active,
             stop_loss_pct, take_profit_pct, max_position_pct,
             created_at, updated_at)
        VALUES
            (gen_random_uuid(), %s,
             'Momentum Growth', 'AI-driven momentum strategy using CNN signals',
             TRUE, 5.00, 15.00, 10.00, NOW(), NOW())
        ON CONFLICT DO NOTHING
    """, (user_id,))

    # Portfolio
    cur.execute("""
        INSERT INTO trading_core.portfolios
            (id, user_id, initial_capital, created_at, updated_at)
        VALUES (gen_random_uuid(), %s, 100000.00, NOW(), NOW())
        ON CONFLICT (user_id) DO NOTHING
    """, (user_id,))

    cur.execute("SELECT id FROM trading_core.portfolios WHERE user_id = %s", (user_id,))
    port_row = cur.fetchone()
    if port_row:
        portfolio_id = port_row[0]
        sample_positions = [
            ("AAPL",  10,  175.50),
            ("NVDA",   5, 620.00),
            ("MSFT",   8, 380.25),
            ("TSLA",   3, 245.00),
        ]
        for sym, qty, price in sample_positions:
            cur.execute("""
                INSERT INTO trading_core.positions
                    (id, portfolio_id, symbol_ticker, quantity, entry_price,
                     status, opened_at)
                VALUES (gen_random_uuid(), %s, %s, %s, %s, 'OPEN', NOW())
                ON CONFLICT DO NOTHING
            """, (portfolio_id, sym, qty, price))

    # Notification preferences
    cur.execute("""
        INSERT INTO trading_core.user_notification_preferences
            (user_id, signal_digest, live_alerts, risk_warnings,
             strategy_changes, weekly_recap)
        VALUES (%s, TRUE, TRUE, TRUE, FALSE, TRUE)
        ON CONFLICT (user_id) DO NOTHING
    """, (user_id,))

    print("      Demo user, subscription, strategy, portfolio and positions inserted.")


def main():
    print("Connecting to PostgreSQL via port-forward localhost:5433 ...")
    try:
        conn = psycopg2.connect(**DB_CONFIG)
    except Exception as e:
        print(f"Connection failed: {e}")
        print("Make sure port-forward is running:")
        print("  kubectl port-forward svc/postgres --address 0.0.0.0 5433:5432 -n trading-saas")
        sys.exit(1)

    conn.autocommit = False
    cur = conn.cursor()

    try:
        seed_symbols(cur)
        conn.commit()

        print(f"\n[2/3] Fetching 2 years of daily OHLCV + indicators for {len(SYMBOLS)} symbols...")
        for i, (ticker, name, *_) in enumerate(SYMBOLS, 1):
            print(f"  [{i:02d}/{len(SYMBOLS)}] {ticker:5s}  {name}")
            seed_prices_and_indicators(cur, ticker)
            conn.commit()

        seed_trading_core(cur)
        conn.commit()

    except Exception as e:
        conn.rollback()
        print(f"\nFatal error: {e}")
        raise
    finally:
        cur.close()
        conn.close()

    print("\nAll done. Database seeded with real market data.")


if __name__ == "__main__":
    main()
