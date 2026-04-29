"""PostgreSQL adapter: reads market data and persists training records."""

import json
from contextlib import contextmanager
from datetime import datetime

import pandas as pd
from sqlalchemy import create_engine, text

from ai_engine.config import get_settings


def _make_engine():
    return create_engine(get_settings().database_url, pool_pre_ping=True, pool_size=2)


@contextmanager
def _conn():
    engine = _make_engine()
    try:
        with engine.begin() as connection:
            yield connection
    finally:
        engine.dispose()


# Reads
def load_ohlcv(symbols: list[str] | None = None, min_rows: int = 200) -> dict[str, pd.DataFrame]:
    """Return {ticker: DataFrame[open,high,low,close,volume]} from stock_prices.

    Only symbols with >= min_rows daily bars are included.
    """
    engine = _make_engine()
    try:
        if symbols:
            sql = text(
                """
                SELECT symbol_ticker, trade_date,
                       open, high, low, close, volume
                FROM market_data.stock_prices
                WHERE symbol_ticker = ANY(:symbols)
                  AND time_frame = 'DAILY'
                ORDER BY symbol_ticker, trade_date ASC
                """
            )
            with engine.connect() as conn:
                df_all = pd.read_sql(sql, conn, params={"symbols": symbols})
        else:
            sql = text(
                """
                SELECT symbol_ticker, trade_date,
                       open, high, low, close, volume
                FROM market_data.stock_prices
                WHERE time_frame = 'DAILY'
                ORDER BY symbol_ticker, trade_date ASC
                """
            )
            with engine.connect() as conn:
                df_all = pd.read_sql(sql, conn)
    finally:
        engine.dispose()

    result: dict[str, pd.DataFrame] = {}
    for ticker, grp in df_all.groupby("symbol_ticker"):
        grp = grp.set_index("trade_date").drop(columns=["symbol_ticker"])
        grp.index = pd.to_datetime(grp.index)
        grp = grp.astype(float)
        if len(grp) >= min_rows:
            result[str(ticker)] = grp
    return result


def load_training_run(run_id: str) -> dict | None:
    with _conn() as conn:
        row = conn.execute(
            text(
                """
                SELECT id,
                       model_version_id,
                       status,
                       hyperparameters,
                       metrics,
                       started_at,
                       finished_at,
                       created_at
                FROM ai_engine.training_runs
                WHERE id = cast(:id as uuid)
                """
            ),
            {"id": run_id},
        ).mappings().first()

    if row is None:
        return None

    return {
        "run_id": str(row["id"]),
        "model_version_id": (
            str(row["model_version_id"]) if row["model_version_id"] is not None else None
        ),
        "status": row["status"],
        "hyperparameters": row["hyperparameters"] or {},
        "metrics": row["metrics"] or {},
        "started_at": row["started_at"].isoformat() if row["started_at"] is not None else None,
        "finished_at": row["finished_at"].isoformat() if row["finished_at"] is not None else None,
        "created_at": (
            row["created_at"].isoformat() if row["created_at"] is not None else None
        ),
    }


# Writes
def upsert_training_run(
    run_id: str,
    status: str,
    hyperparameters: dict,
    started_at: datetime,
    metrics: dict | None = None,
    finished_at: datetime | None = None,
    model_version_id: str | None = None,
) -> None:
    with _conn() as conn:
        conn.execute(
            text(
                """
                INSERT INTO ai_engine.training_runs
                    (id, model_version_id, status, hyperparameters,
                     metrics, started_at, finished_at, created_at)
                VALUES
                    (:id, :mv_id, :status, cast(:hp as jsonb),
                     cast(:metrics as jsonb), :started_at, :finished_at, NOW())
                ON CONFLICT (id) DO UPDATE SET
                    status           = EXCLUDED.status,
                    metrics          = EXCLUDED.metrics,
                    finished_at      = EXCLUDED.finished_at,
                    model_version_id = EXCLUDED.model_version_id
                """
            ),
            {
                "id": run_id,
                "mv_id": model_version_id,
                "status": status,
                "hp": json.dumps(hyperparameters),
                "metrics": json.dumps(metrics or {}),
                "started_at": started_at,
                "finished_at": finished_at,
            },
        )


def upsert_model_version(
    version_id: str,
    version_tag: str,
    architecture: str,
    artifact_path: str,
    metrics: dict,
    is_active: bool = False,
) -> None:
    with _conn() as conn:
        conn.execute(
            text(
                """
                INSERT INTO ai_engine.model_versions
                    (id, version_tag, architecture, metrics,
                     artifact_path, is_active, created_at)
                VALUES
                    (:id, :tag, :arch, cast(:metrics as jsonb),
                     :path, :active, NOW())
                ON CONFLICT (version_tag) DO UPDATE SET
                    metrics       = EXCLUDED.metrics,
                    is_active     = EXCLUDED.is_active,
                    artifact_path = EXCLUDED.artifact_path
                """
            ),
            {
                "id": version_id,
                "tag": version_tag,
                "arch": architecture,
                "metrics": json.dumps(metrics),
                "path": artifact_path,
                "active": is_active,
            },
        )
