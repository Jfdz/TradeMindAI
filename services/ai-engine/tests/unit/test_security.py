import os

os.environ.setdefault("DATABASE_URL", "sqlite:///./test.db")
os.environ.setdefault("CORS_ALLOWED_ORIGINS", "http://localhost:3000,http://127.0.0.1:3000")

from fastapi.testclient import TestClient

from ai_engine.main import app


def test_security_headers_present():
    client = TestClient(app)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.headers["x-content-type-options"] == "nosniff"
    assert response.headers["x-frame-options"] == "DENY"
    assert response.headers["referrer-policy"] == "same-origin"
    assert "max-age=31536000" in response.headers["strict-transport-security"]
    assert "default-src 'self'" in response.headers["content-security-policy"]


def test_cors_allows_configured_origin():
    client = TestClient(app)

    response = client.options(
        "/health",
        headers={
            "Origin": "http://localhost:3000",
            "Access-Control-Request-Method": "GET",
        },
    )

    assert response.status_code == 200
    assert response.headers["access-control-allow-origin"] == "http://localhost:3000"
