"""HTTP client posting reasoning artifacts to trading-core (C6 endpoint).

Wraps `PUT /api/v1/internal/signals/{signalId}/reasoning` and maps the
documented status codes to a typed `SinkResult`:

  - 204 No Content              → PERSISTED
  - 404 Not Found               → SIGNAL_NOT_FOUND
  - 400 / 401 / 5xx / transport → UPSTREAM_FAILED

Design follows the same shape as `TradingCoreClient` from C3c:
  - sync httpx
  - X-Internal-Secret header
  - no retries (trading-core idempotency means we re-attempt at the
    orchestrator level if needed, not here)
"""

from __future__ import annotations

import json
import logging
from typing import Any

import httpx

from ai_engine.core.domain.reasoning_sink import SinkResult

logger = logging.getLogger(__name__)


class TradingCoreReasoningSink:
    """Production ReasoningSinkPort implementation."""

    def __init__(self, base_url: str, internal_secret: str = "", timeout: float = 10.0):
        self._base_url = base_url.rstrip("/")
        self._internal_secret = internal_secret
        self._timeout = timeout

    def persist(self, signal_id: str, payload: dict[str, Any]) -> SinkResult:
        if not signal_id or not signal_id.strip():
            logger.warning("event=reasoning_sink.blank_signal_id")
            return SinkResult.upstream_failed(signal_id, "blank_signal_id")
        if not self._internal_secret:
            logger.error(
                "event=reasoning_sink.no_secret signal_id=%s", signal_id
            )
            return SinkResult.upstream_failed(
                signal_id, "internal_secret_not_configured"
            )

        url = f"{self._base_url}/api/v1/internal/signals/{signal_id}/reasoning"
        headers = {
            "X-Internal-Secret": self._internal_secret,
            "Content-Type": "application/json",
        }
        try:
            response = httpx.put(
                url, content=json.dumps(payload), headers=headers, timeout=self._timeout
            )
        except httpx.RequestError as exc:
            logger.warning(
                "event=reasoning_sink.transport_failed signal_id=%s detail=%s",
                signal_id,
                str(exc),
            )
            return SinkResult.upstream_failed(signal_id, f"transport: {exc!s}")

        status = response.status_code
        if status == 204:
            logger.info(
                "event=reasoning_sink.persisted signal_id=%s outcome=%s",
                signal_id,
                payload.get("outcome"),
            )
            return SinkResult.persisted(signal_id)
        if status == 404:
            logger.info(
                "event=reasoning_sink.signal_not_found signal_id=%s", signal_id
            )
            return SinkResult.signal_not_found(signal_id)
        if status == 401:
            logger.error(
                "event=reasoning_sink.unauthorized signal_id=%s", signal_id
            )
            return SinkResult.upstream_failed(signal_id, "unauthorized")
        if status == 400:
            body = _safe_body(response)
            logger.warning(
                "event=reasoning_sink.bad_request signal_id=%s body=%s",
                signal_id,
                body,
            )
            return SinkResult.upstream_failed(signal_id, f"bad_request: {body}")
        logger.warning(
            "event=reasoning_sink.unexpected_status signal_id=%s status=%d",
            signal_id,
            status,
        )
        return SinkResult.upstream_failed(signal_id, f"http_{status}")


def _safe_body(response: httpx.Response) -> str:
    try:
        return response.text[:200]
    except Exception:
        return "<unreadable>"
