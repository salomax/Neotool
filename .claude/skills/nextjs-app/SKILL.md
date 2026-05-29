---
name: nextjs-app
description: >
  Padrões de implementação para frontend no Invistus. Use ao construir
  componentes React, páginas Next.js, hooks, queries/mutations GraphQL,
  estilos MUI, i18n ou testes Vitest/Playwright. Trigger: editar web/src/
  ou mobile/src/, criar componentes, adicionar queries/mutations, estilizar
  com MUI ou escrever testes de frontend.
paths: web/src/**, mobile/src/**
---

# Next.js App Patterns - Invistus

Stack: **Next.js App Router + TypeScript + Apollo Client + MUI + next-intl**.

Carregue os arquivos de referência conforme a tarefa:

- Buscando dados com Apollo/GraphQL: leia [references/graphql.md](references/graphql.md)
- Executando mutations create/update/delete: leia [references/mutations.md](references/mutations.md)
- Estilizando componentes com MUI: leia [references/styling.md](references/styling.md)
- Escrevendo testes unitários ou E2E: leia [references/tests.md](references/tests.md)
- Procurando comandos npm/pnpm disponíveis: leia [scripts/README.md](scripts/README.md)

## Regras Não Negociáveis

1. **Zero cores hardcoded**: use tokens do MUI theme ou tokens de `web/src/styles/themes/`.
2. **Tipos gerados obrigatórios**: importe GraphQL de `*.generated.ts` ou `types/__generated__/graphql`.
3. **Mutations com refetch usam `useMutationWithRefetch`** quando afetam listas/tabelas.
4. **Toast obrigatório**: toda mutation mostra sucesso e erro ao usuário.
5. **XSS**: nunca use `dangerouslySetInnerHTML` sem DOMPurify.
6. **i18n**: zero strings pt-BR/en hardcoded em JSX.
7. **TypeScript strict**: não use `any` sem justificativa localizada.

## Estrutura de Diretórios

```text
web/src/
  app/                  # Next.js App Router e rotas
  features/             # módulos por feature quando extraídos da rota
  widgets/              # blocos compostos reutilizáveis
  shared/               # componentes, hooks, providers e utilitários comuns
  lib/graphql/          # client, operations geradas e tipos gerados
  styles/themes/        # tokens e tema MUI

mobile/src/
  features/             # módulos mobile por feature
  shared/               # componentes compartilhados mobile
```

## Checklist Antes de Finalizar

- Zero cores hardcoded em `sx`, CSS ou styles inline.
- Tipos GraphQL gerados usados, sem `gql` raw em componente.
- Mutations com side effect mostram toast de sucesso e erro.
- Strings visíveis usam i18n.
- Sem `dangerouslySetInnerHTML` sem sanitização.
- Teste ou typecheck relevante rodado quando viável.
