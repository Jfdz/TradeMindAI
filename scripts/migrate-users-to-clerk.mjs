#!/usr/bin/env node
/**
 * Phase 3 — Migrate TradeMindAI users from Postgres to Clerk.
 *
 * Usage:
 *   CLERK_SECRET_KEY=sk_test_... \
 *   DATABASE_URL=postgres://user:pass@localhost:5432/trading_saas \
 *   node scripts/migrate-users-to-clerk.mjs [--dry-run]
 *
 * Outputs:
 *   clerk-import-results.json  — per-user outcome (created | skipped | failed)
 *
 * Requirements:
 *   node >= 18 (fetch built-in)
 *   npm install pg  (run once: npm install --no-save pg)
 */

import { writeFile } from "node:fs/promises";
import pg from "pg";

const { Client } = pg;

const CLERK_SECRET_KEY = process.env.CLERK_SECRET_KEY;
const DATABASE_URL = process.env.DATABASE_URL;
const DRY_RUN = process.argv.includes("--dry-run");
const DELAY_MS = 250; // stay well within Clerk's 20 req/s rate limit

if (!CLERK_SECRET_KEY) {
  console.error("CLERK_SECRET_KEY is required");
  process.exit(1);
}
if (!DATABASE_URL) {
  console.error("DATABASE_URL is required");
  process.exit(1);
}

// --- Clerk API helper ---

async function clerkPost(path, body) {
  const res = await fetch(`https://api.clerk.com/v1${path}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${CLERK_SECRET_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  const json = await res.json().catch(() => ({}));
  return { status: res.status, body: json };
}

async function findClerkUserByEmail(email) {
  const res = await fetch(
    `https://api.clerk.com/v1/users?email_address=${encodeURIComponent(email)}&limit=1`,
    { headers: { Authorization: `Bearer ${CLERK_SECRET_KEY}` } },
  );
  const json = await res.json().catch(() => []);
  return Array.isArray(json) && json.length > 0 ? json[0] : null;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// --- Main ---

async function main() {
  const db = new Client({ connectionString: DATABASE_URL });
  await db.connect();

  const { rows } = await db.query(`
    SELECT
      id,
      email,
      first_name,
      last_name,
      password_hash,
      active
    FROM trading_core.users
    WHERE active = true
    ORDER BY created_at
  `);

  console.log(`Found ${rows.length} active users to migrate.`);
  if (DRY_RUN) console.log("DRY RUN — no Clerk API calls will be made.");

  const results = [];

  for (const user of rows) {
    const { id, email, first_name, last_name, password_hash } = user;
    await sleep(DELAY_MS);

    if (DRY_RUN) {
      console.log(`DRY   ${email}`);
      results.push({ email, pgId: id, outcome: "dry-run" });
      continue;
    }

    // Skip if already in Clerk
    const existing = await findClerkUserByEmail(email);
    if (existing) {
      console.log(`SKIP  ${email} — already in Clerk (${existing.id})`);
      results.push({ email, pgId: id, outcome: "skipped", clerkId: existing.id });
      continue;
    }

    const payload = {
      email_address: [email],
      first_name: first_name ?? "",
      last_name: last_name ?? "",
      external_id: id,
      skip_password_checks: true,
      skip_password_requirement: false,
    };

    // Include bcrypt hash only when present (some rows may be NULL after migration)
    if (password_hash) {
      payload.password_digest = password_hash;
      payload.password_hasher = "bcrypt";
    } else {
      // No password — Clerk will require the user to set one via "Forgot password"
      payload.skip_password_requirement = true;
    }

    const { status, body } = await clerkPost("/users", payload);

    if (status === 200 || status === 201) {
      const clerkId = body.id;
      console.log(`OK    ${email} → ${clerkId}`);
      results.push({ email, pgId: id, outcome: "created", clerkId });
    } else {
      const errMsg = body.errors?.[0]?.long_message ?? JSON.stringify(body);
      console.error(`FAIL  ${email} — ${status}: ${errMsg}`);
      results.push({ email, pgId: id, outcome: "failed", error: errMsg });
    }
  }

  await db.end();

  const outPath = "clerk-import-results.json";
  await writeFile(outPath, JSON.stringify(results, null, 2));

  const created = results.filter((r) => r.outcome === "created").length;
  const skipped = results.filter((r) => r.outcome === "skipped").length;
  const failed = results.filter((r) => r.outcome === "failed").length;

  console.log(`\nDone. created=${created} skipped=${skipped} failed=${failed}`);
  console.log(`Results written to ${outPath}`);

  if (failed > 0) {
    console.error(`\n${failed} users failed — review ${outPath} and re-run for failed rows.`);
    process.exit(1);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
