# Styling — MUI Design Tokens

## Regra absoluta: zero cores hardcoded

```typescript
// ❌ PROIBIDO — nunca fazer isso
<Box sx={{ backgroundColor: '#FF5733' }} />
<Box sx={{ color: 'rgba(0,0,0,0.5)' }} />
<Box sx={{ borderColor: 'red' }} />

// ✅ Correto — sempre usar tokens
<Box sx={{ backgroundColor: 'error.main' }} />
<Box sx={{ color: 'text.secondary' }} />
<Box sx={{ borderColor: 'divider' }} />
```

## Tokens disponíveis (MUI padrão)

```
Cores semânticas:    primary.main, secondary.main, error.main, warning.main, success.main
Texto:               text.primary, text.secondary, text.disabled
Background:          background.default, background.paper
Bordas:              divider
```

## Tokens customizados do Invistus

Localização: `web/src/styles/themes/tokens.ts`

Sempre consultar este arquivo antes de criar uma nova variável de cor —
o token necessário provavelmente já existe.

## useTheme para lógica dinâmica

```typescript
import { useTheme } from '@mui/material';

const theme = useTheme();
const color = theme.palette.primary.main; // para uso fora do sx prop
```

## Referência completa

`docs/02-development/frontend-web.md`
