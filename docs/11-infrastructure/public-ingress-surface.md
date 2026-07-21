# Public Ingress Surface

Production public HTTP access is routed through the Flux-managed Kubernetes
Ingress resources in `invistus-flux/infra/kubernetes/flux/`. Services remain
`ClusterIP`; public access exists only where an Ingress explicitly allows it.

| Host | Public paths | Owning service | Notes |
| --- | --- | --- | --- |
| `api.invistus.com.br` | `/graphql` | `neotool-graphql-router` | GraphQL traffic continues through the Apollo Router. |
| `auth.invistus.com.br` | `/oauth2/authorize`, `/oauth2/token`, `/oauth2/register`, `/oauth2/login`, `/oauth2/consent`, `/oauth2/assets`, `/.well-known/oauth-authorization-server`, `/.well-known/jwks.json` | `neotool-security` | OAuth 2.1 authorization server and browser login/consent surface. The Ingress must not expose `/`, `/graphql`, `/health`, or management endpoints. |
| `mcp.invistus.com.br` | MCP Streamable HTTP endpoint, `/.well-known/oauth-protected-resource` | MCP service | DNS is provisioned ahead of #416. The MCP Deployment, Service, and Ingress are owned by #416. |
| `invistus.com.br` | `/` | `neotool-web` | Main web application. |
| `www.invistus.com.br` | `/` | Traefik redirect middleware | Redirects to `invistus.com.br`. |
| `webanalytics.invistus.com.br` | `/` | `umami` | Web analytics. |

## OAuth Host Allowlist

`auth.invistus.com.br` routes directly to the `neotool-security` service on port
8080, which also serves the internal GraphQL subgraph, health checks, and
management endpoints. Its Ingress therefore enumerates each public OAuth path
with `pathType: Exact` instead of using a broad `/` prefix.

The public JWKS endpoint is exposed because it contains public key material and
is referenced by OAuth authorization server metadata. `JWT_ISSUER` must be
configured per environment as `https://auth.invistus.com.br` in production.

TLS certificates use the existing `letsencrypt-prod` ClusterIssuer. Because that
issuer uses HTTP-01 validation, DNS for `auth.invistus.com.br` and
`mcp.invistus.com.br` must resolve to the cluster before applying an Ingress that
requests certificates for those hosts.
