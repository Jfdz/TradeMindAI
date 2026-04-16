import asyncio
import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, BackgroundTasks
from pydantic import BaseModel

router = APIRouter(prefix="/api/v1/models", tags=["training"])

# In-memory run registry — replaced by DB adapter in production
_runs: dict[str, dict] = {}


class TrainRequest(BaseModel):
    version_tag: str
    max_epochs: int = 100
    lr: float = 1e-3
    batch_size: int = 64
    patience: int = 10


class TrainResponse(BaseModel):
    run_id: str
    status: str
    started_at: str


@router.post("/train", status_code=202, response_model=TrainResponse)
async def trigger_training(body: TrainRequest, background_tasks: BackgroundTasks):
    """Trigger an async training run. Returns 202 with run_id immediately."""
    run_id = str(uuid.uuid4())
    started_at = datetime.now(timezone.utc).isoformat()
    _runs[run_id] = {"status": "PENDING", "started_at": started_at, "params": body.model_dump()}
    background_tasks.add_task(_run_training, run_id, body)
    return TrainResponse(run_id=run_id, status="PENDING", started_at=started_at)


@router.get("/train/{run_id}", tags=["training"])
async def get_training_status(run_id: str):
    """Poll the status of a training run by run_id."""
    if run_id not in _runs:
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="Run not found")
    return _runs[run_id]


async def _run_training(run_id: str, params: TrainRequest) -> None:
    _runs[run_id]["status"] = "RUNNING"
    # Real implementation wires DataLoader + Trainer; stubbed here until
    # data ingestion pipeline is available (FEAT-09 / FEAT-10).
    await asyncio.sleep(0)
    _runs[run_id]["status"] = "COMPLETED"
    _runs[run_id]["finished_at"] = datetime.now(timezone.utc).isoformat()
