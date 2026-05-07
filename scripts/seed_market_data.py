#!/usr/bin/env python3
"""
Seed script: fetches real market data from Yahoo Finance and populates TradeMindAI DB.

Usage:
  # Local dev (port-forward to in-cluster postgres):
  kubectl port-forward svc/postgres --address 0.0.0.0 5433:5432 -n trading-saas
  pip3 install yfinance psycopg2-binary pandas numpy
  python3 scripts/seed_market_data.py

  # Connect via DATABASE_URL (preferred for prod / CI):
  export DATABASE_URL='postgresql://user:pass@host:5432/db'
  python3 scripts/seed_market_data.py --user-id 95ffd32c-7e64-43a5-9ae1-e0800aeabffd

  # Override symbols / window:
  python3 scripts/seed_market_data.py --symbols AAPL,MSFT --years 1

  # Bootstrap an existing user, skipping the demo user creation:
  python3 scripts/seed_market_data.py --user-id <uuid>

  # Market-data only (no trading_core writes):
  python3 scripts/seed_market_data.py --skip-user

  # Dry-run (open transaction, do everything, then ROLLBACK):
  python3 scripts/seed_market_data.py --dry-run
"""

import argparse
import json
import os
import sys

try:
    import psycopg2
    import yfinance as yf
    import pandas as pd
except ImportError as e:
    print(f"Missing dependency: {e}")
    print("Run: pip3 install yfinance psycopg2-binary pandas numpy")
    sys.exit(1)

DEFAULT_SYMBOLS = [
    ("AAPL",  "Apple Inc.",                  "NASDAQ", "Technology"),
    ("MSFT",  "Microsoft Corporation",       "NASDAQ", "Technology"),
    ("GOOGL", "Alphabet Inc.",               "NASDAQ", "Technology"),
    ("AMZN",  "Amazon.com Inc.",             "NASDAQ", "Consumer Cyclical"),
    ("NVDA",  "NVIDIA Corporation",          "NASDAQ", "Technology"),
    ("TSLA",  "Tesla Inc.",                  "NASDAQ", "Consumer Cyclical"),
    ("META",  "Meta Platforms Inc.",         "NASDAQ", "Technology"),
    ("JPM",   "JPMorgan Chase & Co.",        "NYSE",   "Financial Services"),
    ("V",     "Visa Inc.",                   "NYSE",   "Financial Services"),
    ("NFLX",  "Netflix Inc.",                "NASDAQ", "Communication Services"),
    ("AMD",   "Advanced Micro Devices",      "NASDAQ", "Technology"),
    ("INTC",  "Intel Corporation",           "NASDAQ", "Technology"),
    ("BA",    "Boeing Company",              "NYSE",   "Industrials"),
    ("DIS",   "Walt Disney Company",         "NYSE",   "Communication Services"),
    ("WMT",   "Walmart Inc.",                "NYSE",   "Consumer Defensive"),
    ("PYPL",  "PayPal Holdings Inc.",        "NASDAQ", "Financial Services"),
    ("COIN",  "Coinbase Global Inc.",        "NASDAQ", "Financial Services"),
    ("UBER",  "Uber Technologies Inc.",      "NYSE",   "Technology"),
    ("SPOT",  "Spotify Technology",          "NYSE",   "Communication Services"),
    ("PLTR",  "Palantir Technologies Inc.",  "NYSE",   "Technology"),
]

DEFAULT_METADATA_BY_TICKER = {t[0]: t for t in DEFAULT_SYMBOLS}

SAMPLE_POSITIONS = [
    ("AAPL", 10, 175.50),
    ("NVDA",  5, 620.00),
    ("MSFT",  8, 380.25),
    ("TSLA",  3, 245.00),
]


def parse_args():
    p = argparse.ArgumentParser(description="Seed market data + optional user-scoped trading_core data.")
    p.add_argument("--user-id",
                   help="Existing user UUID to attach portfolio/positions/strategy/preferences to. "
                        "When omitted, falls back to creating demo@trademind.ai.")
    p.add_argument("--symbols",
                   help="Comma-separated tickers to seed. Defaults to the built-in 20-ticker list.")
    p.add_argument("--years", type=int, default=2,
                   help="Historical window in years (default: 2).")
    p.add_argument("--skip-user", action="store_true",
                   help="Skip all trading_core writes; only seed market_data.* tables.")
    p.add_argument("--dry-run", action="store_true",
                   help="Run all inserts inside a transaction, then ROLLBACK. Nothing persisted.")
    return p.parse_args()


def db_connect():
    """Connect via DATABASE_URL if set, else fall back to discrete env vars / localhost defaults."""
    url = os.environ.get("DATABASE_URL")
    if url:
        return psycopg2.connect(url)
    return psycopg2.connect(
        host=os.environ.get("DB_HOST", "localhost"),
        port=int(os.environ.get("DB_PORT", "5432")),
        dbname=os.environ.get("DB_NAME", "trading_saas"),
        user=os.environ.get("DB_USER", "trading_user"),
        password=os.environ.get("DB_PASSWORD", "dev_password_change_in_prod"),
    )


def resolve_symbols(symbols_csv):
    if not symbols_csv:
        return DEFAULT_SYMBOLS
    requested = [t.strip().upper() for t in symbols_csv.split(",") if t.strip()]
    out = []
    for ticker in requested:
        if ticker in DEFAULT_METADATA_BY_TICKER:
            out.append(DEFAULT_METADATA_BY_TICKER[ticker])
        else:
            out.append((ticker, ticker, "UNKNOWN", ""))
    return out


def calculate_rsi(close, period=14):
    delta = close.diff()
    gain = delta.where(delta > 0, 0.0)
    loss = -delta.where(delta < 0, 0.0)
    avg_gain = gain.ewm(com=period - 1, min_periods=period).mean()
    avg_loss = loss.ewm(com=period - 1, min_periods=period).mean()
    rs = avg_gain / avg_loss
    return 100 - (100 / (1 + rs))


def calculate_indicators(df):
    rows = []
    close = df["Close"].squeeze()

    for period in [20, 50, 200]:
        sma = close.rolling(window=period).mean()
        for dt, val in sma.dropna().items():
            rows.append((dt.date(), f"SMA_{period}", float(val), {}))

    for period in [12, 26]:
        ema = close.ewm(span=period, adjust=False).mean()
        for dt, val in ema.dropna().items():
            rows.append((dt.date(), f"EMA_{period}", float(val), {}))

    rsi = calculate_rsi(close, 14)
    for dt, val in rsi.dropna().items():
        rows.append((dt.date(), "RSI_14", float(val), {}))

    ema12 = close.ewm(span=12, adjust=False).mean()
    ema26 = close.ewm(span=26, adjust=False).mean()
    macd = ema12 - ema26
    signal = macd.ewm(span=9, adjust=False).mean()
    hist = macd - signal
    for dt, val in macd.dropna().items():
        rows.append((dt.date(), "MACD", float(val), {
            "signal": float(signal[dt]),
            "histogram": float(hist[dt]),
        }))

    sma20 = close.rolling(window=20).mean()
    std20 = close.rolling(window=20).std()
    upper = sma20 + std20 * 2
    lower = sma20 - std20 * 2
    for dt, val in sma20.dropna().items():
        rows.append((dt.date(), "BOLLINGER_UPPER", float(upper[dt]), {
            "middle": float(val),
            "lower": float(lower[dt]),
        }))

    return rows


def seed_symbols(cur, symbols):
    print(f"\n[1/3] Inserting {len(symbols)} symbols...")
    for ticker, name, exchange, sector in symbols:
        cur.execute("""
            INSERT INTO market_data.symbols (ticker, name, exchange, sector, active)
            VALUES (%s, %s, %s, %s, TRUE)
            ON CONFLICT (ticker) DO UPDATE
                SET name = EXCLUDED.name, updated_at = NOW()
        """, (ticker, name, exchange, sector))
    print("      Done.")


def seed_prices_and_indicators(cur, ticker, period_str):
    df = yf.download(ticker, period=period_str, interval="1d", progress=False, auto_adjust=False)
    if df.empty:
        print("      No data returned, skipping.")
        return 0, 0

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
                ticker, dt.date(),
                float(row["Open"]), float(row["High"]), float(row["Low"]),
                float(row["Close"]), float(row[adj_col]), int(row["Volume"]),
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
    return price_count, ind_count


def resolve_user_id(cur, supplied_user_id):
    """Either return the supplied UUID (verifying it exists) or create demo@trademind.ai and return its id."""
    if supplied_user_id:
        cur.execute("SELECT id FROM trading_core.users WHERE id = %s", (supplied_user_id,))
        row = cur.fetchone()
        if not row:
            raise RuntimeError(
                f"--user-id {supplied_user_id} not found in trading_core.users. "
                "Provide an existing user UUID or omit --user-id to create the demo user."
            )
        print(f"      Using existing user_id={supplied_user_id}")
        return supplied_user_id

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
        raise RuntimeError("Could not retrieve demo user id after insert.")
    user_id = row[0]
    print(f"      Created/located demo user_id={user_id}")
    return user_id


def seed_trading_core(cur, user_id):
    print("\n[3/3] Seeding trading_core sample data...")

    cur.execute("""
        INSERT INTO trading_core.subscriptions (user_id, plan, expires_at)
        VALUES (%s, 'PREMIUM', NULL)
        ON CONFLICT DO NOTHING
    """, (user_id,))

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
        for sym, qty, price in SAMPLE_POSITIONS:
            cur.execute("""
                INSERT INTO trading_core.positions
                    (id, portfolio_id, symbol_ticker, quantity, entry_price,
                     status, opened_at)
                VALUES (gen_random_uuid(), %s, %s, %s, %s, 'OPEN', NOW())
                ON CONFLICT DO NOTHING
            """, (portfolio_id, sym, qty, price))

    cur.execute("""
        INSERT INTO trading_core.user_notification_preferences
            (user_id, signal_digest, live_alerts, risk_warnings,
             strategy_changes, weekly_recap)
        VALUES (%s, TRUE, TRUE, TRUE, FALSE, TRUE)
        ON CONFLICT (user_id) DO NOTHING
    """, (user_id,))

    print("      Subscription, strategy, portfolio and positions inserted.")


def main():
    args = parse_args()
    symbols = resolve_symbols(args.symbols)
    period_str = f"{args.years}y"

    print("Connecting to PostgreSQL...")
    if os.environ.get("DATABASE_URL"):
        print("      Using DATABASE_URL")
    else:
        print(f"      Using DB_HOST={os.environ.get('DB_HOST', 'localhost')} "
              f"DB_PORT={os.environ.get('DB_PORT', '5432')}")

    try:
        conn = db_connect()
    except Exception as e:
        print(f"Connection failed: {e}")
        print("Set DATABASE_URL or DB_HOST/DB_PORT/DB_USER/DB_PASSWORD/DB_NAME.")
        sys.exit(1)

    conn.autocommit = False
    cur = conn.cursor()

    total_prices = 0
    total_indicators = 0
    user_id = None

    try:
        seed_symbols(cur, symbols)

        print(f"\n[2/3] Fetching {args.years}y daily OHLCV + indicators for {len(symbols)} symbols...")
        for i, (ticker, name, *_) in enumerate(symbols, 1):
            print(f"  [{i:02d}/{len(symbols)}] {ticker:5s}  {name}")
            p, ind = seed_prices_and_indicators(cur, ticker, period_str)
            total_prices += p
            total_indicators += ind

        if args.skip_user:
            print("\n[3/3] --skip-user set, skipping trading_core writes.")
        else:
            user_id = resolve_user_id(cur, args.user_id)
            seed_trading_core(cur, user_id)

        if args.dry_run:
            conn.rollback()
            print("\n--dry-run set, ROLLBACK issued. Nothing persisted.")
        else:
            conn.commit()

    except Exception as e:
        conn.rollback()
        print(f"\nFatal error: {e}")
        raise
    finally:
        cur.close()
        conn.close()

    print(
        f"\nseeded_symbols={len(symbols)} "
        f"seeded_bars={total_prices} "
        f"seeded_indicators={total_indicators} "
        f"user_id={user_id or 'none'} "
        f"dry_run={args.dry_run}"
    )
    print("Done.")


if __name__ == "__main__":
    main()
