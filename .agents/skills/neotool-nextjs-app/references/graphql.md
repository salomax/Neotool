# GraphQL — Apollo Client / Frontend

## Regra fundamental

**Sempre usar tipos gerados.** Nunca escrever `gql` strings em componentes.

```typescript
// ✅ Correto
import { useGetTransactionsQuery } from '@/lib/graphql/operations/banking/queries.generated';

const { data, loading, error } = useGetTransactionsQuery({
  variables: { financialAccountId },
});

// ❌ Proibido
import { gql, useQuery } from '@apollo/client';
const GET_TRANSACTIONS = gql`query { ... }`;
```

## Onde ficam os arquivos gerados

`web/src/lib/graphql/operations/` — organizados por serviço/domínio.
Arquivos `*.generated.ts` são gerados automaticamente — não editar manualmente.

## Paginação Relay-style

Para queries paginadas, configurar `relayStylePagination` no Apollo cache.

```typescript
// apollo-client.ts
const cache = new InMemoryCache({
  typePolicies: {
    Query: {
      fields: {
        transactions: relayStylePagination(['financialAccountId']),
      },
    },
  },
});
```

## Referência de padrões completos

`docs/02-development/frontend-web.md`
