import logging
import os
from contextlib import asynccontextmanager

from alembic import command as alembic_command
from alembic.config import Config as AlembicConfig
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

_SECURITY_HSTS = "max-age=31536000; includeSubDomains; preload"
_SECURITY_CSP = (
    "default-src 'self'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'; "
    "object-src 'none'; img-src 'self' data: blob: https:; style-src 'self' 'unsafe-inline'; "
    "script-src 'self' 'unsafe-inline'; connect-src 'self' https: ws: wss: "
    "http://localhost:* http://127.0.0.1:*"
)


class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next) -> Response:
        response = await call_next(request)
        response.headers.setdefault("X-Content-Type-Options", "nosniff")
        response.headers.setdefault("X-Frame-Options", "DENY")
        response.headers.setdefault("Referrer-Policy", "same-origin")
        response.headers.setdefault("Strict-Transport-Security", _SECURITY_HSTS)
        response.headers.setdefault("Content-Security-Policy", _SECURITY_CSP)
        return response


async def _apply_migrations() -> None:
    ini_path = os.path.join(os.path.dirname(__file__), "..", "..", "..", "alembic.ini")
    alembic_cfg = AlembicConfig(ini_path)
    alembic_command.upgrade(alembic_cfg, "head")
    logger.info("Alembic migrations applied")


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.model_loaded = False
    app.state.consumers_ready = False
    app.state.prediction_service = None
    app.state.consumers = []

    try:
        await _apply_migrations()
    except Exception:
        logger.error("Alembic migrations failed — service will not accept traffic", exc_info=True)
        # Do not raise: let the app start so K8s readiness probe can return 503
        yield
        return

    await _start_consumers(app)

    yield

    for consumer in app.state.consumers:
        try:
            await consumer.stop()
        except Exception:
            logger.exception("Error stopping consumer")

    app.state.model_loaded = False


async def _start_consumers(app: FastAPI) -> None:
    """Initialise the prediction service, load the active model, and start RabbitMQ consumers.

    Gracefully skipped when config is missing (e.g. unit tests / first boot without .env).
    """
    try:
        from ai_engine.adapters.out.rabbitmq_consumer import (
            MarketDataEventConsumer,
            PredictionRequestConsumer,
        )
        from ai_engine.config import get_settings
        from ai_engine.core.use_cases.model_registry import ModelRegistry
        from ai_engine.core.use_cases.prediction_service import PredictionService

        settings = get_settings()

        registry = ModelRegistry(settings.model_path)
        svc = PredictionService(registry)
        app.state.prediction_service = svc

        try:
            svc.reload()
            app.state.model_loaded = True
            logger.info("Active model loaded successfully")
        except RuntimeError:
            logger.warning(
                "No active model version found — run scripts/seed_model.py to bootstrap. "
                "Predictions will be unavailable until a model is activated."
            )

        pred_consumer = PredictionRequestConsumer(
            settings.rabbitmq_url,
            _make_sync_predict(app),
        )
        mde_consumer = MarketDataEventConsumer(
            settings.rabbitmq_url,
            _make_market_data_trigger(app, settings),
        )
        app.state.consumers = [pred_consumer, mde_consumer]

        await pred_consumer.start()
        await mde_consumer.start()
        app.state.consumers_ready = True
        logger.info("RabbitMQ consumers started")
    except Exception:
        logger.error(
            "RabbitMQ consumers failed to start — readiness probe will return 503",
            exc_info=True,
        )


def _make_sync_predict(app: FastAPI):
    """Return a sync predict callback for PredictionRequestConsumer."""
    from ai_engine.adapters.out.market_data_client import MarketDataClient
    from ai_engine.config import get_settings

    def _predict(tickers: list[str]) -> list[dict]:
        svc = app.state.prediction_service
        if svc is None or not app.state.model_loaded:
            logger.warning("Prediction requested but service/model not ready")
            return []
        try:
            settings = get_settings()
            client = MarketDataClient(settings.market_data_service_url)
            pairs = []
            for ticker in tickers:
                try:
                    df = client.fetch_ohlcv(ticker)
                    pairs.append((ticker, df))
                except Exception:
                    logger.exception("Failed to fetch OHLCV for %s", ticker)
            if not pairs:
                return []
            results = svc.predict_batch(pairs)
            return [r.__dict__ for r in results]
        except Exception:
            logger.exception("Batch prediction failed")
            return []

    return _predict


def _make_market_data_trigger(app: FastAPI, settings):
    """Return an async trigger callable for MarketDataEventConsumer."""
    import json

    import aio_pika
    from aio_pika import ExchangeType, Message

    from ai_engine.adapters.out.market_data_client import MarketDataClient
    from ai_engine.adapters.out.rabbitmq_consumer import PREDICTION_RESULT_EXCHANGE

    async def _trigger(symbols: list[str]) -> None:
        if not app.state.model_loaded:
            logger.warning("Market data event received but model not loaded — skipping predictions")
            return

        svc = app.state.prediction_service
        client = MarketDataClient(settings.market_data_service_url)

        pairs = []
        for ticker in symbols:
            try:
                df = client.fetch_ohlcv(ticker)
                pairs.append((ticker, df))
            except Exception:
                logger.exception("Failed to fetch OHLCV for %s", ticker)

        if not pairs:
            logger.warning("No OHLCV data retrieved for symbols: %s", symbols)
            return

        try:
            results = svc.predict_batch(pairs)
        except Exception:
            logger.exception("Batch prediction failed for symbols: %s", symbols)
            return

        payload = json.dumps({
            "tickers": symbols,
            "predictions": [r.__dict__ for r in results],
        }).encode()

        try:
            connection = await aio_pika.connect_robust(settings.rabbitmq_url)
            async with connection:
                channel = await connection.channel()
                exchange = await channel.declare_exchange(
                    PREDICTION_RESULT_EXCHANGE,
                    ExchangeType.FANOUT,
                    durable=True,
                )
                await exchange.publish(
                    Message(body=payload, content_type="application/json"),
                    routing_key="",
                )
            logger.info("Published %d predictions for %s", len(results), symbols)
        except Exception:
            logger.exception("Failed to publish prediction results")

    return _trigger


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
