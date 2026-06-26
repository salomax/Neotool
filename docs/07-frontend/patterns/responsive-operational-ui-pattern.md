---
title: Responsive Operational UI Pattern
type: pattern
category: frontend
status: current
version: 1.0.0
tags: [responsive, mobile, sticky, toolbar, list, drawer, web, frontend]
ai_optimized: true
search_keywords: [responsive ui, mobile web, sticky toolbar, dense list, inline edit mobile, responsive operational screen]
related:
  - 07-frontend/patterns/styling-pattern.md
  - 07-frontend/patterns/shared-components-pattern.md
  - 07-frontend/standards/mobile-navigation.md
---

# Responsive Operational UI Pattern

> **Purpose**: Define how dense, action-heavy web screens must adapt to mobile/responsive layouts without degrading usability or leaking feature-specific behavior into shared primitives.

## Overview

Operational screens such as transaction lists, management tables, editing flows, and action-heavy dashboards must not be treated as desktop layouts simply squeezed into narrow viewports. Mobile behavior must be designed around task priority, scanability, and touch interaction.

This pattern defines the default rules for responsive web behavior in Invistus when a screen contains one or more of the following:

- dense lists or table-like rows
- contextual actions
- search and filter toolbars
- inline editing
- fixed or sticky UI regions
- bottom navigation and floating actions

## Core Principles

### 1. Mobile is not squeezed desktop

Responsive work must start by re-evaluating the user task on small screens. If the desktop layout depends on horizontal density, hover affordances, or inline editing, the mobile experience must be redesigned rather than simply reflowed.

### 2. Behavior belongs to the owning composition

If a responsive behavior is specific to one feature, implement it at the feature/page composition level before changing shared primitives such as `Panel`, `Table`, `Layout`, or other reusable containers.

Shared components should only absorb a responsive rule when the abstraction truly owns that behavior across multiple features.

### 3. Fixed surfaces require explicit hierarchy

Every persistent surface on mobile must be classified as one of:

- `static`
- `sticky`
- `fixed`

If more than one sticky/fixed layer exists, the hierarchy must be documented and implemented explicitly, including:

- vertical order
- `top` offset
- `z-index`
- opaque background
- divider/border treatment

Example hierarchy:

1. app header
2. account/context selector
3. filter/search/action toolbar
4. scrollable content
5. FAB
6. bottom navigation

## Layout Rules for Dense Mobile Lists

### Row composition

For dense operational rows, prefer:

- icon/avatar at the far left
- primary label/description beside the icon
- secondary metadata on a second line
- amount/status aligned for fast scanning
- destructive/secondary actions moved out of the row when the row is already dense

### Spacing and separators

- Use consistent horizontal padding across toolbar, date groupers, list rows, and related chips
- Prefer continuous dividers between rows when the goal is compact operational density
- Avoid turning dense lists into detached card stacks unless the card model improves comprehension

### Typography

Typography must be standardized by function, not by isolated component overrides.

If the same mobile size or line height is reused across multiple elements such as:

- date groupers
- row description
- row amount
- account label
- category/tag labels
- month/year filters

it must be promoted to a shared token or constant instead of repeated as raw values.

## Toolbar Rules

### Toolbar composition

Mobile toolbars with month/year filters, search, and actions must:

- align horizontally with the content below
- avoid unnecessary labels when icon-only actions are already clear
- preserve compact density without sacrificing touch usability

### Sticky toolbars

If the toolbar must remain visible during scroll:

- apply sticky behavior only to the necessary surface
- keep alerts/banners outside the sticky region unless explicitly required
- use an opaque background so content does not bleed through
- add a bottom divider when needed to maintain separation
- account for existing sticky layers above it

### Expandable search

Expandable search on mobile must satisfy all of the following:

- collapsed icon is visually centered
- expanded field grows into available horizontal space
- typed text and placeholder remain readable
- input background is opaque enough to preserve contrast
- transitions do not break neighboring controls

## Actions and Editing Rules

### Desktop affordances do not automatically transfer to mobile

Patterns such as:

- kebab menus
- hover actions
- inline secondary actions

often need a different primary interaction in mobile, such as:

- tappable row
- bottom sheet
- contextual menu from row tap
- drawer

### Inline edit

Inline edit should not be assumed responsive by default on dense screens.

When a desktop flow uses inline edit, mobile must explicitly evaluate whether the correct interaction is:

- drawer
- bottom sheet
- dedicated screen

It is acceptable to keep inline edit on desktop only when that is the best interaction for large viewports.

## Interaction Safety Rules

When combining responsive surfaces, always validate:

- sticky headers do not overlap scrollable content incorrectly
- FAB does not cover actionable row content
- bottom navigation does not hide trailing list content
- sheets/drawers do not conflict with row tap behavior
- search expansion does not break layout or clipping

## Review Checklist

- [ ] Mobile behavior was designed around task priority, not only layout compression
- [ ] Feature-specific responsive adjustments were implemented in the owning composition before changing shared primitives
- [ ] Sticky/fixed layers have explicit hierarchy, offsets, background, and divider treatment
- [ ] Horizontal alignment is consistent across toolbar, groupers, list rows, and chips
- [ ] Dense list rows use a mobile-first information hierarchy
- [ ] Repeated typography and spacing values were promoted to tokens/constants when reused
- [ ] Inline edit was explicitly evaluated for mobile instead of assumed
- [ ] Search, FAB, bottom nav, and sticky surfaces coexist without overlap regressions
- [ ] Desktop behavior remains intact when the requirement is mobile-specific

## Anti-patterns

- Reusing desktop table/list interaction unchanged on mobile
- Solving a local responsive problem by patching a shared primitive unnecessarily
- Adding sticky behavior without opaque background
- Repeating magic numbers for height, offset, line height, or spacing across multiple components
- Keeping dense inline edit in narrow viewports when drawer/sheet is more usable

## Related Patterns

- [Styling Pattern](./styling-pattern.md)
- [Shared Components Pattern](./shared-components-pattern.md)
- [Mobile Navigation Specification](../standards/mobile-navigation.md)
