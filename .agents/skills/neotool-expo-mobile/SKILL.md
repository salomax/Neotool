---
name: neotool-expo-mobile
description: >
  Implementation patterns for the Invistus mobile app (React Native + Expo).
  Use when building screens, components, hooks, navigation, or any mobile feature
  in mobile/. Covers: Expo Router navigation and guards, Apollo/GraphQL with
  generated types and offline cache, React Native Paper theming with MD3 tokens,
  i18n with i18next, forms with React Hook Form + Zod, and native-first UX
  (gestures, haptics, bottom sheets, FlatList, skeletons, biometrics, push,
  deep links). Triggers: any task described as mobile, React Native, Expo,
  iOS, Android, native screen, or referencing files under mobile/src or mobile/app.
metadata:
  author: invistus
  version: "1.0"
  paths: "invistus/mobile/src/**, invistus/mobile/app/**"
---

# Expo Mobile Patterns — Invistus

Stack: **React Native 0.76 + Expo SDK 52 + TypeScript + Expo Router + Apollo Client + React Native Paper (MD3) + Zustand + i18next + React Hook Form + Zod**

This is the mobile sibling of `neotool-nextjs-app`. Mobile shares **behavior and contracts** with web, not rendered components. Do **not** port MUI/Next.js code — redesign for touch.

Load reference files based on the task:

- Fetching data with Apollo/GraphQL → [references/graphql.md](references/graphql.md)
- Mutations, feedback, and offline behavior → [references/data-mutations.md](references/data-mutations.md)
- Theming with RN Paper tokens → [references/theming.md](references/theming.md)
- Navigation, route groups, guards, deep linking → [references/navigation.md](references/navigation.md)
- Native-first premium UX (gestures, haptics, sheets, lists, biometrics, push) → [references/native-ux.md](references/native-ux.md)
- Writing tests → [references/tests.md](references/tests.md)

## Non-negotiable rules

1. **Mobile is not a squeezed desktop.** Redesign hierarchy, primary actions, and edit flows for touch. Copying the web/responsive layout is a defect. (See native-ux.md.)
2. **Zero hardcoded colors** — use RN Paper theme tokens via `useTheme()` / `useAppTheme()`. Never inline hex/rgba. (See theming.md.)
3. **Generated GraphQL types are mandatory** — use the codegen `client` preset output in `mobile/src/generated/`. Never hand-write `gql` operations in screens without codegen.
4. **Every mutation shows feedback** — success and error must reach the user (Snackbar/toast + haptic). Never fail silently.
5. **i18n always** — zero hardcoded pt-BR/en strings in JSX. Keys in `mobile/src/locales/*` and per-feature `i18n/`.
6. **TypeScript strict** — no `any` unless truly unavoidable and justified.
7. **Auth is backend authority** — never make security decisions on the client. Store tokens only in `expo-secure-store`, never AsyncStorage.
8. **Lists are virtualized** — use `FlatList`/`FlashList` for any list that can grow. Never `.map()` a remote collection inside a `ScrollView`.
9. **Respect safe areas and platform** — use `react-native-safe-area-context`; honor iOS/Android conventions; support dark mode.

## Directory structure

```
mobile/
  app/                       # Expo Router (file-based routing)
    (auth)/                  # unauthenticated route group
    (tabs)/                  # authenticated tab group
    _layout.tsx              # root layout + providers
    +not-found.tsx
  src/
    features/                # feature modules (screens, hooks, widgets, i18n)
      [feature]/
        screens/
        hooks/
        components/
        i18n/locales/
    shared/
      components/            # ui primitives, forms, feedback, brand
      hooks/                 # useAuth, useTheme, useNetworkStatus, ...
      providers/             # Auth, Apollo, Theme, I18n
      theme/                 # colors.ts, theme.ts (MD3 tokens)
      config/                # env, i18n config
      i18n/
    lib/                     # apollo-client.ts, trace-context.ts
    generated/               # GraphQL codegen output (client preset) — do not edit
    locales/                 # global en.json / pt.json
```

**Rule:** never create files outside these directories. Feature-specific behavior goes in the feature module; only promote to `shared/` when reused across features.

## Pre-completion checklist

- [ ] Screen was designed for touch (not reflowed from web): hierarchy, primary action, and edit flow reconsidered
- [ ] Zero hardcoded colors — RN Paper tokens only
- [ ] GraphQL uses generated types (codegen), not ad-hoc `gql` in components
- [ ] Every mutation shows success and error feedback (Snackbar/toast + haptic where appropriate)
- [ ] All user-facing strings use i18n keys
- [ ] Lists use `FlatList`/virtualization with proper `keyExtractor` and empty state
- [ ] Loading uses skeletons (not bare spinners) for data screens; explicit empty and error states
- [ ] Safe-area insets handled; fixed/sticky surfaces have opaque backgrounds and correct stacking
- [ ] Tokens are stored in SecureStore; no secrets in AsyncStorage
- [ ] Works offline-first where the web reads are cacheable (persisted Apollo cache)
- [ ] TypeScript compiles (no unjustified `any`); `npm run lint` and `npm run typecheck` pass
- [ ] Tested on both iOS and Android (or noted which was validated)

## Also load

- Cross-cutting concerns (auth, errors, i18n): **neotool-conventions**
- Personal-finance / investments domain rules: **neotool-finance-domain**
- The mobile strategy and parity scope: `docs/07-frontend/mobile/README.md`
