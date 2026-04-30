# K8s Deployment Runbook

## Secrets Management

Secrets are **never stored in Git**. They must be created manually in the cluster with `kubectl create secret generic`.

See `base/secrets-template.yml.example` for the complete list of required keys. **Never run `kubectl apply -f` on that file.**

### Create all secrets (fresh cluster)

```bash
# PostgreSQL
PG_PASS=$(openssl rand -base64 24)
kubectl create secret generic postgres-credentials -n trading-saas \
  --from-literal=host=postgres.trading-saas.svc.cluster.local \
  --from-literal=username=trading_user \
  --from-literal=password="$PG_PASS" \
  --from-literal=market-data-db=trading_saas \
  --from-literal=trading-core-db=trading_saas \
  --from-literal=ai-engine-db=trading_saas

# Redis
REDIS_PASS=$(openssl rand -base64 24)
kubectl create secret generic redis-credentials -n trading-saas \
  --from-literal=host=redis.trading-saas.svc.cluster.local \
  --from-literal=password="$REDIS_PASS"
# Set on server after first deploy:
# kubectl exec -n trading-saas redis-0 -- redis-cli CONFIG SET requirepass "$REDIS_PASS"

# RabbitMQ
RABBIT_PASS=$(openssl rand -base64 24)
kubectl create secret generic rabbitmq-credentials -n trading-saas \
  --from-literal=host=rabbitmq.trading-saas.svc.cluster.local \
  --from-literal=username=trading_user \
  --from-literal=password="$RABBIT_PASS"
# Set on server after first deploy:
# kubectl exec -n trading-saas rabbitmq-0 -- rabbitmqctl change_password trading_user "$RABBIT_PASS"

# JWT
kubectl create secret generic jwt-secret -n trading-saas \
  --from-literal=secret=$(openssl rand -base64 48)

# NextAuth
kubectl create secret generic nextauth-secret -n trading-saas \
  --from-literal=secret=$(openssl rand -base64 32)

# Internal service-to-service token
kubectl create secret generic internal-secret -n trading-saas \
  --from-literal=secret=$(openssl rand -hex 32)
```

### Rotate a password

```bash
# Delete and recreate with new value (pods pick it up on next restart)
kubectl delete secret <secret-name> -n trading-saas
kubectl create secret generic <secret-name> -n trading-saas --from-literal=...

# Then restart affected deployments
kubectl rollout restart deployment/<name> -n trading-saas
```

### Verify no REPLACE_ME placeholders remain

```bash
for s in postgres-credentials redis-credentials rabbitmq-credentials jwt-secret nextauth-secret internal-secret; do
  echo -n "$s: "
  kubectl get secret $s -n trading-saas \
    -o go-template='{{range $k,$v := .data}}{{$k}}={{$v | base64decode}} {{end}}' \
    | grep -q REPLACE_ME && echo "❌ CORRUPTED" || echo "✅ OK"
done
```

---

## Diagnosing a Service That Won't Start

```bash
# 1. Check pod status
kubectl get pods -n trading-saas

# 2. If CreateContainerConfigError — missing secret or key
kubectl describe pod <pod-name> -n trading-saas | grep -A5 "Error\|Secret"

# 3. If CrashLoopBackOff — application error, check logs
kubectl logs <pod-name> -n trading-saas --previous

# 4. If pods hang on Init — check init containers
kubectl logs <pod-name> -n trading-saas -c <init-container-name>

# 5. Database connection failure
kubectl exec -n trading-saas postgres-0 -- psql -U trading_user -d trading_saas -c '\du'
```

---

## Secret → Deployment Mapping

| Secret | Key | Used by |
|--------|-----|---------|
| `postgres-credentials` | `host`, `username`, `password`, `market-data-db` | market-data-service |
| `postgres-credentials` | `host`, `username`, `password`, `trading-core-db` | trading-core-service |
| `postgres-credentials` | `host`, `username`, `password`, `ai-engine-db` | ai-engine |
| `redis-credentials` | `host`, `password` | market-data-service, trading-core-service |
| `rabbitmq-credentials` | `host`, `username`, `password` | market-data-service, trading-core-service, ai-engine |
| `jwt-secret` | `secret` | trading-core-service |
| `nextauth-secret` | `secret` | web-app |
| `internal-secret` | `secret` | web-app (INTERNAL_SECRET env var) |

---

## Full Rollout (after secret rotation)

```bash
for svc in market-data-service trading-core-service ai-engine web-app; do
  kubectl rollout restart deployment/$svc -n trading-saas
  kubectl rollout status deployment/$svc -n trading-saas --timeout=5m
done
```
