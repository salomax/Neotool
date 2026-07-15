# Operational List Layouts — Personal Finance

Use this reference when building or changing dense operational pages such as:

- transaction lists
- recurring payment lists
- purchase wishlist
- summary + tabs + toolbar screens
- row/grid dual-mode pages

## Core rule

When the product asks for a page to "follow" an existing screen, treat the reference page as a contract, not inspiration.

That means checking and matching:

- content wrappers and container structure
- horizontal padding and vertical spacing
- toolbar composition and search alignment
- tabs row structure and divider behavior
- summary panel patterns and filter affordances
- drawer/form interaction model
- typography weight/size hierarchy

If the reference is still ambiguous, update the spec instead of improvising local spacing tweaks.

## Wrapper parity

Before changing child components, confirm that the page uses the same structural wrappers as the reference screen.

Check explicitly:

- `Page`
- `SidebarLayout`
- `Container`
- intermediate stacks/boxes that establish padding
- whether summary lives in a side panel, top panel, or mobile sheet

Do not solve wrapper mismatches with one-off margin or padding patches deep in the page.

## Toolbar and tabs row

For list pages with search + primary action + tabs/view toggles:

- search and action alignment must match the reference screen
- view toggles belong in the same visual system as tabs when the reference does so
- avoid duplicate bottom borders between tabs and the content area
- icon-only affordances must have stable accessible labels

## Summary panels

When a summary panel is similar to an existing one:

- reuse the same visual primitives and density rules
- if summary items act as filters elsewhere, prefer the same interaction model
- numeric alignment is part of the contract; do not regress right-aligned metrics into left-aligned text

## Grid/list dual mode

If a page supports both row and grid modes:

- define a local persistence strategy up front
- prefer client-side persistence patterns already used in the codebase
- restore the UI state itself on reload, not just the stored value
- grid cards must preserve action anchoring even when text height varies

## Drawers and forms

- Mobile-specific components should not be reused for desktop flows unless the behavior is truly shared
- For desktop drawers, prefer established non-mobile field patterns from the surrounding domain
- If a flow is domain-constrained, hard-code the invariant in the form where appropriate
  - Example: a "purchase creates transaction" drawer should enforce `DEBIT` if the domain only allows purchases as debits

## External input flows

If the page consumes external inputs such as product URLs:

- field order must reflect causality
  - Example: URL before name/price when URL drives autofill
- fallback behavior must be silent and graceful when data is unavailable
- autofill must not overwrite manual user edits
- loading state must be local and contextual, not global or disruptive
- preview UI should increase confidence without becoming a required step

## Review checklist

- [ ] Reference screen was named and inspected before implementation
- [ ] Wrapper parity was verified before local spacing changes
- [ ] Tabs, toolbar, and divider structure match the reference page
- [ ] Summary uses the same interaction and visual conventions when applicable
- [ ] Grid/list preference persistence restores the actual UI state after refresh
- [ ] External-input flows define fallback, overwrite, and loading behavior explicitly
