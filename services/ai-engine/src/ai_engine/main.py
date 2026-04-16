import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from ai_engine.adapters.in_.health import router as health_router
from ai_engine.adapters.in_.models import router as models_router
from ai_engine.adapters.in_.prediction import router as prediction_router
from ai_engine.adapters.in_.training import router as training_router

logger = logging.getLogger(__name__)


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
    app = FastAPI(
        title="AI Engine",
        description="1D CNN stock price direction prediction service",
        version="0.1.0",
        lifespan=lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_methods=["*"],
        allow_headers=["*"],
    )

    app.include_router(health_router)
    app.include_router(training_router)
    app.include_router(models_router)
    app.include_router(prediction_router)

    return app


app = create_app()
