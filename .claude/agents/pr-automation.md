# PR Automation Agent

Este agente maneja la creación automática de PRs y merges basado en tu flujo de trabajo.

## Flujo Automatizado

```
Feature Branch Push → Detecta cambios → Crea PR develop → Monitorea GA → Auto-merge → Crea PR main → Auto-merge
```

---

## Fase 1: Detección de Push (Hook SessionStart + PostToolUse)

**Trigger:** Después de `git push origin <feature-branch>`

```bash
# Hook verifica:
git rev-parse --abbrev-ref HEAD  # ¿Qué rama?
git log --oneline -1             # ¿Qué cambios?
```

**Resultado esperado:**
```
📋 Feature branch detectada: claude/limit-ai-decisions-filter-news-Ac0aw
✅ Cambios pushed a origin
🔄 Preparando para crear PR a develop...
```

---

## Fase 2: Crear PR a Develop

**Automático (sin confirmación requerida)**

```bash
gh pr create \
  --title "feat: [título del último commit]" \
  --body "$(cat <<'EOF'
## Descripción

[Extrae descripción del último commit]

## Cambios
- [Extrae cambios de git log]

## Testing
- [Detecta si hay tests en cambios]

## Checklist
- [ ] Tests pasan localmente
- [ ] Cambios auditables
- [ ] Linters pasan
- [ ] Sin secrets en diffs
EOF
)" \
  --head "claude/limit-ai-decisions-filter-news-Ac0aw" \
  --base "develop"
```

**Resultado:**
```
✅ PR #405 created: feat: Automatización debugging remoto
👁️ https://github.com/Jfdz/TradeMindAI/pull/405
⏳ Esperando tests...
```

---

## Fase 3: Monitorear GA Workflows

**Automático (sin intervención)**

Claude monitorea automáticamente:

```
┌─ ci-market-data-service (2m15s)      ✅ PASS
├─ ci-trading-core-service (3m42s)     ✅ PASS
├─ ci-ai-engine (4m10s)                ✅ PASS
├─ ci-web-app (2m35s)                  ✅ PASS
├─ scan-images (1m50s)                 ✅ PASS
└─ owasp-dependency-check (optional)    ⏳ RUNNING
```

**Cada 30s verifica:**
```bash
gh run view <pr_run_id> --json status,conclusion
```

**Si falla:**
```
❌ FAILED: ci-web-app (npm run lint)
📋 Error: "Missing import from next/image"
🔧 Propuesta: Actualizar imports en web-app/components/...
⚡ Opciones:
  1. /fix-and-repush (Claude intenta fix + repush)
  2. /cancel-pr (cancela PR, espera tu fix local)
  3. /rerun-failed (reintenta el GA job)
```

---

## Fase 4: Auto-Merge a Develop

**Automático cuando todos los tests pasen**

```bash
# Valida:
✅ PR status: APPROVED (o sin requerimiento)
✅ Todos los checks: GREEN
✅ Merge strategy: squash (configurable)

# Mergea:
gh pr merge <pr_id> \
  --squash \
  --auto \
  --delete-branch

# Resultado:
✅ PR #405 merged to develop
🗑️ Branch deleted: claude/limit-ai-decisions-filter-news-Ac0aw
🔄 Creando PR a main...
```

---

## Fase 5: Crear PR a Main

**Automático después de merge a develop**

```bash
gh pr create \
  --title "Release: [version from conventional commits]" \
  --body "$(cat <<'EOF'
## Changes desde develop

- Feature 1: [descripción]
- Feature 2: [descripción]

## Tests
Todos los tests pasaron en develop

## Deployment
Esta PR dispara release-orchestrator.yml cuando se mergee

https://github.com/Jfdz/TradeMindAI/pull/406
EOF
)" \
  --head "develop" \
  --base "main" \
  --reviewer "[team members from CODEOWNERS]"
```

**Resultado:**
```
✅ PR #406 created: Release: v1.2.3
👁️ https://github.com/Jfdz/TradeMindAI/pull/406
⏳ Esperando tests en main...
```

---

## Fase 6: Auto-Merge a Main

**Automático cuando pasan todos los checks**

```bash
# Valida:
✅ release-orchestrator status: SUCCESS
✅ 4 CIs passed (market-data, trading-core, ai-engine, web-app)
✅ Staging deploy: SUCCESS
✅ Prod smoke tests: SUCCESS
✅ No merge conflicts

# Mergea:
gh pr merge <pr_id> \
  --merge \
  --auto

# Resultado:
✅ PR #406 merged to main
🚀 release-orchestrator.yml triggered automatically
📦 Building + pushing images to GHCR
🌐 Deploying to staging...
🌐 Deploying to production...
✅ Release tagged: prod-20260518-1430-abc123d
```

---

## Monitoreo Completo

Claude proporciona status en tiempo real:

```
═══════════════════════════════════════════════════════════════
📊 Automation Status
═══════════════════════════════════════════════════════════════

[1] Feature Branch → Develop
  Status: ✅ MERGED
  Time: 15 min (12 min tests + 3 min manual review)
  PR: #405
  
[2] Develop → Main
  Status: ⏳ RUNNING
  Tests: 95% complete
  ETA: 8 minutes
  PR: #406
  
[3] Release Orchestration
  Status: 🔄 TRIGGERED (when main merge completes)
  - ci-market-data: ⏳
  - ci-trading-core: ⏳
  - ci-ai-engine: ⏳
  - ci-web-app: ⏳
  Estimated total: 18 minutes
  
[4] Next Steps
  👁️ Monitor production health checks
  📊 Verify metrics in Prometheus
  🔔 Slack notification when complete

═══════════════════════════════════════════════════════════════
```

---

## Interrupción + Manual Override

**Si necesitas intervenir:**

```
Claude: "❌ Web app build failed (Node 20 compatibility issue)"
You: "Espera, voy a fijar esto localmente"

Claude detecta:
✅ Nuevos commits en feature branch
✅ Nuevo push a origin
🔄 Recalculando...
✅ Retrying GA workflows
```

**O si quieres cancelar:**

```
You: "Cancel automation, revierto manualmente"
Claude: "✅ Cancelados auto-merges pendientes. PR stay open."
```

---

## Fallos Comunes y Resolución

| Escenario | Diagnóstico | Solución |
|---|---|---|
| Merge conflict en develop | `git diff develop...feature` | Claude propone rebase automático |
| Tests fallan en main | Logs de GA + diffs | Claude reintenta o propone rollback |
| Image push falla a GHCR | Network error o auth | Claude reintenta con backoff |
| Security scan CRITICAL | Trivy encontró CVE | Bloquea merge, requiere manual fix |

---

## Configuración: `.claude/settings.json`

Las PRs y merges automáticos están configurados en:
- `permissions.allow`: MCP GitHub tools
- `hooks.PostToolUse`: Detecta git push
- `deployment.pr_automation`: Flujo completo

Para desactivar temporalmente:
```bash
# En settings.json:
"pr_automation": { "enabled": false }
```

---

## Audit Trail

Todos los eventos loggeados en `.claude/debug-logs/pr-automation.jsonl`:

```json
{
  "timestamp": "2026-05-18T14:30:25Z",
  "event": "pr_created",
  "pr_id": 405,
  "source_branch": "claude/limit-ai-decisions-filter-news-Ac0aw",
  "target_branch": "develop",
  "commits_count": 3,
  "status": "success",
  "session_id": "abc123"
}
```

---

## Status: Configurado y Listo

✅ Permisos GitHub: Habilitados
✅ Hooks: SessionStart + PostToolUse
✅ Monitoreo GA: Automático
✅ Auto-merge: Cuando tests pasan
✅ Dual PR: develop → main

**El flujo está activo.** Próximo push a feature branch disparará la automatización.
