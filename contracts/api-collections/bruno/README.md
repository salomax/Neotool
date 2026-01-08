# Bruno collections

Versioned collections to explore and manually test Neotool’s API. This set is meant to help troubleshooting and developer onboarding.

## Structure
- `collections/` groups by domain/endpoint. Starts with `supergraph/` for the GraphQL gateway (based on `contracts/graphql/supergraph/supergraph.yaml`).
- `env.local.example` lists expected variables (copy to `.env.local` that stays out of git).
- `.gitignore` shields secrets and local Bruno caches.

## How to use
1) Install Bruno (app or CLI: `npm i -g @usebruno/cli` or `npx @usebruno/cli@latest`).
2) Create your `.env.local` from the example and set `GRAPHQL_URL` and `AUTH_TOKEN`.
3) Open `contracts/api-collections/bruno` in Bruno, or run via CLI:
   ```sh
   cd contracts/api-collections/bruno
   bruno run collections/supergraph -e local --env-file .env.local
   ```
   Use `-e` to switch environments (`local`, `staging`, `prod` as they are added).

## Maintenance
- When the contract changes (`contracts/graphql/supergraph/supergraph.yaml`), update matching requests in the same PR.
- Prefer env vars for URLs and headers (auth, tracing) instead of hardcoded values.
- At the top of each collection, briefly note purpose and dependencies (required auth, seeds, etc.).
