# Styling - MUI Design Tokens

## Regra Absoluta: Zero Cores Hardcoded

```typescript
// Proibido.
<Box sx={{ backgroundColor: "#FF5733" }} />
<Box sx={{ color: "rgba(0,0,0,0.5)" }} />
<Box sx={{ borderColor: "red" }} />

// Correto.
<Box sx={{ backgroundColor: "error.main" }} />
<Box sx={{ color: "text.secondary" }} />
<Box sx={{ borderColor: "divider" }} />
```

## Tokens Disponíveis

```text
Cores semânticas: primary.main, secondary.main, error.main, warning.main, success.main
Texto:            text.primary, text.secondary, text.disabled
Background:       background.default, background.paper
Bordas:           divider
```

## Tokens Customizados do Invistus

Localização: `web/src/styles/themes/tokens.ts`.

Consulte esse arquivo antes de criar nova variável de cor. Se precisar de novo
token, adicione no tema em vez de espalhar literal em componente.

## `useTheme` Para Lógica Dinâmica

```typescript
import { useTheme } from "@mui/material";

const theme = useTheme();
const color = theme.palette.primary.main;
```

## Gotchas

- A regra vale também para `border`, `boxShadow`, SVG inline, chart colors e
  estilos passados para bibliotecas.
- Evite estilos locais duplicando primitives já existentes em `shared/components`
  ou `shared/ui`.
- Componentes compactos não devem usar tipografia hero-scale.

## Referência Completa

`docs/07-frontend/patterns/styling-pattern.md`
