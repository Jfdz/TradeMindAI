"""Internal auth dependency — gates admin routes behind X-Internal-Secret."""

from fastapi import Header, HTTPException, status

from ai_engine.config import get_settings


def require_internal_secret(
        x_internal_secret: str = Header(..., alias="X-Internal-Secret")
) -> None:
    """Dependency that validates the X-Internal-Secret header on admin routes."""
    settings = get_settings()
    if not settings.internal_secret:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Auth not configured"
        )
    if x_internal_secret != settings.internal_secret:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid internal secret"
        )
