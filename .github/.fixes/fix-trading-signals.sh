#!/bin/bash
set -e

echo "=== Fixing duplicate trading signals ==="

# Delete duplicates
echo "Deleting duplicate signals..."
kubectl exec -it -n trading-saas deployment/postgres -- psql -U postgres -d trading_core << 'SQL'
DELETE FROM trading_core.trading_signals 
WHERE id IN (
  SELECT id FROM (
    SELECT id, ROW_NUMBER() OVER (
      PARTITION BY ticker, signal_type, timeframe, DATE(generated_at), entry_price 
      ORDER BY generated_at DESC
    ) AS rn 
    FROM trading_core.trading_signals
  ) t WHERE rn > 1
);
SQL

echo "✓ Duplicates deleted"

# Restart pods
echo "Restarting trading-core-service pods..."
kubectl rollout restart deployment/trading-core-service -n trading-saas

echo "Waiting for rollout to complete..."
kubectl rollout status deployment/trading-core-service -n trading-saas --timeout=5m

echo "✓ Rollout complete"
