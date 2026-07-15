# Native-First Premium UX — Mobile

## Core principle

Mobile is **not** a squeezed desktop. For every screen, re-decide information hierarchy, the single
primary action, and the edit flow around touch and task priority. Reflowing the web layout is a defect.

This is the P0 quality bar (see `docs/07-frontend/mobile/README.md`), not a later polish phase.

## Lists (dense, operational)

- Always `FlatList` / `FlashList` — never `.map()` a remote collection in a `ScrollView`.
- Provide `keyExtractor`, `getItemLayout` when rows are fixed-height, and an explicit `ListEmptyComponent`.
- Row anatomy: entity icon/avatar left · primary content · secondary metadata on a second line ·
  amount/status optimized for scanning.
- Move row actions off-row into **swipe actions** or a **bottom sheet** when the row is dense
  (kebab/hover patterns from desktop do not translate).
- Continuous dividers for compact operational lists; tight vertical rhythm. Pull-to-refresh via
  `RefreshControl`. Infinite scroll via `onEndReached`.

## Edit / create flows

Do not assume dense inline edit works on narrow viewports. Choose deliberately:

- **Bottom sheet** for quick, focused edits (money entry, category pick).
- **Dedicated screen** for multi-field forms.
- Keep inline edit only where density truly demands it.

## Loading, empty, error

- **Skeletons** for data screens, not bare spinners.
- Designed **empty states** with a clear next action.
- **Error states** are recoverable (retry), copy resolved via i18n and the shared error contract.
- With the persisted Apollo cache, show cached data instantly and refresh in background.

## Motion & feedback

- Native stack/tab transitions; consider shared-element/fade where meaningful.
- **Haptics** (`expo-haptics`) on key actions: success/error notifications, selection changes,
  destructive confirmations. Do not overuse.
- Optimistic UI with rollback for mutations (see data-mutations.md).

## Input

- Numeric keypad + masks for money; `keyboardType`, `returnKeyType`, and `KeyboardAvoidingView`
  handled per platform.
- Touch targets ≥ 48dp. Inline validation on blur.

## Trust & device capabilities

- **Biometric unlock** with `expo-local-authentication`; tokens only in `expo-secure-store`.
- **Push notifications** via `expo-notifications` (register token, respect preferences, deep-link
  the tap target).
- **Offline-first** reads from the persisted cache; graceful degradation on poor networks.
- Camera/QR for imports where web already does (`jsqr` on web → `expo-camera`/barcode scanner).

## Platform fit & accessibility

- Respect iOS vs Android conventions and safe areas; support dark mode.
- Accessibility labels/roles on interactive elements; support dynamic type; adequate contrast
  (pull from theme tokens). Aim WCAG-aligned.

## Fixed / sticky surfaces

- Classify each area `static` / `sticky` / `fixed`; define stacking order.
- Sticky/fixed surfaces need opaque backgrounds and correct top/bottom offsets so they never
  collide with the tab bar, headers, or the keyboard.

## Anti-patterns

- Reusing a web component without reevaluating mobile UX.
- `.map()` over a remote list inside a `ScrollView`.
- Spinners where a skeleton belongs; missing empty/error states.
- Hardcoded colors or scattered magic numbers for heights/offsets/padding.
- Tokens or secrets in AsyncStorage instead of SecureStore.
- Blocking the whole screen for a single-field mutation.
