# Execution Plan: Migrate TradeMindAI to Clerk

Source design: `PLAN.md` | Branch: `feature/refactor-auth-clerk`

> **STATUS: CODE COMPLETE** — 17 commits pushed, 99 Vitest + 215 JUnit pass, zero stale next-auth/JJWT references in source. READMEs updated. Two `.env.example` files need manual update (tool permissions block `.env*`). All remaining functional gates require user action (Phase 0 Clerk keys). See "Blocked on Phase 0" section below.

> **Migration correction:** PLAN.md references `V15` — that version already exists. Use **V20** for the `clerk_user_id` migration (current latest is V19).

---

## Status legend

- `[ ]` pending
- `[x]` done
- `[~]` in progress
- `[!]` blocked / needs input

---

## Phase 0 — Clerk Dashboard Setup (manual, no code)

- [ ] **0.1** Create Clerk application `TradeMindAI` at clerk.com → capture dev Frontend API URL (`https://<slug>.clerk.accounts.dev`)
- [ ] **0.2** Auth methods: Email ✓ | Phone ✗ | Username ✗ | Email verification: on | Password: on (min 8, Standard)
- [ ] **0.3a** Google — create OAuth 2.0 client in Google Cloud Console, paste Client ID + Secret into Clerk
- [ ] **0.3b** GitHub — create OAuth App at github.com/settings/developers, paste credentials into Clerk
- [ ] **0.4** Create JWT Template named `backend`: claims `aud/email/name/given_name/family_name`, RS256, 60s lifetime
- [ ] **0.5** Upload logo PNG 150×150 under Customization → Branding (for transactional emails)
- [ ] **0.8a** Capture frontend env vars: `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY`, `CLERK_SECRET_KEY`, sign-in/up URL literals
- [ ] **0.8b** Capture backend env vars: `CLERK_ISSUER_URI` (no trailing slash), `CLERK_AUDIENCE=https://api.trademindai.com`

---

## Phase 1 — Frontend: next-auth → @clerk/nextjs

### 1.1 Dependencies
- [x] `npm uninstall next-auth` && `npm install @clerk/nextjs@^6.0.0` in `services/web-app/`

### 1.2 Middleware
- [x] Rewrite `services/web-app/middleware.ts` — replace NextAuth JWT check with `clerkMiddleware` + `createRouteMatcher`; protect `/dashboard(.*)`; redirect signed-in users away from auth pages

### 1.3 Root layout
- [x] Edit `services/web-app/components/providers.tsx` — wrap with `<ClerkProvider appearance={...}>` (dark theme, cyan `#22d3ee` primary, custom element classes for card/buttons/inputs)

### 1.4 Providers cleanup
- [x] Edit `services/web-app/components/providers.tsx` — remove `<SessionProvider>` and `<SessionWatcher>`; keep `QueryClientProvider` + `ThemeHydrator`

### 1.5 Auth pages (catch-all routes)
- [x] `app/auth/login/[[...sign-in]]/page.tsx` — `<SignIn path="/auth/login" ... />`
- [x] `app/auth/register/[[...sign-up]]/page.tsx` — `<SignUp path="/auth/register" ... />`

### 1.6 Delete NextAuth artifacts
- [x] `services/web-app/lib/auth.ts`
- [x] `services/web-app/lib/auth.test.ts`
- [x] `services/web-app/components/auth/login-form.tsx`
- [x] `services/web-app/components/auth/register-form.tsx`
- [x] `services/web-app/app/api/auth/[...nextauth]/route.ts`
- [x] `services/web-app/types/next-auth.d.ts`

### 1.7 Dashboard session hooks
- [x] Replace `useSession()` / `signOut` from `next-auth/react` → `useUser()` / `useClerk()` from `@clerk/nextjs` in:
  - `services/web-app/app/dashboard/page.tsx`
  - `services/web-app/app/dashboard/settings/page.tsx`
  - `services/web-app/components/dashboard/dashboard-shell.tsx`

### 1.8 Backend proxy
- [x] Create `services/web-app/app/api/proxy/[...path]/route.ts` — calls `auth().getToken({ template: "backend" })`, injects `Authorization: Bearer <token>`, forwards to `API_BASE_URL`
- [x] Update `services/web-app/lib/api-client.ts` — routes all requests through `/api/proxy`
- [x] Route handlers (dashboard, candles, admin, stocks, enrichment) migrated to `auth()` for direct backend calls

### 1.9 Env
- [x] Update `docker-compose.yml`:
  - **Removed:** `NEXTAUTH_SECRET`, `NEXTAUTH_URL`, `NEXT_PUBLIC_API_BASE_URL`, `JWT_SECRET`
  - **Added:** `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY`, `CLERK_SECRET_KEY`, `CLERK_ISSUER_URI`, `CLERK_AUDIENCE`

---

## Phase 2 — Backend: trading-core-service as OAuth2 Resource Server

### 2.1 pom.xml
- [x] Add `spring-boot-starter-oauth2-resource-server`
- [x] Remove `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` and `jjwt.version` property

### 2.2 application.yml
- [x] Remove `trading-core.jwt.*` and `trading-core.redis.token-blacklist-prefix` blocks
- [x] Add `spring.security.oauth2.resourceserver.jwt.issuer-uri: ${CLERK_ISSUER_URI}` and `trading-core.clerk.audience: ${CLERK_AUDIENCE}`

### 2.3 SecurityConfig.java
- [x] Rewrite: OAuth2 resource server with `JwtDecoder` + `AudienceValidator` + `JitProvisioningFilter`

### 2.4 AudienceValidator.java
- [x] Created — validates `aud` claim

### 2.5 JitProvisioningFilter.java
- [x] Created — JIT provisioning: lookup/create user, set `TokenClaims` principal

### 2.6 Domain model
- [x] `User.java` — `clerkUserId` field, `fromClerk()`, `attachClerkUserId()`
- [x] `UserRepository.java` — `findByClerkUserId()`
- [x] `UserRepositoryAdapter.java` — implement `findByClerkUserId`
- [x] `UserJpaEntity.java` — `clerk_user_id` column, `passwordHash` nullable
- [x] `UserEntityMapper.java` — `clerkUserId` mapping

### 2.7 Migration V20
- [x] `V20__add_clerk_user_id_to_users.sql` — adds `clerk_user_id` column + partial unique index + drops NOT NULL on `password_hash`

### 2.8 Delete old auth
- [x] `AuthController.java`, `JwtAuthenticationFilter.java`, auth DTOs, auth use-cases, auth ports, `JwtAdapter`, `RedisRefreshTokenAdapter`, `JwtProperties`, auth exceptions, `AuthControllerTest`

### 2.9 Env
- [x] `docker-compose.yml`: removed `JWT_SECRET`, added `CLERK_ISSUER_URI`, `CLERK_AUDIENCE`

---

## Phase 3 — User Migration to Clerk

- [ ] **3.1** Export active users from Postgres to `/tmp/clerk-users.ndjson` (NDJSON: one JSON body per line, fields: `email_address[]`, `password_digest`, `password_hasher: "bcrypt"`, `first_name`, `last_name`, `external_id`)
- [x] **3.2** Create `scripts/migrate-users-to-clerk.mjs`; run against dev instance: `CLERK_SECRET_KEY=sk_test_... node scripts/migrate-users-to-clerk.mjs`
- [ ] **3.3** Review `clerk-import-failures.json`; resolve any failures (duplicate email, malformed hash, etc.)
- [ ] **3.4** Validate 3 known accounts — email+password login → `/dashboard`, `users.clerk_user_id` populated via JIT filter, original UUIDs intact (FK chains to signals/portfolios valid)

---

## Phase 4 — Cleanup, Tests, Cutover

### Tests
- [x] **4.1a** Remove `next-auth` mocks from Vitest tests; rewrote `middleware.test.ts` and `api-client.test.ts` for Clerk — 95 tests green
- [x] **4.1b** Update Playwright e2e: `@clerk/testing` added; `clerkSetup` global setup (env-guarded); `auth.setup.ts` uses `setupClerkTestingToken` + Clerk form; `auth.spec.ts` uses Clerk field names. Code-complete — requires Phase 0 Clerk keys to run.

### E2E verification gate (all must pass before merge)

| # | Scenario | Expected |
|---|---|---|
| 1 | `/dashboard` unauthenticated | Redirect to `/auth/login?redirect_url=/dashboard` |
| 2 | Email+password (migrated user) | Login inline → `/dashboard`, `users.clerk_user_id` set |
| 3 | New sign-up flow | Verify email → `/dashboard`, `password_hash IS NULL` in DB |
| 4 | "Sign in with Google" | OAuth → `/dashboard` |
| 5 | "Sign in with GitHub" | OAuth → `/dashboard` |
| 6 | `UserButton` → "Manage account" | In-app Clerk modal |
| 7 | "Forgot password?" | Reset email → login with new password works |
| 8 | `/api/proxy/signals` from dashboard | Proxy injects bearer, backend 200 |
| 9 | Token TTL 60s expires | SDK auto-refreshes transparently |
| 10 | `signOut()` | Session cleared, redirect to `/`, dashboard redirects to `/auth/login` |
| 11 | Tampered bearer | Backend 401 (JWKS signature mismatch) |
| 12 | Wrong `aud` claim | Backend 401 (`AudienceValidator`) |
| 13 | Vitest `middleware.test.ts` | Green with `clerkMiddleware` mock |
| 14 | Playwright happy path | Green with Clerk testing helpers |

### Final gates
- [!] **4.2** CORS domain mismatch — `application.yml` dev default is `http://localhost:3000` ✓; `application-prod.yml` + k8s configmap use `https://trademind.es`. Plan originally said `https://app.trademindai.com` — **which is the real prod frontend domain?** Update configmap to whichever is correct before merge.
- [x] **4.3** `npm run test` (web-app) + `./mvnw verify` (trading-core) — all green (95 Vitest + 215 JUnit, 0 failures)
- [ ] **4.4** `npm run e2e` — Playwright suite passes
- [ ] **4.5** Merge PR, watch release-orchestrator CI, smoke test on staging with canary account

---

## Rollout order

1. **Phase 0** — Clerk dashboard setup; no code, zero risk
2. **Phase 1 + Phase 2** together on `feature/refactor-auth-clerk` — big-bang, validate locally first
3. **Phase 3** against dev Clerk instance — validate 3 known accounts
4. **Phase 4** — all tests green, PR merge
5. **Phase 3** against prod Clerk instance during maintenance window → redeploy → canary smoke test

---

---

## Blocked on Phase 0 — What the user must do before merge

Code is complete. Every remaining gate depends on live Clerk keys.

| Action | Who | Blocks |
|---|---|---|
| Create Clerk app + configure JWT template `"backend"` (0.1–0.4) | **User** | Everything |
| Capture `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY`, `CLERK_SECRET_KEY`, `CLERK_ISSUER_URI` | **User** | Local run, e2e, staging |
| Set `.env.local` in `services/web-app/` with the above keys | **User** | `npm run e2e` |
| Add `E2E_EMAIL`, `E2E_PASSWORD` (test Clerk account) to `.env.local` | **User** | `auth.spec.ts`, `auth.setup.ts` |
| Run `scripts/migrate-users-to-clerk.mjs` against dev DB (3.1, 3.3, 3.4) | **User** | Canary smoke test |
| Confirm real prod frontend domain (4.2 CORS mismatch) | **User** | Configmap update |
| Review CORS + run `npm run e2e` with live keys (4.4) | **User/Claude** | 4.5 merge |
| Update `.env.example` (root) — replace `JWT_SECRET`, `NEXTAUTH_SECRET`, `NEXTAUTH_URL`, `NEXT_PUBLIC_API_BASE_URL` with Clerk equivalents | **User** | Developer onboarding |
| Update `services/web-app/.env.example` — replace `NEXTAUTH_SECRET`, `NEXTAUTH_URL`, `NEXT_PUBLIC_API_BASE_URL` with Clerk equivalents (keys above) | **User** | Developer onboarding |

---

## Open decisions (deferred post-MVP)

| # | Topic | Notes |
|---|---|---|
| A | `isAdmin` in frontend | Short-term: `/api/me/admin` route handler querying DB. Long-term: `user.publicMetadata.isAdmin` via Clerk Backend API |
| B | Custom Clerk domain (`clerk.trademindai.com`) | DNS CNAME, changes `issuer-uri` — deferred to prod Phase 0.7 |
| C | MFA | Enable in Clerk dashboard (TOTP/SMS/backup codes); zero code changes |
| D | Webhooks (`user.created/updated/deleted`) | Post-MVP; JIT provisioning covers the gap for now |
| E | `<UserButton />` component | Replaces sign-out in `dashboard-shell.tsx`; consider for richer UX |
