---
title: "Estudo — Trocar o SDD atual (docs/ em Markdown) por Obsidian ou outra ferramenta?"
type: study
category: documentation
status: draft
version: 1.0.0
date: 2026-07-30
tags: [sdd, documentation, tokens, agents, obsidian, tooling]
---

# Estudo — Trocar o SDD atual por Obsidian (ou outra ferramenta)?

> **Veredito: não.** Trocar o editor não move nenhum dos três eixos pedidos.
> O gargalo não é a ferramenta de edição — é **higiene** e **política de recuperação**.
> Recomendação: 3 intervenções cirúrgicas no que já existe (Semble/Graphify/skills), custo ~2 dias.

---

## 1. Diagnóstico do estado atual (medido, não estimado)

Medições feitas em `docs/` no commit atual:

| Métrica | Valor | Leitura |
|---|---|---|
| Arquivos `.md` | 323 | — |
| Palavras totais | 645.530 | **~870k tokens** se carregado inteiro (nunca é) |
| Links internos | 1.199 | — |
| **Links internos quebrados** | **390 (32,5%)** | 🔴 1 em cada 3 ponteiros mente |
| Docs tocados nos últimos 60 dias | 41 de 323 (12,7%) | 🔴 87% do corpus está congelado |
| Frontmatter presente | 194 de 324 (60%) | 🟠 metadado inconsistente → RAG ruim |
| Arquivos `README.md` | 26 | 🟠 nome sem entropia para busca |
| Superfície real de skills (`.agents/skills`) | 57 md / 25.085 palavras (**~34k tokens**) | ✅ é isto que o agente realmente lê |

### 1.1 Deriva estrutural

Diretórios que se sobrepõem e competem pelo mesmo assunto:

- `02-architecture/` **vs** `08-architecture/`
- `08-workflows/` **vs** `workflows/`
- `11-infrastructure/` **vs** `infra/`
- `12-specification-driven-dev/` **vs** `workflows/sdd-workflow.md`
- Duplicatas literais: `financial-institution-ratings-spec copy.md`, `graphql-query-pattern.md` (×2),
  toda a série `01-investment-accounts.md … 09-crypto.md` duplicada em dois lugares.

### 1.2 O contrato declarado nunca foi concluído

`docs/workflows/sdd-workflow.md` e `docs/MANIFEST.md` declaram explicitamente:

> `docs/` → para humanos, **NOT loaded as LLM context**
> `spec/` (raiz) → para LLMs, mínimo, ponteiros para código

**`spec/` não existe na raiz do repositório.** Existe apenas `docs/specs/agent-skills/spec.md` (1 arquivo).
Ou seja: o desenho de SDD que está escrito nunca foi implementado. O que ocupou o lugar de `spec/`
na prática foi `.agents/skills/` — e isso funcionou. Skills referenciam `docs/` apenas ~15 vezes
em 20 skills.

**Conclusão do diagnóstico:** `docs/` já está, de fato, **fora do caminho crítico do agente**.
O custo de token do dia a dia não vem de `docs/`. Vem de exploração de código — que já é atacada
por Semble, Graphify, RTK e Headroom.

### 1.3 Onde a assertividade realmente quebra

`docs/99-ai-context/load-by-scenario.md` — o arquivo cuja função é *dizer ao agente o que carregar* —
tem **25 links quebrados** e aponta para `/client/src/...` (o diretório é `web/` há meses),
`docs/02-architecture/backend-structure.md`, `docs/05-testing/`, `docs/10-decisions/` — nada disso existe.

Este é o modo de falha mais caro que existe: **documentação confiante e errada**. O agente segue,
gasta tokens explorando caminhos inexistentes e produz output desalinhado. Não é um problema
de formato Markdown; é um problema de manutenção sem gate automático.

---

## 2. Avaliação do Obsidian

### O que o Obsidian é

Um editor local de Markdown com grafo de backlinks, busca, Dataview e plugins. **Ele lê exatamente
os mesmos arquivos `.md` que já estão em `docs/`.**

### Efeito nos três eixos pedidos

| Eixo | Efeito do Obsidian | Por quê |
|---|---|---|
| **Assertividade** | ⚪ Neutro / 🟠 risco | Não valida nada em CI. O grafo ajuda *o Marcos* a ver órfãos — mas um script de 30 linhas faz o mesmo com gate no PR. |
| **Performance** | ⚪ Zero | O agente não passa pelo Obsidian. Lê o filesystem. |
| **Consumo de tokens** | ⚪ Zero | Token é função da **política de recuperação**, não do editor. Trocar o editor não muda 1 token. |

### Riscos concretos de adotar

1. **Wikilinks `[[nota]]`** — sintaxe nativa do Obsidian, **não resolvível** por agente nem por
   GitHub. Adotar significa trocar 1.199 links Markdown padrão por um formato que quebra fora do vault.
2. **Plugins no repo** — `.obsidian/` versionado vira ruído; não versionado vira config que
   só existe na sua máquina (o resto do time/agentes não tem).
3. **Dataview** — queries que só renderizam dentro do Obsidian. Doc que só é legível na ferramenta
   é o oposto de doc para agente.
4. **Não resolve o problema real** — os 390 links quebrados continuam quebrados no dia seguinte.

### Ressalva justa

As referências públicas de 2026 que reportam ganho com "Obsidian + agentes" (ex.: [agentcairn](https://github.com/ccf/agentcairn),
[Obsidian como contexto persistente para Claude Code](https://aiartimind.com/reduce-ai-token-costs-how-to-use-obsidian-as-a-persistent-context-for-claude-code/))
**não estão medindo o Obsidian** — estão medindo o **índice semântico + MCP + carregamento em tiers**
que foi construído em cima do vault. O ganho citado (contexto ~262× menor que carregar o vault inteiro)
vem da **recuperação indexada**, não do editor.

**Você já tem essa camada: Semble (busca semântica) + Graphify (grafo de conhecimento) + skills com
divulgação progressiva.** Adotar Obsidian seria comprar o invólucro de algo que já está montado.

---

## 3. Alternativas avaliadas

| Opção | Assertividade | Performance | Tokens | Veredito |
|---|---|---|---|---|
| **Manter `docs/` + fechar o contrato SDD** | 🟢 Alta | 🟢 | 🟢 | ✅ **Recomendado** |
| **Obsidian** | ⚪ | ⚪ | ⚪ | ❌ Erro de categoria |
| **GitHub Spec Kit** (specify CLI) | 🟢 | 🟠 | 🔴 | ⚠️ Ver nota abaixo |
| **Amazon Kiro / BMAD-METHOD** | 🟢 | 🟠 | 🟠 | ❌ Troca de IDE/método inteiro; custo alto |
| **Notion / Confluence** | 🔴 | 🔴 | 🔴 | ❌ Tira o doc do repo; agente perde acesso local |
| **Wiki gerada (Docusaurus/MkDocs)** | 🟠 | ⚪ | ⚪ | ⚪ Resolve navegação humana, não o eixo do agente |

**Nota sobre Spec Kit:** é o padrão de mercado 2026 para SDD (Specify → Plan → Tasks → Implement),
e o seu fluxo `neotool-solution-design → neotool-backlog-gen → neotool-implement` **já é isso**,
customizado para o Invistus. Adotar Spec Kit seria substituir algo sob medida por algo genérico.
Além disso, o modelo do Spec Kit relê spec+plan+tasks a cada turno — as próprias fontes reportam
**20–40% mais gasto de API** que codificação livre. Vai contra o seu eixo de tokens.

---

## 4. Recomendação — 3 intervenções, por ROI

### A. Gate de higiene em CI (~4h) — **maior ROI de longe**

Script + step no GitHub Actions que falha o PR se:

- link interno quebrado (hoje: 390);
- `.md` novo sem frontmatter (`title`, `status`, `date`, `tags`);
- `status: current` em doc sem commit há > 180 dias → rebaixa para `stale`.

Ataca diretamente **assertividade**, que é onde a dor real está.

### B. Fechar o contrato `docs/` vs superfície do agente (~1 dia)

O desenho já está escrito — só falta executar:

1. Declarar `.agents/skills/` como **a** superfície do agente (é o que já acontece de fato).
2. Marcar `docs/` como **human-only** no `AGENTS.md`, com exceção explícita da allowlist que
   as skills referenciam (~15 arquivos).
3. Arquivar em `docs/95-archive/` os ~180 docs sem commit desde Q1. Não deletar — mover e
   marcar `status: archived`. Some do grafo de busca, some dos links quebrados, para de mentir.
4. Consolidar os pares duplicados (`02-`/`08-architecture`, `08-workflows`/`workflows`,
   `11-infrastructure`/`infra`) e apagar os `copy.md`.

### C. Terminar o wiring de recuperação que você já escolheu (~2h)

`graphify-out/` não existe neste checkout — o grafo não está construído aqui.
Rodar `/graphify .` e garantir que Semble e Graphify indexem **código + docs**. Isto é,
literalmente, a camada que os artigos sobre "Obsidian + agentes" estão medindo — sem trocar nada.

### Opcional, sem custo e sem risco

Se quiser o grafo visual para navegação **humana**: abra `docs/` como vault do Obsidian
em modo leitura. Zero mudança no repo — desde que **não** commite `.obsidian/`, não adote
wikilinks `[[ ]]` e não use Dataview em docs versionados.

---

## 5. Resposta direta

**Não é válido trocar hoje.** O Obsidian é um editor; o seu problema é que 1 em cada 3 ponteiros
está quebrado e 87% do corpus está congelado desde o primeiro trimestre. Nenhum editor conserta isso —
um gate de CI conserta, e custa uma tarde.

A camada que de fato reduz token e aumenta assertividade — recuperação indexada com divulgação
progressiva — **você já escolheu e já montou** (Semble + Graphify + skills + RTK + Headroom).
Falta terminar de ligar e limpar o que ficou para trás.

---

**Fontes externas consultadas:**

- [agentcairn — memória de longo prazo para agentes usando vault Obsidian](https://github.com/ccf/agentcairn)
- [Reduce AI Token Costs: Obsidian como contexto persistente para Claude Code](https://aiartimind.com/reduce-ai-token-costs-how-to-use-obsidian-as-a-persistent-context-for-claude-code/)
- [AI Agents Token Optimization & Obsidian Second Brain 2026 — Moksoft](https://moksoft.com/blog/ai-agents-token-optimization-obsidian-llm-second-brain)
- [GitHub Spec Kit — documentação oficial](https://github.github.com/spec-kit/)
- [Spec-Driven Development in 2026: tooling e uso real](https://dev.to/krlz/spec-driven-development-in-2026-what-it-is-the-tooling-and-how-teams-actually-use-it-2fk2)
- [6 Best Spec-Driven Development Tools for AI Coding in 2026 — Augment Code](https://www.augmentcode.com/tools/best-spec-driven-development-tools)
