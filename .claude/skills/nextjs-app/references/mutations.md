# Mutations - useMutationWithRefetch

## Regra

Toda mutation que afeta dados de lista, tabela ou tela que depende de uma query
pai deve usar `useMutationWithRefetch`.

```typescript
import { useMutationWithRefetch } from "@/shared/hooks/mutations";
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils";

const toast = useToast();

const { executeMutation } = useMutationWithRefetch({
  refetchQuery: GetTransactionsDocument,
  refetchVariables: { financialAccountId },
  onRefetch: refetch,
  errorMessage: t("messages.updateFailed"),
});

const handleSave = async (data: FormData) => {
  try {
    await executeMutation(UpdateTransactionDocument, { input: data }, uniqueKey);
    toast.success(t("messages.updated"));
  } catch (error) {
    toast.error(extractErrorMessage(error));
  }
};
```

## Por Que Não Usar `useMutation` Direto?

`useMutationWithRefetch` centraliza:

1. Refetch automático da query pai após sucesso.
2. Deduplicação de chamadas concorrentes via `uniqueKey`.
3. Tratamento consistente de erro e estado.

## Gotchas

- Toasts devem ser internacionalizados.
- Não engula erro silenciosamente depois de mutation falhar.
- Use `uniqueKey` estável quando a mesma ação pode ser disparada várias vezes.
- Se a mutation não afeta lista/tabela, ainda precisa de feedback ao usuário.

## Referências Completas

- `docs/07-frontend/patterns/mutation-pattern.md`
- `docs/07-frontend/patterns/toast-notification-pattern.md`
