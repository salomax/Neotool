---
name: shared-conventions
description: >
  Convenções cross-cutting que se aplicam a TODO desenvolvimento no Invistus,
  independente de backend ou frontend. Cobre fronteiras de segurança,
  auth/permission enforcement, propagação de erros, feedback ao usuário,
  i18n e padrões que cruzam camadas. Use quando a questão envolver
  auth/permissões, tratamento de erros cross-layer ou internacionalização.
---

# Shared Conventions - Invistus

Convenções que se aplicam a backend e frontend. Carregue os arquivos de
referência apenas quando o tema aparecer na tarefa:

- Dúvidas sobre auth, permissões, RBAC ou fronteiras de segurança: leia [references/auth-permissions.md](references/auth-permissions.md)
- Dúvidas sobre tratamento de erros, toasts, mensagens ao usuário ou XSS: leia [references/error-handling.md](references/error-handling.md)
- Dúvidas sobre i18n, strings visíveis ou localização: leia [references/i18n-strategy.md](references/i18n-strategy.md)

## Regra Fundamental

**Frontend é UX apenas. Backend é autoridade.**
Nunca tome decisões de segurança no frontend. Nunca assuma que uma verificação
de permissão no frontend substitui a validação do backend.

## Regras Não Negociáveis

1. Frontend pode esconder UI por permissão, mas nunca autoriza operação.
2. Toda mutation ou ação persistente precisa dar feedback claro de sucesso e erro.
3. Mensagens para usuário devem passar pela camada de i18n do frontend.
4. Erros de backend não podem vazar stack trace, SQL ou detalhes internos.
