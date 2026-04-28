import asyncio
import logging
from datetime import datetime, timezone
from typing import Optional

import numpy as np
from fastapi import APIRouter, BackgroundTasks, Depends
from pydantic import BaseModel

from ai_engine.adapters.in_.auth import require_internal_secret

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/models", tags=["training"])

_runs: dict[str, dict] = {}

WINDOW = 60
HORIZON = 5


class TrainRequest(BaseModel):
    version_tag: str
    max_epochs: int = 100
    lr: float = 1e-3
    batch_size: int = 64
    patience: int = 10
    symbols: Optional[list[str]] = None


class TrainResponse(BaseModel):
    run_id: str
    status: str
    started_at: str


@router.post("/train", status_code=202, response_model=TrainResponse, dependencies=[Depends(require_internal_secret)])
async def trigger_training(body: TrainRequest, background_tasks: BackgroundTasks):
    """Trigger an async CNN training run. Returns 202 with run_id immediately."""
    import uuid
    run_id = str(uuid.uuid4())
    started_at = datetime.now(timezone.utc).isoformat()
    _runs[run_id] = {"status": "PENDING", "started_at": started_at, "params": body.model_dump()}
    background_tasks.add_task(_run_training, run_id, body, datetime.now(timezone.utc))
    return TrainResponse(run_id=run_id, status="PENDING", started_at=started_at)


@router.get("/train/{run_id}", tags=["training"], dependencies=[Depends(require_internal_secret)])
async def get_training_status(run_id: str):
    """Poll the status of a training run."""
    if run_id not in _runs:
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="Run not found")
    return _runs[run_id]


# ── real pipeline ─────────────────────────────────────────────────────────────

async def _run_training(run_id: str, params: TrainRequest, started_at: datetime) -> None:
    _runs[run_id]["status"] = "RUNNING"

    try:
        from ai_engine.adapters.out.db_adapter import (
            load_ohlcv,
            upsert_model_version,
            upsert_training_run,
        )
        from ai_engine.config import get_settings
        from ai_engine.core.domain.data_loader import make_data_loaders
        from ai_engine.core.domain.feature_engineering import compute_features
        from ai_engine.core.domain.label_generator import generate_labels
        from ai_engine.core.domain.normalizer import MinMaxNormalizer
        from ai_engine.core.domain.sequence_builder import build_sequences
        from ai_engine.core.models.cnn import StockCNN
        from ai_engine.core.use_cases.evaluator import Evaluator
        from ai_engine.core.use_cases.model_registry import ModelRegistry
        from ai_engine.core.use_cases.trainer import Trainer

        hp = {
            "max_epochs": params.max_epochs,
            "lr": params.lr,
            "batch_size": params.batch_size,
            "patience": params.patience,
            "window": WINDOW,
            "horizon": HORIZON,
        }

        try:
            upsert_training_run(
                run_id=run_id,
                status="RUNNING",
                hyperparameters=hp,
                started_at=started_at,
            )
        except Exception as db_exc:
            logger.warning("[%s] Could not persist RUNNING status (non-fatal): %s", run_id, db_exc)

        # 1 ── load OHLCV from DB
        logger.info("[%s] Loading OHLCV data...", run_id)
        ohlcv_map = await asyncio.get_event_loop().run_in_executor(
            None, lambda: load_ohlcv(params.symbols, min_rows=WINDOW + HORIZON + 10)
        )
        if not ohlcv_map:
            raise RuntimeError(
                "No symbols with enough data found. Run the market data seed first."
            )
        logger.info("[%s] Loaded %d symbols.", run_id, len(ohlcv_map))

        # 2 ── feature engineering + label generation + sequence building
        all_seqs: list[np.ndarray] = []
        all_labels: list[np.ndarray] = []

        for ticker, df in ohlcv_map.items():
            try:
                feat_df = compute_features(df)
                n = len(feat_df)
                if n < WINDOW + HORIZON:
                    continue

                feat_arr = feat_df.values.astype(np.float32)
                label_arr = generate_labels(feat_df["close"], horizon=HORIZON)

                seqs = build_sequences(feat_arr, window=WINDOW)
                n_valid = n - HORIZON - WINDOW + 1
                if n_valid <= 0:
                    continue

                all_seqs.append(seqs[:n_valid])
                all_labels.append(label_arr[WINDOW - 1: WINDOW - 1 + n_valid])
            except Exception as exc:
                logger.warning("[%s] Skipping %s: %s", run_id, ticker, exc)

        if not all_seqs:
            raise RuntimeError("No usable sequences after feature engineering.")

        sequences = np.concatenate(all_seqs, axis=0)   # (N, 17, 60)
        labels    = np.concatenate(all_labels, axis=0)  # (N,)
        logger.info("[%s] Total samples: %d", run_id, len(sequences))

        # 3 ── normalize (fit on training portion only to avoid leakage)
        train_end = int(len(sequences) * 0.70)
        normalizer = MinMaxNormalizer()
        train_flat = sequences[:train_end].transpose(0, 2, 1).reshape(-1, sequences.shape[1])
        normalizer.fit(train_flat)

        def _norm_seqs(s: np.ndarray) -> np.ndarray:
            n_s, n_f, w = s.shape
            flat = s.transpose(0, 2, 1).reshape(-1, n_f)
            normed = normalizer.transform(flat)
            return normed.reshape(n_s, w, n_f).transpose(0, 2, 1)

        sequences_normed = _norm_seqs(sequences)

        # 4 ── data loaders
        loaders = make_data_loaders(
            sequences_normed, labels,
            batch_size=params.batch_size,
        )
        logger.info(
            "[%s] Split — train: %d  val: %d  test: %d",
            run_id, loaders.train_size, loaders.val_size, loaders.test_size,
        )

        # 5 ── train
        model = StockCNN()
        trainer = Trainer(
            model=model,
            train_loader=loaders.train,
            val_loader=loaders.val,
            lr=params.lr,
            patience=params.patience,
            max_epochs=params.max_epochs,
        )
        logger.info("[%s] Training started (max_epochs=%d)...", run_id, params.max_epochs)
        train_result = await asyncio.get_event_loop().run_in_executor(None, trainer.train)
        logger.info(
            "[%s] Training done — best_epoch=%d  early_stop=%s",
            run_id, train_result.best_epoch, train_result.stopped_early,
        )

        # 6 ── evaluate on test set
        evaluator = Evaluator(model)
        eval_metrics = await asyncio.get_event_loop().run_in_executor(
            None, lambda: evaluator.evaluate(loaders.test)
        )

        best_val = train_result.val_history[train_result.best_epoch]
        metrics = {
            "best_epoch":    train_result.best_epoch,
            "stopped_early": train_result.stopped_early,
            "val_loss":      round(best_val.loss, 6),
            "val_accuracy":  round(best_val.accuracy, 4),
            "test_accuracy": round(eval_metrics.accuracy, 4),
            "precision":     {k: round(v, 4) for k, v in eval_metrics.precision.items()},
            "recall":        {k: round(v, 4) for k, v in eval_metrics.recall.items()},
            "f1":            {k: round(v, 4) for k, v in eval_metrics.f1.items()},
            "train_history": [
                {"epoch": i, "loss": round(e.loss, 6), "acc": round(e.accuracy, 4)}
                for i, e in enumerate(train_result.train_history)
            ],
        }

        # 7 ── save model artifact
        settings = get_settings()
        registry = ModelRegistry(base_path=settings.model_path)
        version_id = registry.save(
            model=model,
            version_tag=params.version_tag,
            architecture="StockCNN-1D",
            hyperparameters=hp,
            metrics=metrics,
        )
        artifact_path = f"{settings.model_path}/{version_id}/model.pt"

        # 8 ── persist to DB (non-fatal if tables missing)
        finished_at = datetime.now(timezone.utc)
        try:
            upsert_model_version(
                version_id=version_id,
                version_tag=params.version_tag,
                architecture="StockCNN-1D",
                artifact_path=artifact_path,
                metrics=metrics,
            )
            upsert_training_run(
                run_id=run_id,
                status="COMPLETED",
                hyperparameters=hp,
                started_at=started_at,
                metrics=metrics,
                finished_at=finished_at,
                model_version_id=version_id,
            )
        except Exception as db_exc:
            logger.warning("[%s] Could not persist results to DB (non-fatal): %s", run_id, db_exc)

        _runs[run_id].update({
            "status":       "COMPLETED",
            "finished_at":  finished_at.isoformat(),
            "version_id":   version_id,
            "metrics":      metrics,
        })
        logger.info("[%s] Completed — version_id=%s  test_acc=%.4f",
                    run_id, version_id, eval_metrics.accuracy)

    except Exception as exc:
        logger.exception("[%s] Training failed: %s", run_id, exc)
        _runs[run_id].update({"status": "FAILED", "error": str(exc)})
        from ai_engine.adapters.out.db_adapter import upsert_training_run
        try:
            upsert_training_run(
                run_id=run_id,
                status="FAILED",
                hyperparameters=_runs[run_id].get("params", {}),
                started_at=started_at,
                metrics={"error": str(exc)},
                finished_at=datetime.now(timezone.utc),
            )
        except Exception:
            pass
