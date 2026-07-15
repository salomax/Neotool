# Responsive Web UI — Operational Screens

## Core principle

Mobile must not be treated as squeezed desktop. In operational screens such as dense lists, tables, editing flows, and action-heavy pages, the mobile experience should be designed around task priority rather than simple reflow.

## When to load this reference

Use these rules when:

- adapting web pages to mobile/responsive layouts
- creating or reviewing dense lists, tables, and grids
- introducing `sticky`, `fixed`, FAB, bottom navigation, or drawers
- choosing between inline edit, drawer, sheet, or dedicated page
- adjusting toolbars with filters, search, and actions

## Composition rules

1. **Prefer solving mobile behavior at the feature/page level**
   - If the behavior is specific to one screen, fix it in feature composition before changing shared `Panel`, `Table`, `Layout`, or other primitives.

2. **Mobile needs explicit hierarchy for fixed areas**
   - Classify each area as `static`, `sticky`, or `fixed`.
   - If multiple sticky layers exist, define their stacking order explicitly.
   - Always specify:
     - `top offset`
     - `z-index`
     - opaque background
     - bottom divider/border when needed

3. **Dense layouts must preserve horizontal alignment**
   - Header, toolbar, date groupers, list rows, chips, and related fields should share the same visual axis.
   - Avoid local margin hacks when the issue is structural.

4. **Mobile typography should be tokenized by function**
   - Do not spread magic `fontSize` and `lineHeight` values across isolated components.
   - If the same size appears in multiple places, promote it to a shared constant/token.

5. **Secondary desktop affordances may need to become primary mobile interactions**
   - Desktop-only patterns like kebab menus, hover actions, and subtle affordances often do not translate well to mobile.
   - Consider row tap, bottom sheet, drawer, or more direct contextual menus.

6. **Dense inline edit should not be assumed responsive**
   - For mobile, explicitly evaluate:
     - drawer
     - bottom sheet
     - dedicated screen
   - Keep inline edit desktop-only when density or focus demands it.

## Heuristics for dense mobile lists

- Entity icon/avatar on the left
- Primary content next to the icon
- Secondary metadata on a second row
- Amount and status optimized for quick scanning
- Actions moved off-row or into sheet/drawer when the row is already dense
- Continuous dividers when the intent is a compact operational list
- Tight vertical rhythm; avoid turning operational lists into loose card piles without a clear reason

## Heuristics for mobile toolbars

- Actions may be icon-only when context is already clear
- Expandable search must have:
  - visually centered icon in collapsed state
  - sufficient width when expanded
  - opaque background and readable contrast for placeholder and typed text
- Action buttons should follow a coherent visual scale relative to typography and neighboring icons
- Sticky toolbars should align horizontally with the scrollable content below

## Responsive review checklist

- [ ] Mobile was redesigned around task priority rather than merely reflowing desktop
- [ ] `sticky` and `fixed` areas have correct offsets and stacking
- [ ] Sticky headers, bottom nav, FAB, drawers, and scroll containers do not conflict
- [ ] Sticky/fixed surfaces have opaque backgrounds
- [ ] Dividers are used deliberately for density and separation
- [ ] Horizontal padding is consistent across toolbar, groupers, and rows
- [ ] Typography is standardized through tokens/constants rather than scattered absolute values
- [ ] Shared primitives were not changed to solve a purely local mobile issue unless that abstraction truly owns the behavior
- [ ] Desktop behavior remains intact when the change is mobile-specific

## Anti-patterns

- Reusing a desktop component without reevaluating mobile UX
- Solving a screen-specific issue by patching a shared primitive unnecessarily
- Sticky surfaces without opaque background
- Repeating magic numbers for heights, line heights, offsets, or padding across components
- Forcing dense inline edit into narrow viewports when drawer/sheet is the better interaction
