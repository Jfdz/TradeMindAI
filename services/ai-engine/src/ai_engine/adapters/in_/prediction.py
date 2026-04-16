from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel, Field

router = APIRouter(prefix="/api/v1", tags=["prediction"])


# ── request / response schemas ───────────────────────────────────────────────

class PredictRequest(BaseModel):
    ticker: str = Field(..., min_length=1, max_length=10)


class PredictResponse(BaseModel):
    ticker: str
    direction: str
    confidence: float
    predicted_change_pct: float
    raw_logits: list[float]


class BatchPredictRequest(BaseModel):
    tickers: list[str] = Field(..., min_length=1, max_length=50)


class BatchPredictResponse(BaseModel):
    predictions: list[PredictResponse]


# ── endpoints ─────────────────────────────────────────────────────────────────

@router.post("/predict", response_model=PredictResponse)
async def predict_single(body: PredictRequest, request: Request):
    """Run inference for a single ticker using the active model."""
    _require_model_loaded(request)
    svc = _get_service(request)
    try:
        result = svc.predict_one(body.ticker, _fetch_ohlcv(body.ticker))
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc))
    return PredictResponse(**result.__dict__)


@router.post("/predict/batch", response_model=BatchPredictResponse)
async def predict_batch(body: BatchPredictRequest, request: Request):
    """Run inference for up to 50 tickers in a single forward pass."""
    _require_model_loaded(request)
    svc = _get_service(request)
    pairs = [(t, _fetch_ohlcv(t)) for t in body.tickers]
    try:
        results = svc.predict_batch(pairs)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc))
    return BatchPredictResponse(predictions=[PredictResponse(**r.__dict__) for r in results])


# ── helpers ───────────────────────────────────────────────────────────────────

def _require_model_loaded(request: Request) -> None:
    if not getattr(request.app.state, "model_loaded", False):
        raise HTTPException(status_code=503, detail="Model not loaded")


def _get_service(request: Request):
    svc = getattr(request.app.state, "prediction_service", None)
    if svc is None:
        raise HTTPException(status_code=503, detail="Prediction service not initialised")
    return svc


def _fetch_ohlcv(ticker: str):
    # Data fetching from market-data-service is wired in FEAT-10+.
    # Raises NotImplementedError so integration tests can mock this boundary.
    raise NotImplementedError(f"OHLCV data fetch for {ticker} not yet wired")
