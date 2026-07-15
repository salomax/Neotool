# Error Handling

## Frontend: sempre mostrar toast

Toda mutation DEVE mostrar feedback ao usuário — sucesso e erro.

```typescript
import { useToast } from '@/shared/hooks/useToast';

const { showSuccess, showError } = useToast();

try {
  await executeMutation(...);
  showSuccess('Operação realizada com sucesso');
} catch (error) {
  showError(extractErrorMessage(error));
}
```

## XSS — nunca usar dangerouslySetInnerHTML sem sanitização

```typescript
// ❌ PROIBIDO
<div dangerouslySetInnerHTML={{ __html: content }} />

// ✅ Se HTML for inevitável
import DOMPurify from 'isomorphic-dompurify';
<div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(content) }} />
```

## Backend: erros não devem vazar detalhes internos

Mensagens de erro no backend não devem expor stack traces, queries SQL,
ou detalhes de implementação para o cliente.
