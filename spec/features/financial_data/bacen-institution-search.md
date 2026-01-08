# Bacen Institution Search Frontend

**Type**: Frontend Only  
**Module**: financial_data  
**Status**: Draft

## Problem/Context

Users need to search and browse Brazilian Central Bank (BACEN) financial institutions by name or CNPJ. The search should be fast and intuitive, with results displayed in a card grid that supports infinite scrolling for large result sets.

## Solution/Approach

Build a search page with:
1. **Fixed search bar** at the top using existing `SearchableAutocomplete` component
2. **Card grid** with infinite scroll below the search bar
3. **Generic Card component** for reusability
4. **Bacen Institution Card** component that displays institution data

## Components to Build

### 1. Generic Card Component

- **Location**: `web/src/shared/components/ui/primitives/Card.tsx`
- **Purpose**: Reusable card wrapper with consistent styling and hover effects
- **Props**: 
  - `children`: ReactNode
  - `onClick?`: () => void
  - `variant?`: "outlined" | "elevated"
  - `hoverable?`: boolean (default: true) - enables smooth hover effect
  - `sx?`: SxProps
- **Features**:
  - Smooth, slight hover effect (transition on elevation/shadow)
  - Cursor pointer when onClick is provided

### 2. Card Grid with Infinite Scroll

- **Location**: `web/src/shared/components/ui/layout/CardGrid.tsx`
- **Purpose**: Responsive grid container with infinite scroll support
- **Props**:
  - `items`: T[]
  - `renderItem`: (item: T) => ReactNode
  - `columns?`: number | { xs?: number, sm?: number, md?: number, lg?: number, xl?: number }
  - `gap?`: number
  - `onLoadMore?`: () => void
  - `hasNextPage?`: boolean
  - `loading?`: boolean
  - `loadingComponent?`: ReactNode
  - `emptyComponent?`: ReactNode
  - `errorComponent?`: ReactNode
- **Features**:
  - Responsive grid (auto-adjusts columns based on screen size)
  - Intersection Observer for automatic loading (80% scroll threshold)
  - Load More button as fallback
  - Uses `useRelayPagination` hook for pagination state

### 3. Bacen Institution Card

- **Location**: `web/src/features/financial_data/components/BacenInstitutionCard.tsx`
- **Purpose**: Displays institution information in card format
- **Props**:
  - `institution`: BacenInstitution (from GraphQL)
  - `onClick?`: () => void
- **Displays**:
  - Logo (circular, with fallback)
  - Institution name
  - Institution type
  - Financial metrics (profit, equity, YoY growth, Basileia index)
  - Institution type badge in footer
- **Behavior**:
  - Clicking card navigates to institution detail page (to be created later)

### 4. Search Page

- **Location**: `web/src/app/(financial-data)/bacen-institutions/page.tsx`
- **Purpose**: Main search page combining all components
- **Features**:
  - Fixed search bar at top (centered, using SearchableAutocomplete)
  - Card grid below with infinite scroll
  - Handles search query state
  - Integrates with GraphQL queries

## GraphQL Integration

### Autocomplete Query

```graphql
query SearchBacenInstitutionsAutocomplete($query: String!) {
  searchBacenInstitutionsAutocomplete(query: $query) {
    suggestions {
      id
      name
      cnpj
      logoUrl
    }
  }
}
```

### Main Query (with pagination)

```graphql
query BacenInstitutions($query: String, $first: Int, $after: String) {
  bacenInstitutions(query: $query, first: $first, after: $after) {
    edges {
      node {
        id
        name
        logoUrl
        profit
        equity
        yoyEquityGrowth
        basileiaIndex
        institutionType
      }
      cursor
    }
    pageInfo {
      hasNextPage
      hasPreviousPage
      startCursor
      endCursor
    }
    totalCount
  }
}
```

## Success Criteria

1. **Search Functionality**
   - User can type in search bar and see autocomplete suggestions
   - Search is debounced (300ms default)
   - Search filters results in card grid

2. **Card Grid Display**
   - Cards display in responsive grid (3-4 columns on desktop, 1-2 on mobile)
   - Cards show all required institution data
   - Grid layout is visually consistent

3. **Infinite Scroll**
   - When user scrolls near bottom (80% threshold), next page loads automatically
   - Load More button appears as fallback
   - Loading state shows skeleton cards
   - No duplicate items when loading more pages

4. **Performance**
   - Initial load shows first 50 items
   - Subsequent pages load smoothly without jank
   - Search resets pagination to first page

5. **Accessibility**
   - Cards are keyboard navigable
   - Screen reader friendly
   - Proper ARIA labels

6. **Responsive Design**
   - Works on mobile, tablet, and desktop
   - Grid adapts columns based on screen size
   - Search bar remains fixed and accessible

7. **User Experience**
   - Cards have slight, smooth hover effects
   - Clicking a card navigates to institution detail page
   - Empty state shows clear messaging when no results
   - Error state shows clear messaging when search fails

8. **Code Organization**
   - useRelayConnectionManagement hook moved to appropriate location (data/ folder)
   - All existing imports updated to reflect new location

## Integration Points

### SearchableAutocomplete Component
- **Path**: `web/src/shared/components/ui/forms/SearchableAutocomplete.tsx`
- **Purpose**: Use existing autocomplete component for search bar
- **Pattern**: Follow the same pattern as other search implementations

### useRelayPagination Hook
- **Path**: `web/src/shared/hooks/pagination/useRelayPagination.ts`
- **Purpose**: Manage cursor-based pagination state
- **Pattern**: Use for infinite scroll pagination logic

### useRelayConnectionManagement Hook
- **Path**: `web/src/shared/hooks/data/useRelayConnectionManagement.ts` (moved from authorization/)
- **Purpose**: Generic hook for managing Relay connection queries and state (pagination, search, sorting)
- **Pattern**: Use for GraphQL query management with pagination
- **Migration**: This hook will be moved from `authorization/` to `data/` folder as part of this feature implementation, as it's a generic utility not specific to authorization

### StatCard Component
- **Path**: `web/src/shared/ui/cards/StatCard.tsx`
- **Purpose**: Reference for card styling patterns
- **Pattern**: Follow MUI Card usage patterns

### Skeleton Component
- **Path**: `web/src/shared/components/ui/primitives/Skeleton.tsx`
- **Purpose**: Use CardSkeleton variant for loading states
- **Pattern**: Show skeleton cards while loading

### Management Pattern
- **Path**: `docs/07-frontend/patterns/management-pattern.md`
- **Purpose**: Reference for page layout patterns
- **Pattern**: Follow management page structure if applicable

## Technical Constraints

1. **GraphQL**: Must use Relay-style pagination (cursor-based)
2. **Initial Load**: First page should load 50 items (`first: 50`)
3. **Search Reset**: Changing search query resets pagination to first page
4. **Responsive**: Grid must work on all screen sizes
5. **Performance**: Use `startTransition` for non-urgent pagination updates
6. **MUI Components**: Use Material-UI components for consistency

## Open Decisions (Resolved)

1. **Navigation**: ✅ User clicks card → Navigate to new page (to be created later)
2. **Empty State**: ✅ Show messaging letting user know there are no results
3. **Error Handling**: ✅ Show messaging letting user know the search can't be done
4. **Card Hover States**: ✅ Yes, slight and smooth hover effect (general behavior for all cards)

## For LLM Implementation

### Suggested Approach

1. **Move useRelayConnectionManagement Hook**
   - Move from `web/src/shared/hooks/authorization/useRelayConnectionManagement.ts` to `web/src/shared/hooks/data/useRelayConnectionManagement.ts`
   - Update all imports in authorization hooks (useUserManagement, useRoleManagement, useGroupManagement)
   - Update exports in index files

2. **Start with Generic Card**
   - Create reusable Card component in primitives
   - Keep it simple and extensible
   - Add smooth hover effect with transition

3. **Build Card Grid**
   - Use MUI Grid or custom CSS Grid
   - Implement Intersection Observer for scroll detection (80% threshold)
   - Integrate with `useRelayPagination` hook
   - Add Load More button as fallback
   - Add empty state component
   - Add error state component

4. **Create Bacen Institution Card**
   - Use Generic Card as wrapper
   - Format financial values (R$ currency, percentages)
   - Handle missing logo with fallback
   - Style according to design prototype
   - Add onClick handler for navigation

5. **Build Search Page**
   - Set up route and page component
   - Integrate SearchableAutocomplete
   - Connect to GraphQL queries
   - Wire up Card Grid with infinite scroll
   - Handle search state and pagination reset
   - Add navigation on card click

6. **Add Loading States**
   - Use Skeleton component for loading cards
   - Show loading indicator in search bar

7. **Test Responsive Behavior**
   - Verify grid columns on different screen sizes
   - Test infinite scroll on mobile devices

### Key Files to Create/Modify

**Refactoring:**
- `web/src/shared/hooks/data/useRelayConnectionManagement.ts` (move from authorization/)
- `web/src/shared/hooks/authorization/useUserManagement.ts` (update import)
- `web/src/shared/hooks/authorization/useRoleManagement.ts` (update import)
- `web/src/shared/hooks/authorization/useGroupManagement.ts` (update import)
- `web/src/shared/hooks/authorization/index.ts` (update exports)
- `web/src/shared/hooks/data/index.ts` (add export)

**New Components:**
- `web/src/shared/components/ui/primitives/Card.tsx` (new)
- `web/src/shared/components/ui/layout/CardGrid.tsx` (new)
- `web/src/features/financial_data/components/BacenInstitutionCard.tsx` (new)
- `web/src/app/(financial-data)/bacen-institutions/page.tsx` (new)
- `web/src/lib/graphql/operations/financial_data/bacen-institutions.ts` (new, GraphQL operations)

**Updates:**
- `web/src/shared/components/ui/primitives/index.ts` (update exports)
- `web/src/shared/components/ui/layout/index.ts` (update exports)

