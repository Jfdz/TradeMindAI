# Plan: Automatización de Debugging Remoto sin SSH Manual

## Visión General
Automatizar la ejecución de comandos contra Ubuntu + Kubernetes sin necesidad de lanzarlos manualmente vía SSH mientras debugueamos. Crear un sistema de agentes que ejecuten comandos automáticamente cuando sea necesario consultar Kubernetes, logs, métricas o estado de servicios.

**Objetivo:** Conversación fluida donde Claude ejecuta comandos automáticamente cuando los necesita para investigar problemas.

---

## Arquitectura de Solución

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
  "permissions": {
    "ssh": {
      "allow": ["exec", "query-logs", "describe-resources"],
      "block": ["write-files", "restart-services"],
      "require_confirm": ["sudo-commands", "destructive-ops"]
    },
    "k8s": {
      "allow": ["get", "describe", "logs", "metrics"],
      "block": ["apply", "delete", "patch"],
      "auto_execute_readonly": true
    }
  },
  "remote": {
    "host_config": "./.claude/ssh-config.json",
    "timeout_ms": 5000,
    "cache_ttl_ms": 30000,
    "log_path": "./.claude/debug-logs/"
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

## Casos de Uso

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

## Métricas de Éxito

| KPI | Target |
|---|---|
| Tiempo de diagnóstico (antes manual) | 10+ min → **<1 min** |
| Comandos ejecutados manualmente vía SSH | ~20/sesión → **2-3/sesión** |
| Accuracy de diagnóstico automático | — → **>85%** |
| Falsos positivos (alertas auto) | — → **<5%** |
| Cobertura de escenarios de debugging | — → **>80%** |

---

## Próximos Pasos

1. **Hoy:** Crear `.claude/ssh-config.json` + `.claude/k8s-contexts.json`
2. **Mañana:** Implementar MCP server básico con `execK8s()`
3. **Semana 1:** MVP funcional en Claude Code web
4. **Semana 2-3:** Auto-execution + streaming
5. **Semana 4-5:** Smart diagnosis

---

## Notas de Implementación

- **No reinventar la rueda:** Usar `@anthropic-ai/sdk` tools existentes + mejorar con SSH
- **Fallback seguro:** Si no hay conexión remota, degradar a modo manual (pedir comandos)
- **Testing:** Simular K8s failures localmente (kind cluster en Docker)
- **Documentación:** Cada agente necesita README con ejemplos
- **Versionado:** Guardar histórico de diagnósticos en `.claude/debug-logs/` para mejorar patrones

---

**Estado:** Listo para implementación  
**Prioridad:** Alta (mejora productividad de debugging x10)  
**Esfuerzo:** 4-5 semanas (full-time)
