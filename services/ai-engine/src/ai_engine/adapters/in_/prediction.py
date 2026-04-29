from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field

from ai_engine.adapters.in_.auth import require_internal_secret

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


@router.post(
    "/predict/publish",
    response_model=BatchPredictResponse,
    dependencies=[Depends(require_internal_secret)],
)
async def predict_and_publish(body: BatchPredictRequest, request: Request):
    """Run inference for the given tickers and publish results to RabbitMQ.

    Trading-core consumes the published message and persists the signals to the database.
    Requires X-Internal-Secret header (admin route).
    """
    _require_model_loaded(request)
    svc = _get_service(request)

    pairs: list = []
    fetch_errors: list[str] = []
    for ticker in body.tickers:
        try:
            ohlcv = _fetch_ohlcv(ticker)
            pairs.append((ticker, ohlcv))
        except Exception as exc:
            fetch_errors.append(f"{ticker}: {exc}")

    if not pairs:
        raise HTTPException(
            status_code=422,
            detail=f"Failed to fetch OHLCV for all tickers. Errors: {fetch_errors}",
        )

    try:
        results = svc.predict_batch(pairs)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc))

    publish_fn = getattr(request.app.state, "publish_predictions", None)
    rabbitmq_url = getattr(request.app.state, "rabbitmq_url", None)
    if publish_fn is None or rabbitmq_url is None:
        raise HTTPException(status_code=503, detail="Publisher not initialised — RabbitMQ consumers not started")

    payload = {
        "tickers": [pair[0] for pair in pairs],
        "predictions": [r.__dict__ for r in results],
    }

    try:
        await publish_fn(rabbitmq_url, payload)
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"Failed to publish predictions: {exc}")

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
    from ai_engine.adapters.out.market_data_client import MarketDataClient
    from ai_engine.config import get_settings
    return MarketDataClient(get_settings().market_data_service_url).fetch_ohlcv(ticker)
