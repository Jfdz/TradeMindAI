# Plan: Add K8s ConfigMap / Secret Env Vars

**Date:** 2026-04-29
**Files to modify:** `infrastructure/k8s/base/configmaps.yml`, `infrastructure/k8s/base/secrets-template.yml`, `infrastructure/k8s/base/web-app.yml`, `infrastructure/k8s/base/trading-core-service.yml`

---

## Context

User wants to add three environment variables to the Kubernetes cluster:
- `ADMIN_EMAILS=jfdzn28@icloud.com`
- `INTERNAL_SECRET=<generated-value>`
- `AI_ENGINE_SERVICE_URL=http://ai-engine:8000`

---

## What Each Variable Means

### INTERNAL_SECRET
**What it is:** A shared secret string used as the value of the `X-Internal-Secret` HTTP header. This header authenticates service-to-service calls so that internal endpoints (e.g. market-data `/api/v1/ingestion`) cannot be called from the public internet.

**What value to use:** Generate a strong random string on your Ubuntu machine:
```bash
openssl rand -hex 32
```
Copy that output — that is your `INTERNAL_SECRET`.

**Security:** It is sensitive → must go in a K8s **Secret**, not a ConfigMap.

---

### ADMIN_EMAILS
**What it is:** A comma-separated list of email addresses that should receive admin privileges in the app.

**Sensitivity:** Not sensitive (it's just an email address) → goes in a **ConfigMap**.

---

### AI_ENGINE_SERVICE_URL
**What it is:** The internal cluster URL for the AI engine service.

**Important finding:** Based on the codebase, `AI_ENGINE_SERVICE_URL` is read by **trading-core-service** (via `application.yml:109` → `AiEngineProperties.java`), **not** by web-app. The web-app has no code that reads this variable.

**Decision:** Add it to `trading-core-service-config` ConfigMap (where it belongs), and also to `web-app-config` if you need it there for future use. The plan below adds it to both to be safe.

---

## Changes Required

### 1. `infrastructure/k8s/base/configmaps.yml`

**In `web-app-config`** — add:
```yaml
  ADMIN_EMAILS: "jfdzn28@icloud.com"
  AI_ENGINE_SERVICE_URL: "http://ai-engine.trading-saas.svc.cluster.local:8000"
```

**In `trading-core-service-config`** — add:
```yaml
  AI_ENGINE_SERVICE_URL: "http://ai-engine.trading-saas.svc.cluster.local:8000"
```
(Note: full cluster DNS name is safer than `http://ai-engine:8000` which relies on same-namespace DNS shorthand — both work but full name is more explicit.)

---

### 2. `infrastructure/k8s/base/secrets-template.yml`

Add a new secret block for `INTERNAL_SECRET`:
```yaml
---
apiVersion: v1
kind: Secret
metadata:
  name: internal-secret
  namespace: trading-saas
type: Opaque
data:
  secret: BASE64_ENCODED_VALUE_HERE
```

> The actual live secret must be applied via `kubectl` with your real value, **not** committed to git.

---

### 3. `infrastructure/k8s/base/web-app.yml`

Mount the new env vars into the web-app container:
```yaml
- name: ADMIN_EMAILS
  valueFrom:
    configMapKeyRef:
      name: web-app-config
      key: ADMIN_EMAILS
- name: AI_ENGINE_SERVICE_URL
  valueFrom:
    configMapKeyRef:
      name: web-app-config
      key: AI_ENGINE_SERVICE_URL
- name: INTERNAL_SECRET
  valueFrom:
    secretKeyRef:
      name: internal-secret
      key: secret
```

---

### 4. `infrastructure/k8s/base/trading-core-service.yml`

Mount `AI_ENGINE_SERVICE_URL` into the trading-core container:
```yaml
- name: AI_ENGINE_SERVICE_URL
  valueFrom:
    configMapKeyRef:
      name: trading-core-service-config
      key: AI_ENGINE_SERVICE_URL
```

---

## Apply Order on the Ubuntu Machine

After committing the manifest changes, apply them with:

```bash
# 1. Apply the ConfigMap changes
kubectl apply -f infrastructure/k8s/base/configmaps.yml

# 2. Create the INTERNAL_SECRET secret with the REAL value (NOT from the template)
#    First generate the value:
SECRET=$(openssl rand -hex 32)
echo "Your secret: $SECRET"

#    Then create it in K8s:
kubectl create secret generic internal-secret \
  --from-literal=secret="$SECRET" \
  -n trading-saas \
  --dry-run=client -o yaml | kubectl apply -f -

# 3. Apply the updated deployment manifests
kubectl apply -f infrastructure/k8s/base/web-app.yml
kubectl apply -f infrastructure/k8s/base/trading-core-service.yml

# 4. Restart pods to pick up new env vars
kubectl rollout restart deployment/web-app deployment/trading-core-service -n trading-saas

# 5. Verify env vars are present
kubectl exec -n trading-saas deployment/web-app -- printenv | grep -E "ADMIN|INTERNAL|AI_ENGINE"
kubectl exec -n trading-saas deployment/trading-core-service -- printenv | grep "AI_ENGINE"
```

---

## Verification

- `kubectl exec ... -- printenv` confirms vars are visible inside each pod
- No pod crash-loops after restart (check `kubectl get pods -n trading-saas`)
- The `INTERNAL_SECRET` value is **never committed to git** — only the template placeholder is in source control