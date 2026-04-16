-- ============================================================================
-- Trading SaaS - PostgreSQL Schema Initialization
-- ============================================================================
-- This script creates the three isolated schemas for the platform.
-- Run once on first PostgreSQL startup (mounted as docker-entrypoint-initdb.d).
--
-- Schema ownership:
--   market_data   → market-data-service
--   trading_core  → trading-core-service
--   ai_engine     → ai-engine
--
-- Services NEVER cross schema boundaries. Cross-schema data access
-- must go through the owning service's REST API.
-- ============================================================================

-- Create schemas
CREATE SCHEMA IF NOT EXISTS market_data;
CREATE SCHEMA IF NOT EXISTS trading_core;
CREATE SCHEMA IF NOT EXISTS ai_engine;

-- Grant schema ownership to the application user
ALTER SCHEMA market_data OWNER TO trading_user;
ALTER SCHEMA trading_core OWNER TO trading_user;
ALTER SCHEMA ai_engine OWNER TO trading_user;

-- Enable UUID generation extension (used by all services)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Verify schemas were created
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'market_data') THEN
        RAISE EXCEPTION 'Schema market_data was not created';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'trading_core') THEN
        RAISE EXCEPTION 'Schema trading_core was not created';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'ai_engine') THEN
        RAISE EXCEPTION 'Schema ai_engine was not created';
    END IF;
    RAISE NOTICE 'All 3 schemas created successfully: market_data, trading_core, ai_engine';
END $$;
