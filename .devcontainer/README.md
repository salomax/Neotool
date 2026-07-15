# Invistus DevPod — Setup Guide

Ambiente de desenvolvimento reproduzível baseado em Dev Container + DevPod.
A mesma definição roda idêntica no MacBook Pro (via Colima) e em qualquer
servidor Linux na nuvem.

---

## Como os serviços ficam disponíveis

O devcontainer usa **Docker-in-Docker**: dentro do workspace existe um daemon
Docker próprio, isolado do host. Os containers que você vê no `docker ps` do
Mac rodam no Docker do host — dentro do DevPod, o `docker ps` começa vazio.

O script `start-infra.sh` (invocado automaticamente no `postStartCommand`)
sobe o **mesmo** `docker-compose.local.yml` que você já usa, de dentro do
workspace. Resultado:

```
DevPod workspace
└── Docker daemon (docker-in-docker)
    ├── neotool-postgres
    ├── neotool-pgbouncer
    ├── neotool-kafka
    ├── neotool-minio
    └── neotool-vault
```

Cada workspace tem seu próprio stack isolado — sem conflito de porta entre
agentes paralelos.

**Serviços que sobem automaticamente** (toda vez que o workspace inicia):
- `database` — postgres + pgbouncer
- `kafka` — Kafka KRaft
- `storage` — MinIO
- `secrets` — Vault (modo dev)

**Serviços que você sobe manualmente conforme necessário:**
- `gateway` — Apollo Router (precisa do supergraph compilado: `neotool graphql generate`)
- `observability` — Grafana, Prometheus, Loki, Tempo (pesado; opcional)
- Serviços Kotlin — em desenvolvimento; rodar com `./gradlew :security:run` etc.

---

## Pré-requisitos — Mac

### 1. Colima

```bash
brew install colima docker docker-compose

# Iniciar com recursos adequados
colima start \
  --cpu 4 \
  --memory 8 \
  --disk 60 \
  --vm-type vz \
  --vz-rosetta          # ARM Mac: habilita Rosetta para imagens x86_64

docker info             # deve mostrar daemon rodando
```

> Para compilar serviços Kotlin + rodar testes E2E com múltiplos serviços, use `./scripts/colima-start.sh` (6 CPUs / 14 GB + 4 GB swap; Mac com 18 GB).

### 2. DevPod

```bash
brew install --cask devpod
```

Ou CLI:

```bash
curl -L -o devpod \
  "https://github.com/loft-sh/devpod/releases/latest/download/devpod-darwin-arm64"
chmod +x devpod && sudo mv devpod /usr/local/bin/
```

Registrar o provider Docker:

```bash
devpod provider add docker
devpod provider use docker
```

### 3. Parar os containers do host (recomendado para paridade)

Os containers do host ficam em standby enquanto o DevPod está ativo para
evitar conflito de porta (ambos tentariam expor 5432, 9092, etc.):

```bash
docker compose \
  -f invistus/infra/docker/docker-compose.local.yml \
  --profile database --profile kafka --profile storage --profile secrets \
  stop
```

---

## Subir o workspace — Local (Mac + Colima)

```bash
cd invistus   # raiz do repo

devpod up . \
  --provider docker \
  --ide vscode          # ou --ide none para SSH puro

# Abrir shell
devpod ssh invistus
```

Ao entrar no workspace, o `start-infra.sh` já terá executado e você verá
os containers rodando:

```bash
docker ps
# neotool-postgres   healthy
# neotool-pgbouncer  healthy
# neotool-kafka      healthy
# neotool-minio      healthy
# neotool-vault      healthy
```

---

## Subir o workspace — Nuvem (provider SSH)

Requisitos do servidor: Ubuntu 22.04+, Docker instalado, usuário com sudo,
chave SSH configurada.

```bash
# Instalar Docker no servidor
ssh user@<VPS_IP> "curl -fsSL https://get.docker.com | sh && sudo usermod -aG docker \$USER"

# Registrar provider SSH
devpod provider add ssh
devpod provider use ssh

# Subir workspace (clone + build + start-infra tudo automático)
devpod up git@github.com:<org>/invistus.git \
  --provider ssh \
  --provider-option HOST=<VPS_IP> \
  --provider-option USER=<SSH_USER> \
  --ide none

devpod ssh invistus
```

---

## N agentes paralelos (escala horizontal)

Cada agente paralelo precisa de workspace + stack de infra próprios.
Com provider de cloud (AWS, GCP, Azure), cada `devpod up` provisiona uma
VM dedicada — sem conflito de porta, Docker daemon separado:

```bash
# Agente 1
devpod up git@github.com:<org>/invistus.git \
  --provider aws \
  --provider-option MACHINE_TYPE=t3.xlarge \
  --name agent-01

# Agente 2 (VM independente)
devpod up git@github.com:<org>/invistus.git \
  --provider aws \
  --provider-option MACHINE_TYPE=t3.xlarge \
  --name agent-02

devpod list
# NAME       STATUS   PROVIDER
# agent-01   Running  aws
# agent-02   Running  aws
```

Cada workspace tem git worktree próprio. Sem conflito de porta (VMs separadas).

> **Custo:** N workspaces = N VMs. Sempre execute `devpod stop <nome>` ao
> terminar. `devpod delete <nome>` para liberar completamente.

---

## Primeiros passos dentro do workspace

```bash
# Conferir infra (já deve estar rodando)
docker ps

# Subir Apollo Router (depois de gerar o supergraph)
neotool graphql generate
docker compose -f infra/docker/docker-compose.local.yml --profile gateway up -d

# Build de um serviço Kotlin
cd service/kotlin
./gradlew :security:shadowJar --build-cache

# Frontend
cd web && npm install && npm run dev

# Teste Playwright headless
cd web && npx playwright test --reporter=list

# neotool CLI
neotool --help
neotool validate --web
```

---

## Ciclo de vida

| Ação | Comando |
|------|---------|
| Subir workspace | `devpod up <repo-ou-dir> --provider <provider>` |
| Abrir shell | `devpod ssh <nome>` |
| Listar workspaces | `devpod list` |
| Parar (preserva dados) | `devpod stop <nome>` |
| Retomar | `devpod up <nome>` |
| Destruir (libera recursos) | `devpod delete <nome>` |
| Rebuild da imagem | `devpod up <nome> --recreate` |

---

## Secrets

Injete via `--env` no `devpod up` — nunca commite no repo:

```bash
devpod up . \
  --provider docker \
  --env ANTHROPIC_API_KEY=sk-... \
  --env GITHUB_TOKEN=ghp_... \
  --env GEMINI_API_KEY=...
```

Use um **PAT fine-grained** do GitHub restrito ao repo Invistus com escopos
`Contents`, `Issues`, `Pull Requests` — sem `admin`, `org` ou `delete`.

O banco de **produção** nunca deve ser acessível no workspace. Use sempre o
Postgres local do `docker-compose.local.yml`.

---

## Configurar o dev-agent (logins e chaves)

O agente `neotool-dev-agent.py` é uma ponte Slack → pipelines de IA (Claude, Codex, Cursor).
Ele precisa de dois tipos de chaves: uma para autenticação com o Slack, e outras para cada agente.

### Autenticação Claude (recomendado)

O agente Claude utiliza a CLI `claude` do sistema, que se autentica via `claude login` (OAuth de assinatura):

```bash
# Dentro do workspace DevPod:
claude login
```

Você será direcionado a `https://claude.ai/` para autenticar com sua conta Anthropic.
A sessão é armazenada em `~/.claude/` e reutilizada automaticamente.

**Alternativa: API Key (headless)**  
Se preferir usar `ANTHROPIC_API_KEY` em vez de login interativo (ex. CI/CD, servidor headless):
```bash
# Em ~/.vault-keys/api-keys.env:
ANTHROPIC_API_KEY=sk-ant-...
```
Note que faturamento de API é separado do plano de assinatura.

### Chaves necessárias do agente

Adicione as seguintes chaves em `~/.vault-keys/api-keys.env` (criado em `postCreateCommand`):

| Variável | Descrição | Escopo |
|----------|-----------|--------|
| `GOOGLE_API_KEY` | Classificador + planejador Gemini (orchestrator). Veja `routing.yaml` linhas 25-30 | [Gemini API](https://aistudio.google.com/apikey) |
| `OPENAI_API_KEY` | Agente Codex (stage_impl). Veja `routing.yaml` linha 49 | [OpenAI API](https://platform.openai.com/api-keys) |
| `GH_TOKEN` | GitHub CLI (`gh`) — cria issues, PRs, checkpoints. PAT fine-grained recomendado | GitHub → Settings → Developer settings → Personal access tokens |
| `ANTHROPIC_API_KEY` | *(Opcional)* Usado se `claude login` não estiver disponível | [Anthropic API](https://console.anthropic.com/account/keys) |

**Formato do arquivo:**
```bash
# ~/.vault-keys/api-keys.env
GOOGLE_API_KEY=AIzaSy...
OPENAI_API_KEY=sk-proj-...
GH_TOKEN=ghp_...
# ANTHROPIC_API_KEY=sk-ant-... (comentado se usar claude login)
```

### Tokens Slack por DevPod

Cada DevPod tem sua própria integração Slack, armazenada em:

```
~/.vault-keys/devpod-{N}.env
```

Onde `{N}` é o número do DevPod (padrão: 1). Configure via `NEOTOOL_DEVPOD_N` se usar múltiplos:

```bash
# ~/.vault-keys/devpod-1.env
SLACK_BOT_TOKEN=xoxb-...
SLACK_APP_TOKEN=xapp-...
```

Verificação de startup (log do agente):
```bash
# O agente valida SLACK_APP_TOKEN.startswith("xapp-") no startup (neotool-dev-agent.py:316)
# Se faltar, o agent não inicia
```

### Iniciar e verificar o agente

```bash
# Dentro do workspace, ou via devpod ssh:
neotool dev agent start        # carrega api-keys.env + devpod-N.env automaticamente
neotool dev agent status       # mostra PID + health check
neotool dev agent logs         # tail do log
neotool dev agent restart      # stop + start
```

**Health check manual:**
```bash
curl http://127.0.0.1:3285/health
# Resposta: {"status": "ok", "agent": "neotool-dev-agent", "mode": "socket", "devpod": "1"}
```

A porta padrão é **3285** (configurável via `NEOTOOL_AGENT_PORT`).
O watchdog (iniciado automaticamente ao start) reinicia o agente a cada 30s se não responder.

---

## Configurar uma nova integração com Slack

Para conectar um novo workspace Slack a este agente:

### 1. Criar um novo Slack App

1. Acesse [api.slack.com/apps](https://api.slack.com/apps)
2. Clique em **Create New App** → **From scratch**
3. Escolha um nome (ex. `neotool-agent`) e selecione o workspace
4. Clique em **Create App**

### 2. Habilitar Socket Mode

1. Na barra lateral, clique em **Socket Mode**
2. Ative **Enable Socket Mode**
3. Você verá um aviso: *"Your app will use WebSocket connections rather than webhooks"*
4. Salve

### 3. Gerar tokens

#### App-Level Token (xapp-*)

1. Ainda em **Socket Mode**, procure por **App-Level Tokens**
2. Clique em **Generate Token and Set Scopes**
3. Escolha um nome (ex. `socket-mode-token`)
4. Procure pelo escopo `connections:write` e marque-o
5. Clique em **Generate**
6. Copie o token — começa com `xapp-` — e guarde temporariamente

**Cole em `~/.vault-keys/devpod-{N}.env`:**
```bash
SLACK_APP_TOKEN=xapp-1-XXXXXXX...
```

#### Bot Token (xoxb-*)

1. Na barra lateral, clique em **OAuth & Permissions**
2. Procure por **Bot Token Scopes** (em "Scopes")
3. Clique em **Add an OAuth Scope** e adicione:
   - `chat:write` — para postar mensagens (neotool-dev-agent.py:229, 94)
   - `channels:history` *(recomendado)* — para ler histórico de canais
   - `groups:history` *(recomendado)* — para ler histórico de grupos privados
   - `im:history` *(recomendado)* — para ler histórico de DMs
4. Salve (a página deve ter atualizado o **Bot User OAuth Token** gerado no topo)
5. Copie o token — começa com `xoxb-`

**Cole em `~/.vault-keys/devpod-{N}.env`:**
```bash
SLACK_BOT_TOKEN=xoxb-XXXX...
```

### 4. Subscrever a eventos de mensagem

1. Na barra lateral, clique em **Event Subscriptions**
2. Ative **Enable Events**
3. Procure por **Subscribe to bot events**
4. Clique em **Add Bot User Event** e adicione:
   - `message.channels` — mensagens em canais públicos (usado via Socket Mode)
   - Opcionalmente: `message.groups`, `message.im` para grupos/DMs
5. Salve

A integração é feita via Socket Mode, então o agente conecta via WebSocket e recebe eventos em tempo real.
(Não configura Webhook URL neste caso.)

### 5. Instalar o app no workspace

1. Na barra lateral, clique em **Install App**
2. Clique em **Install to Workspace**
3. Revise as permissões e clique em **Allow**
4. Você voltará à página e verá o **Bot User OAuth Token** confirmado

### 6. Adicionar o bot a canais

No Slack, convide o bot aos canais onde deseja ouvir:
```
/invite @nome-do-seu-app
```

### 7. Validar a configuração

Dentro do workspace DevPod:

```bash
# Reiniciar o agente para carregar os novos tokens
neotool dev agent restart

# Checar se iniciou sem erros
neotool dev agent logs
# Procure por linhas como:
#   "neotool-dev-agent iniciando (DevPod 1, Socket Mode)"
#   "SLACK_BOT_TOKEN: ✓"
#   "SLACK_APP_TOKEN: ✓"
#   "Health server em http://127.0.0.1:3285/health"

# Testar health check
curl http://127.0.0.1:3285/health
```

### 8. Enviar um teste

No Slack, em um canal onde o bot está:
```
Olá agente
```

Você deve ver:
- ⏳ Trabalhando... (resposta do agente)
- [após alguns segundos] A resposta da IA

Se nada acontecer, verifique:
- Logs do agente: `neotool dev agent logs | tail -50`
- Socket Mode ainda ativo na console: api.slack.com/apps → seu app → Socket Mode
- Bot está no canal: `/invite @seu-app` novamente

---

## Troubleshooting

**`docker ps` vazio após entrar no workspace**
O start-infra pode ter falhado. Execute manualmente:
```bash
.devcontainer/start-infra.sh
```

**`docker: command not found` logo após entrar**
O daemon docker-in-docker ainda está iniciando. Aguarde ~10s e tente novamente.

**Playwright `executable doesn't exist`**
```bash
ls /ms-playwright          # deve listar chromium, firefox, webkit
sudo chmod -R 755 /ms-playwright
```

**Porta já em uso no workspace**
Sinal de que os containers do host ainda estão rodando com as mesmas portas.
Pare os containers do host antes de subir o DevPod (ver passo 3 dos pré-requisitos).

**Colima sem recursos suficientes**
```bash
./scripts/colima-start.sh
# ou manualmente:
colima stop
colima start --cpu 6 --memory 14 --disk 100 --vm-type vz --vz-rosetta
```

**Agente não inicia com erro de SLACK_APP_TOKEN**  
Verifique se o token começa com `xapp-`:
```bash
cat ~/.vault-keys/devpod-1.env | grep SLACK_APP_TOKEN
```
O agente valida esse prefixo no startup (neotool-dev-agent.py:316).

**Agente inicia mas não responde**  
```bash
# Checar que a porta está aberta
curl http://127.0.0.1:3285/health

# Se falhar, checar logs
tail -50 /tmp/neotool-agent.log

# Se OPENAI_API_KEY ou GOOGLE_API_KEY estão faltando, o agente não pode rodar stages
neotool dev agent logs | grep "AUSENTE"
```
