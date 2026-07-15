# i18n Strategy

## Frontend

Usar `next-intl` para todas as strings visíveis ao usuário.
**Strings hardcoded em JSX são proibidas** — mesmo pt-BR.

```typescript
// ❌ PROIBIDO
<Button>Salvar</Button>
<Typography>Erro ao carregar</Typography>

// ✅ Correto
import { useTranslations } from 'next-intl';
const t = useTranslations('common');
<Button>{t('save')}</Button>
```

## Checklist antes de finalizar qualquer componente

- [ ] Nenhuma string pt-BR/en hardcoded em JSX
- [ ] Todas as strings passam por `t('key')`
- [ ] Chaves adicionadas aos arquivos de mensagem (pt-BR e en)

## Localização dos arquivos de mensagem

`web/messages/` — arquivos por locale (pt-BR.json, en.json)
