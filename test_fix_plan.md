# Plan: Fix `test_training_flow_completes` (UndefinedTable on `ai_engine.training_runs`)

## Context

The CI test `test_training_flow_completes` is failing because the training status endpoint was changed to read from the database for cross-replica consistency:

- `services/ai-engine/src/ai_engine/adapters/in_/training.py:73-81` — `GET /api/v1/models/train/{run_id}` now calls `load_training_run(run_id)` instead of reading from the in-memory `_runs` dict.
- `services/ai-engine/src/ai_engine/adapters/out/db_adapter.py:70-99` — `load_training_run` runs `SELECT … FROM ai_engine.training_runs WHERE id = …`.

The CI test Postgres has no `ai_engine` schema (no migrations run), so the query fails with `psycopg2.errors.UndefinedTable`.

The test fixture already monkeypatches `upsert_training_run` and `upsert_model_version`, but **`load_training_run` is not mocked**, so the test polls a real DB that doesn't exist.

## Fix

Add a single monkeypatch in `test_training_flow_completes` that maps `load_training_run` to read from the in-memory `training_router._runs` dict (which `_run_training` already updates with status / finished_at / version_id / metrics).

### File to modify
`services/ai-engine/tests/integration/test_api_flow.py` (around line 117, alongside the existing db_adapter monkeypatches)

### Patch (logical change)

Add inside `test_training_flow_completes`, right after the existing `db_adapter.upsert_*` monkeypatches:

```python
def _mock_load_training_run(run_id):
    run = training_router._runs.get(run_id)
    if run is None:
        return None
    return {
        "run_id": run_id,
        "model_version_id": run.get("version_id"),
        "status": run.get("status"),
        "hyperparameters": run.get("params", {}),
        "metrics": run.get("metrics", {}),
        "started_at": run.get("started_at"),
        "finished_at": run.get("finished_at"),
        "created_at": run.get("started_at"),
    }

monkeypatch.setattr(db_adapter, "load_training_run", _mock_load_training_run)
```

### Why this shape
- `db_adapter.load_training_run` returns a dict with keys `run_id, model_version_id, status, hyperparameters, metrics, started_at, finished_at, created_at`.
- The `_runs` dict (populated by `_run_training`) holds `status, started_at, params, finished_at, version_id, metrics` — the mock maps these onto the DB-shaped dict.
- The test only asserts `status == "COMPLETED"` and `"finished_at" in response.json()`, so the mapping is sufficient.

### Why monkeypatch `db_adapter.load_training_run` (not `training_router.load_training_run`)
The endpoint uses a function-local import: `from ai_engine.adapters.out.db_adapter import load_training_run`. Function-local `from X import Y` resolves to the current attribute on the module each call, so patching the source module is correct.

## Verification

1. Push the fix to `main` to trigger `ci-ai-engine.yml`.
2. The `Run tests` step should show 16/16 passing.
3. Locally (if pytest is available):
   ```bash
   cd services/ai-engine && pytest tests/integration/test_api_flow.py -v
   ```
   `test_training_flow_completes` should pass with COMPLETED status.

## Out of scope
- No code changes to `training.py`, `db_adapter.py`, or `main.py` — those are correct as written.
- No new env vars or CI workflow changes.

---

## Previous plan (kept for context, archived)

The earlier plan to test the training endpoint manually on the live ai-engine pod has been completed: training succeeded end-to-end and the model now persists to the database. The remaining issue is the unit test mock gap above.