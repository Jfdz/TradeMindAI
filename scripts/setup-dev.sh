#!/usr/bin/env bash
# ============================================================================
# Trading SaaS - Developer Setup Script
# ============================================================================
# Run this once after cloning the repo to set up your local dev environment.
# Usage: bash scripts/setup-dev.sh
# ============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "============================================"
echo " Trading SaaS - Developer Setup"
echo "============================================"

# --- Check prerequisites ---
echo ""
echo "[1/6] Checking prerequisites..."

check_cmd() {
    if ! command -v "$1" &>/dev/null; then
        echo "  ERROR: '$1' not found. Please install it first."
        exit 1
    fi
    echo "  OK: $1 $(${2:-$1 --version 2>&1 | head -1})"
}

check_cmd docker
check_cmd "docker compose" "docker compose version"
check_cmd java "java -version 2>&1 | head -1"
check_cmd mvn "mvn -version 2>&1 | head -1"
check_cmd python3 "python3 --version"
check_cmd node "node --version"
check_cmd npm "npm --version"

# --- Copy .env ---
echo ""
echo "[2/6] Setting up environment variables..."
if [ ! -f .env ]; then
    cp .env.example .env
    echo "  Created .env from .env.example"
    echo "  NOTE: Review and update .env with your actual credentials."
else
    echo "  .env already exists — skipping."
fi

# --- Start infrastructure ---
echo ""
echo "[3/6] Starting infrastructure (PostgreSQL, Redis, RabbitMQ)..."
docker compose up -d postgres redis rabbitmq

echo "  Waiting for PostgreSQL to be healthy..."
until docker compose exec postgres pg_isready -U trading_user -d trading_saas 2>/dev/null; do
    sleep 1
done
echo "  PostgreSQL is ready."

# --- Install Python deps ---
echo ""
echo "[4/6] Installing Python dependencies for ai-engine..."
if [ -f services/ai-engine/requirements-dev.txt ]; then
    pip install -q -r services/ai-engine/requirements-dev.txt
    echo "  Python deps installed."
else
    echo "  No requirements-dev.txt yet — skipping (will be added in E1-F06-PBI-01)."
fi

# --- Install Node deps ---
echo ""
echo "[5/6] Installing Node.js dependencies for web-app..."
if [ -f services/web-app/package.json ]; then
    cd services/web-app && npm install --silent && cd "$REPO_ROOT"
    echo "  Node deps installed."
else
    echo "  No package.json yet — skipping (will be added in E4-F14-PBI-01)."
fi

# --- Seed data ---
echo ""
echo "[6/6] Seeding sample data..."
bash scripts/seed-data.sh

echo ""
echo "============================================"
echo " Setup complete!"
echo "============================================"
echo ""
echo " Next steps:"
echo "   make up           — start all services"
echo "   make logs         — tail all logs"
echo "   make test         — run all tests"
echo "   make db-shell     — open psql shell"
echo ""
