# Theming — React Native Paper (MD3)

## Absolute rule: zero hardcoded colors

```tsx
// ❌ FORBIDDEN
<View style={{ backgroundColor: '#1976d2' }} />
<Text style={{ color: 'rgba(0,0,0,0.5)' }} />

// ✅ Correct — always use theme tokens
const theme = useTheme();
<View style={{ backgroundColor: theme.colors.primary }} />
<Text style={{ color: theme.colors.onSurfaceVariant }} />
```

## Where the theme lives

- `mobile/src/shared/theme/colors.ts` — light/dark token sets (`lightColors`, `darkColors`).
- `mobile/src/shared/theme/theme.ts` — extends `MD3LightTheme` / `MD3DarkTheme` with those colors.
- `ThemeProvider` (in `shared/providers`) wires Paper's `PaperProvider` and dark-mode switching.

Always consult `colors.ts` before introducing a new color — the token you need almost certainly
exists. If a genuinely new semantic color is required, add it to `colors.ts` (both light and dark),
never inline it.

## Accessing the theme

```tsx
import { useTheme } from 'react-native-paper';

function AmountLabel() {
  const theme = useTheme();
  return <Text style={{ color: theme.colors.error }}>...</Text>;
}
```

For values used dynamically outside JSX, read from `theme.colors.*` / `theme.roundness` etc.

## Design-token alignment with web

Mobile tokens mirror the web design system intent (MD3 vs MUI). Keep the **semantic** meaning
aligned (primary, error, success, surface, outline...) so the two platforms feel like one product.
The token set in `colors.ts` is explicitly marked "Can be shared with web" — treat it as the
translation layer, not a place for one-off values.

## Typography & spacing

- Use Paper `Text` variants (`headlineLarge`, `titleMedium`, `bodyMedium`, ...) instead of raw
  `fontSize`.
- Do not scatter magic spacing numbers. Promote repeated spacing/sizing to shared constants
  when reused across screens.

## Dark mode

Both light and dark must be correct. Never assume a light background — always pull surface/on-surface
tokens from the active theme. Test both modes.
