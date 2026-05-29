# Auth & Permissions

## Fronteira de Segurança

Frontend permission checks são somente para esconder UI. O backend enforça
todas as regras de autorização. Nunca salte uma chamada ao backend baseado
apenas em um check do frontend.

```typescript
// Correto: esconde elemento de UI.
{hasPermission("banking:transaction:edit") && <EditButton />}

// Proibido: bypass de lógica baseado em permissão frontend.
if (!hasPermission("banking:transaction:edit")) return;
```

## Regra de Ouro

Se o usuário manipular o frontend para contornar ou esconder o check de
permissão, a operação ainda deve falhar no backend. O frontend é conveniente,
não seguro.

## Implicações Práticas

- Resolvers, services e clientes internos precisam validar tenant/account scope.
- Qualquer operação que recebe `accountId`, `financialAccountId` ou outro ID
  de recurso deve validar que o usuário atual pode acessar esse recurso.
- Permissões no frontend melhoram navegação e reduzem ruído, mas não reduzem
  a obrigação de validação no backend.
