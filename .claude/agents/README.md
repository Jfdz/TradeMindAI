# 🤖 Automation Agents - TradeMindAI

Cinco fases automáticas que cubren todo el ciclo de vida: desde feature branches hasta producción.

---

## 📋 Tabla de Contenidos

1. **[Fase 1: GA Monitoring](#fase-1-github-actions-monitoring)** - Monitorea workflows automáticamente
2. **[Fase 2: K8s Debugging](#fase-2-k8s-read-only-debugging)** - Diagnósticos sin cambios
3. **[Fase 3: Hotfix](#fase-3-hotfix-support)** - Emergencias con rollback automático
4. **[Fase 4: Local Build](#fase-4-local-build)** - Testing rápido en staging
5. **[Fase 5: Decision Tree](#fase-5-decision-tree)** - Auto-elección de estrategia

---

## Fase 1: GitHub Actions Monitoring

**Qué hace:** Monitorea en tiempo real tus workflows de GA. Detecta fallos, analiza logs, sugiere re-runs.

**Cuándo usar:**
- Acabas de pushear cambios a `main` o `develop`
- Quieres saber el estado de tests/security scans
- Necesitas feedback sobre fallos en GA

**Uso:**

```bash
# Monitor la rama actual
./.claude/agents/ga-monitor.sh develop

# Monitor con número de PR
./.claude/agents/ga-monitor.sh feature-branch 405
```

**Qué reporta:**
```
✅ PASSED: ci-market-data-service (1m30s)
✅ PASSED: ci-trading-core-service (2m15s)
✅ PASSED: ci-ai-engine (2m45s)
✅ PASSED: ci-web-app (1m50s)
✅ Todos los tests pasaron

🚀 Ready for auto-merge
```

**Salida JSON:**
- `.claude/debug-logs/ga-monitor.jsonl` - Audit trail de monitoreos

---

## Fase 1+ (Continuación): PR Automation

**Qué hace:** Crea PRs automáticamente, monitorea tests, mergea cuando pasan.

**Flujo completo:**
```
Feature Branch Push
    ↓
Detecta cambios automáticamente (hook)
    ↓
Crea PR a develop (auto-generado desde commits)
    ↓
Monitorea GA tests (15-20 min)
    ↓
Mergea si TODO PASA
    ↓
Crea PR a main
    ↓
Monitorea + mergea a main
    ↓
release-orchestrator.yml se dispara
```

**Uso:**

```bash
# Crear PR automáticamente (se ejecuta después de git push)
./.claude/agents/pr-automation-enhanced.sh auto

# O crear PR específico
./.claude/agents/pr-automation-enhanced.sh create feature-branch develop

# O mergear PR existente
./.claude/agents/pr-automation-enhanced.sh merge 405 develop
```

**Qué crea:**
- PR con título del último commit
- Descripción con lista de cambios
- Checklist automático
- Auto-merge cuando pasan tests

**Salida JSON:**
- `.claude/debug-logs/pr-automation.jsonl`

---

## Fase 2: K8s Read-Only Debugging

**Qué hace:** Auto-ejecuta diagnósticos sin hacer cambios. Ideal para investigar problemas.

**Cuándo usar:**
- Pod está crasheando: `CrashLoopBackOff`, `OOMKilled`, `Error`
- Quieres entender qué está pasando sin intervenir
- Necesitas logs e información de eventos
- ¿Por qué el servicio no responde?

**Uso:**

```bash
# Diagnosticar pod específico
./.claude/agents/k8s-debug.sh pod ai-engine-7d9f7c5b8

# Diagnosticar deployment
./.claude/agents/k8s-debug.sh deployment trading-core-service

# Diagnosticar namespace completo
./.claude/agents/k8s-debug.sh namespace trading-saas

# Ver logs en vivo
./.claude/agents/k8s-debug.sh logs trading-core-service-abc123

# Verificar conectividad de servicio
./.claude/agents/k8s-debug.sh service trading-core-service
```

**Qué reporta:**
```
1️⃣  Pod Status
    NAME                               READY   STATUS   ...
    ai-engine-7d9f7c5b8-xyzabc        0/1     OOMKilled

2️⃣  Recent Events
    OOMKilled: Memory limit exceeded

3️⃣  Container Logs (Last 100 lines)
    OutOfMemoryError: Java heap space
    ...

4️⃣  Analysis
    ❌ Pod is in error state
    💾 Likely cause: Out of Memory (OOMKilled)
    Suggestion: Increase memory requests/limits
```

**Read-Only:** Nada se modifica. Solo diagnóstico.

---

## Fase 3: Hotfix Support

**Qué hace:** Hotfixes con validaciones obligatorias, health checks, y rollback automático.

**Cuándo usar:**
- 🚨 PROD ESTÁ DOWN
- Necesitas rollback urgente
- Hay que patchear versión anterior
- Requiere: validación imagen, dry-run, health check, rollback automático

**Validaciones Obligatorias:**
1. ✅ Imagen existe en GHCR
2. ✅ Dry-run sin errores
3. ✅ Health check post-deploy
4. ✅ Auto-rollback si falla

**Uso:**

```bash
# Hotfix completo (con validaciones)
./.claude/agents/hotfix.sh hotfix trading-core trading-core-service v1.2.1

# Rollback a versión anterior
./.claude/agents/hotfix.sh rollback trading-core

# Validar que imagen existe
./.claude/agents/hotfix.sh validate-image trading-core-service v1.2.1
```

**Flujo:**
```
1. Valida imagen existe
2. Ejecuta dry-run
3. Deploy
4. Health check POST-deploy
5. Si falla: rollback automático
6. Notifica Slack
```

**Salida JSON:**
- `.claude/debug-logs/hotfix.jsonl`
- `.claude/debug-logs/rollback.jsonl` (si ocurre rollback)

---

## Fase 4: Local Build

**Qué hace:** Compila localmente, build Docker, push a GHCR, deploya a staging.

**Tiempo:** ~5 minutos vs 15+ con GA

**Cuándo usar:**
- Estás testando feature en staging (sin esperar GA)
- Quieres feedback rápido (5 min)
- Solo staging, no production
- Ya pasaron tests locales, ahora a staging

**Uso:**

```bash
# Build + push + deploy
./.claude/agents/local-build.sh trading-core-service

# Con tag específico
./.claude/agents/local-build.sh trading-core-service dev-feature-xyz123

# Web app
./.claude/agents/local-build.sh web-app

# AI engine
./.claude/agents/local-build.sh ai-engine
```

**Flujo:**
```
1. mvn clean verify (local)
   → BUILD SUCCESS, 127 tests passed

2. docker build (local)
   → Image created: ghcr.io/jfdz/trading-core:dev-feature-xyz

3. docker push (local)
   → Image pushed to GHCR

4. kubectl set image (staging namespace)
   → Deployment updated

5. Health check
   → ✅ Staging deployment live
   → https://staging.trademind.es
```

**Permisos:**
- ⚠️ Requiere `docker login ghcr.io`
- ⚠️ Requiere acceso kubectl a staging

---

## Fase 5: Decision Tree

**Qué hace:** Auto-detecta contexto y elige automáticamente qué estrategia usar.

**Reglas de decisión:**
```
¿Prod down + pods crashed?        → HOTFIX (rollback)
¿Feature branch + commits?         → PR_AUTOMATION
¿Debug commit o errores recientes? → K8S_DEBUG
¿Code changes en feature?          → LOCAL_BUILD (staging)
¿Main/develop push?                → GA_MONITORING
Default:                            → GA_MONITORING
```

**Uso:**

```bash
# Auto-detecta y elige estrategia
./.claude/agents/decision-tree.ts

# Salida:
# Decision:
#   Strategy: PR_AUTOMATION (confidence: 90%)
#   Reason: 📝 Feature branch with unpushed commits detected
#   Next Steps:
#     1. git push origin feature-branch
#     2. ./.claude/agents/pr-automation-enhanced.sh auto
#     3. Monitor: ./.claude/agents/ga-monitor.sh feature-branch
```

**Salida JSON:**
- `.claude/debug-logs/decision-tree.jsonl`

---

## 🚀 Quick Start: Tu Flujo Típico

### Escenario 1: Feature Development

```bash
# 1. Trabajas en feature branch
git checkout -b feature/awesome-thing
# ... haces cambios ...

# 2. Commits
git add .
git commit -m "feat: implement awesome thing"

# 3. Push
git push origin feature/awesome-thing

# 4. Claude detecta automáticamente (SessionStart hook)
# → PR creada automáticamente
# → Monitorea tests
# → Mergea si pasan
```

### Escenario 2: Prod Emergency

```bash
# 1. ¡Prod está down!
# 2. Decision tree detecta
./.claude/agents/decision-tree.ts
# → Decision: HOTFIX (confidence: 95%)

# 3. Diagnostica
./.claude/agents/k8s-debug.sh pod <pod-name>

# 4. Hotfix con validaciones
./.claude/agents/hotfix.sh hotfix trading-core trading-core-service v1.2.1
# → Validación imagen
# → Dry-run
# → Deploy
# → Health check
# → ✅ Auto-rollback si falla

# 5. Slack notification
```

### Escenario 3: Feature Testing (Sin GA)

```bash
# 1. Feature branch con cambios
git checkout -b feature/quick-fix
# ... haces cambios en trading-core-service ...
git add .
git commit -m "fix: quick fix"

# 2. Testea localmente
mvn test

# 3. Deploy a staging (5 min, sin GA)
./.claude/agents/local-build.sh trading-core-service

# 4. Test en https://staging.trademind.es

# 5. Si OK, push y PR
git push && ./.claude/agents/pr-automation-enhanced.sh auto
```

---

## 📊 Logging & Audit

Todos los eventos se guardan en JSON para auditoría:

```bash
# Ver PR automation events
cat .claude/debug-logs/pr-automation.jsonl | jq

# Ver hotfixes
cat .claude/debug-logs/hotfix.jsonl | jq

# Ver decisiones tomadas
cat .claude/debug-logs/decision-tree.jsonl | jq

# Ver rolling back
cat .claude/debug-logs/rollback.jsonl | jq

# Monitor todo
tail -f .claude/debug-logs/*.jsonl | jq
```

---

## 🔐 Seguridad

**Staging:** Direct K8s permitido (test, rollback, patch)
**Production:** GitHub Actions ÚNICO (validación required)
**Emergencias:** Direct K8s hotfix con rollback mandatorio
**Auditoría:** Todos los comandos loggeados en `.jsonl`

---

## ✅ Requisitos

```bash
# Verificar que tenemos todo
which kubectl        # Kubernetes CLI
which gh            # GitHub CLI
which docker        # Docker
which git           # Git
which jq            # JSON processor

# Verificar acceso
kubectl cluster-info          # ¿Acceso a K8s?
gh auth status                # ¿Acceso a GitHub?
docker info                   # ¿Docker daemon?
```

---

## 🛠️ Troubleshooting

### "Cannot connect to Kubernetes"
```bash
# Check kubeconfig
cat $KUBECONFIG
# O usar el archivo específico
export KUBECONFIG=~/.kube/config
```

### "Unauthorized: Docker push"
```bash
# Login a GHCR
docker login ghcr.io
# Usar token personal de GitHub
```

### "mvn command not found"
```bash
# Usar wrapper del proyecto
./mvnw clean verify
# O instalar Maven global
```

### "Script not executable"
```bash
chmod +x .claude/agents/*.sh
```

---

## 📖 Documentación Completa

- **Fase 1:** `ga-monitor.sh` + `pr-automation-enhanced.sh`
- **Fase 2:** `k8s-debug.sh`
- **Fase 3:** `hotfix.sh`
- **Fase 4:** `local-build.sh`
- **Fase 5:** `decision-tree.ts`

---

## 🎯 Estado

✅ **TODAS LAS FASES IMPLEMENTADAS**

- [x] Fase 1: GA Monitoring + PR Automation
- [x] Fase 2: K8s Read-Only Debugging
- [x] Fase 3: Hotfix Support con validaciones
- [x] Fase 4: Local Build sin GA
- [x] Fase 5: Decision Tree automático

**Próximo push a feature branch dispará la automatización completa.**

---

Hecho por Claude • 2026-05-18
