# Auth & Permissions

## Fronteira de segurança

Frontend permission checks são **somente para esconder UI**. O backend
enforça tudo. Nunca salte uma chamada ao backend baseado em check de frontend.

```typescript
// ✅ Correto — esconde elemento de UI
{hasPermission('banking:transaction:edit') && <EditButton />}

// ❌ Nunca — bypass de lógica baseado em permissão frontend
if (!hasPermission('banking:transaction:edit')) return;
```

## Regra de ouro

Se o usuário manipular o frontend para esconder o check de permissão,
a operação AINDA DEVE falhar no backend. O frontend é conveniente, não seguro.
