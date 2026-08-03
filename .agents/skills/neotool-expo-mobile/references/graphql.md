# GraphQL — Apollo Client / Mobile

## Fundamental rule

**Always use generated types.** The mobile app uses GraphQL Code Generator with the
**`client` preset** (see `mobile/codegen.ts`), output to `mobile/src/generated/`.

```typescript
// ✅ Correct — write the operation with the generated `gql`, get a typed document
import { gql } from '@/generated';

const GetInstitutions = gql(`
  query GetInstitutions($search: String) {
    bacenInstitutions(search: $search) { id name codInst }
  }
`);

const { data, loading, error } = useQuery(GetInstitutions, {
  variables: { search },
});
```

- `gql` comes from `@/generated` (the codegen `client` preset), **not** from `@apollo/client`.
- Run `pnpm codegen` (or `codegen:watch`) after adding/changing an operation.
- Never edit files in `mobile/src/generated/` by hand.

> Note: this differs from web, which imports pre-generated hooks from
> `@/lib/graphql/operations/*.generated.ts`. Keep the operation **shape** aligned with the
> web equivalent so a future shared GraphQL library is a lift, not a rewrite (contract drift is a tracked risk — see `docs/02-development/mobile-expo.md`).

## Apollo client is already configured

`mobile/src/lib/apollo-client.ts` provides:

- **Auth link** — injects `Bearer` token read from `expo-secure-store` (`accessToken`).
- **Retry link** and **error link** — `UNAUTHENTICATED` triggers session handling.
- **Persisted cache** — `apollo3-cache-persist` over AsyncStorage for **offline-first reads**.
- **Trace context** — `traceparent` header via `buildTraceparent()`.

Do not create a second client. Consume the shared one through `ApolloProvider`.

## Offline-first reads

Because the cache is persisted, prefer cache-friendly fetch policies for read screens:

```typescript
const { data } = useQuery(GetPortfolio, {
  fetchPolicy: 'cache-and-network',   // instant from cache, refresh in background
  nextFetchPolicy: 'cache-first',
});
```

Show cached data immediately; reflect background refresh with a subtle indicator, not a full-screen spinner.

## Pagination

For growable lists, configure field policies in the cache (`relayStylePagination` or
`offsetLimitPagination`) and render with `FlatList` + `onEndReached`. Never accumulate pages in a `ScrollView`. See native-ux.md.

## Loading / empty / error

Every query-backed screen must handle the three states explicitly: skeletons while loading,
a designed empty state, and a recoverable error state (retry). See native-ux.md.
