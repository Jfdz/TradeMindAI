# Plan: Automatización de Debugging Remoto sin SSH Manual

## Visión General
Automatizar la ejecución de comandos contra Ubuntu + Kubernetes sin necesidad de lanzarlos manualmente vía SSH mientras debugueamos. Crear un sistema **híbrido** que:

1. **GitHub Actions** para deploys normales (validación + auditoría)
2. **Direct K8s** para debugging, hotfixes, y investigation (velocidad)
3. **Local Build** para feature branches (testing sin esperar GA)

**Objetivo:** Conversación fluida donde Claude elige automáticamente la estrategia correcta y ejecuta comandos sin prompts manuales.

---

## Estrategia Híbrida: Flujo de Decisión

```
┌─────────────────────────────────────────────────────────────┐
│ Claude detecta la necesidad (merge/deploy/debug/test)       │
└────────────────────────┬────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
   ¿Merge/Deploy?   ¿Debugging?   ¿Feature Testing?
        │                │                │
        ▼                ▼                ▼
   GitHub Actions   Direct K8s      Local Build
   (15-20 min)      (1-3 min)       (5 min)
        │                │                │
        └────────────────┼────────────────┘
                         │
                    ✅ Result
```

---

## Arquitectura de Solución (3 Estrategias Integradas)

### Estrategia 1: GitHub Actions (Deploy Normal)

**Cuándo usar:**
- ✅ Merges a `main` o `develop`
- ✅ Deployments planificados a staging/prod
- ✅ Cambios críticos (migrations, secrets, config)
- ✅ Necesitas validación (tests, linters, security scans)

**Flujo:**
```
User: "Mergear PR #305 a main"
  ↓
Claude: gh pr merge 305 --merge --auto-delete-branch
  ↓
Si target es main: dispara release-orchestrator.yml
  ↓
Claude monitorea GA jobs (tests, builds, security scans)
  ↓
Si todo pasa: Deploy a staging automático
  ↓
Si staging OK: Propone deploy a production
  ↓
User confirma → deploy-production.yml con human approval
```

**Ventajas:**
- ✅ Tests + linters ejecutados
- ✅ Auditable (historial en GitHub)
- ✅ Reproducible (mismo proceso que manual)
- ✅ Security scans incluidos
- ✅ Image scans (Trivy)

**Desventajas:**
- ❌ Lento (5-20 min por workflow)
- ❌ Si falla, esperar a reintentar

---

### Estrategia 2: Direct K8s (Debugging + Hotfixes)

**Cuándo usar:**
- ✅ Debugging de problemas (logs, metrics, describe)
- ✅ Investigation (no cambios)
- ✅ Hotfixes urgentes (prod está down)
- ✅ Feature branch testing (local)
- ✅ Rollbacks (revertir versión anterior)

**Flujo:**
```
User: "¿Por qué ai-engine crasheó?"
  ↓
Claude automáticamente:
  1. kubectl get pods -l app=ai-engine -o wide
  2. kubectl logs <pod> --tail=200
  3. kubectl describe pod <pod>
  4. kubectl events --sort-by='.lastTimestamp'
  ↓
Claude analiza logs sin prompts
  ↓
Claude propone fix (code change o kubectl patch)
```

**Ventajas:**
- ✅ Instantáneo (1-3 min)
- ✅ Directo para debugging
- ✅ Mejor UX conversacional
- ✅ Auto-rollback en emergencias

**Desventajas:**
- ❌ Saltea tests/validaciones
- ❌ Menos auditable
- ⚠️ Requiere validaciones manuales

**Validaciones Obligatorias para Hotfixes:**
```bash
1. Verify image exists en GHCR
2. Dry-run: kubectl set image ... --dry-run=client
3. Post-deploy health check (3 retries)
4. Auto-rollback si falla
5. Slack alert con detalles
```

---

### Estrategia 3: Local Build (Feature Testing)

**Cuándo usar:**
- ✅ Feature branches en desarrollo
- ✅ Testing rápido sin esperar GA
- ✅ Pre-validación antes de PR

**Flujo:**
```
User: "Testear cambio en trading-core en staging"
  ↓
Claude:
  1. ./mvnw clean verify (local)
  2. docker build -t trading-core:dev . (local)
  3. docker push ghcr.io/jfdz/trading-core:dev
  4. kubectl set image deployment/trading-core \
     trading-core=ghcr.io/jfdz/trading-core:dev
  5. kubectl rollout status
  ↓
Test en staging sin esperar GA (5 min total)
```

**Ventajas:**
- ✅ Fast feedback (5 min vs 15 min)
- ✅ No espera GA
- ✅ Ideal para iteración

**Desventajas:**
- ❌ Saltea CI checks
- ⚠️ Solo para staging, nunca prod

---

### Nivel 1: Infraestructura Base (Semana 1)

#### 1.1 Configuración SSH & Kubectl Automatizado
**Archivos:**
- `.claude/ssh-config.json` — Credenciales y endpoints seguros
- `.claude/k8s-contexts.json` — Contextos de Kubernetes configurados
- `.claude/hooks/pre-debug.sh` — Script de inicialización

**Implementación:**
```json
// .claude/ssh-config.json
{
  "ubuntu_host": {
    "host": "your-ubuntu-ip",
    "user": "your-user",
    "identity_file": "$HOME/.ssh/id_rsa",
    "port": 22,
    "jump_host": null,
    "auto_connect": true
  },
  "k8s": {
    "context": "trading-saas",
    "namespace": "default",
    "kubeconfig": "$HOME/.kube/config"
  }
}
```

**Tareas:**
- [ ] Crear configuración SSH/K8s en `.claude/`
- [ ] Setupear kubeconfig para acceso remoto
- [ ] Validar conectividad automática al iniciar sesión
- [ ] Crear función auxiliar `connect-remote()` en `.claude/helpers.sh`

---

### Nivel 2: Agente de Debugging Automático (Semana 2-3)

#### 2.1 Agente "RemoteDebugger" (MCP Server Custom)

**Responsabilidad:** Ejecutar comandos SSH y Kubectl automáticamente sin prompt manual.

**Interfaz:**
```typescript
// Pseudo-código para el agente
interface RemoteDebuggerAgent {
  // Ejecuta comando SSH sin confirmación si es read-only
  execSSH(cmd: string, sudo?: boolean): Promise<string>;
  
  // Consulta Kubernetes (pods, logs, events, etc.)
  queryK8s(resource: "pods" | "svc" | "logs" | "events", query: string): Promise<string>;
  
  // Tail logs en tiempo real (mode streaming)
  tailLogs(pod: string, container?: string, follow?: boolean): AsyncIterator<string>;
  
  // Describe recursos (pods, nodes, etc.)
  describeResource(type: string, name: string, namespace?: string): Promise<string>;
  
  // Ejecuta test o build en remoto
  runRemoteCommand(script: string, workdir?: string): Promise<string>;
}
```

**Construcción:**
- Basado en MCP (Model Context Protocol) — Claude SDK tools
- Integración con Bash tool existente pero **con autenticación SSH preconfigurada**
- Logging estructurado de todos los comandos ejecutados
- Caché de resultados para evitar llamadas redundantes (TTL: 30s para K8s, 5m para logs)

**Características Anti-Error:**
- Whitelist de comandos permitidos (no ejecutar `rm -rf` sin confirmación)
- Dry-run mode para comandos destructivos
- Timeout configurables por comando
- Retry automático con backoff exponencial para fallos transitorios
- Rollback hooks si comando deja estado inconsistente

---

#### 2.2 Agente "KubernetesExplorer"

**Responsabilidad:** Navegar y diagnosticar estado del cluster K8s automáticamente.

**Flujo Típico:**
1. Claude detecta que un pod está crasheando → automáticamente ejecuta:
   - `kubectl get pods -o wide` (estado actual)
   - `kubectl logs <pod> --tail=50` (últimos 50 logs)
   - `kubectl describe pod <pod>` (eventos y configuración)
   - `kubectl top nodes` (consumo de recursos)
2. Agente retorna resultados formateados
3. Claude analiza y propone fix sin pedir más comandos manuales

**Implementación:**
```bash
# .claude/agents/k8s-explorer.sh
#!/bin/bash
# Comando: explore-pod <pod-name> [namespace]
# Retorna: JSON con estado, logs, eventos, métricas

POD=$1
NS=${2:-default}

(
  echo "=== STATE ==="
  kubectl get pod $POD -n $NS -o json | jq '.'
  
  echo "=== RECENT LOGS ==="
  kubectl logs $POD -n $NS --tail=100 2>/dev/null || echo "No logs available"
  
  echo "=== EVENTS ==="
  kubectl describe pod $POD -n $NS | grep -A 50 "Events:"
  
  echo "=== NODE METRICS ==="
  NODE=$(kubectl get pod $POD -n $NS -o jsonpath='{.spec.nodeName}')
  kubectl top node $NODE 2>/dev/null || echo "Metrics unavailable"
) | tee ~/.claude/debug-cache/${POD}.log
```

---

### Nivel 3: Integración en Claude Code (Semana 3-4)

#### 3.1 Claude Code Web Integration

**Flujo:**
1. Usuario abre sesión en `claude.ai/code`
2. En settings, conecta kubeconfig (upload `.kube/config`)
3. `.claude/settings.json` almacena referencia encriptada
4. Cuando necesita debuggear, Claude ejecuta automáticamente:

```typescript
// En la conversación, Claude dice:
// "Veo que trading-core-service está en CrashLoopBackOff. 
//  Déjame verificar los logs automáticamente..."
// Luego ejecuta (sin pedir permiso si es read-only):
await remoteDebugger.queryK8s("logs", "trading-core-service");
```

**Configuración en `.claude/settings.json`:**
```json
{
  "deployment": {
    "default_strategy": "auto-detect",
    "strategies": {
      "github-actions": {
        "trigger_with": "gh workflow run",
        "workflows": {
          "pr_merge": "gh pr merge",
          "normal_deploy": "release-orchestrator.yml",
          "hotfix_deploy": "deploy-production.yml"
        },
        "wait_for_completion": true,
        "max_wait_minutes": 20,
        "require_checks_pass": true,
        "monitor_artifacts": true
      },
      "direct-k8s": {
        "allow_for": ["debugging", "hotfix-urgent", "rollback"],
        "read_only_auto": ["get", "logs", "describe", "top", "events"],
        "require_confirm_for": ["patch", "set-image", "scale", "delete"],
        "require_validations": [
          "image-exists-in-registry",
          "health-check-post-deploy",
          "auto-rollback-enabled",
          "slack-notification"
        ],
        "hotfix_timeout_minutes": 5,
        "max_retries": 3,
        "backoff_ms": [1000, 2000, 4000]
      },
      "local-build": {
        "allow_for": ["feature-branch-testing"],
        "skip_ga": true,
        "target_env": "staging",
        "build_cmds": ["mvn clean verify", "docker build", "docker push"],
        "notify_team": true,
        "auto_rollback_on_failure": true
      }
    }
  },
  "permissions": {
    "github": {
      "allow": ["pr-merge", "workflow-run", "branch-create"],
      "block": ["force-push", "delete-branch"],
      "require_confirm": ["close-pr", "delete-release"]
    },
    "k8s": {
      "readonly": ["get", "describe", "logs", "top", "events"],
      "mutation": ["patch", "set-image", "scale", "delete"],
      "hotfix_only": ["rollout-undo", "rollout-history"],
      "auto_execute_readonly": true,
      "require_confirm_mutation": true
    },
    "ssh": {
      "allow": ["exec", "query-logs", "describe-resources"],
      "block": ["write-files", "restart-services"],
      "require_confirm": ["sudo-commands", "destructive-ops"]
    }
  },
  "remote": {
    "host_config": "./.claude/ssh-config.json",
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

#### 3.2 Claude Code Terminal Integration

**Usando `@claude-code` Skill o Bash tool mejorado:**

```bash
# El usuario en terminal hace:
$ claude --agent debug

# Claude arranca agente que queda "escuchando":
# - Monitorea kubectl logs en streaming
# - Si detecta ERROR/EXCEPTION, automáticamente:
#   - Ejecuta describe pod
#   - Consulta metrics
#   - Propone fix
#   - Ejecuta fix si usuario confirma

# Alternativamente, usuario escribe mientras tanto:
$ claude "Por qué trading-core-service no levanta?"

# Claude automáticamente:
# 1. Ejecuta: kubectl get pods -A | grep trading-core
# 2. Ve que está en CrashLoopBackOff
# 3. Ejecuta: kubectl logs <pod> --tail=100
# 4. Lee logs
# 5. Ejecuta: kubectl describe pod <pod>
# 6. Sintetiza el problema
# 7. Propone solución
```

---

### Nivel 4: Agente de Debugging Autónomo (Semana 4-5)

#### 4.1 "AutoDebugger" — Agente Proactivo

**Características:**
- Monitorea continuamente estado de servicios
- Detecta anomalías (CrashLoopBackOff, OOMKilled, Pending, etc.)
- Ejecuta diagnóstico automático sin esperar que hagas preguntas
- Reporta findings en conversación

**Implementación:**
```bash
# .claude/agents/auto-debugger.sh
# Corre cada N segundos (configurable: 10s, 30s, 1m)

watch_pods() {
  while true; do
    FAILED_PODS=$(kubectl get pods -A --field-selector=status.phase!=Running,status.phase!=Succeeded)
    
    if [ ! -z "$FAILED_PODS" ]; then
      # Automáticamente ejecuta diagnóstico
      echo "$FAILED_PODS" | while read POD NS; do
        ./k8s-explorer.sh $POD $NS >> ~/.claude/debug-logs/auto.log
      done
      
      # Notifica a Claude (via webhook o file watch)
      echo "ALERT: Failed pods detected" > ~/.claude/alerts/current
    fi
    
    sleep ${WATCH_INTERVAL:-30}
  done
}
```

**Integración con Claude:**
- Claude Code web: Periodically checks `.claude/alerts/` directory
- Claude Code terminal: Agent stays running, consumes alerts in real-time
- Conversación automática: "Detecté que ai-engine pod está en CrashLoopBackOff desde hace 2min. Aquí están los logs..."

---

### Nivel 5: Debugging Inteligente con Contexto (Semana 5)

#### 5.1 "SmartDebugger" — Análisis Contextual

**Características:**
- Entiende tu arquitectura (4 servicios + DB + RabbitMQ)
- Cuando ves un error en ai-engine, automáticamente verifica:
  - Estado del pod ai-engine
  - Conectividad a trading-core-service
  - Estado de PostgreSQL
  - Cola de RabbitMQ
  - Logs de correlación (via correlation_id)

**Flujo:**
```
Usuario: "El backtest de AAPL no termina"
         ↓
Claude automáticamente:
1. kubectl get pod ai-engine -o wide
2. kubectl logs ai-engine --tail=200 | grep AAPL
3. kubectl logs trading-core-service | grep correlation_id_from_ai_engine
4. SELECT * FROM pg_stat_statements WHERE query LIKE '%AAPL%'
5. rabbitmq-admin report (si disponible)
         ↓
"Encontré que la consulta SQL está en full table scan. 
 Aquí está el índice que necesitas..."
```

---

## Implementación por Fases

### Fase 1: MVP (1-2 semanas)
**Entregable:** Ejecutar comandos Kubectl automáticamente sin prompts manuales

- [ ] Setup `.claude/ssh-config.json` + `.claude/k8s-contexts.json`
- [ ] Crear MCP server básico: `remoteDebugger.execK8s()`
- [ ] Integración en Claude Code web (settings + permissions)
- [ ] Test: "Mostrar pods fallidos" ejecuta automáticamente
- [ ] Logging básico en `.claude/debug-logs/`

**Archivos a crear:**
```
.claude/
├── ssh-config.json
├── k8s-contexts.json
├── agents/
│   └── k8s-explorer.sh
├── helpers/
│   └── remote.sh
├── hooks/
│   └── pre-debug.sh
├── debug-logs/
│   └── .gitkeep
└── settings.json (actualizar con permisos)
```

### Fase 2: Auto-Execution (2-3 semanas)
**Entregable:** Claude ejecuta comandos automáticamente sin confirmación

- [ ] Whitelist de comandos read-only (get, describe, logs)
- [ ] Sistema de permisos granular (`.claude/permissions.json`)
- [ ] Caché de resultados (30s TTL)
- [ ] Timeout y retry logic
- [ ] Test: "¿Por qué crasheó trading-core?" → logs + describe automáticos

### Fase 3: Streaming & Real-Time (3-4 semanas)
**Entregable:** Tail logs y monitoreo en vivo

- [ ] `tailLogs()` con streaming
- [ ] Monitor de pods fallidos (watch loop)
- [ ] Alertas hacia Claude
- [ ] Test: Crashear un pod, ver cómo Claude automáticamente diagnostica

### Fase 4: Smart Diagnosis (4-5 semanas)
**Entregable:** Auto-debugging con contexto arquitectónico

- [ ] Multi-pod diagnosis (correlacionar entre servicios)
- [ ] Análisis de logs con ML (detectar patrones de error)
- [ ] Sugerencias de fix basadas en histórico
- [ ] Test: Error complejo que cruce múltiples servicios

---

## Technical Stack

### Tools & Frameworks
| Componente | Tech |
|---|---|
| **MCP Server** | Anthropic SDK + Node.js |
| **SSH/K8s Client** | `kubectl` CLI + `node-ssh` lib |
| **Logging** | Pino (JSON structured logs) |
| **Caching** | In-memory + file-based (`.claude/cache/`) |
| **Monitoring** | `kubectl watch` + file system watchers |
| **Config** | JSON + env vars (nunca hardcode) |

### File Structure
```
TradeMindAI/
├── .claude/
│   ├── CLAUDE.md (existente)
│   ├── settings.json (actualizar)
│   ├── ssh-config.json ← NUEVA
│   ├── k8s-contexts.json ← NUEVA
│   ├── permissions.json ← NUEVA
│   ├── agents/ ← NUEVA
│   │   ├── k8s-explorer.sh
│   │   ├── auto-debugger.sh
│   │   └── smart-debugger.ts
│   ├── helpers/ ← NUEVA
│   │   ├── remote.sh
│   │   └── k8s.sh
│   ├── hooks/ ← NUEVA
│   │   └── pre-debug.sh
│   ├── debug-logs/ ← NUEVA
│   │   └── .gitkeep
│   └── cache/ ← NUEVA
│       └── .gitkeep
└── ... (rest of project)
```

---

## Security & Best Practices

### Credenciales
- ❌ **NO:** Guardar SSH keys o kubeconfig en git
- ✅ **SÍ:** Referenciar via env vars o `.claude/ssh-config.json` (git-ignored)
- ✅ **SÍ:** Cifrar credenciales en `.local.json` (nunca en `.json`)

### Permisos
- **Read-Only Automático:** `get`, `describe`, `logs`, `top` ejecutan sin confirmación
- **Confirmación Requerida:** `apply`, `delete`, `patch`, `exec` (entrada remota)
- **Bloqueado:** `delete-deployment`, `scale`, `update-secret` sin revisión manual

### Logging & Auditoría
```json
// Cada comando grabado en: .claude/debug-logs/audit.jsonl
{
  "timestamp": "2026-05-18T12:34:56Z",
  "command": "kubectl logs ai-engine --tail=100",
  "user": "claude-code",
  "status": "success",
  "duration_ms": 245,
  "output_size_bytes": 12453,
  "correlation_id": "debug-sess-abc123"
}
```

---

## Ejemplos de Flujos Conversacionales Reales

### Flujo 1: PR Normal (GitHub Actions)

```
User: "Mergear PR #305 a main cuando sea"

Claude:
  1. ✅ "PR #305 detectada. Mergeando a main..."
  2. gh pr merge 305 --merge --auto-delete-branch
  3. ✅ "PR merged. Disparando release-orchestrator..."
  4. gh workflow run release-orchestrator.yml -f sha=<sha>
  5. 🔄 [Monitoring GA jobs...]
  6. ✅ "✅ Tests passed (2m45s), building images..."
  7. ✅ "✅ Images pushed, deploying to staging..."
  8. ✅ "✅ Staging health checks OK (3 services up)"
  9. ⏳ "Production ready. Awaiting your confirmation..."
  10. User: "Desplegar a producción"
  11. gh workflow run deploy-production.yml -f sha=<sha> -f confirm=deploy
  12. ✅ "Production deployment complete. New version live."
```

**Estrategia usada:** GitHub Actions
**Tiempo total:** 18-20 min
**Validaciones:** Tests ✓ Linters ✓ Security scans ✓ Image scans ✓

---

### Flujo 2: Prod Down — Hotfix (Direct K8s)

```
User: "trading-core no responde, arréglalo"

Claude automáticamente:
  1. kubectl get pod -l app=trading-core -o wide
     → Veo 3 pods en CrashLoopBackOff (restarted 5x en 2 min)
  2. kubectl logs <pod> --tail=100
     → Encontré: NullPointerException en ReasoningNewsSnapshot.fromArtifact()
  3. kubectl describe pod <pod>
     → Events muestran: pod killed due to Unhandled exception
  4. 🚨 "CRITICAL: trading-core crasheando por bug en ReasoningNews"
  5. "Opciones:
     - A) Rollback a v1.2.1 (2 min, estable)
     - B) Esperar fix en PR #306 + GA (15 min, nuevo código)"
  
  User: "Rollback a v1.2.1"
  
  6. 🔄 Validaciones automáticas:
     - ✅ Image v1.2.1 existe en GHCR
     - ✅ Dry-run: kubectl set image ... --dry-run=client
  7. kubectl set image deployment/trading-core trading-core=v1.2.1
  8. kubectl rollout status deployment/trading-core
     → Rollout progress: 0%... 33%... 100%
  9. ✅ Health checks: GET /api/v1/signals → 200 OK ✓
  10. ✅ "Rollback complete. Monitoring metrics..."
  11. Slack: "@oncall Trading-core rolled back to v1.2.1 (NullPointerException fixed)"
  12. 🔍 "PR #306 que arregla el bug está en review. Mergear cuando esté aprobado."
```

**Estrategia usada:** Direct K8s (hotfix)
**Tiempo total:** 3 min
**Auto-features:** Health checks ✓ Auto-rollback enabled ✓ Slack alert ✓

---

### Flujo 3: Debugging Performance (Direct K8s)

```
User: "Las queries de backtesting son lentas. Investigá"

Claude automáticamente:
  1. kubectl exec trading-core-service-abc123 -- \
     psql -U postgres -d trading -c \
     "SELECT query, calls, mean_time, stddev_time 
      FROM pg_stat_statements 
      WHERE query LIKE '%backtest%' 
      ORDER BY mean_time DESC LIMIT 10;"
     
     → Resultado: SELECT query (12000ms) sin índice en user_id
  
  2. kubectl logs trading-core-service --tail=200 | grep backtest
     → Veo: "Full table scan on trading_signals (100M rows)"
  
  3. kubectl top pod trading-core-service
     → CPU: 78%, Memory: 14/16GB
  
  4. 🔍 "Encontré el problema:
     - Query: SELECT * FROM trading_signals WHERE user_id=?
     - Sin índice en user_id (full table scan)
     - Tabla: 100M rows = 12 segundos por query
     - Propuesta: CREATE INDEX idx_trading_signals_user_id"
  
  5. "¿Ejecuto la migración en staging?"
     User: "Sí, en staging"
  
  6. kubectl exec trading-core-service-staging -- \
     psql -U postgres -d trading -c \
     "CREATE INDEX CONCURRENTLY idx_trading_signals_user_id 
      ON trading_signals(user_id);"
     → Índice creado en 45 segundos
  
  7. Re-test: Query time: 12000ms → 145ms ✓
  
  8. "🎯 Mejora de 98.8%. Listo para migración a prod en próximo deploy."
```

**Estrategia usada:** Direct K8s (investigation)
**Tiempo total:** 5 min
**Resultado:** Root cause + fix identificados sin cambio de código

---

### Flujo 4: Feature Branch Testing (Local Build)

```
User: "Testear cambio en trading-core sin esperar GA"

Claude:
  1. 🔄 "Compilando trading-core localmente..."
  2. ./mvnw clean verify
     → BUILD SUCCESS (tests passed: 127)
  3. 🐳 "Buildando imagen Docker..."
  4. docker build -t ghcr.io/jfdz/trading-core:dev-abc123 .
     → Image pushed: ghcr.io/jfdz/trading-core:dev-abc123
  5. 🚀 "Deployando a staging..."
  6. kubectl set image deployment/trading-core \
     trading-core=ghcr.io/jfdz/trading-core:dev-abc123 -n staging
  7. kubectl rollout status deployment/trading-core -n staging
     → 100% complete
  8. ✅ "Feature deployada en staging en 5 min (vs 15 min con GA)"
  9. "Url: https://staging.tradingsaas.com"
  10. "Nota: Esto es solo para testing. Antes de mergear a main, 
       necesita pasar la PR review y GA checks."
```

**Estrategia usada:** Local Build
**Tiempo total:** 5 min
**Ambiente:** Staging (nunca prod)

---

## Casos de Uso por Tipo

### UC1: Debugging de Crashloop
```
Usuario: "¿Por qué ai-engine no levanta?"

Claude automáticamente:
1. kubectl get pod -l app=ai-engine -o wide
2. kubectl logs <pod> --tail=200
3. kubectl describe pod <pod>
4. kubectl events (últimos 5 min)

→ Identifica OOMKilled → propone aumentar memory limit
```

### UC2: Debugging de Performance
```
Usuario: "Las queries de backtesting son lentas"

Claude automáticamente:
1. kubectl exec trading-core-service -- \
   SELECT query, calls, mean_time FROM pg_stat_statements 
   ORDER BY mean_time DESC LIMIT 10
2. kubectl logs trading-core-service | grep "backtest"
3. kubectl top pod trading-core-service

→ Identifica missing índice → propone migración
```

### UC3: Debugging de Conectividad
```
Usuario: "Web app no puede hablar con trading-core"

Claude automáticamente:
1. kubectl exec web-app -- curl -v trading-core-service:8082/health
2. kubectl logs trading-core-service | grep "connection"
3. kubectl describe svc trading-core-service
4. kubectl get networkpolicies

→ Identifica NetworkPolicy bloqueando → fix aplicado
```

---

## Árbol de Decisión para Claude

Claude debe seguir este árbol para elegir la estrategia automáticamente:

```
┌─ Usuario pide: ¿Qué hacer?
│
├─ "Mergear/Desplegar código a main/prod"
│  └─→ GITHUB ACTIONS
│      • ga pr merge
│      • release-orchestrator.yml
│      • deploy-production.yml
│      • Monitorear workflows
│      • Esperar validaciones
│      Tiempo: 15-20 min
│
├─ "Prod está down / Hotfix urgente"
│  ├─ ¿Hay código fix en PR?
│  │  ├─ Sí → Esperar GA (mejor)
│  │  └─ No → DIRECT K8S ROLLBACK
│  │         • Verificar imagen anterior
│  │         • Validar health checks
│  │         • Auto-rollback si falla
│  │         Tiempo: 2-3 min
│  │
│  └─ Si es issue de config/db:
│     DIRECT K8S PATCH
│     • kubectl patch
│     • Health check post-patch
│     Tiempo: 1-2 min
│
├─ "¿Por qué está fallando? Investigá"
│  └─→ DIRECT K8S (read-only)
│      • kubectl get, logs, describe, top
│      • No cambios (investigación)
│      • Auto-execute (no confirmar)
│      • Cache resultados (30s)
│      Tiempo: 1-5 min
│
└─ "Testear cambio en feature branch"
   └─→ LOCAL BUILD
       • mvn clean verify
       • docker build + push
       • kubectl set-image (staging)
       • Health check
       • NO esperar GA
       Tiempo: 5 min (vs 15 con GA)
```

---

## Configuración de Permisos por Estrategia

| Comando | GA | Direct-K8s (read) | Direct-K8s (hotfix) | Local-Build |
|---|---|---|---|---|
| `pr merge` | ✅ | ❌ | ❌ | ❌ |
| `workflow run` | ✅ | ❌ | ❌ | ❌ |
| `kubectl get/logs` | ❌ | ✅ Auto | ✅ Auto | ✅ Auto |
| `kubectl describe` | ❌ | ✅ Auto | ✅ Auto | ✅ Auto |
| `kubectl patch` | ❌ | ❌ | ✅ Confirm | ❌ |
| `kubectl set-image` | ❌ | ❌ | ✅ (validado) | ✅ (staging) |
| `docker build/push` | ❌ | ❌ | ❌ | ✅ (staging) |
| `rollout undo` | ❌ | ❌ | ✅ (hotfix) | ❌ |

---

## Métricas de Éxito

| KPI | Target |
|---|---|
| **Debugging**  |  |
| Tiempo diagnóstico (antes manual) | 10+ min → **<2 min** |
| Comandos manuales por sesión | ~20 → **2-3** |
| Accuracy diagnóstico automático | — → **>85%** |
| Falsos positivos en alertas | — → **<5%** |
| **Deployments** |  |
| % deploys vía GA (prod) | — → **100%** |
| % hotfixes vía Direct K8s | — → **<5%** (emergencias) |
| Tiempo PR merge → prod | 20 min → **18 min** (monitoreo) |
| Rollback time en emergencia | — → **<3 min** |
| **Feature Testing** |  |
| Tiempo feedback en staging | 15 min → **5 min** |
| % features pre-validated en staging | — → **>90%** |

---

## Roadmap de Implementación (Híbrido)

### Fase 1: MVP - GitHub Actions Integration (1 semana)
**Entregable:** Claude analiza y monitorea GA workflows

- [ ] Crear `.claude/settings.json` con config de GA
- [ ] Implementar `analyzeGitHubWorkflow()` en agente
- [ ] Integración con `gh workflow run`
- [ ] Monitoreo de job status y logs
- [ ] Test: "Mergear PR #305" dispara GA automáticamente

**Archivos:**
```
.claude/
├── settings.json (GitHub Actions config)
├── agents/
│   └── github-actions-monitor.ts
└── helpers/
    └── ga-client.ts
```

---

### Fase 2: MVP - Direct K8s Read-Only (1 semana)
**Entregable:** Claude ejecuta kubectl get/logs/describe automáticamente

- [ ] Crear `.claude/k8s-contexts.json`
- [ ] Implementar `queryK8s()` con validaciones
- [ ] Auto-execute para read-only commands
- [ ] Caché de resultados (30s TTL)
- [ ] Test: "¿Por qué ai-engine crasheó?" ejecuta automáticamente

**Archivos:**
```
.claude/
├── k8s-contexts.json
├── agents/
│   └── k8s-explorer.sh
├── helpers/
│   └── k8s.sh
└── cache/
    └── .gitkeep
```

---

### Fase 3: Hotfix Support - Direct K8s Mutations (1 semana)
**Entregable:** Claude puede hacer rollback + patch con validaciones

- [ ] Implementar `hotfixDirectK8s()` con validaciones
- [ ] Image validation (existe en registry)
- [ ] Dry-run antes de cambios
- [ ] Health checks post-deploy
- [ ] Auto-rollback en falla
- [ ] Test: Prod hotfix con rollback automático

---

### Fase 4: Feature Testing - Local Build (1 semana)
**Entregable:** Claude puede buildear y testear en staging sin GA

- [ ] Implementar `buildAndDeployStaging()`
- [ ] Local mvn/docker build
- [ ] Push a GHCR (staging tag)
- [ ] Deploy a staging K8s
- [ ] Test: Feature branch feedback en 5 min

---

### Fase 5: Decision Tree & UX (1 semana)
**Entregable:** Claude elige automáticamente qué estrategia usar

- [ ] Implementar decision tree (árbol de decisión)
- [ ] Detectar contexto automáticamente
- [ ] Elegir GA vs Direct K8s vs Local Build
- [ ] Test: Todos los flujos conversacionales

**Total: 5 semanas**

---

## Secuencia de Habilitación en Claude Code

### Semana 1-2: GitHub Actions (sin riesgo)
```
"Mergear PR #305"
→ Claude automáticamente dispara GA
→ Monitorea workflows
→ Reporta resultados
```

### Semana 3-4: Read-Only K8s (sin cambios)
```
"¿Por qué ai-engine está down?"
→ Claude automáticamente: logs, describe, metrics
→ Diagnostica sin cambios
```

### Semana 5: Hotfix + Local Build (con validaciones)
```
"Prod está down"
→ Claude: rollback automático + validaciones
"Testear feature en staging"
→ Claude: build local + deploy staging (5 min)
```

---

## Notas de Implementación

- **No reinventar la rueda:** Usar `@anthropic-ai/sdk` tools + mejorar con K8s/GA APIs
- **Fallback seguro:** Si no hay conexión remota → degradar a modo manual
- **Testing local:** Simular K8s failures con kind cluster en Docker
- **Documentación:** README para cada agente con ejemplos reales
- **Auditoría:** Todos los comandos loggeados en `.claude/debug-logs/audit.jsonl`
- **Recuperación:** Cada acción tiene rollback automático si falla

---

## Seguridad: Restricciones por Entorno

```
STAGING
├─ Direct K8s: ✅ Permitido (test, rollback, patch)
├─ Local Build: ✅ Permitido
└─ Rollout: ✅ Automático

PRODUCTION
├─ GitHub Actions: ✅ ÚNICO camino (validación required)
├─ Direct K8s hotfix: ✅ SOLO en emergencia (rollback mandatorio)
├─ Local Build: ❌ NUNCA
└─ Require confirmation: ✅ Human approval antes de prod deploy
```

---

**Estado:** Plan completo con estrategia híbrida integrada  
**Prioridad:** Alta (mejora productividad x10 + seguridad)  
**Esfuerzo:** 5 semanas iterativas  
**Riesgo:** Bajo (fases incremental, rollbacks automáticos)
