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

> Para compilar serviços Kotlin + rodar testes E2E, prefira 6 CPUs / 12 GB.

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
colima stop
colima start --cpu 6 --memory 12 --disk 80 --vm-type vz --vz-rosetta
```
