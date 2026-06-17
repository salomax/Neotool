---
title: GitHub Issue Template
type: template
category: requirement-management
status: current
version: 1.0.0
date: 2026-06-16
tags: [template, github, issue, requirements, acceptance-criteria]
ai_optimized: true
search_keywords: [issue, github, requirement, acceptance criteria, backlog, branch]
---

# GitHub Issue Template

> **Purpose**: Padrão para criação de issues no GitHub. Toda issue deve ser refinada com Marcos antes de ir para o backlog ativo. O Claude usa este template nas threads de refinamento para gerar issues prontas para criar via `gh` CLI ou GitHub MCP.

---

## Como usar

1. **Descreva o requisito** na thread do Claude (informal, pode ser em português)
2. **Claude preenche este template** com base na conversa
3. **Revise e refine** com Marcos (escopo, critérios de aceite)
4. **Claude cria a issue** via GitHub MCP ou `gh` CLI
5. **Claude cria a branch** com a convenção definida abaixo

---

## Template de Issue

```markdown
## Contexto

<!-- Por que este requisito existe? Qual problema resolve? Qual o valor de negócio? -->
[Descreva o contexto e motivação]

## Requisito

<!-- O que precisa ser construído, em termos claros e objetivos -->
[Descreva o que deve ser implementado]

## Comportamento esperado

<!-- O que deve acontecer quando o requisito estiver completo? -->
[Descreva o comportamento do sistema após a implementação]

## Critérios de aceite

<!-- Lista objetiva e verificável. Use Given/When/Then ou checkboxes. -->

- [ ] [Critério 1]
- [ ] [Critério 2]
- [ ] [Critério 3]

## Escopo

### Inclui
- [O que está dentro do escopo desta issue]

### Não inclui
- [O que está explicitamente fora do escopo — evita scope creep]

## Informações técnicas

- **Trilha**: <!-- Black (finanças pessoais) | Silver (investimentos) | Infra | Cross-cutting -->
- **Tipo**: <!-- feat | fix | refactor | chore | docs -->
- **Esforço estimado**: <!-- XS (<1h) | S (1-3h) | M (3-8h) | L (dia+) | XL (2+ dias) -->
- **Serviços afetados**: <!-- backend | frontend | mobile | workers | infra -->
- **Spec SDD**: <!-- Link para spec/features/nome-da-feature.md (se existir) -->
- **Backlog ref**: <!-- [BK-N] (se mapeado no backlog.md) -->

## Dependências

- **Bloqueado por**: <!-- #issue ou [BK-N] -->
- **Bloqueia**: <!-- #issue ou [BK-N] -->

## Branch

<!-- Convenção: {tipo}/GH-{número}-{slug-curto} -->
<!-- Exemplo: feat/GH-42-pdf-password-support -->
`{tipo}/GH-{número}-{slug}`

## Notas de refinamento

<!-- Decisões tomadas durante o refinamento com Marcos -->
<!-- Registre aqui qualquer ambiguidade resolvida ou premissa adotada -->
```

---

## Tipos de issue (labels)

| Label | Uso |
|-------|-----|
| `feat` | Nova funcionalidade |
| `fix` | Correção de bug |
| `refactor` | Refatoração sem mudança de comportamento |
| `chore` | Tarefas de manutenção, deps, configs |
| `docs` | Documentação |
| `spike` | Investigação / PoC |

## Labels de prioridade

| Label | Critério |
|-------|----------|
| `P0` | Crítico / bloqueante — para imediatamente |
| `P1` | Próximo sprint |
| `P2` | Planejado |
| `P3` | Futuro / backlog |

## Labels de trilha

| Label | Domínio |
|-------|---------|
| `track:black` | Movimentação financeira, transações, NFC-e |
| `track:silver` | Investimentos, portfólio |
| `track:infra` | Infraestrutura, DevOps |
| `track:cross` | Cross-cutting (auth, billing, observability) |

---

## Convenção de branch

```
{tipo}/GH-{número-da-issue}-{slug-descritivo}
```

**Exemplos:**
```
feat/GH-42-pdf-password-support
fix/GH-87-transaction-duplicate-on-import
refactor/GH-103-transaction-rules-service
chore/GH-12-update-kotlin-deps
```

**Regras:**
- Sempre atrelar ao número da issue criada no GitHub
- Slug em inglês, kebab-case, máximo 5 palavras
- Nunca criar branch antes da issue existir

---

## Critérios de aceite — boas práticas

Prefira critérios **objetivos e verificáveis**:

✅ Bom:
```
- [ ] PDF com senha é processado quando a senha correta é fornecida
- [ ] PDF com senha inválida retorna erro claro ao usuário
- [ ] PDFs sem senha continuam funcionando normalmente
```

❌ Evitar:
```
- [ ] Funciona bem
- [ ] Interface amigável
- [ ] Performance aceitável
```

Para fluxos complexos, use **Given/When/Then**:
```
Given: usuário faz upload de PDF protegido por senha
When: fornece a senha correta
Then: transações são importadas normalmente

Given: usuário faz upload de PDF protegido por senha
When: fornece senha incorreta
Then: mensagem de erro específica é exibida (não erro genérico)
```

---

## Relacionado

- [Requirement Workflow](../../../08-workflows/requirement-workflow.md) — workflow completo de refinamento
- [Backlog](../../00-planning/backlog.md) — backlog master com [BK-N]
- [SDD Workflow](../../workflows/sdd-workflow.md) — como specs SDD se conectam a issues
- [Feature Form](../feature-templates/feature-creation/feature-form.md) — para features maiores que precisam de spec completa
