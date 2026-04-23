#!/usr/bin/env bash
# ============================================================================
# Trading SaaS - Database Seed Script
# ============================================================================
# Inserts sample symbols and a demo user into the database for local dev.
# Requires infrastructure to be running (make infra-up).
# Usage: bash scripts/seed-data.sh
# ============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Load env vars if .env exists
if [ -f "$REPO_ROOT/.env" ]; then
    set -a
    # shellcheck source=/dev/null
    source "$REPO_ROOT/.env"
    set +a
fi

POSTGRES_USER="${POSTGRES_USER:-trading_user}"
POSTGRES_DB="${POSTGRES_DB:-trading_saas}"

echo "Seeding database..."

# Check infra is up
if ! docker compose -f "$REPO_ROOT/docker-compose.yml" exec postgres pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" &>/dev/null; then
    echo "ERROR: PostgreSQL is not running. Run 'make infra-up' first."
    exit 1
fi

# Run seed SQL
docker compose -f "$REPO_ROOT/docker-compose.yml" exec -T postgres psql \
    -U "$POSTGRES_USER" \
    -d "$POSTGRES_DB" <<'SQL'

-- ============================================================================
-- Sample symbols (inserted into market_data schema after Flyway migrations run)
-- ============================================================================
-- NOTE: This seed runs only after the Flyway migrations have created the
-- market_data.symbols table (E1-F02-PBI-04). If the table doesn't exist yet,
-- this is a no-op.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'market_data' AND table_name = 'symbols'
    ) THEN
        INSERT INTO market_data.symbols (ticker, name, exchange, is_active)
        VALUES
            ('AAPL',  'Apple Inc.',               'NASDAQ', true),
            ('MSFT',  'Microsoft Corporation',     'NASDAQ', true),
            ('GOOGL', 'Alphabet Inc.',             'NASDAQ', true),
            ('TSLA',  'Tesla, Inc.',               'NASDAQ', true),
            ('AMZN',  'Amazon.com, Inc.',          'NASDAQ', true),
            ('NVDA',  'NVIDIA Corporation',        'NASDAQ', true),
            ('META',  'Meta Platforms, Inc.',      'NASDAQ', true),
            ('AMD',   'Advanced Micro Devices',    'NASDAQ', true)
        ON CONFLICT (ticker) DO NOTHING;

        RAISE NOTICE 'Seeded % symbols', (SELECT COUNT(*) FROM market_data.symbols);
    ELSE
        RAISE NOTICE 'market_data.symbols table does not exist yet — run Flyway migrations first';
    END IF;
END $$;

-- ============================================================================
-- Demo user (inserted after trading_core schema migrations run)
-- ============================================================================
-- Password: demo1234 (BCrypt hash below, cost factor 12)

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'trading_core' AND table_name = 'users'
    ) THEN
        INSERT INTO trading_core.users (email, password_hash, first_name, last_name, is_active)
        VALUES (
            'demo@tradingsaas.dev',
            '$2a$12$LHkBLDoajVROsPlHCj.EyOKxwNZN1ElfD3MtM6hHK8Z5LiCo3BNWG',
            'Demo',
            'User',
            true
        )
        ON CONFLICT (email) DO NOTHING;

        RAISE NOTICE 'Demo user created: demo@tradingsaas.dev / demo1234';
    ELSE
        RAISE NOTICE 'trading_core.users table does not exist yet — run Flyway migrations first';
    END IF;
END $$;

SQL

echo "Seed complete."
