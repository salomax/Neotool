# neotool DevPod — Arquitetura do Pipeline de Desenvolvimento com IA

## Visão Geral

O DevPod é o ambiente de desenvolvimento containerizado do Invistus. Além de rodar os serviços locais, ele hospeda o **neotool-dev-agent** — um orquestrador de agentes de IA que recebe mensagens do Slack e coordena Claude, Codex e Cursor para executar as etapas do pipeline de desenvolvimento.

```
Slack ←──WebSocket──→ neotool-dev-agent → Router → Agentes
         Socket Mode     (slack_bolt)     (Gemini)   claude-dev
                                                      codex-dev
                                                      cursor-dev
```

O agente **abre uma conexão WebSocket outbound** para o Slack (Socket Mode). Não há inbound HTTP — sem Worker, sem Tunnel no caminho de mensagens.

O Cloudflare Tunnel continua presente apenas para **acesso web externo** (ex: `devpod-N-web.invistus.com.br`).

---

## Componentes

### neotool-dev-agent (`/workspaces/invistus/.devcontainer/neotool-dev-agent.py`)

Slack Socket Mode bridge baseado na SDK `slack_bolt`. Mantém conexão WebSocket com o Slack, recebe mensagens, posta "⏳ Trabalhando..." e despacha para o router em background thread.

Também expõe um health server HTTP em `127.0.0.1:3285` para compatibilidade com watchdog e `neotool dev agent status`.

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

## Convenção de Hostnames (apenas acesso web)

O Cloudflare Tunnel serve apenas para acesso web externo ao DevPod. Não é usado para mensagens Slack.

| Serviço | Hostname | Porta interna |
|---|---|---|
| Next.js (frontend) | `devpod-1-web.invistus.com.br` | `3000` |
| (futuro) GraphQL API | `devpod-1-api.invistus.com.br` | `4000` |

---

## Setup de um Novo DevPod

### 1. Criar Slack App (uma vez por DevPod)

No [api.slack.com/apps](https://api.slack.com/apps), crie um novo app:

1. **Nome**: `neotool-devpod-N`
2. **Event Subscriptions** → Subscribe to Bot Events → `message.channels`
3. **Socket Mode** → Enable Socket Mode → gerar **App-Level Token** (`xapp-...`) com scope `connections:write`
4. **OAuth & Permissions** → Bot Token Scopes: `channels:history`, `chat:write`, `channels:read`
5. Instale o app no workspace → copie o **Bot Token** (`xoxb-...`)
6. Convide o app para o canal `#devpod-N`

### 2. Criar `~/.vault-keys/devpod-N.env`

```env
SLACK_APP_TOKEN=xapp-1-...    # App-Level Token (Socket Mode)
SLACK_BOT_TOKEN=xoxb-...      # Bot Token
```

> Cada DevPod tem um arquivo separado: `devpod-1.env`, `devpod-2.env`, etc.

### 3. Configurar `NEOTOOL_DEVPOD_N` no DevPod

No `devcontainer.json`, o `remoteEnv` define qual DevPod é este container:

```json
"remoteEnv": {
  "NEOTOOL_DEVPOD_N": "1"
}
```

Ao criar um DevPod 2, clone o devcontainer.json e mude para `"2"`.

### 4. api-keys.env (compartilhado)

Em `~/.vault-keys/api-keys.env` (compartilhado entre todos os DevPods):

```env
ANTHROPIC_API_KEY=sk-ant-...
OPENAI_API_KEY=sk-...
GOOGLE_API_KEY=...
GH_TOKEN=ghp_...
```

### 5. Cloudflare Tunnel (opcional — apenas para acesso web externo)

```bash
# Criar tunnel (uma vez por DevPod)
cloudflared tunnel create devpod-N
# Salva credenciais em ~/.cloudflare/<tunnel-id>.json

# Salvar token para acesso no container
cloudflared tunnel token <tunnel-id> > ~/.cloudflare/devpod-N.token
```

No dashboard Cloudflare, configure CNAME para `devpod-N-web.invistus.com.br`.

---

## Operação

### Gerenciar o agente (neotool-dev-agent)

```bash
neotool dev agent start     # inicia (carrega api-keys + devpod-N.env automaticamente)
neotool dev agent stop      # para
neotool dev agent restart   # para e reinicia
neotool dev agent logs      # tail -f do log (Ctrl-C para sair)
neotool dev agent attach    # roda em foreground (debug)
neotool dev agent status    # mostra PID e health check
```

### Expor via Cloudflare Tunnel (acesso web externo)

```bash
neotool dev tunnel start 1   # inicia tunnel do devpod-1
neotool dev tunnel status    # mostra URLs ativas
neotool dev tunnel stop      # para
neotool dev tunnel logs      # tail -f
```

O tunnel é iniciado **on-demand** (não automático).

### Limpar estado de uma thread

```bash
# Remove state file de uma thread específica (perde issue_number e current_stage)
rm /tmp/bridge-sessions/<thread-id>.json
```

---

## Múltiplos DevPods em Paralelo

Cada DevPod é um container isolado com seu próprio `localhost`. Não há conflito de porta entre containers.

O que precisa ser único por DevPod:
- **Slack App** — um app por DevPod (cada app só entra no canal `#devpod-N` correspondente)
- **`devpod-N.env`** — tokens Slack específicos do app

O que pode ser compartilhado:
- `api-keys.env` (ANTHROPIC_API_KEY, GOOGLE_API_KEY, etc.)
- Bind-mounts de `~/.claude`, `~/.codex`, `~/.vault-keys`, `~/.cloudflare`
