# Infrastructure runbook

Operational toggles for the `trading-saas` namespace. Keep this file in sync with the manifests it describes.

## Nightly signal-inference cron (LLM cost kill switch)

The `daily-signal-inference` CronJob (`infrastructure/k8s/base/cronjobs.yml`) drives the nightly batch that calls
Anthropic for every tracked ticker. It is the dominant source of LLM spend. As of EXECUTION_PLAN C4 it ships
**suspended** (`spec.suspend: true`): no scheduled runs fire until an operator deliberately resumes it. The
dashboard "Generate Signals" button (wired in C3) is the only trigger while it is suspended.

The sibling `model-retraining` CronJob is unaffected — it does not drive LLM calls.

### Resume for an overnight test

```bash
kubectl -n trading-saas patch cronjob daily-signal-inference \
  --type=merge -p '{"spec":{"suspend":false}}'

# Wait for the next 21:00 UTC tick, OR fire one immediately:
kubectl -n trading-saas create job "manual-$(date +%s)" \
  --from=cronjob/daily-signal-inference
```

### Suspend again when done

```bash
kubectl -n trading-saas patch cronjob daily-signal-inference \
  --type=merge -p '{"spec":{"suspend":true}}'
```

A `patch` is an in-cluster override and does not update the manifest. To make the change durable, edit
`spec.suspend` in `infrastructure/k8s/base/cronjobs.yml` and let the release pipeline reconcile it — otherwise the
next deploy reverts the cluster to the manifest's value.

### Verify state

```bash
kubectl -n trading-saas get cronjob daily-signal-inference -o jsonpath='{.spec.suspend}'
# Expect: true while suspended.

kubectl -n trading-saas get cronjob daily-signal-inference -o wide
# LAST SCHEDULE stops advancing at the next tick after a suspended deploy.
```

Confirm zero new `pod/manual-…` jobs and zero new `reasoning_outcome` rows in the database after a skipped
21:00 UTC cycle.
