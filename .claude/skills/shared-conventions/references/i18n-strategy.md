# i18n Strategy

## Frontend

Use `next-intl` e os helpers de i18n existentes para todas as strings visíveis
ao usuário. Strings hardcoded em JSX são proibidas, inclusive pt-BR.

```typescript
// Proibido.
<Button>Salvar</Button>
<Typography>Erro ao carregar</Typography>

// Correto.
import { useTranslations } from "next-intl";

const t = useTranslations("common");
<Button>{t("save")}</Button>
```

## Onde Ficam as Mensagens

- Mensagens compartilhadas: `web/src/shared/i18n/locales/`
- Mensagens por app/feature: procure arquivos `i18n` próximos da rota ou
  feature, por exemplo `web/src/app/personal-finance/i18n/`.
- Antes de criar novo arquivo de mensagens, procure o domínio de i18n já usado
  pelo componente ou página que está sendo alterado.

## Checklist Antes de Finalizar Componentes

- Nenhuma string pt-BR/en hardcoded em JSX.
- Todas as strings visíveis passam por `t("key")` ou helper equivalente.
- Chaves adicionadas aos locales suportados pelo domínio alterado.
- Toasts, labels, placeholders, aria-labels e mensagens de validação também
  estão localizados.
