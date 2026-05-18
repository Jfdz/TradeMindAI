# 🪝 Git Hooks Setup

Automatización completa en tu máquina local. Cada `git commit` y `git push` ejecutarán automáticamente los agentes.

---

## ⚡ Setup en 1 Minuto

**Ejecuta esto en la raíz del proyecto:**

```bash
bash .claude/install-hooks.sh
```

**Eso es todo.** Los hooks están instalados.

---

## ✅ Verificar Instalación

```bash
ls -la .git/hooks/

# Deberías ver:
# post-commit  (ejecutado después de commits)
# post-push    (ejecutado después de push)
```

---

## 🚀 Ahora Que Funciona

Desde **VS Code terminal** (o cualquier terminal local):

### Escenario 1: Feature Branch

```bash
# 1. Haces cambios
echo "# mi feature" >> README.md

# 2. Commit
git add README.md
git commit -m "feat: mi feature"

# 🪝 Hook: post-commit se ejecuta
# → decision-tree.ts analiza contexto
# → Propone estrategia (PR_AUTOMATION, LOCAL_BUILD, etc)

# 3. Push
git push origin feature/mi-feature

# 🪝 Hook: post-push se ejecuta
# → pr-automation-enhanced.sh crea PR automáticamente
# → Monitorea tests en GA
# → Mergea cuando pasan
# → Crea PR a main
# → Todo automático
```

### Escenario 2: Main/Develop Branch

```bash
# 1. Push a main
git push origin main

# 🪝 Hook: post-push se ejecuta
# → ga-monitor.sh monitorea workflows
# → release-orchestrator.yml se dispara
# → 4 CIs + staging + prod automático
```

---

## 📝 Qué Hace Cada Hook

### `post-commit` Hook
**Cuándo:** Después de cada `git commit`
**Qué hace:**
- Detecta en qué rama estás
- Ejecuta `decision-tree.ts`
- Analiza contexto (cambios, errores recientes, etc)
- Propone estrategia (GA, K8s, Hotfix, Local Build, PR)

**Salida:**
```
📊 Analizando contexto...
Decision:
  Strategy: PR_AUTOMATION (confidence: 90%)
  Reason: 📝 Feature branch with unpushed commits detected
  Next Steps:
    1. git push origin feature-branch
    2. PR automation se ejecutará en post-push
```

---

### `post-push` Hook
**Cuándo:** Después de cada `git push`
**Qué hace:**
- **Feature branch:** Ejecuta `pr-automation-enhanced.sh`
  - Crea PR a develop
  - Monitorea tests en GA
  - Mergea cuando pasan
  - Crea PR a main
  - Mergea a main
  
- **Main/develop:** Ejecuta `ga-monitor.sh`
  - Monitorea workflows de GA
  - Reporta progreso en tiempo real

---

## 🛠️ Troubleshooting

### "Hook no se ejecutó"

**Verificar permisos:**
```bash
# Los hooks deben ser ejecutables
ls -la .git/hooks/post-commit .git/hooks/post-push

# Si dice -rw- (sin x), hacerlos ejecutables:
chmod +x .git/hooks/post-*
```

**Desinstalar y reinstalar:**
```bash
# Elimina hooks viejos
rm .git/hooks/post-commit .git/hooks/post-push

# Reinstala
bash .claude/install-hooks.sh
```

### "Scripts no encontrados"

Los hooks buscan scripts en `./.claude/agents/`

Verificar que existen:
```bash
ls -la .claude/agents/*.sh
ls -la .claude/agents/decision-tree.ts
```

### "Error: command not found"

Los hooks necesitan herramientas:
```bash
which kubectl          # Kubernetes
which gh              # GitHub CLI
which git             # Git
which bash            # Bash
```

---

## 📊 Logs & Debug

Los hooks ejecutan silenciosamente por defecto. Para ver qué pasó:

```bash
# Ver últimos eventos PR automation
tail .claude/debug-logs/pr-automation.jsonl | jq

# Ver últimos eventos GA monitoring
tail .claude/debug-logs/ga-monitor.jsonl | jq

# Ver última decisión tomada
tail .claude/debug-logs/decision-tree.jsonl | jq

# Monitorear tiempo real
tail -f .claude/debug-logs/*.jsonl | jq '.event, .status'
```

---

## ❌ Desinstalar Hooks

Si necesitas desactivar temporalmente:

```bash
rm .git/hooks/post-commit .git/hooks/post-push
```

Para reinstalar:
```bash
bash .claude/install-hooks.sh
```

---

## 🔄 Flujo Completo (Ejemplo Real)

```bash
# 1. Crear feature branch
git checkout -b feature/awesome-thing

# 2. Hacer cambios
echo "awesome code" > src/awesome.js
git add src/awesome.js
git commit -m "feat: implement awesome thing"

# 🪝 post-commit se ejecuta
# → decision-tree.ts analiza
# → Output: "Strategy: PR_AUTOMATION"

# 3. Push
git push origin feature/awesome-thing

# 🪝 post-push se ejecuta
# → pr-automation-enhanced.sh detecta feature branch
# → Crea PR a develop
# → "✅ PR created: #405"
# → Monitorea tests por 20 min
# → "✅ Todos los tests pasaron"
# → Mergea a develop
# → Crea PR a main
# → Mergea a main
# → release-orchestrator.yml se dispara

# TODO AUTOMÁTICO DESDE UN git push
```

---

## 📋 Próximos Pasos

1. **Ejecutar instalador:**
   ```bash
   bash .claude/install-hooks.sh
   ```

2. **Verificar:**
   ```bash
   ls -la .git/hooks/ | grep post-
   ```

3. **Probar con un commit pequeño:**
   ```bash
   echo "test" >> .git/SETUP_COMPLETE
   git add .git/SETUP_COMPLETE
   git commit -m "test: hooks setup"
   # Debería ejecutar decision-tree
   ```

4. **Hacer push:**
   ```bash
   git push
   # Debería ejecutar pr-automation o ga-monitor
   ```

---

**¡Listo!** Desde ahora tu workflow es totalmente automático. 🚀
