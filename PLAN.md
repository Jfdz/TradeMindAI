# Plan: Migrar TradeMindAI a Clerk como único proveedor de identidad

## Contexto

La página de login (`/auth/login`) tiene un formulario completo con email/password (que sí funciona vía NextAuth credentials → Spring Boot) y dos botones sociales **Google** y **GitHub** que no hacen nada (son `<button type="button">` sin handler). El usuario pidió enchufar un IdP real para que esas opciones funcionen y dar más alternativas de login/registro tradicionales.

Decisiones tomadas con el usuario:

1. **Full Clerk**: Clerk es la única fuente de identidad — email/password, Google, GitHub y futuros providers viven en Clerk. Spring Boot pasa a ser **OAuth2 Resource Server** que valida JWT firmados por Clerk (RS256, JWKS). Se elimina NextAuth + endpoints `/api/v1/auth/{login,register,refresh,logout}`. Usuarios existentes se migran a Clerk con sus bcrypt hashes (Clerk los soporta nativamente vía Backend API con `password_hasher: "bcrypt"`).
2. **UI inline (no redirect off-domain)**: la gran ventaja de Clerk sobre Auth0. Usamos los componentes `<SignIn />` y `<SignUp />` de `@clerk/nextjs` **embebidos dentro de nuestro `AuthShell`** en `/auth/login` y `/auth/register`. El usuario nunca sale de `app.trademindai.com`. Brandeamos la UI vía la prop `appearance` con tokens Tailwind (cyan, bg-0, font-display) para que matchee el resto del producto.
3. **Setup desde cero**: el plan incluye creación de instances Clerk (dev y prod), configuración de auth methods, social connections (Google + GitHub con OAuth clients propios), JWT template para el backend, branding y captura de envs.
4. **URLs**: **mantenemos `/auth/login` y `/auth/register`** (Clerk no reserva ningún namespace de URL — el conflicto con `/auth/*` era específico del SDK Auth0). Convertimos cada uno en optional catch-all route (`[[...sign-in]]`) porque los componentes de Clerk navegan internamente entre sub-pasos (verify-email, MFA, etc.).
5. **Idioma**: todo el frontend (UI, labels, status, mensajes, prompts) en **inglés**. Sin español. (Regla global del producto.)

Stack actual relevante:
- `services/web-app` — Next.js 15.5 (App Router) + TypeScript + Tailwind 3.4 + Radix UI + shadcn-style. Usa `next-auth@^4.24.11` con CredentialsProvider.
- `services/trading-core-service` — Spring Boot 3.5 / Java 21, port 8082. JJWT 0.12.6 (HS256, 15-min access + 7-day refresh en cookie), BCrypt(12), Redis blacklist.
- DB: Postgres, schema `trading_core.users` (id UUID, email UNIQUE, password_hash, first_name, last_name, timezone, active, subscription_id).

SDKs target (vigentes a 2026-05-10):
- `@clerk/nextjs` v6.x (App Router native, hooks `useUser`/`useAuth`, helper `auth()` server-side, `clerkMiddleware()` para middleware.ts).
- `spring-boot-starter-oauth2-resource-server` (gestionado por Spring Boot 3.5 BOM, sin pin explícito). Drop JJWT entero.
- `svix` (Node) si decidimos enchufar webhooks `user.created` (no en MVP — JIT provisioning cubre el caso).

---

## Fase 0 — Setup en Clerk dashboard (manual, sin código)

### 0.1 Applications (instances)
- En clerk.com → New Application → nombre `TradeMindAI`.
- Esto crea automáticamente una instance `development`. Más tarde se promueve a `production` con dominio custom.
- En dev, Clerk asigna un Frontend API URL tipo `https://<random-slug>.clerk.accounts.dev`. Lo capturamos.

### 0.2 Authentication methods
- User & Authentication → Email, Phone, Username:
  - Enable **Email address** (required, used as primary identifier).
  - Disable **Phone number** (no lo usamos).
  - Disable **Username** (login solo por email).
- Email verification: **enabled** (Clerk manda el código). Soft enforcement — no bloqueamos backend en MVP.
- Password: enabled, min length 8, complexity "Standard".

### 0.3 Social connections
- User & Authentication → Social Connections:
  - **Google**: enable. Usar **OAuth client propio de TradeMindAI** (no Clerk shared dev keys en prod):
    1. Google Cloud Console → Credentials → OAuth 2.0 Client ID (Web application).
    2. Authorized redirect URI: `https://<frontend-api>.clerk.accounts.dev/v1/oauth_callback` (Clerk muestra la URL exacta en el dashboard).
    3. Pegar Client ID + Secret en Clerk.
  - **GitHub**: enable. github.com/settings/developers → New OAuth App con la callback URL que Clerk muestra.
- Ambas connections quedan disponibles automáticamente en `<SignIn />` y `<SignUp />` — sin código.

### 0.4 JWT Templates (para el backend)
- JWT Templates → New template → nombre `backend`.
- Claims:
  ```json
  {
    "aud": "https://api.trademindai.com",
    "email": "{{user.primary_email_address}}",
    "name": "{{user.full_name}}",
    "given_name": "{{user.first_name}}",
    "family_name": "{{user.last_name}}"
  }
  ```
- Token lifetime: 60s (default — short-lived; el SDK lo renueva automáticamente cuando el frontend lo pide).
- Signing algorithm: **RS256** (default, public via JWKS).
- Esto es lo que el frontend pasará al backend en cada call vía `getToken({ template: "backend" })`.

### 0.5 Branding (mayormente en código)
La mayoría del branding lo hacemos vía la prop `appearance` en los componentes (Fase 1.7). En el dashboard solo:
- Customization → Branding → logo (PNG 150x150).
- Esto se usa en emails transaccionales (verify email, password reset).

### 0.6 Webhooks (opcional, diferido)
- Si más adelante queremos sincronización proactiva (en vez de JIT), añadir endpoint `/api/webhooks/clerk` y suscribir a `user.created`, `user.updated`, `user.deleted`. Para MVP **no se hace** — el `JitProvisioningFilter` en el backend crea filas DB al primer request autenticado.

### 0.7 Production instance (cuando llegue)
- Clone production from dev. Configurar dominio custom (`clerk.trademindai.com`) → instrucciones de DNS (CNAME). Esto cambia el `issuer-uri` y las claves; envs separados.

### 0.8 Capturar envs (por entorno)

| Clerk dashboard | Frontend env |
|---|---|
| API Keys → Publishable key (`pk_test_...`) | `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` |
| API Keys → Secret key (`sk_test_...`) | `CLERK_SECRET_KEY` |
| literal | `NEXT_PUBLIC_CLERK_SIGN_IN_URL=/auth/login` |
| literal | `NEXT_PUBLIC_CLERK_SIGN_UP_URL=/auth/register` |
| literal | `NEXT_PUBLIC_CLERK_SIGN_IN_FALLBACK_REDIRECT_URL=/dashboard` |
| literal | `NEXT_PUBLIC_CLERK_SIGN_UP_FALLBACK_REDIRECT_URL=/dashboard` |

| Backend env | Valor |
|---|---|
| `CLERK_ISSUER_URI` | `https://<frontend-api>.clerk.accounts.dev` (dev) / `https://clerk.trademindai.com` (prod). **Sin trailing slash** (Clerk no lo añade en `iss`). |
| `CLERK_AUDIENCE` | `https://api.trademindai.com` (matchea el JWT template `aud`) |

---

## Fase 1 — Frontend: swap a `@clerk/nextjs`

### 1.1 Dependencias (en `services/web-app/`)
```
npm uninstall next-auth
npm install @clerk/nextjs@^6.0.0
```

### 1.2 Reescribir: `services/web-app/middleware.ts`
```ts
import { clerkMiddleware, createRouteMatcher } from "@clerk/nextjs/server";

const isProtected = createRouteMatcher(["/dashboard(.*)"]);
const isAuthPage = createRouteMatcher(["/auth/login(.*)", "/auth/register(.*)"]);

export default clerkMiddleware((auth, req) => {
  if (isProtected(req)) auth.protect();
  // Si ya hay sesión y entra a las páginas de auth, redirigir a /dashboard
  if (isAuthPage(req) && auth().userId) {
    const url = req.nextUrl.clone();
    url.pathname = "/dashboard";
    return Response.redirect(url);
  }
});

export const config = {
  matcher: [
    "/((?!_next/static|_next/image|favicon.ico|sitemap.xml|robots.txt|.*\\..*).*)",
    "/(api|trpc)(.*)",
  ],
};
```
`auth.protect()` redirige automáticamente a `NEXT_PUBLIC_CLERK_SIGN_IN_URL` (`/auth/login`) preservando el `returnBackUrl`. No hace falta lógica manual.

### 1.3 Update: `services/web-app/app/layout.tsx`
Envolver con `<ClerkProvider>`. Pasamos el `appearance` global aquí para que tema oscuro + cyan se herede a todos los componentes de Clerk (User Profile, Sign In, Sign Up):
```tsx
import { ClerkProvider } from "@clerk/nextjs";
import { dark } from "@clerk/themes";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <ClerkProvider
      appearance={{
        baseTheme: dark,
        variables: {
          colorPrimary: "#22d3ee",
          colorBackground: "#0c1018",
          colorInputBackground: "#0f1419",
          colorText: "#e5e7eb",
          colorTextSecondary: "#9ca3af",
          colorTextOnPrimaryBackground: "#0c1018",
          borderRadius: "12px",
          fontFamily: "var(--font-sans)",
        },
        elements: {
          card: "border border-border shadow-glow bg-transparent",
          headerTitle: "font-display text-white",
          headerSubtitle: "text-text-2",
          socialButtonsBlockButton: "border border-border hover:bg-white/5",
          formButtonPrimary: "bg-cyan text-bg-0 hover:bg-cyan/90 shadow-glow rounded-full font-semibold",
          dividerLine: "bg-border",
          formFieldInput: "bg-bg-2 border-border focus:border-cyan",
          footerActionLink: "text-cyan hover:text-white",
        },
      }}
    >
      <html lang="en">
        <body>{children}</body>
      </html>
    </ClerkProvider>
  );
}
```

### 1.4 Limpiar: `services/web-app/components/providers.tsx`
Quitar `<SessionProvider>` y `<SessionWatcher>` de NextAuth. Dejar solo `QueryClientProvider` + `ThemeHydrator`. Clerk no necesita provider en `providers.tsx` (ya está en `layout.tsx`).

### 1.5 Convertir las páginas de auth en optional catch-all routes
Clerk con `routing="path"` necesita capturar sub-pasos (`/auth/login/factor-one`, `/auth/login/verify-email-address-link`, etc.) en la misma ruta.

Mover:
- `app/auth/login/page.tsx` → `app/auth/login/[[...sign-in]]/page.tsx`
- `app/auth/register/page.tsx` → `app/auth/register/[[...sign-up]]/page.tsx`

Borrar el contenido anterior (form + react-hook-form + zod) y reemplazar por:

`app/auth/login/[[...sign-in]]/page.tsx`:
```tsx
import { SignIn } from "@clerk/nextjs";
import { AuthShell } from "@/components/auth/auth-shell";

export default function LoginPage() {
  return (
    <AuthShell
      mode="login"
      eyebrow="Secure access"
      title="Sign in to TradeMindAI"
      description="Continue with email or your social account to open the dashboard, signals, and portfolio tools."
    >
      <SignIn
        path="/auth/login"
        routing="path"
        signUpUrl="/auth/register"
        fallbackRedirectUrl="/dashboard"
        appearance={{
          elements: {
            rootBox: "w-full",
            card: "shadow-none border-none bg-transparent p-0",
            header: "hidden",  // ya tenemos heading en AuthShell
            footer: "bg-transparent",
          },
        }}
      />
    </AuthShell>
  );
}
```

`app/auth/register/[[...sign-up]]/page.tsx`:
```tsx
import { SignUp } from "@clerk/nextjs";
import { AuthShell } from "@/components/auth/auth-shell";

export default function RegisterPage() {
  return (
    <AuthShell
      mode="register"
      eyebrow="Open an account"
      title="Create your trading profile"
      description="Sign up with email or a social account and move directly into the dashboard."
    >
      <SignUp
        path="/auth/register"
        routing="path"
        signInUrl="/auth/login"
        fallbackRedirectUrl="/dashboard"
        appearance={{
          elements: {
            rootBox: "w-full",
            card: "shadow-none border-none bg-transparent p-0",
            header: "hidden",
            footer: "bg-transparent",
          },
        }}
      />
    </AuthShell>
  );
}
```
La estética la heredan del `<ClerkProvider appearance>` global; los overrides locales solo neutralizan el card/header/shadow que ya da `AuthShell`.

### 1.6 Borrar
- `services/web-app/lib/auth.ts`
- `services/web-app/lib/auth.test.ts`
- `services/web-app/components/auth/login-form.tsx`
- `services/web-app/components/auth/register-form.tsx`
- `services/web-app/app/api/auth/[...nextauth]/route.ts`
- `services/web-app/types/next-auth.d.ts`
- `SessionWatcher` component si vive en archivo aparte

`AuthShell` se mantiene tal cual — los hrefs de los tabs (`/auth/login` y `/auth/register`) ya apuntan a los slugs correctos. No tocar.

### 1.7 Reemplazar `useSession()` en dashboard
Archivos: `app/dashboard/page.tsx`, `app/dashboard/settings/page.tsx`, `components/dashboard/dashboard-shell.tsx`.
```ts
// antes (NextAuth)
import { useSession, signOut } from "next-auth/react";
const { data: session } = useSession();
// session.user.email, session.user.name, session.isAdmin

// después (Clerk)
import { useUser, useClerk } from "@clerk/nextjs";
const { user, isLoaded } = useUser();
const { signOut } = useClerk();
// user.primaryEmailAddress?.emailAddress, user.fullName, user.imageUrl, user.id
```
- "Sign out" button → `onClick={() => signOut(() => router.push("/"))}` o `<SignOutButton />` (componente listo) o `<UserButton />` (avatar + menú).
- `isAdmin`: corto plazo, route handler server-side `/api/me/admin` que consulta DB (`users.is_admin` o derivado de subscription), fetcheado con React Query. Largo plazo: añadir claim custom al JWT template `backend` o usar Clerk **publicMetadata** (`user.publicMetadata.isAdmin`) actualizado vía Backend API o webhook.

### 1.8 Pasar el access token al backend — pattern proxy
Mismo pattern que el plan Auth0: el token vive server-side y el browser solo habla con un proxy de Next.js que inyecta el bearer.

Nuevo `app/api/proxy/[...path]/route.ts`:
```ts
import { auth } from "@clerk/nextjs/server";
import { NextResponse, type NextRequest } from "next/server";

const BACKEND = process.env.API_BASE_URL ?? "http://localhost:8082";

async function forward(req: NextRequest, ctx: { params: { path: string[] } }) {
  const { getToken } = auth();
  const token = await getToken({ template: "backend" });
  if (!token) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  const url = `${BACKEND}/api/v1/${ctx.params.path.join("/")}${req.nextUrl.search}`;
  const headers = new Headers(req.headers);
  headers.set("Authorization", `Bearer ${token}`);
  headers.delete("host");
  headers.delete("cookie");

  const res = await fetch(url, {
    method: req.method,
    headers,
    body: ["GET", "HEAD"].includes(req.method) ? undefined : await req.text(),
    cache: "no-store",
  });
  return new NextResponse(res.body, {
    status: res.status,
    headers: { "content-type": res.headers.get("content-type") ?? "application/json" },
  });
}
export { forward as GET, forward as POST, forward as PATCH, forward as PUT, forward as DELETE };
```
Importante: `getToken({ template: "backend" })` devuelve un JWT con el `aud=https://api.trademindai.com` que el resource server valida.

Update `lib/api-client.ts` (igual que en plan Auth0):
```ts
const API_BASE_URL = "/api/proxy";  // browser → Next.js → Spring (con token)
async function requestJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const proxied = path.replace(/^\/api\/v1/, "");  // "/api/v1/signals" → "/signals"
  const response = await fetch(`${API_BASE_URL}${proxied}`, {
    ...options,
    cache: "no-store",
    headers: {
      Accept: "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });
  // resto del manejo de errores: sin cambios
}
```

### 1.9 Env frontend (`.env.local`, `.env.example`, docker-compose, k8s)
**Quitar**: `NEXTAUTH_SECRET`, `NEXTAUTH_URL`, `ADMIN_EMAILS`.

**Añadir**:
```
NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY=pk_test_...
CLERK_SECRET_KEY=sk_test_...
NEXT_PUBLIC_CLERK_SIGN_IN_URL=/auth/login
NEXT_PUBLIC_CLERK_SIGN_UP_URL=/auth/register
NEXT_PUBLIC_CLERK_SIGN_IN_FALLBACK_REDIRECT_URL=/dashboard
NEXT_PUBLIC_CLERK_SIGN_UP_FALLBACK_REDIRECT_URL=/dashboard
API_BASE_URL=http://localhost:8082   # server-side only, usado por el proxy
```
Eliminar `NEXT_PUBLIC_API_BASE_URL` si nada más lo usa.

---

## Fase 2 — Backend `trading-core-service` como OAuth2 Resource Server

### 2.1 `pom.xml`
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```
Eliminar las tres dependencias `io.jsonwebtoken:jjwt-*` y la propiedad `jjwt.version`.

### 2.2 `application.yml`
Eliminar el bloque `trading-core.jwt` y `trading-core.redis.token-blacklist-prefix` enteros. Añadir:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${CLERK_ISSUER_URI}
          # JWKS se autodescubre desde issuer-uri/.well-known/openid-configuration
          # (Clerk expone tanto OIDC discovery como JWKS estándar)

trading-core:
  clerk:
    audience: ${CLERK_AUDIENCE}
```
Eliminar `JWT_*` de `application-dev.yml` / `application-prod.yml`. Añadir `CLERK_ISSUER_URI`, `CLERK_AUDIENCE` a deployment manifests.

### 2.3 Reescribir `config/SecurityConfig.java`
- Quitar `JwtAuthenticationFilter` del constructor y del filter chain.
- Quitar el bean `passwordEncoder()` (BCryptPasswordEncoder ya no se usa).
- Quitar `.requestMatchers("/api/v1/auth/**").permitAll()`.
- Añadir `.oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthConverter())))`.
- Añadir `JitProvisioningFilter` después del `BearerTokenAuthenticationFilter`.

Sketch del filter chain:
```java
http
  .csrf(AbstractHttpConfigurer::disable)
  .cors(Customizer.withDefaults())
  .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
  .authorizeHttpRequests(auth -> auth
      .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus", "/actuator/metrics/**").permitAll()
      .requestMatchers("/api/v1/subscriptions/plans").permitAll()
      .requestMatchers("/api/v1/backtests/symbols/*/available").permitAll()
      .requestMatchers("/api/v1/ingestion/**").hasRole("ADMIN")
      .requestMatchers("/api/v1/models/**").hasRole("ADMIN")
      .anyRequest().authenticated())
  .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthConverter())))
  .addFilterAfter(jitProvisioningFilter, BearerTokenAuthenticationFilter.class)
  .addFilterAfter(rateLimitFilter, JitProvisioningFilter.class);
```

Beans:
```java
@Bean
JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
                      @Value("${trading-core.clerk.audience}") String audience) {
  NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
  decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
      JwtValidators.createDefaultWithIssuer(issuer),
      new AudienceValidator(audience)));
  return decoder;
}

@Bean
Converter<Jwt, AbstractAuthenticationToken> jwtAuthConverter() {
  JwtAuthenticationConverter c = new JwtAuthenticationConverter();
  // Clerk no emite "permissions" o "scope" en el template default; autoridades vacías por ahora.
  // Los roles se derivan en el JIT filter desde la fila users (campo subscription/plan).
  c.setJwtGrantedAuthoritiesConverter(jwt -> List.of());
  c.setPrincipalClaimName("sub");  // "user_2abc..." en Clerk
  return c;
}
```

### 2.4 Nuevo `config/AudienceValidator.java`
```java
package com.tradingsaas.tradingcore.config;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;

public final class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final String audience;
    public AudienceValidator(String audience) { this.audience = audience; }
    @Override public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt.getAudience().contains(audience)) return OAuth2TokenValidatorResult.success();
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "Required audience missing", null));
    }
}
```

### 2.5 Nuevo `adapter/in/web/JitProvisioningFilter.java`
First authenticated request: busca user por `clerk_user_id`; si no existe, busca por email (legacy migrado) y le adjunta el id; si tampoco, crea row nueva. Re-envuelve la authentication para que los controllers reciban el `TokenClaims` que ya conocen (Option B — cero cambios en controllers).
```java
@Component
public class JitProvisioningFilter extends OncePerRequestFilter {
  private final UserRepository userRepository;

  public JitProvisioningFilter(UserRepository userRepository) { this.userRepository = userRepository; }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a instanceof JwtAuthenticationToken jwt) {
      String clerkUserId = jwt.getName();  // ej. "user_2abc..."
      String email = jwt.getToken().getClaimAsString("email");
      String firstName = jwt.getToken().getClaimAsString("given_name");
      String lastName = jwt.getToken().getClaimAsString("family_name");

      User user = userRepository.findByClerkUserId(clerkUserId)
          .or(() -> email == null ? Optional.empty() : userRepository.findByEmail(email)
              .map(u -> { u.attachClerkUserId(clerkUserId); return userRepository.save(u); }))
          .orElseGet(() -> userRepository.save(User.fromClerk(clerkUserId, email, firstName, lastName)));

      String plan = user.getSubscription() != null ? user.getSubscription().getPlan().name() : "FREE";
      var enriched = new JwtAuthenticationToken(jwt.getToken(), jwt.getAuthorities(),
          new TokenClaims(user.getId(), user.getEmail(), plan));
      SecurityContextHolder.getContext().setAuthentication(enriched);
    }
    chain.doFilter(req, res);
  }
}
```

### 2.6 Cambios al modelo de dominio
- `domain/model/User.java`: añadir campo `String clerkUserId` (nullable), factory `User.fromClerk(String clerkUserId, String email, String firstName, String lastName)`, mutator `attachClerkUserId(String)`.
- `domain/port/out/UserRepository.java`: añadir `Optional<User> findByClerkUserId(String clerkUserId);`
- `adapter/out/persistence/UserRepositoryAdapter.java`: implementar.
- `adapter/out/persistence/entity/UserJpaEntity.java`: añadir `@Column(name = "clerk_user_id", unique = true, length = 64) private String clerkUserId;`. Cambiar `passwordHash` a `nullable = true`.
- `adapter/out/persistence/mapper/UserJpaMapper.java`: tolerar `passwordHash` null.

### 2.7 Flyway `V15__add_clerk_user_id_to_users.sql`
```sql
ALTER TABLE trading_core.users ADD COLUMN clerk_user_id VARCHAR(64);
CREATE UNIQUE INDEX idx_users_clerk_user_id ON trading_core.users(clerk_user_id) WHERE clerk_user_id IS NOT NULL;
ALTER TABLE trading_core.users ALTER COLUMN password_hash DROP NOT NULL;
```

### 2.8 Borrar
- `adapter/in/web/AuthController.java`
- `adapter/in/web/JwtAuthenticationFilter.java`
- DTOs en `adapter/in/web/dto/`: `LoginRequest`, `LoginResponse`, `RefreshResponse`, `RegisterRequest`, `RegisterResponse`
- `application/usecase/`: `LoginService`, `RefreshTokenService`, `LogoutService`, `RegisterUserService` y los matching `domain/port/in/*UseCase`
- `domain/port/out/JwtTokenPort.java`, `TokenBlacklistPort.java`
- `adapter/out/security/JwtAdapter.java`
- `adapter/out/cache/RedisRefreshTokenAdapter.java`
- `config/JwtProperties.java`
- `domain/exception/InvalidCredentialsException.java`, `TokenBlacklistedException.java`
- Tests asociados

### 2.9 Env backend
**Quitar** de compose, k8s, scripts dev: `JWT_SECRET`, `JWT_ACCESS_TOKEN_EXPIRY`, `JWT_REFRESH_TOKEN_EXPIRY`.
**Añadir**: `CLERK_ISSUER_URI=https://<frontend-api>.clerk.accounts.dev`, `CLERK_AUDIENCE=https://api.trademindai.com`.
**Mantener**: `APP_CORS_ALLOWED_ORIGINS=http://localhost:3000,https://app.trademindai.com`. Con el proxy de Next.js, el backend nunca recibe llamadas cross-origin desde el browser; se puede tightenear más.

---

## Fase 3 — Migración de usuarios existentes a Clerk

A diferencia de Auth0 (que tiene job batch dedicado), Clerk se migra **vía Backend API one-by-one con un script**. Soporta bcrypt nativamente con `password_hasher: "bcrypt"`.

### 3.1 Export desde Postgres
```sql
COPY (
  SELECT json_build_object(
    'email_address', ARRAY[email],
    'password_digest', password_hash,
    'password_hasher', 'bcrypt',
    'first_name', first_name,
    'last_name', last_name,
    'external_id', id::text,
    'skip_password_checks', false,
    'skip_password_requirement', false
  ) AS line
  FROM trading_core.users
  WHERE active = true
) TO '/tmp/clerk-users.ndjson';
```
Cada línea es el cuerpo exacto del POST a Clerk Backend API. El hash `$2a$12$...` de `BCryptPasswordEncoder(12)` es compatible tal cual (Clerk acepta `$2a`, `$2b`, `$2y`).

### 3.2 Script de import (Node + Clerk Backend API)
Crear `scripts/migrate-users-to-clerk.mjs` (no en `services/web-app`, puede ir en `scripts/` raíz):
```js
import fs from "node:fs";
import readline from "node:readline";

const SECRET = process.env.CLERK_SECRET_KEY;       // sk_test_... o sk_live_...
const INPUT = process.argv[2] ?? "/tmp/clerk-users.ndjson";

const rl = readline.createInterface({ input: fs.createReadStream(INPUT) });
let ok = 0, fail = 0;
const failures = [];

for await (const line of rl) {
  const body = JSON.parse(line);
  try {
    const res = await fetch("https://api.clerk.com/v1/users", {
      method: "POST",
      headers: { "Authorization": `Bearer ${SECRET}`, "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      failures.push({ email: body.email_address[0], status: res.status, error: await res.text() });
      fail++;
    } else {
      ok++;
    }
  } catch (e) {
    failures.push({ email: body.email_address[0], error: String(e) });
    fail++;
  }
  // Rate limit: Clerk Backend API permite ~20 req/sec en free tier
  await new Promise(r => setTimeout(r, 60));
}
fs.writeFileSync("clerk-import-failures.json", JSON.stringify(failures, null, 2));
console.log(`Imported ${ok}, failed ${fail}`);
```
Correr: `CLERK_SECRET_KEY=sk_test_... node scripts/migrate-users-to-clerk.mjs`.

### 3.3 Backfill de `clerk_user_id` en DB
**Lazy (recomendado)**: el `JitProvisioningFilter` matchea por email al primer request autenticado y escribe `clerk_user_id`. Cero scripts adicionales. La unique index hace el path idempotente bajo concurrencia.

**Eager opcional** (post-migration completion): tras correr el script, `clerk-import-failures.json` lista los failures; si querés backfill inmediato del éxito, hacer una segunda pasada que lea `GET https://api.clerk.com/v1/users?external_id=<uuid>` por cada user y `UPDATE trading_core.users SET clerk_user_id = ? WHERE id = ?`.

### 3.4 Verificación
- Tres cuentas conocidas. A una le hacemos password reset desde Clerk dashboard (valida flujo de recuperación). Las otras dos mantienen credenciales originales.
- Login vía `/auth/login` con credenciales originales → redirect a `/dashboard`.
- Inspeccionar fila `users` → `clerk_user_id` populated, `id` UUID intacto (FKs a `subscriptions`, `portfolios`, `trading_signals` siguen válidas).
- `GET /api/proxy/users/me` → 200, mismo UUID que antes.

---

## Fase 4 — Cleanup, CORS, test plan, cutover

### 4.1 Code cleanup
- `npm prune` tras quitar next-auth.
- Borrar mocks de `next-auth/react` en tests vitest.
- Reescribir `middleware.test.ts` mockeando `@clerk/nextjs/server`'s `clerkMiddleware`.
- Playwright: usar **storageState** + el Testing Token de Clerk. Clerk emite `__client_uat` y `__session` cookies; en `setup project` se loguea una cuenta de test vía la API y se persiste el storage para los specs. Alternativa: usar Clerk's `@clerk/testing` helpers oficiales (`setupClerkTestingToken`).

### 4.2 CORS
Backend sigue necesitando `http://localhost:3000` y `https://app.trademindai.com` en `APP_CORS_ALLOWED_ORIGINS`. Si se usa el proxy (default del plan), se puede tightenear más en prod.

### 4.3 Test plan E2E

| # | Escenario | Expectativa |
|---|---|---|
| 1 | Visit `/dashboard` sin sesión | `clerkMiddleware`'s `auth.protect()` redirige a `/auth/login` con `?redirect_url=/dashboard` |
| 2 | "Continue with email" (en el `<SignIn>`) + creds de usuario migrado | Login inline, redirect a `/dashboard`, `users.clerk_user_id` populated |
| 3 | "Sign up" link → `<SignUp />` + nuevo email/password | Verify email code → `/dashboard`, fila nueva con `password_hash IS NULL` |
| 4 | Botón "Continue with Google" en `<SignIn />` | Google consent → `/dashboard` |
| 5 | Botón "Continue with GitHub" en `<SignIn />` | GitHub OAuth → `/dashboard` |
| 6 | `<UserButton />` en dashboard → "Manage account" | Modal in-app con email, password, social accounts gestionados por Clerk |
| 7 | "Forgot password?" en `<SignIn />` | Email reset, login con nueva pass funciona |
| 8 | `/api/proxy/signals` desde dashboard | Proxy llama `getToken({ template: "backend" })`, inyecta bearer, backend devuelve 200 |
| 9 | Token expira (TTL 60s) | SDK refresca automáticamente al siguiente `getToken()` |
| 10 | `signOut()` button | Sesión Clerk limpia, redirect a `/`, `/dashboard` redirige a `/auth/login` |
| 11 | Bearer manipulado | Backend 401 (signature failure vs JWKS de Clerk) |
| 12 | Token con `aud` incorrecta | Backend 401 (`AudienceValidator`) |
| 13 | Vitest `middleware.test.ts` | Pasa con mock de `clerkMiddleware` |
| 14 | Playwright happy path login → signals list | Pasa con `storageState` + Clerk testing helpers |

### 4.4 Orden de rollout sugerido
1. **Phase 0** (dashboard Clerk + Google/GitHub OAuth apps) — no toca código, no rompe nada.
2. **Phase 2 backend + Phase 1 frontend juntos** en branch `claude/tradingview-mcp-analysis-ukMtu`. Big-bang sin shims. Antes: validar en local con cuentas test.
3. **Phase 3** user import en instance dev primero. Validar con cuentas test.
4. Repetir Phase 3 en instance prod durante ventana de mantenimiento corta. Redeploy frontend+backend.
5. Smoke test con cuenta canary antes de abrir tráfico real.
6. Phase 4 cleanup commit cuando todo verde.

---

## Archivos críticos a crear/modificar

Los archivos donde vive el peso del cambio:

- `services/web-app/middleware.ts` — **rewrite**, `clerkMiddleware` + protected routes
- `services/web-app/app/layout.tsx` — **edit**, envolver con `<ClerkProvider>` + appearance global
- `services/web-app/app/auth/login/[[...sign-in]]/page.tsx` — **nuevo** (mover del `app/auth/login/page.tsx`), renderiza `<SignIn />` dentro de `AuthShell`
- `services/web-app/app/auth/register/[[...sign-up]]/page.tsx` — **nuevo**, renderiza `<SignUp />`
- `services/web-app/app/api/proxy/[...path]/route.ts` — **nuevo**, inyecta bearer vía `auth().getToken({ template: "backend" })`
- `services/trading-core-service/src/main/java/com/tradingsaas/tradingcore/config/SecurityConfig.java` — **rewrite**, resource server
- `services/trading-core-service/src/main/java/com/tradingsaas/tradingcore/adapter/in/web/JitProvisioningFilter.java` — **nuevo**, JIT user creation con principal `TokenClaims`
- `services/trading-core-service/src/main/resources/db/migration/V15__add_clerk_user_id_to_users.sql` — **nuevo**, schema change
- `scripts/migrate-users-to-clerk.mjs` — **nuevo**, batch import script

---

## Decisiones que quedan abiertas (validar durante implementación)

1. **`isAdmin` en frontend**: corto plazo route handler `/api/me/admin` que consulta DB. Largo plazo, dos opciones — (a) custom claim en el JWT template `backend` poblado desde `publicMetadata`, (b) endpoint que devuelve `user.publicMetadata` (mantenido vía Backend API). No en este plan.
2. **Email verification**: Clerk envía el código por defecto. Soft enforcement; no bloqueamos backend en MVP.
3. **Custom domain Clerk** (`clerk.trademindai.com` en lugar de `<slug>.clerk.accounts.dev`): nice-to-have para branding y cookies samesite. Diferido a Phase 0.7 prod setup.
4. **MFA**: trivial activar en Clerk (User & Authentication → Multi-factor → TOTP/SMS/backup codes). Sin cambios de código. Decidir si on-by-default en prod.
5. **Webhooks**: si más adelante queremos sincronización proactiva (en vez de JIT), enchufar `/api/webhooks/clerk` con `user.created/updated/deleted` y verificación svix. No en MVP.
6. **Rate limiting**: el `RateLimitFilter` actual del backend se mantiene. Verificar que sigue corriendo después del `JitProvisioningFilter`.
7. **`UserButton`**: Clerk ofrece un componente `<UserButton />` (avatar + dropdown con "Manage account", "Sign out") que reemplaza nuestro sign-out actual. Considerar usarlo en `dashboard-shell.tsx` para UX más rica.

---

## Verificación end-to-end (cómo probar tras implementar)

1. **Setup local**:
   ```
   cd services/web-app && npm install
   cp .env.example .env.local  # rellenar CLERK_*
   npm run dev
   cd ../trading-core-service && ./mvnw spring-boot:run
   ```
2. **Smoke test manual** (en `http://localhost:3000`):
   - Visitar `/dashboard` → redirige a `/auth/login?redirect_url=/dashboard`
   - El `<SignIn />` renderiza inline en la card de `AuthShell` con tema cyan/dark
   - Click "Sign in with Google" → Google consent → vuelta a `/dashboard`
   - Click "Sign in with GitHub" → GitHub OAuth → vuelta a `/dashboard`
   - Email + password (cuenta migrada) → `/dashboard`
   - `<UserButton />` (o sign-out manual) → vuelve a `/`
   - Crear cuenta vía `/auth/register` → verify email code → `/dashboard`, fila nueva en `users`
3. **Backend directo**: en DevTools Network capturar un token con `aud=https://api.trademindai.com`, luego:
   ```
   curl -H "Authorization: Bearer <token>" http://localhost:8082/api/v1/users/me
   ```
   → 200.
4. **Tests**: `npm run test` en web-app + `./mvnw test` en trading-core-service. Playwright `npm run e2e` con Clerk testing helpers configurados.
5. **Bulk import dry run**: ejecutar Phase 3.2 contra instance dev, validar 3 cuentas conocidas hacen login.