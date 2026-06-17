---
title: Requirement Workflow — Claude-Driven
type: workflow
category: requirement-management
status: current
version: 1.0.0
date: 2026-06-16
tags: [workflow, requirements, github, issues, claude, refinement]
ai_optimized: true
search_keywords: [requirement, issue, github, refinement, acceptance criteria, branch, backlog]
---

# Requirement Workflow — Claude-Driven

> **Philosophy**: Requisitos nascem de conversas. Claude estrutura, Marcos refina, GitHub registra.

---

## Visão geral

```
Ideia/Problema
     │
     ▼
Thread no Claude  ──→  Claude preenche o template
     │
     ▼
Refinamento com Marcos  ──→  escopo + critérios de aceite
     │
     ▼
Claude cria a Issue no GitHub
     │
     ▼
Claude cria a Branch (convenção GH-N)
     │
     ▼
(Opcional) Claude gera spec SDD em spec/features/
     │
     ▼
Dev implementa
```

---

## Etapas detalhadas

### Etapa 1 — Captura do requisito (informal)

O requisito pode começar de qualquer forma:
- Uma ideia descrita em português coloquial
- Um bug reportado
- Uma melhoria identificada durante uso
- Um item do backlog que precisa ser detalhado

**O Claude não precisa de um formato específico aqui.** A conversa é livre.

**Exemplo de abertura de thread:**
```
"Quero adicionar suporte a PDF com senha no upload de extratos.
Às vezes o banco protege o arquivo e o sistema rejeita sem dar
uma mensagem boa pro usuário."
```

---

### Etapa 2 — Claude estrutura o requisito

Com base na conversa, Claude preenche o [GitHub Issue Template](../91-templates/requirement-templates/github-issue-template.md) e apresenta para revisão.

**O que Claude faz nesta etapa:**
- Identifica o contexto e motivação
- Formula o requisito de forma objetiva
- Propõe critérios de aceite verificáveis
- Define escopo (inclui / não inclui)
- Sugere labels, trilha, esforço estimado
- Proposta de nome de branch

**Claude apresenta o rascunho e pergunta:**
> "Esse escopo está correto? Faltou algum critério de aceite?"

---

### Etapa 3 — Refinamento

Esta etapa é uma conversa na thread. Claude atualiza o template conforme as decisões.

**Perguntas típicas de refinamento:**

- O escopo está certo? O que está fora?
- Os critérios de aceite são verificáveis?
- Há edge cases que precisam ser cobertos?
- Há dependências com outras issues ou [BK-N]?
- Qual a prioridade? (P0/P1/P2/P3)
- Precisamos de uma spec SDD completa ou a issue basta?

**Ao final do refinamento**, o template deve estar completo e aprovado por Marcos.

---

### Etapa 4 — Criação da Issue no GitHub

Com o template aprovado, Claude cria a issue via **GitHub MCP** (se conectado) ou gera o comando `gh` para execução local.

**Via GitHub MCP:**
```
Claude cria a issue diretamente com título, body, labels e assignees.
```

**Via `gh` CLI (fallback):**
```bash
gh issue create \
  --title "[feat] PDF upload com suporte a senha" \
  --body "$(cat /tmp/issue-body.md)" \
  --label "feat,P1,track:black" \
  --repo invistus/invistus
```

Claude registra o número da issue criada para uso na branch.

---

### Etapa 5 — Criação da Branch

Com o número da issue em mãos:

```bash
git checkout -b feat/GH-{número}-{slug}
git push -u origin feat/GH-{número}-{slug}
```

**Exemplo:**
```bash
git checkout -b feat/GH-42-pdf-password-support
git push -u origin feat/GH-42-pdf-password-support
```

Claude executa isso via Bash ou fornece o comando para o dev executar.

---

### Etapa 6 — Spec SDD (quando necessário)

Se a issue for uma feature nova ou mudança de comportamento significativa, Claude cria (ou sugere criar) uma spec SDD:

```bash
# Estrutura sugerida
spec/features/{slug-da-feature}.md
```

A spec segue o formato do [SDD Workflow](../workflows/sdd-workflow.md):
- Problem / Solution
- Success Criteria (espelha os critérios de aceite da issue)
- Integration Points (onde explorar no código)
- Technical Constraints

**Quando NÃO precisar de spec:**
- Bugs simples
- Chores / dependências
- Refactors menores com padrão já estabelecido

---

### Etapa 7 — Linkagem ao Backlog

Se a issue for mapeada a um `[BK-N]` existente, Claude atualiza `docs/00-planning/backlog.md` com o número da issue:

```markdown
#### [BK-4] Phase 4 v3 — PDF Upload com suporte a senha
- **Status:** `todo`
- **GitHub Issue:** #42
- **Branch:** `feat/GH-42-pdf-password-support`
```

Se for um requisito novo (sem BK existente), Claude adiciona uma entrada nova no backlog na prioridade correta.

---

## Estados de uma issue

```
rascunho (thread Claude)
    │
    ▼
em refinamento (thread Claude + Marcos)
    │
    ▼
aprovada → criada no GitHub (status: open)
    │
    ├──→ in progress (branch criada, dev trabalhando)
    │
    ├──→ em revisão (PR aberto)
    │
    └──→ done (PR merged, issue fechada)
```

---

## Instruções para o Claude — como agir nesta thread

Quando Marcos abrir uma thread descrevendo um requisito, Claude deve:

1. **Escutar** sem interromper — deixar Marcos descrever livremente
2. **Perguntar** apenas o essencial se algo for ambíguo (uma pergunta por vez)
3. **Preencher** o template e apresentar de forma clara
4. **Refinar** iterativamente conforme o feedback
5. **Confirmar** antes de criar a issue: _"Posso criar a issue com este conteúdo?"_
6. **Criar** a issue e a branch
7. **Atualizar** o backlog se aplicável
8. **Sugerir** spec SDD se a complexidade justificar

**Tom:** direto, sem burocracia. O objetivo é que criar um requisito bem estruturado seja rápido e natural.

---

## Exemplo completo

### Conversa inicial
```
Marcos: "Quero que o app consiga processar PDFs de extrato que estão
protegidos por senha. O Bradesco às vezes manda assim."
```

### Claude responde com rascunho
```markdown
## Contexto
Extratos do Bradesco frequentemente vêm protegidos por senha,
impedindo o processamento automático. O sistema atual rejeita esses
arquivos sem mensagem de erro clara.

## Requisito
Suporte a upload de PDFs protegidos por senha: o usuário fornece a
senha e o sistema processa normalmente.

## Comportamento esperado
- Upload aceita PDF com senha quando o usuário fornece a senha correta
- Mensagem de erro específica quando a senha está errada
- PDFs sem senha continuam funcionando sem alteração

## Critérios de aceite
- [ ] Campo de senha aparece quando PDF protegido é detectado
- [ ] PDF é processado com sucesso quando senha correta é fornecida
- [ ] Erro claro (não genérico) quando senha incorreta
- [ ] PDFs sem senha processados sem nenhuma mudança de UX
- [ ] Senha não é armazenada após o processamento

## Escopo
Inclui: detecção de PDF protegido, input de senha, processamento
Não inclui: PDFs com múltiplas senhas, senha de edição (apenas abertura)

## Trilha: Black | Tipo: feat | Esforço: S | Serviços: backend + frontend
## Branch sugerida: feat/GH-{N}-pdf-password-support
```

### Refinamento
```
Marcos: "Adiciona também: se o usuário tentar 3 vezes com senha
errada, mostra uma dica de onde achar a senha."
Claude: [atualiza critério de aceite]
```

### Aprovação
```
Marcos: "Ok, pode criar."
Claude: [cria issue #42, cria branch feat/GH-42-pdf-password-support,
         atualiza BK-4 no backlog]
```

---

## Relacionado

- [GitHub Issue Template](../91-templates/requirement-templates/github-issue-template.md)
- [Backlog Master](../00-planning/backlog.md)
- [SDD Workflow](../workflows/sdd-workflow.md)
- [Feature Form](../91-templates/feature-templates/feature-creation/feature-form.md)
