---
title: "Projeto SDD-SOTA — Prompt de execução para agente"
type: project-brief
category: documentation
status: draft
version: 1.0.0
date: 2026-07-30
tags: [sdd, agents, tokens, retrieval, project, prompt]
related: [estudo-sdd-tooling-2026-07.md]
---

# Projeto SDD-SOTA — Prompt de execução

> **Como usar:** cole o bloco inteiro abaixo (seções 1–10) como prompt inicial de um agente
> com acesso de escrita ao repositório Invistus. O agente deve executar fase a fase,
> parando em cada gate para aprovação humana.

---

## 1. Missão

Elevar o sistema de Specification-Driven Development (SDD) do repositório Invistus ao
estado da arte, otimizando três eixos **mensuráveis**, nesta ordem de prioridade:

1. **Assertividade** — o agente encontra a informação certa e ela está correta.
2. **Consumo de tokens** — menos tokens no caminho quente para a mesma tarefa.
3. **Performance** — menos turnos e menos exploração cega até a primeira edição correta.

Você **não** vai adotar ferramenta nova de edição (Obsidian, Notion, Spec Kit, Kiro).
A decisão já foi tomada e está justificada em `docs/00-planning/estudo-sdd-tooling-2026-07.md`.
Você vai **reestruturar o que existe**.

---

## 2. Contexto medido (baseline em 2026-07-30 — não precisa re-derivar, mas confirme)

### Repositório

- Monorepo Nx. Backend Kotlin/Micronaut em `service/kotlin/`. Web Next.js em `web/`.
  Mobile React Native/Expo em `mobile/`. Contratos em `contracts/` e `docs/06-contracts/`.
- **Não existe `client/`** — vários docs ainda apontam para lá. É `web/`.
- Superfície real do agente: `.agents/skills/` (symlink de `.claude/skills`), 20 skills,
  57 arquivos `.md`, ~25.085 palavras (~34k tokens).
- `AGENTS.md` na raiz. **Não existe `CLAUDE.md`.**
- Stack de economia de contexto já instalada: RTK (`RTK.md`), Semble (busca semântica),
  Graphify (grafo de conhecimento), Headroom (proxy). Ver `AGENTS.md`.

### `docs/`

| Métrica | Baseline |
|---|---|
| Arquivos `.md` | 323 |
| Palavras | 645.530 (~870k tokens) |
| Links internos | 1.199 |
| **Links quebrados** | **390 (32,5%)** |
| Docs com commit nos últimos 60 dias | 41 (12,7%) |
| Frontmatter presente | 194/324 (60%) |
| `README.md` homônimos | 26 |

### Dívidas estruturais conhecidas

- Diretórios concorrentes: `02-architecture` vs `08-architecture`; `08-workflows` vs `workflows`;
  `11-infrastructure` vs `infra`; `12-specification-driven-dev` vs `workflows/sdd-workflow.md`.
- Duplicatas literais: `financial-institution-ratings-spec copy.md`, `graphql-query-pattern.md` (×2),
  série `01-investment-accounts.md … 09-crypto.md` duplicada.
- `docs/99-ai-context/load-by-scenario.md` — o roteador de contexto — tem **25 links quebrados**.
- `docs/workflows/sdd-workflow.md` e `docs/MANIFEST.md` declaram que `docs/` **não** é contexto de LLM
  e que `spec/` (raiz) é a superfície do agente. **`spec/` nunca foi criado.** O papel foi ocupado
  de fato por `.agents/skills/`. Você vai **formalizar a realidade**, não ressuscitar `spec/`.

---

## 3. Restrições inegociáveis

1. **Nada de deletar histórico.** Docs obsoletos são **movidos** para `docs/95-archive/` com
   `status: archived` no frontmatter. Git preserva, mas o arquivo tem que sair do caminho de busca.
2. **Um PR por fase.** Nunca misture fases. Cada PR precisa passar no gate da própria fase.
3. **Sem wikilinks `[[ ]]`, sem Dataview, sem `.obsidian/`.** Markdown padrão, links relativos.
4. **Nenhuma mudança de comportamento de código.** Este projeto move e reescreve documentação,
   frontmatter, skills e scripts de CI. Se precisar tocar código de produção, **pare e pergunte**.
5. **Toda métrica declarada tem que ser reproduzível por script versionado** em `scripts/docs/`.
   "Melhorou" sem número não conta.
6. **Idioma:** documentação técnica em inglês; briefs de planejamento em `00-planning/` podem ser PT-BR.
   Siga o padrão do arquivo vizinho.

---

## 4. Fases

Execute em ordem. **Não avance sem passar no gate.**

---

### Fase 0 — Baseline e instrumentação

**Objetivo:** tornar tudo mensurável antes de mudar qualquer coisa. Sem isto, o resto é fé.

**Ações**

1. Criar `scripts/docs/audit.py` que emite JSON com: contagem de arquivos, palavras, tokens estimados,
   links internos totais/quebrados (com lista), cobertura de frontmatter, docs sem commit há > 180 dias,
   arquivos órfãos (nenhum link aponta para eles), basenames duplicados.
2. Criar `scripts/docs/eval/questions.yaml` com **20 perguntas-teste** representativas do trabalho real.
   Cubra os dois backends de conhecimento (docs e código) e as três camadas. Exemplos obrigatórios:
   - "Como crio um resolver GraphQL com checagem de permissão no banking service?"
   - "Qual o padrão de mutation no web, incluindo feedback obrigatório ao usuário?"
   - "Onde ficam os schemas Kafka do import de PDF de fatura e qual o contrato do evento `done`?"
   - "Qual o padrão de migration Flyway e a convenção de UUID v7?"
   - "Como escrevo um teste E2E Playwright para uma tela de transações?"
   Cada pergunta tem: `expected_files` (caminhos que a resposta correta deve citar) e
   `forbidden_files` (caminhos obsoletos que indicam falha).
3. Rodar o eval no estado atual. Registrar por pergunta: acerto (sim/não), tokens consumidos,
   número de turnos, arquivos lidos.
4. Gravar tudo em `docs/00-planning/sdd-baseline-2026-07.json` + resumo em Markdown.

**Gate 0 (validação)**

- [ ] `scripts/docs/audit.py` roda limpo e reproduz os números da seção 2 (±2%).
- [ ] 20 perguntas rodadas, com métrica de acerto/tokens/turnos registrada.
- [ ] Baseline commitado. **Este arquivo é o juiz de todas as fases seguintes.**

---

### Fase 1 — Higiene e consolidação

**Objetivo:** parar a sangria. Eliminar os 390 links quebrados e os 87% de corpus congelado.

**Ações**

1. **Arquivar:** mover para `docs/95-archive/` (preservando o subcaminho) todo doc sem commit
   desde 2026-04-30 **e** não referenciado por nenhuma skill, `AGENTS.md` ou doc ativo.
   Marcar `status: archived` e adicionar `archived_date`.
   Gere a lista candidata e **apresente para aprovação humana antes de mover**.
2. **Consolidar diretórios concorrentes:** fundir `08-architecture` → `02-architecture`;
   `workflows` → `08-workflows`; `infra` → `11-infrastructure`;
   `12-specification-driven-dev` → `08-workflows/sdd/`. Deixe stub de redirecionamento
   apenas onde houver link externo (fora de `docs/`) apontando para o caminho antigo.
3. **Deduplicar:** remover `* copy.md`; para pares reais, manter o mais recente e arquivar o outro.
4. **Consertar links:** corrigir os 390. Regras conhecidas: `/client/src/**` → `web/src/**`;
   caminhos para docs inexistentes → apontar para o doc consolidado ou remover o link
   (link removido é melhor que link mentiroso).
5. **Reescrever `docs/99-ai-context/load-by-scenario.md`** com os caminhos reais. Ele será
   substituído por mecanismo automático na Fase 2 — nesta fase, apenas torne-o verdadeiro.
6. **Gate de CI:** `scripts/docs/check.py` + step no GitHub Actions que falha o PR se:
   link interno quebrado; `.md` novo sem frontmatter obrigatório (`title`, `type`, `status`, `date`, `tags`);
   `status: current` em doc sem commit há > 180 dias.

**Gate 1 (validação)**

- [ ] `audit.py`: **0 links quebrados**.
- [ ] Cobertura de frontmatter em docs ativos: **100%**.
- [ ] Zero basenames duplicados fora de `README.md`.
- [ ] CI falha comprovadamente num PR de teste com link quebrado proposital (mostre o run vermelho).
- [ ] Re-rodar eval: acerto **≥ baseline**, `forbidden_files` citados = **0**.

---

### Fase 2 — Roteador de contexto via frontmatter (`applies_to`)

**Objetivo:** substituir roteamento manual por resolução automática. É a fase de maior ROI/custo.

**Ações**

1. Definir o contrato em `docs/91-templates/frontmatter-contract.md`:
   ```yaml
   applies_to:            # globs de código que este doc governa
     - "service/kotlin/**/resolver/*.kt"
   authority: normative   # normative | reference | historical
   supersedes: []         # caminhos que este doc torna obsoletos
   token_cost: 1200       # estimado, para orçamento de contexto
   ```
   - `normative` = regra a seguir. `reference` = explicação. `historical` = ADR/contexto, não seguir.
2. Preencher `applies_to` em **todos os docs `normative`** — comece por `07-frontend/patterns/`
   (12 arquivos, escopo controlado) como piloto, valide, depois estenda para `05-backend/patterns/`,
   `04-domain/`, `06-contracts/`, `09-security/`.
3. Criar `scripts/docs/context-for.py <arquivo-ou-glob>` que resolve: dado um caminho de código,
   retorna a lista ordenada de docs normativos aplicáveis + custo de token somado.
4. Reescrever `docs/99-ai-context/load-by-scenario.md` como **índice gerado** por esse script
   (arquivo gerado, com header `<!-- GENERATED — do not edit -->`, regenerado no CI).
5. Atualizar `AGENTS.md`: instruir o agente a rodar `context-for.py` antes de editar um arquivo,
   em vez de carregar docs por intuição.

**Gate 2 (validação)**

- [ ] Piloto: para os 12 patterns de `07-frontend/`, `context-for.py web/src/features/**/x.tsx`
      retorna exatamente o conjunto que um humano escolheria (revise à mão, documente divergências).
- [ ] 100% dos docs `authority: normative` têm `applies_to` não vazio.
- [ ] Nenhum glob de `applies_to` aponta para caminho inexistente (checado no CI).
- [ ] Re-rodar eval: **tokens por pergunta ≤ 70% do baseline**, acerto ≥ baseline.

---

### Fase 3 — Co-location (doc junto do código)

**Objetivo:** tornar o drift estruturalmente improvável. Doc que vive ao lado do código morre no mesmo PR.

**Ações**

1. Classificar cada doc ativo em:
   - **Local** (governa um módulo específico) → move para junto do código.
   - **Transversal** (ADR, glossário, domínio, runbook, onboarding) → **permanece em `docs/`**.
2. Mover os locais, mantendo `applies_to`:
   - `docs/05-backend/patterns/*` → `service/kotlin/<módulo>/docs/`
   - `docs/07-frontend/patterns/*` → `web/docs/patterns/`
   - `docs/07-frontend/mobile/*`, `standards/mobile-*` → `mobile/docs/`
   - `docs/03-features/<feature>/*` → junto do módulo dono, quando houver um dono claro;
     se a feature atravessa camadas, **mantenha em `docs/03-features/`** e registre por quê.
3. Deixar em `docs/README.md` um mapa curto (≤ 60 linhas) de "o que ficou onde e por quê".
4. Adicionar regra no CI: PR que altera `service/kotlin/<m>/**` sem tocar `service/kotlin/<m>/docs/**`
   emite **warning** (não falha) quando o diff excede N linhas — sinal de drift, não bloqueio.

**Gate 3 (validação)**

- [ ] `audit.py`: 0 links quebrados após a movimentação.
- [ ] `context-for.py` continua resolvendo corretamente (rode a suíte do Gate 2 de novo).
- [ ] Semble e Graphify reindexam sem erro; `graphify query` responde às 5 perguntas de arquitetura do eval.
- [ ] Re-rodar eval: acerto ≥ baseline, **turnos até a primeira edição correta ≤ 80% do baseline**.

---

### Fase 4 — Contract-first: doc gerado em vez de escrito

**Objetivo:** eliminar por construção a classe de erro "doc repete a verdade e diverge dela".

**Ações**

1. Mapear toda duplicação manual de fonte formal já existente:
   schema GraphQL, migrations Flyway, JSON schemas Kafka (`docs/06-contracts/kafka/**`),
   enums, feature flags Unleash.
2. Para cada uma: substituir o texto duplicado por **referência ao artefato**
   (caminho + âncora) ou por **bloco gerado** delimitado por
   `<!-- BEGIN GENERATED: <fonte> -->` … `<!-- END GENERATED -->`.
3. Criar `scripts/docs/generate.py` que regenera todos os blocos a partir das fontes.
4. CI: falha se `generate.py --check` detectar bloco gerado fora de sincronia.

**Gate 4 (validação)**

- [ ] `generate.py --check` verde.
- [ ] Teste de mutação: altere um campo do schema GraphQL numa branch descartável;
      o CI **precisa** falhar apontando o doc dessincronizado. Anexe a evidência.
- [ ] Contagem de palavras de `docs/06-contracts/` reduzida sem perda de informação recuperável.

---

### Fase 5 — Divulgação progressiva nas skills

**Objetivo:** encolher o caminho quente. Hoje as skills somam ~34k tokens.

**Ações**

1. Auditar as 20 skills. Para cada uma, medir o tamanho do corpo (`SKILL.md`) vs `references/`.
2. Refatorar para o padrão: **corpo ≤ 500 palavras**, contendo apenas
   (a) quando ativar, (b) regras invioláveis, (c) roteamento para `references/` e para `context-for.py`.
   Todo detalhe procedimental vai para `references/*.md`, carregado sob demanda.
   `neotool-conventions` já segue esse padrão — use como referência.
3. Remover das skills qualquer conteúdo que agora seja resolvível por `context-for.py`.
   **Skill não deve duplicar doc.**
4. Verificar que nenhuma skill referencia caminho movido nas Fases 1–3.

**Gate 5 (validação)**

- [ ] Soma dos corpos de `SKILL.md`: **≤ 12k tokens** (baseline ~34k incluindo references).
- [ ] Nenhuma skill referencia caminho inexistente (checado no CI).
- [ ] Re-rodar eval: acerto ≥ baseline **e** tokens ≤ 60% do baseline.
- [ ] Teste de ativação: para cada skill, 3 prompts que **devem** ativá-la e 3 que **não devem**.
      Sem falso negativo; no máximo 1 falso positivo no total.

---

### Fase 6 — Specs executáveis

**Objetivo:** fechar o loop. Pattern que muda quebra teste, e o teste força o doc a mudar.

**Ações**

1. Para cada doc `authority: normative` com bloco de código: extrair o snippet de um
   teste/arquivo real, ou marcá-lo como `<!-- illustrative -->` se não for extraível.
2. Criar `scripts/docs/verify-snippets.py`: para snippets com `source:` no fence
   (ex.: ```kotlin source=service/kotlin/asset/resolver/AssetResolver.kt#L20-L45),
   verificar que o trecho ainda bate com a fonte.
3. CI: falha em snippet dessincronizado.

**Gate 6 (validação)**

- [ ] ≥ 70% dos snippets em docs normativos têm `source:` verificável; o resto marcado como ilustrativo.
- [ ] Teste de mutação: altere o código-fonte de um snippet; CI **precisa** falhar. Anexe evidência.

---

### Fase 7 — Fechamento e relatório

**Ações**

1. Rodar `audit.py` + eval completo. Produzir `docs/00-planning/sdd-resultado-2026-XX.md`
   com tabela **baseline vs final** para: links quebrados, cobertura de frontmatter,
   palavras ativas, tokens do caminho quente, acerto do eval, tokens/pergunta, turnos/tarefa.
2. Atualizar `AGENTS.md` e `docs/08-workflows/sdd/` com o fluxo final (o contrato agora é real).
3. Registrar ADR em `docs/92-adr/` documentando a decisão de arquitetura de documentação
   (inclui a rejeição de Obsidian/Spec Kit e o porquê).
4. Propor cadência de manutenção: `audit.py` semanal no CI, eval mensal.

**Gate 7 (definição de pronto)**

- [ ] 0 links quebrados; 100% frontmatter em docs ativos.
- [ ] Tokens do caminho quente **≤ 60% do baseline**.
- [ ] Acerto do eval **≥ baseline + 15 pontos percentuais**; `forbidden_files` = 0.
- [ ] Todos os gates de CI ativos e comprovadamente vermelhos quando violados.
- [ ] ADR registrado.

---

## 5. Como validar (regras gerais)

- **Toda validação é script ou evidência anexada.** Nunca "revisei e está bom".
- **Teste de mutação é obrigatório** em todo gate de CI: quebre de propósito e mostre o build vermelho.
  Gate que nunca falhou não é gate.
- **O eval é o juiz final.** `audit.py` mede higiene; só o eval mede assertividade.
- **Regressão = rollback.** Se uma fase piorar acerto ou tokens, reverta o PR e reporte a causa
  antes de tentar de novo.

---

## 6. Riscos e como mitigar

| Risco | Mitigação |
|---|---|
| Arquivar doc ainda útil | Lista candidata aprovada por humano antes de mover; nada é deletado |
| Co-location espalha demais e ninguém acha | `docs/README.md` com mapa; `context-for.py` como ponto de entrada único |
| `applies_to` vira burocracia abandonada | CI valida globs; doc normativo sem `applies_to` falha o build |
| Eval enviesado (perguntas fáceis demais) | 20 perguntas fixadas na Fase 0, **antes** de qualquer mudança; proibido editá-las depois |
| Fases se misturam e a causa do ganho some | Um PR por fase, eval rodado ao fim de cada uma |

---

## 7. Anti-padrões (não faça)

- Não introduza Obsidian, Notion, Spec Kit, Kiro ou BMAD.
- Não crie `spec/` na raiz — `.agents/skills/` já é a superfície do agente.
- Não use wikilinks `[[ ]]`, Dataview ou qualquer sintaxe que só renderize numa ferramenta.
- Não delete arquivos: arquive.
- Não escreva doc novo nas Fases 1–3. O objetivo é **reduzir e corrigir**, não produzir.
- Não toque em código de produção. Se for necessário, pare e pergunte.

---

## 8. Formato de reporte por fase

Ao terminar cada fase, produza:

```
## Fase N — <nome>
**PR:** #<n>
**Ações executadas:** <lista curta>
**Métricas:** <tabela antes/depois>
**Gate:** <cada checkbox com evidência: link do run de CI, output do script>
**Desvios do plano:** <o que mudou e por quê>
**Bloqueios / decisões necessárias:** <perguntas para o humano>
```

Pare e aguarde aprovação. Não inicie a fase seguinte sozinho.

---

## 9. Leitura obrigatória antes de começar

- `docs/00-planning/estudo-sdd-tooling-2026-07.md` — diagnóstico e justificativa das decisões
- `AGENTS.md` — RTK, Semble, Graphify, Headroom
- `.agents/skills/neotool-conventions/references/token-discipline.md`
- `.agents/skills/neotool-conventions/references/semble-search.md`
- `docs/workflows/sdd-workflow.md` e `docs/MANIFEST.md` — o contrato declarado que você vai reconciliar
- `docs/99-ai-context/load-by-scenario.md` — o roteador quebrado que você vai substituir

---

## 10. Primeira ação

Não comece a mexer. Comece assim:

1. Confirme os números da seção 2 rodando as medições você mesmo. Reporte divergências.
2. Apresente o **rascunho das 20 perguntas do eval** para aprovação humana.
3. Só depois de aprovadas, execute a Fase 0.
