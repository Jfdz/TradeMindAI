from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Model loading happens here in future PBIs (E2-F07)
    app.state.model_loaded = False
    yield
    app.state.model_loaded = False


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

    return app


app = create_app()
