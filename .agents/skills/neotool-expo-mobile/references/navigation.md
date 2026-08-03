# Navigation — Expo Router

## File-based routing

Routes live in `mobile/app/`. Root layout is `app/_layout.tsx` (providers + `Stack`).
Route groups organize access:

- `app/(auth)/` — unauthenticated screens (`login`, `signup`, `forgot-password`, `reset-password`).
- `app/(tabs)/` — authenticated tab navigation (`index`, `profile`, `settings`, ...).
- `app/+not-found.tsx` — fallback.

Add a screen by creating the file; the file path is the route. Use `(group)` folders to share a
layout and to separate auth vs authenticated trees.

```tsx
// app/_layout.tsx (already present)
<Stack screenOptions={{ headerShown: false, animation: 'slide_from_right' }}>
  <Stack.Screen name="index" />
  <Stack.Screen name="(auth)" />
  <Stack.Screen name="(tabs)" />
  <Stack.Screen name="+not-found" />
</Stack>
```

## Route guards

Auth gating is decided by session state (from `AuthProvider` / `useAuth`), not by hiding UI.
Redirect at the layout level:

```tsx
import { Redirect } from 'expo-router';
import { useAuth } from '@/shared/hooks/useAuth';

export default function TabsLayout() {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) return <SplashKeepAlive />;
  if (!isAuthenticated) return <Redirect href="/(auth)/login" />;
  return <Tabs>{/* ... */}</Tabs>;
}
```

Never rely on client-side guards for security — the backend is authority (neotool-conventions).

## Navigating

```tsx
import { router, Link } from 'expo-router';
router.push('/financial-data');
router.replace('/(tabs)');           // after login, no back to auth
<Link href={`/financial-data/${id}`}>...</Link>
```

## Deep linking

`expo-linking` is configured. Deep links drive:

- Password reset (`reset-password` with a token param) and email verification.
- Push-notification tap targets.

Resolve link params inside the target screen; validate/expire tokens server-side. Define the URL
scheme and universal/app links in `app.config.ts` / `app.json`.

## Native navigation UX

- Prefer native stack transitions; use gesture-based back (enabled by default on iOS).
- Bottom tab bar is the primary authenticated nav; a bottom-sheet/drawer holds secondary items
  (Settings, theme, sign out) — follow `docs/02-development/mobile-expo.md`
  and implement natively, not as a web component.
- Honor safe areas with `react-native-safe-area-context`; the tab bar must not overlap content
  (add bottom inset padding on scroll containers).
- Titles/headers reduced and simplified vs desktop; no breadcrumbs.
