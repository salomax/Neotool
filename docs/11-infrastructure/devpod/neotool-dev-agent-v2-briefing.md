# neotool-dev-agent v2 — Briefing

## O que é

O neotool-dev-agent é o pipeline de desenvolvimento AI do Invistus. Recebe mensagens no Slack, classifica a intenção, e despacha para o agente certo (claude, codex, cursor) executar tarefas de desenvolvimento.

## Arquitetura v1 (atual)

```
Slack → Socket Mode (slack_bolt) → neotool-dev-agent.py → router.py → agente CLI
```

**Pipeline:**
```
📋 issue → 🔍 design → 📝 backlog → ⚙️ impl → 🧐 review → 🧪 validate → 🚀 pr
```

**Componentes:**
- `router.py` — Gemini 2.5 Flash Lite classifica intent + `needs_confirmation`; dispatcha agente
- `neotool-dev-agent.py` — Socket Mode bridge; status estruturado no Slack; progress a cada 30s
- `agents/claude-dev`, `codex-dev`, `cursor-dev` — wrappers dos CLIs com session management
- `fetch_issue_context(N)` — injeta título + body + labels + últimos 5 comentários da issue em todo prompt
- `_setup_impl_branch(N)` — cria `feat/GH-{N}-{slug}` via `gh issue view` antes do impl
- `fetch_branch_diff()` — injeta `git diff main...branch` no review

**Agentes por stage:**
| Stage | Agente | Modelo |
|---|---|---|
| issue, design, backlog | claude | sonnet-4-6 |
| impl | codex | gpt-5.4 |
| review | claude | sonnet-4-6 |
| validate, pr | cursor | auto |

---

## O que funciona bem

- Classificação via Gemini — sem regex de ambiguidade secundário; `needs_confirmation` vem do próprio modelo
- Issue como fonte de verdade — contexto completo injetado em todo dispatch
- Branch automática — codex nunca cria branch errada
- Status estruturado no Slack — sem raw logs, só `⚙️ impl · codex · 01:42`
- Mensagem final com breadcrumb + próximo stage sugerido

---

## Limitações conhecidas (v1 → v2)

### 1. fast_track não orquestra agentes diferentes
`fast_track` manda a mensagem crua para um único agente (claude). Funciona para issue+design+backlog (todos claude), mas não para sequências que incluem codex ou cursor.

### 2. Codex output parsing é frágil
O codex CLI não tem `--output-format json`. O wrapper parseia o último bloco de texto da sessão interativa. Se o formato do CLI mudar, quebra.

### 3. Sem retry nem checkpoint por stage
Se um stage falhar no meio de uma sequência, não há recuperação automática. O usuário precisa reiniciar manualmente.

### 4. stage_review não testado em produção
Recém implementado. O diff truncado em 8000 chars pode perder contexto em PRs grandes.

### 5. Sessão codex por regex
O UUID de sessão é extraído do output com grep. Frágil se o formato mudar.

---

## Direções para v2

### Opção A — Orquestração sequencial no router
`route()` detecta fast_track, itera pelos stages em loop, despacha um por vez, posta update no Slack entre cada um. Robusto mas síncrono — um stage falha, para tudo.

### Opção B — Issue como checkpoint (preferida)
Cada stage posta resultado como comentário na issue. Se a sessão morrer ou um stage falhar, o próximo dispatch lê os comentários e sabe de onde retomar. A issue é o estado persistente, não o `/tmp/`.

### Opção C — Sub-agente orquestrador
Claude recebe o pedido completo e delega para os outros agentes via tool calls. Mais parecido com o Claude Agent SDK. Requer que claude-dev exponha ferramentas para chamar codex-dev e cursor-dev.

### Opção D — Plano de execução aprovado
Antes de rodar, Gemini monta um plano (`[stage_issue, stage_design, stage_backlog]` com agentes e estimativas). Usuário aprova o plano no Slack. Só então executa. Evita surpresas em sequências longas.

---

## Arquivos relevantes

```
.devcontainer/
  neotool-dev-agent.py   # Socket Mode bridge, status Slack, _run()
  router.py              # Classifier, Stage Machine, Dispatcher, fetch_issue_context
  agents/
    claude-dev           # wrapper claude CLI (JSON output parse)
    codex-dev            # wrapper codex CLI (texto parse do último bloco)
    cursor-dev           # wrapper cursor agent CLI
    session-mgr.py       # persiste session UUIDs por agente
    slack-status.sh      # posta status intermediário via curl

scripts/cli/commands/dev.sh   # neotool dev agent start/stop/restart/logs
.devcontainer/start-infra.sh  # carrega api-keys.env + devpod-N.env no boot
.devcontainer/neotool-watchdog # reinicia agent se morrer
```

**Env:**
- `~/.vault-keys/api-keys.env` — OPENAI_API_KEY, GOOGLE_API_KEY, GH_TOKEN
- `~/.vault-keys/devpod-N.env` — SLACK_APP_TOKEN, SLACK_BOT_TOKEN
- `NEOTOOL_DEVPOD_N=1` em `devcontainer.json` remoteEnv

---

## Decisões de design já tomadas

- **ANTHROPIC_API_KEY não necessária** — claude-dev usa CLI OAuth, não API key
- **Cloudflare Tunnel** — mantido só para acesso web (`devpod-N-web.invistus.com.br`), removido do path Slack
- **`set -a; source; set +a`** obrigatório ao carregar env files — `source` simples não exporta para processos filhos
- **`needs_confirmation` via Gemini** — regex de ambiguidade secundário removido; Gemini é a única fonte de verdade
- **Agente explícito nunca confirma** — "codex ...", "@claude" → dispatch direto sem confirmação
