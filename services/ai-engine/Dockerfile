# Stage 1: Build
FROM python:3.11-slim AS build
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

# Stage 2: Runtime
FROM python:3.11-slim
RUN useradd -m -r appuser
WORKDIR /app
COPY --from=build /install /usr/local
COPY src/ ./src/
COPY alembic/ ./alembic/
COPY alembic.ini ./alembic.ini
COPY pyproject.toml ./pyproject.toml
RUN pip install --no-cache-dir --no-deps -e .
USER appuser
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1
ENTRYPOINT ["uvicorn", "ai_engine.main:app", "--host", "0.0.0.0", "--port", "8000"]
