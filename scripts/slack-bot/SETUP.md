# Setup: DevPod + Slack Channel

Guia para configurar um novo DevPod com pipeline de IA integrado ao Slack.

## Arquitetura

```
Slack → Cloudflare Worker → Cloudflare Tunnel → neotool-dev-agent (:3285)
                                                       └─▶ router.py (Gemini)
                                                             ├─▶ claude-dev
                                                             ├─▶ codex-dev
                                                             └─▶ cursor-dev
```

---

## Pré-requisitos

- Conta Cloudflare com domínio `invistus.com.br`
- `cloudflared` instalado no Mac: `brew install cloudflared`
- `wrangler` instalado: `npm install -g wrangler && wrangler login`
- DevPod rodando (`neotool dev init`)

---

## Passo 1 — Criar o Cloudflare Tunnel

No **dashboard Cloudflare → Zero Trust → Networks → Tunnels → Create tunnel**:

1. Connector: **Cloudflared** → nome: `devpod-N`
2. Em **Public Hostnames**, adicione duas entradas:

   | Subdomain           | Domain          | Service                |
   |---------------------|-----------------|------------------------|
   | `devpod-N-agent`    | `invistus.com.br` | `http://localhost:3285` |
   | `devpod-N-web`      | `invistus.com.br` | `http://localhost:3000` |

3. Copie o **Tunnel ID** (formato UUID) exibido na tela

Salve o token do tunnel no Mac:
```bash
cloudflared tunnel login   # abre browser (uma vez por máquina)
cloudflared tunnel token <tunnel-id> > ~/.cloudflare/devpod-N.token
```

---

## Passo 2 — Criar o Slack App

1. Acesse https://api.slack.com/apps → **Create New App → From scratch**
2. Nome: `Invistus Dev` | Workspace: seu workspace
3. Em **OAuth & Permissions → Bot Token Scopes**, adicione:
   - `chat:write`
   - `chat:write.public`
4. Clique **Install to Workspace** e copie o **Bot Token** (`xoxb-...`)
5. Em **Basic Information**, copie o **Signing Secret**

---

## Passo 3 — Criar canal no Slack

1. Crie o canal `#devpod-N`
2. `/invite @Invistus Dev` dentro do canal
3. Obtenha o **Channel ID**: clique no nome do canal → *Copy link* → o ID é a parte final da URL (`C0BEEESR2DS`)

---

## Passo 4 — Configurar api-keys.env no DevPod

Em `~/.vault-keys/api-keys.env` dentro do DevPod:

```env
ANTHROPIC_API_KEY=sk-ant-...
OPENAI_API_KEY=sk-...
GOOGLE_API_KEY=...
GH_TOKEN=ghp_...
NEOTOOL_AGENT_SECRET=<openssl rand -hex 32>
```

---

## Passo 5 — Deploy do Worker

Configure os secrets e variáveis:

```bash
cd scripts/slack-bot
npm install

# Secrets (não ficam no wrangler.toml)
wrangler secret put SLACK_BOT_TOKEN        # xoxb-...
wrangler secret put SLACK_SIGNING_SECRET   # signing secret do app
wrangler secret put NEOTOOL_AGENT_SECRET   # mesmo valor do api-keys.env
```

Edite `wrangler.toml` para adicionar o canal:
```toml
[vars]
CHANNEL_DEVPOD_MAP = "C0BEEESR2DS:devpod-N-agent.invistus.com.br"
# Múltiplos DevPods (separados por vírgula):
# CHANNEL_DEVPOD_MAP = "C0BEEESR2DS:devpod-1-agent.invistus.com.br,CXXXXXXXX:devpod-2-agent.invistus.com.br"
```

Deploy:
```bash
npm run deploy
# → https://invistus-slack-bot.invistus-goose.workers.dev
```

---

## Passo 6 — Configurar Event Subscriptions no Slack

1. No Slack app → **Event Subscriptions → Enable Events**
2. Request URL: `https://invistus-slack-bot.invistus-goose.workers.dev`
   - O Slack verifica a URL automaticamente
3. Em **Subscribe to bot events**, adicione: `message.channels`
4. Salve e reinstale o app

---

## Passo 7 — Iniciar o DevPod

Dentro do DevPod:

```bash
# Iniciar o bridge (carrega api-keys automaticamente)
neotool dev agent start

# Expor via Cloudflare Tunnel (on-demand)
neotool dev tunnel start N

# Verificar
neotool dev agent status
neotool dev tunnel status
```

---

## Operação diária

```bash
neotool dev agent start|stop|restart|logs|attach|status
neotool dev tunnel start <N>|stop|status|logs
```

O tunnel é **on-demand** — inicie quando quiser receber mensagens do Slack e pare quando não precisar. Múltiplos DevPods podem rodar em paralelo sem conflito.

---

## Troubleshooting

| Problema | Verificar |
|---|---|
| Bot não responde no Slack | Cloudflare Worker → Observability → Logs |
| Request não chega no agent | `neotool dev agent logs` |
| Tunnel offline | `neotool dev tunnel status` e `neotool dev tunnel logs` |
| Dois cloudflared rodando | `ps aux \| grep cloudflared` → mate o antigo |
| SECRET inválido | Confirme que `NEOTOOL_AGENT_SECRET` bate entre `api-keys.env` e Worker |
| Sessão Claude corrompida | `rm /tmp/bridge-sessions/<thread-id>.json` |
