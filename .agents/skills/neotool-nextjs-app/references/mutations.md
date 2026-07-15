# Mutations — useMutationWithRefetch

## Regra

Toda mutation que afeta dados de lista/tabela DEVE usar `useMutationWithRefetch`.

```typescript
import { useMutationWithRefetch } from '@/shared/hooks/mutations';
import { useToast } from '@/shared/hooks/useToast';

const { showSuccess, showError } = useToast();

const { executeMutation } = useMutationWithRefetch({
  refetchQuery: GetTransactionsDocument,
  refetchVariables: { financialAccountId },
  onRefetch: refetch,
  errorMessage: 'Falha ao atualizar transação',
});

// No handler:
const handleSave = async (data: FormData) => {
  try {
    await executeMutation(UpdateTransactionMutation, { input: data }, uniqueKey);
    showSuccess('Transação atualizada com sucesso');
  } catch (error) {
    showError(extractErrorMessage(error));
  }
};
```

## Por que não usar `useMutation` diretamente?

`useMutationWithRefetch` garante:
1. Refetch automático da query pai após sucesso
2. Deduplicação de chamadas concorrentes via `uniqueKey`
3. Tratamento de erro consistente

## Referência completa

`docs/07-frontend/patterns/mutation-pattern.md`
