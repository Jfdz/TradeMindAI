# api-specs

OpenAPI 3.0 specifications for all Trading SaaS services.

Specs are shared here so the frontend and inter-service clients can generate typed clients from a single source of truth.

## Files

| File | Service | Description |
|---|---|---|
| `market-data-service.yaml` | market-data-service | Symbols, prices, indicators endpoints |
| `trading-core-service.yaml` | trading-core-service | Auth, signals, strategies, backtests endpoints |
| `ai-engine.yaml` | ai-engine | Predict, train, model management endpoints |

## Usage

Generate a TypeScript client for the frontend:

```bash
npx @openapitools/openapi-generator-cli generate \
  -i shared/api-specs/trading-core-service.yaml \
  -g typescript-fetch \
  -o services/web-app/src/lib/generated
```
