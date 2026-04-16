from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse

router = APIRouter(tags=["health"])


@router.get("/health")
async def health():
    return {"status": "ok"}


@router.get("/ready")
async def ready(request: Request):
    if not request.app.state.model_loaded:
        return JSONResponse(status_code=503, content={"status": "not ready", "reason": "model not loaded"})
    return {"status": "ready"}
