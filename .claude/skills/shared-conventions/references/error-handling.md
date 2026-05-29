# Error Handling

## Frontend: Sempre Mostrar Toast

Toda mutation deve mostrar feedback ao usuário para sucesso e erro.

```typescript
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils";

const toast = useToast();

try {
  await executeMutation(...);
  toast.success(t("messages.saved"));
} catch (error) {
  toast.error(extractErrorMessage(error));
}
```

## XSS: Sanitização Obrigatória

Nunca use `dangerouslySetInnerHTML` sem sanitização explícita.

```typescript
// Proibido.
<div dangerouslySetInnerHTML={{ __html: content }} />

// Permitido somente quando HTML for inevitável.
import DOMPurify from "isomorphic-dompurify";

<div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(content) }} />
```

## Backend: Não Vazar Detalhes Internos

Mensagens de erro retornadas ao cliente não devem expor stack traces, queries
SQL, nomes de tabelas internas, secrets, tokens, paths locais ou detalhes de
implementação. Logue detalhes internamente e retorne uma mensagem segura.

## Gotchas

- Não renderize `error.message` cru em telas públicas se o erro veio do backend.
- Toasts também precisam usar i18n quando a mensagem for controlada pelo frontend.
- Falhas parciais em mutations devem deixar claro o que foi persistido e o que
  precisa de nova tentativa.
