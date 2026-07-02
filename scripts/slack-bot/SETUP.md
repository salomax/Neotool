# Setup: Slack Bot + Cloudflare Tunnel + Goose

## Visão geral

```
Slack (celular/desktop)
  └─▶ Cloudflare Worker (invistus-slack-bot)
        └─▶ Cloudflare Tunnel (devpod-N.invistus.com.br)
              └─▶ Goose ACP server (porta 8888, dentro do DevPod)
                    └─▶ código do Invistus
```

---

## Pré-requisitos

- Conta Cloudflare com o domínio `invistus.com.br` configurado
- `wrangler` instalado: `npm install -g wrangler` + `wrangler login`
- DevPod rodando (`neotool dev init`)

---

## Passo 1 — Criar o Cloudflare Tunnel

Para cada DevPod, faça no **dashboard Cloudflare**:

1. Acesse **Zero Trust → Networks → Tunnels → Create tunnel**
2. Nome sugerido: `devpod-1`
3. Copie o **tunnel token** exibido
4. Em **Public hostnames**, adicione:
   - Subdomain: `devpod-1`
   - Domain: `invistus.com.br`
   - Service: `HTTP` → `localhost:8888`
5. Salve

Adicione o token em `~/.vault-keys/api-keys.env` no Mac:
```
   =eyJhIjoiMTIzNDU...
GOOSE_SERVER_SECRET=gere-um-secret-aleatorio-aqui
```

Gere o `GOOSE_SERVER_SECRET` com: `openssl rand -hex 32`

---

## Passo 2 — Criar o Slack App

1. Acesse https://api.slack.com/apps → **Create New App** → **From scratch**
2. Nome: `Goose DevPod` | Workspace: seu workspace
3. Em **OAuth & Permissions → Scopes → Bot Token Scopes**, adicione:
   - `chat:write`
   - `chat:write.public`
4. Em **OAuth & Permissions**, clique **Install to Workspace** e copie o **Bot User OAuth Token** (`xoxb-...`)
5. Em **Basic Information**, copie o **Signing Secret**

---

## Passo 3 — Criar KV namespace no Cloudflare

```bash
wrangler kv namespace create SESSIONS
```

Copie o `id` retornado e substitua em `wrangler.toml`:
```toml
[[kv_namespaces]]
binding = "SESSIONS"
id = "SEU_ID_AQUI"
```

---

## Passo 4 — Obter o Channel ID do Slack

Para cada canal DevPod que você criar (ex: `#devpod-1`):
1. Abra o canal no Slack desktop
2. Clique no nome do canal → **Copy link**
3. O ID é a parte final da URL: `https://app.slack.com/client/TXXXXXX/**C12345ABC**`

Edite `wrangler.toml`:
```toml
[vars]
CHANNEL_DEVPOD_MAP = "C12345ABC:devpod-1.invistus.com.br"
# Para múltiplos DevPods:
# CHANNEL_DEVPOD_MAP = "C12345ABC:devpod-1.invistus.com.br,C67890DEF:devpod-2.invistus.com.br"
```

---

## Passo 5 — Deploy do Worker

```bash
cd scripts/slack-bot
npm install

# Adicionar secrets (não ficam no wrangler.toml)
wrangler secret put SLACK_BOT_TOKEN        # cole o xoxb-...
wrangler secret put SLACK_SIGNING_SECRET   # cole o signing secret
wrangler secret put GOOSE_SERVER_SECRET    # mesmo valor do api-keys.env

# Deploy
npm run deploy

✔ Would you like to register a workers.dev subdomain now? … yes
✔ What would you like your workers.dev subdomain to be? It will be accessible at https://<subdomain>.workers.dev … invistus-goose

Deployed invistus-slack-bot triggers (98.88 sec)
  https://invistus-slack-bot.invistus-goose.workers.dev
```

---

## Passo 6 — Configurar Event Subscriptions no Slack

1. No Slack app, vá em **Event Subscriptions → Enable Events**
2. Request URL: `https://invistus-slack-bot.invistus-goose.workers.dev`
3. O Slack vai verificar a URL (o Worker responde ao challenge automaticamente)
4. Em **Subscribe to bot events**, adicione: `message.channels`
5. Salve e reinstale o app no workspace

---

## Passo 7 — Criar canais e convidar o bot

No Slack:
1. Crie o canal `#devpod-1` (ou o nome que quiser)
2. `/invite @Goose DevPod` dentro do canal
3. Anote o Channel ID (Passo 4) e atualize `wrangler.toml`
4. Redeploy: `npm run deploy`

---

## Uso

**Nova tarefa** — manda uma mensagem no canal:
```
cria um endpoint GET /portfolios no banking-service com paginação
```

**Continuação** — responde na mesma thread:
```
adiciona um filtro por data também
```

**Múltiplos DevPods** — use canais separados:
- `#devpod-1` → tarefas no DevPod 1
- `#devpod-2` → tarefas no DevPod 2

---

## Troubleshooting

| Problema | Verificar |
|---|---|
| Bot não responde | `wrangler tail` (logs em tempo real) |
| Goose não conecta | `tail ~/.local/share/goose/acp-server.log` no DevPod |
| Tunnel offline | `tail ~/.local/share/goose/cloudflared.log` no DevPod |
| Sessão perdida | Threads têm 7 dias de validade no KV |

