from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel

from ai_engine.adapters.in_.auth import require_internal_secret

router = APIRouter(prefix="/api/v1/models", tags=["models"])


class ModelVersionResponse(BaseModel):
    version_id: str
    version_tag: str
    architecture: str
    metrics: dict
    is_active: bool
    saved_at: str


class ActivateResponse(BaseModel):
    version_id: str
    activated: bool


def _registry(request: Request):
    reg = getattr(request.app.state, "model_registry", None)
    if reg is None:
        raise HTTPException(status_code=503, detail="Model registry not initialised")
    return reg


@router.get(
    "",
    response_model=list[ModelVersionResponse],
    dependencies=[Depends(require_internal_secret)]
)
async def list_models(request: Request):
    """Return metadata for all saved model versions."""
    reg = _registry(request)
    versions = []
    for vid in reg.list_versions():
        try:
            meta = reg.get_metadata(vid)
            versions.append(ModelVersionResponse(
                version_id=meta["version_id"],
                version_tag=meta["version_tag"],
                architecture=meta["architecture"],
                metrics=meta.get("metrics", {}),
                is_active=meta.get("is_active", False),
                saved_at=meta["saved_at"],
            ))
        except Exception:
            continue
    return versions


@router.get(
    "/active",
    response_model=ModelVersionResponse,
    dependencies=[Depends(require_internal_secret)]
)
async def get_active_model(request: Request):
    """Return metadata for the currently active model version."""
    reg = _registry(request)
    vid = reg.active_version_id()
    if vid is None:
        raise HTTPException(status_code=404, detail="No active model version set")
    meta = reg.get_metadata(vid)
    return ModelVersionResponse(
        version_id=meta["version_id"],
        version_tag=meta["version_tag"],
        architecture=meta["architecture"],
        metrics=meta.get("metrics", {}),
        is_active=True,
        saved_at=meta["saved_at"],
    )


@router.post(
    "/{version_id}/activate",
    response_model=ActivateResponse,
    dependencies=[Depends(require_internal_secret)]
)
async def activate_model(version_id: str, request: Request):
    """Activate a model version and reload the prediction service."""
    reg = _registry(request)
    try:
        reg.activate(version_id)
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc))

    # Reload prediction service so subsequent requests use the new weights
    svc = getattr(request.app.state, "prediction_service", None)
    if svc is not None:
        svc.reload()

    request.app.state.model_loaded = True
    return ActivateResponse(version_id=version_id, activated=True)
