# Mobile Navigation Specification

**Status:** Implemented  
**Date:** 2026-02-09  
**Related Requirements:** [MOBILE_LAYOUT_REQUIREMENTS.md](./MOBILE_LAYOUT_REQUIREMENTS.md)

## Overview

The mobile navigation system allows users to navigate the application on devices with screen widths smaller than **960px** (MUI `md` breakpoint). It replaces the desktop sidebar with a bottom navigation bar and a secondary navigation drawer.

## Components

### 1. Bottom Navigation Bar (`BottomNavigationBar`)

A fixed bar at the bottom of the screen providing quick access to primary routes.

*   **Visibility:** Hidden on desktop (`md` and up), visible on mobile (`sm` and down).
*   **Height:** `64px` + Safe Area Inset.
*   **Items:**
    1.  **Home** (`/`)
    2.  **Data** (`/financial-data`)
    3.  **Profile** (`/profile`)
    4.  **Menu** (Triggers `MobileNavigationDrawer`)
*   **Interaction:**
    *   Direct routing for Home, Data, and Profile.
    *   Opens the secondary drawer for "Menu".
*   **Accessibility:**
    *   Touch targets: `56x56px` minimum.
    *   ARIA roles: `navigation`, `button`.
*   **Internationalization:**
    *   Keys: `routes.home`, `routes.financialData`, `routes.profile`, `routes.menu`.

### 2. Mobile Navigation Drawer (`MobileNavigationDrawer`)

A temporary drawer that slides up from the bottom, containing secondary navigation items and actions.

*   **Trigger:** "Menu" button in the Bottom Navigation Bar.
*   **Content:**
    *   **Settings** (`/settings`) - Restricted by permissions.
    *   **Theme Toggle** (Dark/Light mode switch).
    *   **Sign Out** (Logout action).
*   **Behavior:** Closes on item click or background click.

### 3. Responsive Header (`AppHeader`)

The application header adapts to the mobile layout.

*   **Height:** Reduced to `~56px` on mobile.
*   **Layout:**
    *   Left margin removed (sidebar is hidden).
    *   Breadcrumbs hidden.
    *   AI Assistant hidden.
    *   Title size reduced (`h6`).

## Layout & Styling

*   **Breakpoints:**
    *   **Mobile:** `< 960px` (`theme.breakpoints.down("md")`).
    *   **Desktop:** `>= 960px`.
*   **Margins:**
    *   Content `marginLeft`: `0` on mobile, `84px` (SidebarRail width) on desktop.
    *   Content `paddingBottom`: `64px` + Safe Area on mobile (to prevent obstruction by Bottom Nav).
*   **Theme Tokens:**
    *   `theme.custom.layout.mobile.bottomNavHeight`: `64`
    *   `theme.custom.layout.mobile.touchTarget`: `48` (min)

## File Structure

*   `src/shared/ui/navigation/BottomNavigationBar.tsx` - Main mobile nav component.
*   `src/shared/ui/navigation/MobileNavigationDrawer.tsx` - Secondary menu drawer.
*   `src/shared/ui/shell/AppHeader.tsx` - Responsive header logic.
*   `src/shared/ui/shell/AppShell.tsx` - Layout container and conditional rendering.

## i18n Keys

Added to `locales/{lang}/common.json`:

```json
"routes": {
  "menu": "Menu"
}
```
