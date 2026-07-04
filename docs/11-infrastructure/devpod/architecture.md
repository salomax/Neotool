# neotool DevPod — Arquitetura do Pipeline de Desenvolvimento com IA

## Visão Geral

O DevPod é o ambiente de desenvolvimento containerizado do Invistus. Além de rodar os serviços locais, ele hospeda o **neotool-dev-agent** — um orquestrador de agentes de IA que recebe mensagens do Slack e coordena Claude, Codex e Cursor para executar as etapas do pipeline de desenvolvimento.

```
Slack → Cloudflare Worker → Cloudflare Tunnel → neotool-dev-agent → Router → Agentes
                                                      :3285          (Gemini)   claude-dev
                                                                               codex-dev
                                                                               cursor-dev
```

---

## Componentes

### neotool-dev-agent (`/workspaces/invistus/.devcontainer/neotool-dev-agent.py`)

Bridge HTTP que recebe requisições do Cloudflare Worker via `POST /chat`. Responde imediatamente com `202 Accepted` e processa em background thread. Quando o agente termina, posta o resultado no Slack via `chat.update`.

Porta: `3285` (configurável via `NEOTOOL_AGENT_PORT`)

### router.py (`/workspaces/invistus/.devcontainer/router.py`)

Orquestrador interno. Composto por três camadas:

1. **Classifier** — usa Gemini 2.5 Flash Lite para classificar a intenção da mensagem (via `GOOGLE_API_KEY`). Fallback rule-based se Gemini falhar.
2. **Stage Machine** — mantém estado da thread em `/tmp/bridge-sessions/<thread-id>.json`. Persiste `issue_number`, `current_stage` e session IDs de cada agente.
3. **Dispatcher** — invoca o wrapper do agente correto via `subprocess.run()`.

### Agentes (`/workspaces/invistus/.devcontainer/agents/`)

| Wrapper | CLI | Modelo | Stage padrão |
|---|---|---|---|
| `claude-dev` | `claude` (Claude Code) | `claude-sonnet-4-6` | issue, design, backlog |
| `codex-dev` | `codex` | `gpt-5.4` | implementação |
| `cursor-dev` | `agent` (Cursor CLI) | auto | validação, PR |

Todos os wrappers:
- Gerenciam session resume via `session-mgr.py`
- Postam status intermediário no Slack via `slack-status.sh`
- Logam em `/tmp/neotool-agent.log`

### Pipeline de Stages

```
stage_issue → stage_design → stage_backlog → stage_impl → stage_validate → stage_pr
     1              2              3              4               5              -
```

O usuário aprova cada transição respondendo `sim` / `ok` / `aprovado` no Slack. O router mantém `current_stage` no estado da thread.

**Fast track**: o usuário pode pedir múltiplos stages de uma vez ("faz tudo", "de uma vez").

---

## Convenção de Hostnames

Cada DevPod tem seu próprio Cloudflare Tunnel com hostnames no padrão:

```
devpod-{n}-{service}.invistus.com.br
```

| Serviço | Hostname | Porta interna |
|---|---|---|
| neotool-dev-agent (Slack) | `devpod-1-agent.invistus.com.br` | `3285` |
| Next.js (frontend) | `devpod-1-web.invistus.com.br` | `3000` |
| (futuro) GraphQL API | `devpod-1-api.invistus.com.br` | `4000` |

Isso permite rodar múltiplos DevPods em paralelo, cada um com seu canal Slack e suas URLs independentes.

---

## Setup de um Novo DevPod

### 1. Cloudflare — criar tunnel

```bash
# No Mac (uma vez por DevPod)
cloudflared tunnel create devpod-N
# Cria ~/.cloudflare/devpod-N.json com as credenciais
```

### 2. Cloudflare — configurar DNS

No dashboard Cloudflare, adicione CNAMEs para cada hostname apontando para `<tunnel-id>.cfargotunnel.com`:
- `devpod-N-agent.invistus.com.br`
- `devpod-N-web.invistus.com.br`

### 3. Criar config do tunnel

Crie `~/.cloudflare/devpod-N.yml` no Mac:

```yaml
tunnel: <tunnel-id>
credentials-file: /home/dev/.cloudflare/devpod-N.json

ingress:
  - hostname: devpod-N-agent.invistus.com.br
    service: http://localhost:3285
  - hostname: devpod-N-web.invistus.com.br
    service: http://localhost:3000
  - service: http_status:404
```

O `~/.cloudflare/` é montado como bind-mount read-only no container (ver `devcontainer.json`).

### 4. Cloudflare Worker — criar por DevPod

Cada DevPod tem seu próprio Worker no Cloudflare. O Worker recebe o webhook do Slack e encaminha para a URL do tunnel:

```js
const BRIDGE_URL = "https://devpod-N-agent.invistus.com.br"
```

Configure o Worker para responder ao canal Slack do DevPod (`#devpod-N`).

### 5. Slack — canal por DevPod

Crie um canal `#devpod-N` e configure o Slack App para enviar eventos ao Worker do DevPod correspondente.

### 6. api-keys.env

Em `~/.vault-keys/api-keys.env` (compartilhado entre DevPods):

```env
ANTHROPIC_API_KEY=sk-ant-...
OPENAI_API_KEY=sk-...
GOOGLE_API_KEY=...
GH_TOKEN=ghp_...
CLOUDFLARE_TUNNEL_TOKEN=...    # não usado mais — tunnel é on-demand
NEOTOOL_AGENT_SECRET=...       # opcional — autenticação do bridge
```

---

## Operação

### Iniciar o bridge (automático via start-infra.sh)

```bash
# Roda ao subir o DevPod — não precisa chamar manualmente
.devcontainer/start-infra.sh
```

### Reiniciar o bridge manualmente

```bash
pkill -f neotool-dev-agent.py || true
source ~/.vault-keys/api-keys.env
python3 /workspaces/invistus/.devcontainer/neotool-dev-agent.py &
```

### Expor via Cloudflare Tunnel (on-demand)

```bash
# Iniciar
neotool-tunnel start 1

# Verificar status e hostnames ativos
neotool-tunnel status

# Parar
neotool-tunnel stop

# Ver logs
neotool-tunnel logs
```

O tunnel é iniciado **on-demand** (não automático) para permitir múltiplos DevPods rodando em paralelo sem conflito.

### Ver logs do agente

```bash
tail -f /tmp/neotool-agent.log
```

### Limpar estado de uma thread

```bash
# Remove state file de uma thread específica (perde issue_number e current_stage)
rm /tmp/bridge-sessions/<thread-id>.json

# Ou edita preservando campos importantes
python3 -c "
import json; f='/tmp/bridge-sessions/<thread-id>.json'
d=json.load(open(f)); d.pop('claude_session', None)
json.dump(d, open(f,'w'), indent=2)"
```

---

## Múltiplos DevPods em Paralelo

Cada DevPod é um container isolado com seu próprio `localhost`. Não há conflito de porta entre containers.

O que precisa ser único por DevPod:
- **Cloudflare Tunnel** — um tunnel ID por DevPod (credenciais em `~/.cloudflare/devpod-N.json`)
- **Cloudflare Worker** — um Worker por DevPod, apontando para o hostname certo
- **Canal Slack** — um `#devpod-N` por DevPod

O que pode ser compartilhado:
- `api-keys.env` (ANTHROPIC_API_KEY, GOOGLE_API_KEY, etc.)
- Bind-mounts de `~/.claude`, `~/.codex`, `~/.vault-keys`
