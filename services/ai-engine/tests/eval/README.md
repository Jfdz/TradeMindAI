# Reasoning eval corpus (C8)

JSONL corpus consumed by `scripts/run_reasoning_eval.py` and the
matching pytest suite (`tests/unit/test_reasoning_eval_harness.py`).
Every line is one case fed through `ReasoningValidator`.

## Run

```
make eval-reasonings              # from repo root, defaults
python -m scripts.run_reasoning_eval --min-correctness 0.95 \
        --max-false-positive-rate 0.05
```

Non-zero exit when either gate fires. CI wires this on PRs touching
`ReasoningValidator` or the corpus.

## Case shape

```json
{
  "id": "META-bullish-grounded-base",
  "description": "human-readable purpose of this case",
  "signal": {
    "ticker": "META",
    "signal_type": "BUY",
    "confidence": 0.62,
    "entry_price": 603.0,
    "predicted_change_pct": 4.5,
    "generated_at": "2026-05-13T12:00:00Z"
  },
  "context": {
    "schema_version": "v1.0",
    "ticker": "META",
    "generated_at": "2026-05-13T12:00:00Z",
    "price_facts": { ... fields per PriceFacts dataclass ... },
    "news": [ { "id": 1, "headline": "...", "published_at": "...", "url": "..." } ],
    "errors": []
  },
  "candidate_payload": {
    "text": "the reasoning text we expect the LLM would emit",
    "price_refs": ["sma_200"],
    "news_refs": ["https://reuters.com/x"]
  },
  "expected": {
    "validator_passes": true,
    "violation_types": []
  },
  "ground_truth_text": "optional: human reference for token-overlap scoring"
}
```

`expected.violation_types` lists the rule tags
(`ungrounded_number`, `ungrounded_news_url`,
`missing_low_confidence_label`, `forbidden_absolute_word`) the
validator should produce for this case. When `validator_passes` is
`true` the list must be empty.

## Adding a case

1. Pick a fresh `id`. Use `TICKER-purpose` so duplicates are obvious.
2. Reuse one of the canonical anchors (META at 603.0, AAPL at 173.45)
   to keep the price_facts blob compact, or define your own.
3. If the case is `validator_passes: true`, include
   `ground_truth_text` so the token-overlap metric covers it.
4. Run `make eval-reasonings` locally before opening the PR.

## Metrics

| Metric | Meaning |
| --- | --- |
| `validator_correctness` | fraction of cases where pass/fail matched `expected` |
| `hallucination_detection_rate` | of hallucinated cases, fraction the validator flagged with the right rule type(s) |
| `false_positive_rate` | of pass-expected cases, fraction the validator wrongly rejected |
| `token_overlap_mean` | Jaccard token overlap, candidate vs ground truth, averaged across passing cases |

`token_overlap_mean` is forward-looking infrastructure — both strings
are hand-written today, so it only reports our own consistency. When
real LLM outputs are captured into the corpus it becomes a coherence
proxy versus the human reference.
