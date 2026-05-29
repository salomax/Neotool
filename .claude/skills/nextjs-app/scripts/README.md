# Scripts & Commands - nextjs-app

## Web

Rode a partir de `web/` salvo indicação contrária.

```bash
# Dev server
pnpm dev

# Build
pnpm build

# Typecheck
pnpm typecheck

# Lint
pnpm lint

# Testes unitários
pnpm test:unit

# Testes E2E
pnpm test:e2e
```

Confira `web/package.json` antes de rodar porque nomes de scripts podem mudar.

## Codegen GraphQL

```bash
pnpm codegen
```

Rode após alteração de schema GraphQL ou operações consumidas pelo frontend.

## Raiz do Repositório

Na raiz, `package.json` contém comandos Nx agregados, como checks de múltiplos
projetos. Use comandos de módulo durante desenvolvimento para evitar rodadas
desnecessariamente amplas.
