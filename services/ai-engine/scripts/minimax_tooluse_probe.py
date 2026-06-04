"""Fase 0 spike — which MiniMax route gives reliable structured (tool) output?

Decides the Phase 1 adapter route for wiring MiniMax into the ai-engine reasoning
pipeline. Uses raw httpx for full control over the exact HTTP path, so a stray
``MINIMAX_BASE_URL`` in the environment can't silently send the request to the
wrong endpoint (which confounded the first run).

It probes both candidate routes with the same forced-tool intent the production
``AnthropicLlmReasoningClient`` relies on:

  - ROUTE A — Anthropic-compatible Messages API at ``{host}/anthropic/v1/messages``.
    If it honours ``tools`` + forced ``tool_choice``, Phase 1 can reuse the
    Anthropic SDK client verbatim with ``base_url={host}/anthropic`` + the
    bearer token.

  - ROUTE B — OpenAI-compatible Chat Completions at ``{host}/v1/chat/completions``.
    MiniMax's primary API. MiniMax M2.x/M3 reject the function-spec
    ``tool_choice`` dict and accept only the string enum ``{"none","auto"}``
    (TradingAgents openai_client.py), so the adapter binds the tool with
    ``tool_choice="auto"`` and tolerates a prose fallback. M-series reasoning
    models also need ``reasoning_split`` in the body.

The bearer token is read only from the environment and never logged (a redacted
prefix is printed so you can confirm the right credential was picked up).

Run (PowerShell):

    $env:MINIMAX_OAUTH_TOKEN = "<tu token de la sub MiniMax>"
    # opcional: $env:MINIMAX_MODEL = "MiniMax-M2.7"   (default abajo)
    cd services/ai-engine
    uv run python scripts/minimax_tooluse_probe.py

``--host`` overrides the host (default ``https://api.minimax.io``). The probe
ignores ``MINIMAX_BASE_URL`` on purpose so route paths are deterministic.

Exit codes: 0 = a route gave a tool call (see which + whether forcing worked),
3 = no route produced structured output (inspect errors), 1 = misuse.
"""

from __future__ import annotations

import argparse
import json
import os

DEFAULT_HOST = "https://api.minimax.io"
DEFAULT_MODEL = "MiniMax-M2.7"

_SYSTEM = (
    "Trading-signal reasoning writer for retail investors. Tool-only output. "
    "Call emit_reasoning exactly once. No free text."
)
_USER = (
    "<context><signal>ticker: AAPL\ntype: BUY\nconfidence: 0.7300\n"
    "entry_price: 195.42</signal>\n<price_facts>close: 195.42\nsma_200: 178.55\n"
    "rsi_14: 61.2</price_facts>\n<news>(no recent news)</news></context>\n"
    "Generate the reasoning by calling emit_reasoning exactly once."
)

# Anthropic-shaped tool (input_schema) — mirrors core/domain/reasoning_prompts.py.
_ANTHROPIC_TOOL = {
    "name": "emit_reasoning",
    "description": "Emit grounded trading-signal reasoning. Call exactly once.",
    "input_schema": {
        "type": "object",
        "properties": {
            "reasoning": {"type": "string", "maxLength": 400},
            "refusal": {"type": "boolean"},
        },
        "required": ["reasoning", "refusal"],
    },
}
# OpenAI-shaped function tool (parameters) — same schema, different envelope.
_OPENAI_TOOL = {
    "type": "function",
    "function": {
        "name": "emit_reasoning",
        "description": "Emit grounded trading-signal reasoning. Call exactly once.",
        "parameters": _ANTHROPIC_TOOL["input_schema"],
    },
}


def _redacted(token: str) -> str:
    return "****" if len(token) <= 8 else f"{token[:4]}...{token[-4:]} (len={len(token)})"


def _post(client, url: str, headers: dict, body: dict):
    """POST json, return (status, parsed_json_or_text)."""
    resp = client.post(url, headers=headers, json=body)
    try:
        return resp.status_code, resp.json()
    except Exception:
        return resp.status_code, resp.text


def _probe_anthropic(client, host: str, token: str, model: str, tool_choice, label: str):
    url = f"{host}/anthropic/v1/messages"
    headers = {
        "authorization": f"Bearer {token}",
        "anthropic-version": "2023-06-01",
        "content-type": "application/json",
    }
    body = {
        "model": model,
        "max_tokens": 350,
        "temperature": 0.2,
        "system": _SYSTEM,
        "tools": [_ANTHROPIC_TOOL],
        "tool_choice": tool_choice,
        "messages": [{"role": "user", "content": _USER}],
    }
    print(f"\n── ROUTE A (Anthropic) · {label} · tool_choice={tool_choice} ──\n   POST {url}")
    status, payload = _post(client, url, headers, body)
    if status != 200:
        print(f"   HTTP {status}: {str(payload)[:200]}")
        return False
    blocks = payload.get("content", []) if isinstance(payload, dict) else []
    tool_use = next((b for b in blocks if b.get("type") == "tool_use"), None)
    has = "YES" if tool_use else "no"
    print(f"   HTTP 200  stop_reason={payload.get('stop_reason')}  tool_use={has}")
    if tool_use:
        print(f"   tool_use.input keys={sorted((tool_use.get('input') or {}).keys())}")
    return bool(tool_use)


def _probe_openai(client, host: str, token: str, model: str, tool_choice, label: str):
    url = f"{host}/v1/chat/completions"
    headers = {"authorization": f"Bearer {token}", "content-type": "application/json"}
    body = {
        "model": model,
        "max_tokens": 350,
        "temperature": 0.2,
        "messages": [
            {"role": "system", "content": _SYSTEM},
            {"role": "user", "content": _USER},
        ],
        "tools": [_OPENAI_TOOL],
        "tool_choice": tool_choice,
    }
    # M-series reasoning models split <think> into reasoning_details when asked.
    if model.upper().startswith("MINIMAX-M"):
        body["reasoning_split"] = True
    print(f"\n── ROUTE B (OpenAI) · {label} · tool_choice={tool_choice!r} ──\n   POST {url}")
    status, payload = _post(client, url, headers, body)
    if status != 200:
        print(f"   HTTP {status}: {str(payload)[:200]}")
        return False
    choices = payload.get("choices", []) if isinstance(payload, dict) else []
    msg = choices[0].get("message", {}) if choices else {}
    tool_calls = msg.get("tool_calls")
    fin = choices[0].get("finish_reason") if choices else None
    print(f"   HTTP 200  finish_reason={fin}  tool_calls={'YES' if tool_calls else 'no'}")
    if tool_calls:
        names = [tc.get("function", {}).get("name") for tc in tool_calls]
        print(f"   tool_calls names={names}")
    elif msg.get("content"):
        print(f"   content[:160]={str(msg['content'])[:160]!r}")
    return bool(tool_calls)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default=os.getenv("MINIMAX_HOST", DEFAULT_HOST))
    parser.add_argument("--model", default=os.getenv("MINIMAX_MODEL", DEFAULT_MODEL))
    parser.add_argument("--token-env", default="MINIMAX_OAUTH_TOKEN")
    args = parser.parse_args()
    host = args.host.rstrip("/")

    token = os.getenv(args.token_env, "").strip()
    if not token:
        print(f"ERROR: set {args.token_env} to your MiniMax token first.")
        return 1
    try:
        import httpx
    except ImportError:
        print("ERROR: httpx not installed. Use the ai-engine venv (uv run ...).")
        return 1

    print("MiniMax structured-output probe (Fase 0 spike, raw httpx)")
    print(f"  host  = {host}   (MINIMAX_BASE_URL ignored on purpose)")
    print(f"  model = {args.model}")
    print(f"  token = {_redacted(token)}  (from ${args.token_env})")

    results: dict[str, bool] = {}
    with httpx.Client(timeout=40.0) as client:
        # Route A: Anthropic-compat, forced then auto.
        results["A_forced"] = _probe_anthropic(
            client,
            host,
            token,
            args.model,
            {"type": "tool", "name": "emit_reasoning"},
            "forced",
        )
        if not results["A_forced"]:
            results["A_auto"] = _probe_anthropic(
                client,
                host,
                token,
                args.model,
                {"type": "auto"},
                "auto",
            )
        # Route B: OpenAI-compat, forced dict then auto.
        results["B_forced"] = _probe_openai(
            client,
            host,
            token,
            args.model,
            {"type": "function", "function": {"name": "emit_reasoning"}},
            "forced",
        )
        if not results["B_forced"]:
            results["B_auto"] = _probe_openai(
                client,
                host,
                token,
                args.model,
                "auto",
                "auto",
            )

    print("\n" + "=" * 64)
    print("RESULTS:", json.dumps(results))
    if results.get("A_forced"):
        print(
            "==> ROUTE A VIABLE (forced). Reuse AnthropicLlmReasoningClient w/ base_url=/anthropic."
        )
        return 0
    if results.get("A_auto"):
        print("==> ROUTE A works on 'auto' only. Anthropic SDK + parse, no forcing.")
        return 0
    if results.get("B_forced"):
        print("==> ROUTE B VIABLE (forced dict honoured). OpenAI-compat adapter, force the tool.")
        return 0
    if results.get("B_auto"):
        print("==> ROUTE B (auto). OpenAI-compat adapter: bind tool, tool_choice='auto', parse")
        print("    tool_calls, prose-fallback -> ReasoningResult.error. This is the expected path")
        print("    for MiniMax M2.x/M3 per TradingAgents.")
        return 0
    print("==> NO STRUCTURED OUTPUT on either route. Inspect HTTP errors above.")
    print("    Try another --model (MiniMax-Text-01) or confirm the token scope/endpoint.")
    return 3


if __name__ == "__main__":
    raise SystemExit(main())
