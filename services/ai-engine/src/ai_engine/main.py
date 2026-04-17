import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

from ai_engine.adapters.in_.health import router as health_router
from ai_engine.adapters.in_.models import router as models_router
from ai_engine.adapters.in_.prediction import router as prediction_router
from ai_engine.adapters.in_.training import router as training_router

logger = logging.getLogger(__name__)


class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next) -> Response:
        response = await call_next(request)
        response.headers.setdefault("X-Content-Type-Options", "nosniff")
        response.headers.setdefault("X-Frame-Options", "DENY")
        response.headers.setdefault("Referrer-Policy", "same-origin")
        response.headers.setdefault("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
        response.headers.setdefault(
            "Content-Security-Policy",
            "default-src 'self'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'; object-src 'none'; "
            "img-src 'self' data: blob: https:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; "
            "connect-src 'self' https: ws: wss: http://localhost:* http://127.0.0.1:*",
        )
        return response


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.model_loaded = False
    app.state.prediction_service = None
    app.state.consumers = []

    _start_consumers(app)

    yield

    for consumer in app.state.consumers:
        try:
            await consumer.stop()
        except Exception:
            logger.exception("Error stopping consumer")

    app.state.model_loaded = False


def _start_consumers(app: FastAPI) -> None:
    """Wire RabbitMQ consumers when config + model are available.

    Skipped at startup if config is missing (e.g. during tests / first boot
    before .env is provided); consumers can be started later via admin endpoint.
    """
    try:
        from ai_engine.config import get_settings
        from ai_engine.adapters.out.rabbitmq_consumer import (
            MarketDataEventConsumer,
            PredictionRequestConsumer,
        )

        settings = get_settings()

        def _predict(tickers: list[str]) -> list[dict]:
            svc = app.state.prediction_service
            if svc is None:
                return []
            return []  # full wiring in FEAT-10 once data fetch is available

        pred_consumer = PredictionRequestConsumer(settings.rabbitmq_url, _predict)
        mde_consumer = MarketDataEventConsumer(settings.rabbitmq_url, lambda syms: None)
        app.state.consumers = [pred_consumer, mde_consumer]
    except Exception:
        logger.warning("RabbitMQ consumers not started (config missing or broker unreachable)")


def create_app() -> FastAPI:
    from ai_engine.config import get_settings

    settings = get_settings()
    app = FastAPI(
        title="AI Engine",
        description="1D CNN stock price direction prediction service",
        version="0.1.0",
        lifespan=lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.parsed_cors_allowed_origins(),
        allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
        allow_headers=["Authorization", "Content-Type", "X-Requested-With", "X-Correlation-ID"],
        expose_headers=["X-Correlation-ID"],
        allow_credentials=False,
    )
    app.add_middleware(SecurityHeadersMiddleware)

    app.include_router(health_router)
    app.include_router(training_router)
    app.include_router(models_router)
    app.include_router(prediction_router)

    return app


app = create_app()
