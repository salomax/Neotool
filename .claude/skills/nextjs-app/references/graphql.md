# GraphQL - Apollo Client / Frontend

## Regra Fundamental

Sempre use tipos e documents gerados. Não escreva `gql` strings dentro de
componentes.

```typescript
// Correto.
import { useGetTransactionsQuery } from "@/lib/graphql/operations/banking/queries.generated";

const { data, loading, error } = useGetTransactionsQuery({
  variables: { financialAccountId },
});

// Proibido em componente.
import { gql, useQuery } from "@apollo/client";
const GET_TRANSACTIONS = gql`query { ... }`;
```

## Onde Ficam os Arquivos Gerados

- Operações compartilhadas: `web/src/lib/graphql/operations/`.
- Tipos globais: `web/src/lib/graphql/types/__generated__/graphql`.
- Algumas features têm `api/*.generated.ts` local. Se já existir padrão local
  na feature, siga o padrão local.

Arquivos `*.generated.ts` não devem ser editados manualmente.

## Paginação Relay-style

Para queries paginadas, configure o cache Apollo conforme o padrão existente.

```typescript
const cache = new InMemoryCache({
  typePolicies: {
    Query: {
      fields: {
        transactions: relayStylePagination(["financialAccountId"]),
      },
    },
  },
});
```

## Quando Rodar Codegen

Rode codegen depois de alterar schema GraphQL do backend ou arquivos de operação
do frontend. Depois ajuste imports e tipos quebrados.

## Referências Completas

- `docs/07-frontend/patterns/graphql-query-pattern.md`
- `docs/07-frontend/patterns/graphql-mutation-pattern.md`
