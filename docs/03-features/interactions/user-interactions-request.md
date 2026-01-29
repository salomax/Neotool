# Interactions Module Specification

## Overview

- **Purpose:** Provide a generic, flexible system for tracking user interactions with resources across the platform. Supports favorites, views, pins, preferences, and any custom interaction types.
- **Scope:** Backend service/API (GraphQL) to store, query, and manage user-resource interactions for both authenticated and anonymous users.
- **Key differentiator:** Single abstraction handles multiple use cases (favorites, recent views, preferences, pins) without requiring separate implementations.
- **Out of scope (current impl):** Real-time sync across devices, interaction analytics/insights, recommendation engine, social features (sharing favorites).

## Goals

- Unified interaction model that handles all user-resource relationship types.
- Support both authenticated users (permanent storage) and anonymous users (session-based with TTL).
- Seamless merge of anonymous interactions when user authenticates.
- Generic enough to support any resource type without schema changes.
- High performance for common queries (my favorites, recent views, has interaction).
- Clean GraphQL API with proper pagination support.

## Use Cases

| Use Case | resource_type | interaction_type | metadata |
|----------|---------------|------------------|----------|
| Favorite a product | `product` | `FAVORITE` | `{}` |
| View history | `product` | `VIEW` | `{ "viewedAt": "timestamp" }` |
| Save a search | `search` | `FAVORITE` | `{ "query": "...", "filters": {...} }` |
| Feature preference | `feature` | `PREFERENCE` | `{ "order": 1 }` |
| Hide an item | `product` | `HIDE` | `{ "reason": "not_interested" }` |
| Pin to top | `report` | `PIN` | `{ "position": 0 }` |
| Recently accessed | `document` | `VIEW` | `{ "accessCount": 5 }` |

## Functional Requirements

### 1) Interaction Model

**Core concept:** `UserInteraction = (user_id, resource_type, resource_id, interaction_type, metadata, timestamp)`

**Interaction Types (MVP):**
- `FAVORITE` - User-marked items for quick access
- `VIEW` - Automatically tracked views/access history
- `PIN` - User-pinned items (ordered)
- `HIDE` - User-hidden items (exclusion lists)
- `PREFERENCE` - User preferences for features/settings

**Must implement now:**
- CRUD operations for interactions
- Query interactions by type, resource type, or both
- Check if specific interaction exists (boolean)
- Reorder pinned/favorited items
- Bulk operations (add/remove multiple)
- Pagination with cursor-based navigation

**Explicitly NOT required now:**
- Interaction analytics
- Recommendations based on interactions
- Social sharing of favorites
- Cross-device real-time sync

**Prepared (no implementation):**
- Custom interaction types beyond the MVP set
- Interaction-based notifications
- Export/import of user interactions

### 2) Anonymous User Support

**Strategy:** Session-based storage with automatic TTL cleanup.

**Requirements:**
- Generate stable session ID on first interaction (stored in browser)
- Store anonymous interactions with 30-day TTL
- Automatic cleanup of expired sessions via scheduled job
- Session ID format: UUID v4

**Anonymous interaction limits:**
- Max 1000 interactions per session (prevent abuse)
- Max 100 interactions per resource_type per session
- Rate limit: 100 interactions per hour per session

### 3) Authentication Merge Flow

**When user signs in:**
1. Retrieve all anonymous interactions for current session
2. Merge into authenticated user's interactions (upsert logic)
3. On conflict: keep newer interaction, merge metadata
4. Delete anonymous session data after successful merge
5. Clear session ID from browser storage

**Merge rules:**
- `FAVORITE`: Keep if not already favorited, update timestamp
- `VIEW`: Merge view counts, keep latest viewedAt
- `PIN`: Preserve user's authenticated pin order, append new pins at end
- `HIDE`: Union of hidden items
- `PREFERENCE`: Authenticated preferences take precedence

### 4) Data Storage

**Separate tables for authenticated vs anonymous:**

```sql
-- For authenticated users (permanent)
CREATE TABLE app.user_interactions (
  id UUID PRIMARY KEY DEFAULT uuidv7(),
  user_id UUID NOT NULL REFERENCES security.users(id) ON DELETE CASCADE,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(255) NOT NULL,
  interaction_type VARCHAR(32) NOT NULL,
  metadata JSONB DEFAULT '{}',
  position INT,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),
  version BIGINT DEFAULT 0,

  UNIQUE(user_id, resource_type, resource_id, interaction_type)
);

-- For anonymous users (TTL-based)
CREATE TABLE app.anonymous_interactions (
  id UUID PRIMARY KEY DEFAULT uuidv7(),
  session_id VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(255) NOT NULL,
  interaction_type VARCHAR(32) NOT NULL,
  metadata JSONB DEFAULT '{}',
  created_at TIMESTAMPTZ DEFAULT NOW(),
  expires_at TIMESTAMPTZ DEFAULT NOW() + INTERVAL '30 days',

  UNIQUE(session_id, resource_type, resource_id, interaction_type)
);
```

**Why separate tables:**
- Different lifecycle (permanent vs TTL)
- Different indexes optimized for each access pattern
- Simpler cleanup logic for anonymous data
- Clear data ownership boundaries

### 5) API Surface (GraphQL)

**Types:**

```graphql
enum InteractionType {
  FAVORITE
  VIEW
  PIN
  HIDE
  PREFERENCE
}

type UserInteraction {
  id: ID!
  resourceType: String!
  resourceId: String!
  interactionType: InteractionType!
  metadata: JSON
  position: Int
  createdAt: DateTime!
  updatedAt: DateTime!
}

type UserInteractionEdge {
  node: UserInteraction!
  cursor: String!
}

type UserInteractionConnection {
  edges: [UserInteractionEdge!]!
  pageInfo: PageInfo!
  totalCount: Int!
}
```

**Queries:**

```graphql
extend type Query {
  """
  Get current user's interactions with filtering.
  Works for both authenticated and anonymous users.
  For anonymous users, requires sessionId header.
  """
  myInteractions(
    resourceType: String
    interactionType: InteractionType
    first: Int = 20
    after: String
  ): UserInteractionConnection!

  """
  Check if current user has a specific interaction.
  Fast boolean check optimized for UI rendering.
  """
  hasInteraction(
    resourceType: String!
    resourceId: String!
    interactionType: InteractionType!
  ): Boolean!

  """
  Get multiple interaction checks in a single query.
  Useful for rendering lists with interaction states.
  """
  hasInteractions(
    checks: [InteractionCheckInput!]!
  ): [InteractionCheckResult!]!

  """
  Get interaction counts by type for current user.
  """
  interactionCounts(
    resourceType: String
  ): [InteractionCount!]!
}

input InteractionCheckInput {
  resourceType: String!
  resourceId: String!
  interactionType: InteractionType!
}

type InteractionCheckResult {
  resourceType: String!
  resourceId: String!
  interactionType: InteractionType!
  exists: Boolean!
}

type InteractionCount {
  interactionType: InteractionType!
  count: Int!
}
```

**Mutations:**

```graphql
extend type Mutation {
  """
  Toggle an interaction (add if not exists, remove if exists).
  Returns the interaction if added, null if removed.
  """
  toggleInteraction(input: ToggleInteractionInput!): UserInteraction

  """
  Add an interaction. Fails if already exists (use toggle for idempotent operations).
  """
  addInteraction(input: AddInteractionInput!): UserInteraction!

  """
  Remove an interaction. Returns true if removed, false if not found.
  """
  removeInteraction(input: RemoveInteractionInput!): Boolean!

  """
  Update interaction metadata or position.
  """
  updateInteraction(input: UpdateInteractionInput!): UserInteraction!

  """
  Reorder interactions of a specific type.
  Accepts ordered list of interaction IDs, updates positions accordingly.
  """
  reorderInteractions(input: ReorderInteractionsInput!): [UserInteraction!]!

  """
  Bulk add interactions. Skips duplicates.
  """
  addInteractions(input: AddInteractionsInput!): [UserInteraction!]!

  """
  Bulk remove interactions. Returns count of removed items.
  """
  removeInteractions(input: RemoveInteractionsInput!): Int!

  """
  Merge anonymous interactions into authenticated user.
  Called automatically on sign-in, but exposed for manual trigger.
  """
  mergeAnonymousInteractions(sessionId: String!): MergeResult!
}

input ToggleInteractionInput {
  resourceType: String!
  resourceId: String!
  interactionType: InteractionType!
  metadata: JSON
}

input AddInteractionInput {
  resourceType: String!
  resourceId: String!
  interactionType: InteractionType!
  metadata: JSON
  position: Int
}

input RemoveInteractionInput {
  resourceType: String!
  resourceId: String!
  interactionType: InteractionType!
}

input UpdateInteractionInput {
  id: ID!
  metadata: JSON
  position: Int
}

input ReorderInteractionsInput {
  resourceType: String!
  interactionType: InteractionType!
  orderedIds: [ID!]!
}

input AddInteractionsInput {
  interactions: [AddInteractionInput!]!
}

input RemoveInteractionsInput {
  resourceType: String!
  interactionType: InteractionType!
  resourceIds: [String!]!
}

type MergeResult {
  merged: Int!
  skipped: Int!
  conflicts: Int!
}
```

### 6) Session ID Handling

**Header-based approach for anonymous users:**

```
X-Session-Id: <uuid>
```

**Backend behavior:**
1. If user is authenticated (JWT present): use user_id, ignore session header
2. If user is anonymous: require X-Session-Id header
3. If neither: return error for mutation, empty result for queries

**Frontend responsibility:**
- Generate session ID on first interaction attempt
- Store in localStorage: `neotool_session_id`
- Include in all GraphQL requests via Apollo link
- Clear on successful sign-in (after merge completes)

### 7) Performance Optimizations

**Indexes:**

```sql
-- Fast lookup for "my favorites of type X"
CREATE INDEX idx_user_interactions_user_type
  ON app.user_interactions(user_id, interaction_type);

-- Fast lookup for specific resource
CREATE INDEX idx_user_interactions_resource
  ON app.user_interactions(user_id, resource_type, resource_id);

-- Fast "has interaction" check
CREATE INDEX idx_user_interactions_check
  ON app.user_interactions(user_id, resource_type, resource_id, interaction_type);

-- Ordering for pinned/favorited items
CREATE INDEX idx_user_interactions_position
  ON app.user_interactions(user_id, interaction_type, position)
  WHERE position IS NOT NULL;

-- Recent views query
CREATE INDEX idx_user_interactions_recent
  ON app.user_interactions(user_id, interaction_type, updated_at DESC)
  WHERE interaction_type = 'VIEW';

-- Anonymous session lookup
CREATE INDEX idx_anon_interactions_session
  ON app.anonymous_interactions(session_id);

-- Anonymous cleanup
CREATE INDEX idx_anon_interactions_expires
  ON app.anonymous_interactions(expires_at);
```

**Caching strategy:**
- Cache `hasInteraction` results in Apollo Client (5 min TTL)
- Invalidate on toggle/add/remove mutations
- Use optimistic updates for instant UI feedback

## Non-Functional Requirements

### Performance & Scalability

- **Query latency:** p95 < 50ms for `hasInteraction`, p95 < 100ms for `myInteractions`
- **Write latency:** p95 < 100ms for toggle/add/remove
- **Batch operations:** Support up to 100 items per bulk operation
- **Pagination:** Cursor-based, max 100 items per page
- **Concurrent users:** Support 10k concurrent users without degradation

### Data Retention

- **Authenticated users:** Interactions stored indefinitely (until user deletion)
- **Anonymous users:** 30-day TTL, automatic cleanup
- **Soft delete:** Not implemented; hard delete with audit log

### Security

- **Authorization:** Users can only access/modify their own interactions
- **Anonymous abuse prevention:** Rate limiting + session limits
- **Input validation:** Strict validation on resource_type and interaction_type
- **No PII in metadata:** Avoid storing sensitive data in metadata field

## Frontend Integration

### React Hook

```typescript
// hooks/useInteraction.ts
export function useInteraction(
  resourceType: string,
  resourceId: string,
  interactionType: InteractionType
) {
  const sessionId = useAnonymousSession();
  const { user } = useAuth();

  const { data, loading } = useQuery(HAS_INTERACTION_QUERY, {
    variables: { resourceType, resourceId, interactionType },
    context: { headers: user ? {} : { 'X-Session-Id': sessionId } },
  });

  const [toggleMutation] = useMutation(TOGGLE_INTERACTION_MUTATION, {
    optimisticResponse: {
      toggleInteraction: data?.hasInteraction ? null : {
        __typename: 'UserInteraction',
        id: 'temp-id',
        resourceType,
        resourceId,
        interactionType,
        metadata: {},
        position: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    },
    update: (cache, { data }) => {
      cache.writeQuery({
        query: HAS_INTERACTION_QUERY,
        variables: { resourceType, resourceId, interactionType },
        data: { hasInteraction: data?.toggleInteraction !== null },
      });
    },
  });

  const toggle = useCallback(() => {
    toggleMutation({
      variables: { input: { resourceType, resourceId, interactionType } },
      context: { headers: user ? {} : { 'X-Session-Id': sessionId } },
    });
  }, [resourceType, resourceId, interactionType, user, sessionId]);

  return {
    isActive: data?.hasInteraction ?? false,
    loading,
    toggle,
  };
}
```

### Component Example

```tsx
// components/FavoriteButton.tsx
export function FavoriteButton({
  resourceType,
  resourceId
}: {
  resourceType: string;
  resourceId: string;
}) {
  const { isActive, loading, toggle } = useInteraction(
    resourceType,
    resourceId,
    'FAVORITE'
  );

  return (
    <IconButton
      onClick={toggle}
      disabled={loading}
      aria-label={isActive ? 'Remove from favorites' : 'Add to favorites'}
    >
      {isActive ? <StarFilled /> : <StarOutline />}
    </IconButton>
  );
}
```

### Session Management Hook

```typescript
// hooks/useAnonymousSession.ts
const SESSION_KEY = 'neotool_session_id';

export function useAnonymousSession(): string {
  const [sessionId, setSessionId] = useState<string>(() => {
    if (typeof window === 'undefined') return '';
    return localStorage.getItem(SESSION_KEY) || '';
  });

  useEffect(() => {
    if (!sessionId) {
      const newSessionId = crypto.randomUUID();
      localStorage.setItem(SESSION_KEY, newSessionId);
      setSessionId(newSessionId);
    }
  }, [sessionId]);

  return sessionId;
}

export function clearAnonymousSession(): void {
  localStorage.removeItem(SESSION_KEY);
}
```

### Auth Integration

```typescript
// In AuthProvider or sign-in handler
async function onSignInSuccess(user: User, sessionId: string) {
  if (sessionId) {
    // Merge anonymous interactions
    await mergeAnonymousInteractions({ variables: { sessionId } });
    clearAnonymousSession();
  }
}
```

## Implementation Plan

### Phase 0 - Foundations

**Checkpoint 0.1: Module setup**
- Create `service/kotlin/interactions` module
- Define package root `io.github.salomax.neotool.interactions`
- Add Micronaut wiring, build config, CI integration
- No features yet; scaffolding only

### Phase 1 - Core Data Model

**Checkpoint 1.1: Database schema**
- Create migration for `user_interactions` table
- Create migration for `anonymous_interactions` table
- Add all indexes
- Create entity classes with optimistic locking

**Checkpoint 1.2: Repository layer**
- Implement `UserInteractionRepository`
- Implement `AnonymousInteractionRepository`
- Add batch operation support

### Phase 2 - Service Layer

**Checkpoint 2.1: Core service**
- Implement `InteractionService` with CRUD operations
- Add session ID validation
- Implement toggle logic

**Checkpoint 2.2: Merge service**
- Implement `InteractionMergeService`
- Add conflict resolution logic
- Add merge transaction handling

### Phase 3 - GraphQL API

**Checkpoint 3.1: Queries**
- Implement `myInteractions` with pagination
- Implement `hasInteraction` and `hasInteractions`
- Implement `interactionCounts`

**Checkpoint 3.2: Mutations**
- Implement toggle, add, remove mutations
- Implement bulk operations
- Implement reorder mutation
- Implement merge mutation

### Phase 4 - Frontend Integration

**Checkpoint 4.1: Hooks and utilities**
- Create `useInteraction` hook
- Create `useAnonymousSession` hook
- Add Apollo link for session header

**Checkpoint 4.2: Components**
- Create `FavoriteButton` component
- Create `InteractionList` component
- Add to existing UI where needed

### Phase 5 - Cleanup and Optimization

**Checkpoint 5.1: Background jobs**
- Implement anonymous session cleanup job (daily)
- Add monitoring for cleanup metrics

**Checkpoint 5.2: Performance testing**
- Load test with 10k concurrent users
- Optimize slow queries if needed
- Add caching layer if required

## Configuration

### Service Configuration

```yaml
# application.yml
interactions:
  anonymous:
    ttl-days: 30
    max-per-session: 1000
    max-per-resource-type: 100
    rate-limit-per-hour: 100
  pagination:
    default-page-size: 20
    max-page-size: 100
  cleanup:
    enabled: true
    cron: "0 0 3 * * *"  # Daily at 3 AM
```

### Environment Variables

```bash
# Anonymous session TTL (days)
INTERACTIONS_ANONYMOUS_TTL_DAYS=30

# Max interactions per anonymous session
INTERACTIONS_ANONYMOUS_MAX_PER_SESSION=1000

# Cleanup job enabled
INTERACTIONS_CLEANUP_ENABLED=true
```

## Observability

### Metrics

- `interactions_total{type, operation}` - Counter of operations by type
- `interactions_latency_seconds{type, operation}` - Latency histogram
- `anonymous_sessions_active` - Gauge of active anonymous sessions
- `anonymous_sessions_cleaned` - Counter of cleaned sessions
- `merge_operations_total{result}` - Counter of merge operations

### Logging

Structured logs for:
- Interaction created/updated/deleted
- Anonymous session created
- Merge operation (with counts)
- Cleanup job results
- Rate limit violations

### Alerts

- High latency on `hasInteraction` (> 100ms p95)
- Cleanup job failure
- Rate limit abuse patterns
- Database connection issues

## Migration & Rollout

### Phase 1: Internal Testing
- Deploy to staging environment
- Test with internal users
- Verify merge flow on sign-in

### Phase 2: Gradual Rollout
- Enable for 10% of users via feature flag
- Monitor error rates and latency
- Increase to 50%, then 100%

### Phase 3: Feature Integration
- Add favorite buttons to product listings
- Add recent views to user dashboard
- Enable search result personalization

## Testing Strategy

### Unit Tests
- Service layer logic (toggle, merge, cleanup)
- Conflict resolution rules
- Session validation

### Integration Tests
- Full CRUD flow with database
- Merge operation with real data
- Cleanup job execution

### E2E Tests
- Anonymous user flow
- Sign-in with merge
- Favorite button interactions

## Reference Implementations

### Reddit/HackerNews
- `votes` table with `(user_id, item_type, item_id, vote_type)`
- Separate view history with TTL

### Notion
- `user_content_interactions` with JSONB metadata
- Favorites as "starred pages"
- Recent items via last_accessed query

### Spotify
- `user_library_items` with type discriminator
- Playlists as interaction containers
- Heavy Redis caching for recents

### GitHub
- Stars = interaction `star` on repos
- Watch = interaction with notification levels
- Metadata includes notification preferences

---

## Implementation Checklist (MVP)

### Database
- [ ] Create `user_interactions` table migration
- [ ] Create `anonymous_interactions` table migration
- [ ] Add all required indexes
- [ ] Create entity classes

### Backend
- [ ] Create module structure
- [ ] Implement repositories
- [ ] Implement `InteractionService`
- [ ] Implement `InteractionMergeService`
- [ ] Add GraphQL queries
- [ ] Add GraphQL mutations
- [ ] Add cleanup scheduled job
- [ ] Add rate limiting

### Frontend
- [ ] Create `useInteraction` hook
- [ ] Create `useAnonymousSession` hook
- [ ] Add Apollo link for session header
- [ ] Create `FavoriteButton` component
- [ ] Integrate merge on sign-in

### Testing
- [ ] Unit tests for services
- [ ] Integration tests for repositories
- [ ] E2E tests for user flows

### Documentation
- [ ] API documentation
- [ ] Frontend integration guide
- [ ] Runbook for operations
