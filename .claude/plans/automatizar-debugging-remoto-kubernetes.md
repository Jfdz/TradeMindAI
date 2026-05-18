# Plan: Automatización de Debugging Remoto sin SSH Manual
## Basado en Infraestructura CI/CD Existente

---

## Contexto: Infraestructura de GitHub Actions (Ya Existe)

**TradeMindAI cuenta con un sistema CI/CD muy sofisticado:**

| Componente | Status | Detalles |
|---|---|---|
| **CI Wrappers (4 servicios)** | ✅ | `ci-{market-data,trading-core,ai-engine,web-app}.yml` |
| **Reusable CI Bodies** | ✅ | `ci-*-reusable.yml` (patrón dual: trigger + lógica) |
| **Release Orchestrator** | ✅ | `push main` → 4 CIs paralelos → staging → prod auto |
| **Deployment Staging** | ✅ | Manual (`workflow_dispatch`) con smoke tests |
| **Deployment Prod** | ✅ | Auto-deploy en main + rollback automático en falla |
| **Security Scanning** | ✅ | Trivy (imágenes) + OWASP (deps) + SonarQube |
| **Self-Hosted Runners** | ✅ | K8s deployments |
| **Image Registry** | ✅ | GHCR con tagging inteligente |

**Workflows Totales: 20 archivos**
- 4 CI wrappers + 4 reusable CI bodies
- 4 reusable deploy bodies + 2 deploy wrappers
- 4 security scans + utilities (opencode, cleanup, seed-data, load-tests)

---

## Visión: Claude Complementa (No Reemplaza) GitHub Actions

```
GitHub Actions (EXISTENTE - No cambiar)
├─ PR checks → tests + security → merge
├─ Push main → release-orchestrator
│  ├─ 4 CIs paralelos (push_image: true)
│  ├─ Deploy staging automático
│  └─ Deploy prod automático (con rollback)
├─ Manual: workflow_dispatch para staging/prod
└─ Scheduled: security scans, cleanup

NUEVO: Claude Automation (COMPLEMENTARIO)
├─ Monitorea GA workflows (entiende release-orchestrator)
├─ Debugging automático (Direct K8s read-only)
├─ Hotfixes en emergencias (Direct K8s con validaciones)
└─ Feature testing local (Local build → staging, sin GA)
```

**El plan implementa 3 estrategias:**

1. **GA Monitoring** — Claude entiende tus workflows y monitorea automáticamente
2. **Direct K8s** — Para debugging/hotfixes sin esperar GA (1-3 min)
3. **Local Build** — Para feature branches sin GA (5 min)

---

## Estrategia 1: GitHub Actions Monitoring

### Flujo Actual (que Claude debe entender):

```
User: "Mergear PR #305 a main"
  ↓
[PR merge + push to main]
  ↓
release-orchestrator.yml dispara:
  ├─ ci-market-data-service-reusable.yml (push_image: true)
  ├─ ci-trading-core-service-reusable.yml (PostgreSQL, Redis services)
  ├─ ci-ai-engine-reusable.yml (PostgreSQL service)
  └─ ci-web-app-reusable.yml (E2E con Playwright)
  ↓
Cada CI:
  ├─ Job: build-test (mvn/pip/npm)
  ├─ Job: docker-build-push (Buildx → GHCR)
  └─ Job: security-scan (Trivy gating)
  ↓
Si todos pasan: deploy-staging-reusable.yml automático
  ├─ Verifica 4 imágenes existen en GHCR
  ├─ Deploy rolling (timeout: 5 min cada servicio)
  └─ Smoke tests (curl /health endpoints)
  ↓
Si staging OK: deploy-production-reusable.yml automático
  ├─ Deploy rolling (timeout: 10 min)
  ├─ Prod smoke tests
  ├─ Auto-rollback si falla
  └─ Git tag si éxito: prod-YYYYMMDD-HHMM-<SHA>
```

### Lo que Claude Debe Hacer:

✅ **Monitorear GA Jobs:**
```
User: "Pusheé cambios a main"
Claude automáticamente:
  1. Detecta que release-orchestrator disparó
  2. Monitorea los 4 CIs en paralelo
  3. Reporta progreso: "Testing trading-core (45s)..."
  4. Si algo falla: "❌ web-app E2E falló. Checkout logs"
  5. Si todo pasa: "✅ Prod deployment initiated. Monitoring..."
```

✅ **Analizar Fallos:**
```
CI falla en "npm run build"
Claude:
  1. Accede a GA logs
  2. Lee el error: "next/image import missing"
  3. Propone: "Falta actualizar next.config.js en web-app"
  4. Ofrece: "¿Hago el fix y repush a main?"
```

✅ **Proponer Re-runs:**
```
Fallo transitorio (network timeout)
Claude:
  1. Identifica que es transitorio
  2. Dispara: gh run rerun <id> --failed
  3. Monitorea de nuevo
```

---

## Estrategia 2: Direct K8s (Debugging + Hotfixes)

**Cuándo NO usar GA:**

```
"¿Por qué ai-engine crasheó?"                    → Debugging (read-only)
"Prod está down"                                  → Hotfix (con validaciones)
"Testear cambio sin esperar GA"                   → Local build
"Verifica conectividad ai-engine → trading-core"  → Investigation
```

### Debugging (Read-Only Automático)

```
User: "¿Por qué ai-engine está en CrashLoopBackOff?"

Claude automáticamente (sin confirmación):
  1. kubectl get pods -l app=ai-engine -o wide
  2. kubectl logs <pod> --tail=200
  3. kubectl describe pod <pod>
  4. kubectl get events --sort-by='.lastTimestamp'
  5. kubectl top pod <pod>
  ↓
Claude analiza:
  "Encontré: OOMKilled (memory: 18/16GB usado)
   Pod requests 16GB, kubelet killed after 120s.
   Propuesta: aumentar requests a 24GB"
```

**Commands ejecutados automáticamente (sin prompt):**
- `kubectl get`
- `kubectl logs`
- `kubectl describe`
- `kubectl top`
- `kubectl events`

---

### Hotfix en Producción (con Validaciones)

```
User: "Prod está down, rollback a versión anterior"

Claude automáticamente:
  1. ✅ Verifica imagen v1.2.1 existe en GHCR
  2. ✅ Dry-run: kubectl set image ... --dry-run=client
  3. kubectl set image deployment/trading-core \
     trading-core=ghcr.io/.../trading-core:v1.2.1
  4. kubectl rollout status deployment/trading-core (timeout: 5 min)
  5. ✅ Health check: curl /api/v1/signals → 200 OK
  6. Si falla: auto-rollback a versión anterior
  7. Slack alert: "@oncall Trading-core rolled back to v1.2.1"
  ↓
Result: Prod up en 2-3 min vs 15+ min esperando GA
```

**Validaciones Obligatorias:**
- [ ] Imagen existe en GHCR
- [ ] Dry-run sin errores
- [ ] Health check post-deploy
- [ ] Auto-rollback habilitado
- [ ] Slack notification

---

### Local Build (Feature Testing)

```
User: "Testear cambio en trading-core en staging (sin GA)"

Claude:
  1. ./mvnw clean verify (local)
     → BUILD SUCCESS, 127 tests passed
  2. docker build -t ghcr.io/jfdz/trading-core:dev-abc123 .
     → Image pushed
  3. kubectl set image deployment/trading-core \
     trading-core=ghcr.io/.../trading-core:dev-abc123 \
     -n staging
  4. kubectl rollout status (timeout: 3 min)
  5. ✅ curl https://staging.trademind.es/health → 200
  ↓
Feature live en staging en 5 min (vs 15 con GA)
Nota: Esto es solo staging. Main requiere GA.
```

---

## Configuración: `.claude/settings.json`

```json
{
  "deployment": {
    "default_strategy": "auto-detect",
    "strategies": {
      "github-actions": {
        "monitor_workflows": true,
        "workflows_to_track": [
          "release-orchestrator.yml",
          "deploy-staging-reusable.yml",
          "deploy-production-reusable.yml"
        ],
        "wait_for_completion": true,
        "max_wait_minutes": 20,
        "analyze_failures": true,
        "suggest_reruns": true
      },
      "direct-k8s": {
        "read_only_auto": [
          "get",
          "logs",
          "describe",
          "top",
          "events"
        ],
        "hotfix_validations": [
          "image-exists-in-registry",
          "dry-run-first",
          "health-check-post-deploy",
          "auto-rollback-enabled",
          "slack-notification"
        ],
        "hotfix_timeout_minutes": 5,
        "auto_rollback_on_failure": true
      },
      "local-build": {
        "allow_for": [
          "feature-branch-testing"
        ],
        "skip_ga": true,
        "target_env": "staging",
        "only_staging": true
      }
    }
  },
  "permissions": {
    "github": {
      "allow": [
        "pr-merge",
        "workflow-run",
        "view-logs"
      ],
      "require_confirm": [
        "force-push",
        "delete-branch"
      ]
    },
    "k8s": {
      "readonly_auto_execute": [
        "get",
        "logs",
        "describe",
        "top",
        "events"
      ],
      "hotfix_require_confirm": [
        "set-image",
        "patch",
        "scale"
      ],
      "never_allow": [
        "delete",
        "apply",
        "edit-secret"
      ]
    }
  },
  "remote": {
    "k8s_config": "./.claude/k8s-contexts.json",
    "timeout_ms": 5000,
    "cache_ttl_ms": {
      "k8s_queries": 30000,
      "logs": 60000,
      "events": 10000
    },
    "log_path": "./.claude/debug-logs/",
    "audit_log": "./.claude/debug-logs/audit.jsonl"
  }
}
```

---

## Árbol de Decisión Automático

```
Claude detecta evento:
├─ User: "Mergear PR #305"
│  └─→ GA Monitoring
│     ├─ Espera release-orchestrator
│     ├─ Monitorea 4 CIs
│     ├─ Si pasa: propone deploy prod
│     └─ Tiempo: 15-20 min
│
├─ User: "¿Por qué ai-engine crasheó?"
│  └─→ Direct K8s (read-only)
│     ├─ kubectl get/logs/describe (auto)
│     ├─ Analiza sin cambios
│     ├─ Propone fix
│     └─ Tiempo: 1-2 min
│
├─ User: "Prod está down, arregla"
│  └─→ Direct K8s (hotfix)
│     ├─ Validaciones automáticas
│     ├─ Rollback a v1.2.1
│     ├─ Health checks
│     └─ Tiempo: 3 min
│
└─ User: "Testear feature en staging"
   └─→ Local Build
      ├─ mvn verify local
      ├─ docker build + push
      ├─ kubectl set-image (staging)
      └─ Tiempo: 5 min
```

---

## Flujos Conversacionales Reales

### Flujo 1: PR Normal → GA Monitoring

```
User: "Mergear PR #305 a main cuando sea"

Claude:
  1. "✅ PR #305 aprobado. Mergeando..."
  2. gh pr merge 305 --merge --auto-delete-branch
  3. "Push a main detectado. release-orchestrator disparó..."
  4. 🔄 Monitorea GA jobs:
     - ci-market-data-service: ✅ (1m30s)
     - ci-trading-core-service: ✅ (2m15s)
     - ci-ai-engine: ✅ (2m45s)
     - ci-web-app: ✅ (1m50s)
  5. "✅ Todos los tests pasaron"
  6. "🚀 Staging deploy iniciado..."
  7. 🔄 Monitorea deploy-staging-reusable:
     - Rolling deploy en progreso (3/4 ready)
     - Smoke tests ejecutándose
  8. "✅ Staging deployado correctamente"
  9. "🚨 Production deploy automático en 30 segundos (o cancelar)"
  10. "✅ Production deployment complete"

Tiempo total: 18 min (automated monitoring, no manual steps)
```

---

### Flujo 2: Prod Down → Hotfix en 3 min

```
User: "trading-core no responde, arréglalo"

Claude automáticamente:
  1. kubectl get deployment trading-core -o json
     → Veo: 1/3 pods running (2 CrashLoopBackOff)
  2. kubectl logs <pod> --tail=50
     → java.lang.NullPointerException en ReasoningNewsSnapshot
  3. "🚨 CRITICAL: trading-core crasheando por NullPointerException
     Última versión (v1.3.0) tiene bug. v1.2.1 está estable.
     
     Opciones:
     - A) Rollback a v1.2.1 (2 min, garantizado)
     - B) Esperar PR fix + GA (15 min, nuevo código)"
  
  User: "Rollback a v1.2.1"
  
  4. 🔄 Validaciones automáticas:
     ✅ Image v1.2.1 exists in GHCR
     ✅ Dry-run: kubectl set image ... --dry-run=client
  5. kubectl set image deployment/trading-core \
     trading-core=ghcr.io/.../trading-core:v1.2.1
  6. 🔄 Monitorea rollout: 0% → 33% → 100%
  7. ✅ Health check: GET /api/v1/signals → 200 OK
  8. Slack: "@oncall Trading-core rolled back to v1.2.1"
  
  Tiempo total: 3 min (auto-validated)
  Pod count: 1/3 → 3/3 (healthy)
```

---

### Flujo 3: Investigation (Sin Cambios)

```
User: "¿Por qué backtests tardan 5 min?"

Claude automáticamente (read-only, sin confirmación):
  1. kubectl logs -f deployment/ai-engine | grep backtest
     → Veo: "PyTorch compiling CUDA kernels (first run)"
  2. kubectl top pod <ai-engine-pod>
     → CPU: 100%, Memory: 18/24GB
  3. kubectl describe pod <ai-engine-pod>
     → Veo: `memory requests: 24GB` (correcto)
  4. "🔍 Root cause: First PyTorch run compiles CUDA kernels
     → Mejora: Precompile kernels en Docker image build
     
     Propuesta:
     - Agregar warmup step en Dockerfile
     - Compilar kernels en build time (no en runtime)
     - Resultado esperado: Primer backtest < 1 min"
  
  [No se hace cambio alguno en este flujo]
```

---

### Flujo 4: Feature Testing Local (Staging)

```
User: "Testear cambio en trading-core en staging"

Claude:
  1. "📦 Compilando trading-core localmente..."
  2. ./mvnw clean verify
     → BUILD SUCCESS (tests: 111/111 passed)
  3. "🐳 Buildando Docker image..."
  4. docker build -t ghcr.io/jfdz/trading-core:dev-abc123 .
     → Image pushed (1.2GB)
  5. "🚀 Deployando a staging..."
  6. kubectl set image deployment/trading-core \
     trading-core=ghcr.io/.../trading-core:dev-abc123 \
     -n staging
  7. kubectl rollout status deployment/trading-core -n staging
     → Rolling update: 1/3 ready... 2/3... 3/3
  8. ✅ Health check: curl https://staging.tradingsaas/health
  9. "✅ Feature deployada en staging (5 min)
     Url: https://staging.tradingsaas/dashboard
     
     Nota: Esto es staging only, sin pasar GA.
     Para mergear a main, requiere PR + GA checks."
```

---

## Fases de Implementación (5 Semanas)

### Fase 1: GA Integration (Semana 1)
**Entregable:** Claude monitorea release-orchestrator y otros workflows

- [ ] Leer y entender todos los `.github/workflows/*.yml`
- [ ] Implementar `monitorGitHubWorkflow()` agent
- [ ] Integración con `gh workflow run`
- [ ] Monitoreo automático de jobs en paralelo
- [ ] Análisis de fallos (logs parsing)
- [ ] Sugerencia de re-runs

**Test:** "Pusheé a main" → Claude monitorea automáticamente

---

### Fase 2: K8s Read-Only (Semana 2)
**Entregable:** Claude ejecuta kubectl read-only automáticamente

- [ ] Crear `.claude/k8s-contexts.json`
- [ ] Implementar `queryK8s()` (get/logs/describe/top/events)
- [ ] Auto-execute sin confirmación
- [ ] Caché de resultados (30s)
- [ ] Análisis automático de logs

**Test:** "¿Por qué ai-engine crasheó?" → Diagnóstico automático

---

### Fase 3: Hotfix Support (Semana 3)
**Entregable:** Claude puede hacer rollback + patch con validaciones

- [ ] Implementar `hotfixDirectK8s()` con validaciones
- [ ] Image validation (GHCR)
- [ ] Dry-run antes de cambios
- [ ] Health checks post-deploy
- [ ] Auto-rollback en falla
- [ ] Slack notifications

**Test:** Prod hotfix con rollback automático

---

### Fase 4: Local Build (Semana 4)
**Entregable:** Claude testea features en staging sin GA

- [ ] Implementar `buildAndDeployStaging()`
- [ ] Local mvn/npm build
- [ ] Docker build + push (GHCR)
- [ ] Deploy a staging K8s
- [ ] Health checks

**Test:** Feature branch → staging en 5 min

---

### Fase 5: Decision Tree (Semana 5)
**Entregable:** Claude elige automáticamente qué estrategia usar

- [ ] Implementar árbol de decisión
- [ ] Detectar contexto (merge/debug/hotfix/test)
- [ ] Elegir GA vs Direct K8s vs Local Build
- [ ] Flujos conversacionales completos

**Total: 5 semanas iterativas**

---

## Archivos a Crear/Modificar

```
.claude/
├── settings.json (actualizar con deployment config)
├── k8s-contexts.json (NUEVO)
├── debug-logs/ (NUEVO)
│   ├── .gitkeep
│   └── audit.jsonl (se genera)
├── agents/ (NUEVO)
│   ├── github-actions-monitor.ts
│   ├── k8s-explorer.sh
│   ├── hotfix-validator.sh
│   └── local-build.sh
└── helpers/ (NUEVO)
    ├── ga-client.ts
    ├── k8s.sh
    └── docker.sh
```

---

## Integración Segura

**Staging:**
- ✅ Direct K8s: Permitido (test, rollback, patch)
- ✅ Local Build: Permitido (feature testing)

**Production:**
- ✅ GitHub Actions: ÚNICO camino (validación required)
- ✅ Direct K8s: SOLO hotfix en emergencia (con rollback)
- ❌ Local Build: NUNCA

**Auditoría:**
- ✅ Todos los comandos loggeados en `.claude/debug-logs/audit.jsonl`
- ✅ Slack notifications para cambios críticos
- ✅ Auto-rollback en falla

---

## KPIs de Éxito

| Métrica | Target |
|---|---|
| Tiempo diagnóstico (antes manual) | 10+ min → **<2 min** |
| Comandos manuales por sesión | ~20 → **2-3** |
| Hotfix time en emergencia | — → **<3 min** |
| Feature test feedback | 15 min → **5 min** |
| Accuracy diagnóstico automático | — → **>85%** |
| False positives | — → **<5%** |

---

**Estado:** Listo para implementación (basado en infraestructura existente)  
**Esfuerzo:** 5 semanas iterativas (complementario, no reemplazo)  
**Riesgo:** Bajo (validaciones automáticas + rollback + auditoría)
