# ADR-XXX: [Short Decision Title]

**Status**: [Proposed | Accepted | Deprecated | Superseded by ADR-YYY]
**Date**: YYYY-MM-DD
**Deciders**: [Who made this decision]
**Tags**: [relevant, tags, for, search]

## Context

[What is the issue we're facing? What forces are at play? What constraints exist?]

**Key Points:**
- [Why is this decision needed?]
- [What problem does it solve?]
- [What context should the LLM know?]

Example:
- We need unique, sortable identifiers for all entities
- UUID v4 provides uniqueness but not time-ordering
- Database performance suffers from random UUID inserts
- Time-ordered IDs improve index performance by 40%

## Decision

[What did we decide to do?]

**We will [decision statement].**

Example:
We will use UUID v7 for all entity primary keys instead of UUID v4.

## Rationale

[Why did we make this decision? What are the benefits?]

- [Benefit 1]
- [Benefit 2]
- [Trade-off we accepted]

Example:
- UUID v7 provides chronological ordering while maintaining uniqueness
- Database B-tree indexes perform better with sequential inserts
- Time-ordering helps with debugging and audit trails
- Maintains UUID compatibility with existing systems
- Trade-off: Slightly reveals creation time (acceptable for our use case)

## Consequences

### Positive
- [Good outcome 1]
- [Good outcome 2]

### Negative
- [Downside or limitation 1]
- [Downside or limitation 2]

### Neutral
- [Change that's neither good nor bad]

Example:

### Positive
- 40% improvement in database insert performance
- Better query performance on time-range filters
- Easier debugging with chronological IDs

### Negative
- Creation timestamp can be inferred from ID (minor privacy concern)
- Requires custom UUID generator (not standard library)

### Neutral
- All new code must use UUID v7 generator
- Existing UUID v4 IDs grandfathered (no migration needed)

## Implementation

[How should this decision be implemented? What should LLMs do?]

**For LLMs implementing features:**
- [Concrete instruction 1]
- [Concrete instruction 2]

**Example code/pattern to follow:**
- Path: `/path/to/example/code`
- Key files: `SpecificFile.kt`

Example:

**For LLMs implementing features:**
- Use `UuidV7Generator.generate()` for all new entity IDs
- Do NOT use `UUID.randomUUID()` for entities
- Follow pattern in `/service/kotlin/common/src/main/kotlin/domain/UuidV7Generator.kt`

**Example entity:**
```kotlin
@Entity
class User(
    @Id
    val id: UUID = UuidV7Generator.generate(),
    // ... other fields
)
```

## Alternatives Considered

### [Alternative 1 Name]
**Description**: [What was this option?]
**Pros**: [Benefits]
**Cons**: [Drawbacks]
**Why rejected**: [Reason]

### [Alternative 2 Name]
**Description**: [What was this option?]
**Pros**: [Benefits]
**Cons**: [Drawbacks]
**Why rejected**: [Reason]

Example:

### UUID v4 (Status Quo)
**Description**: Continue using random UUID v4
**Pros**: Standard library support, well-understood
**Cons**: Random inserts hurt database performance, no time ordering
**Why rejected**: Performance impact too significant at scale

### Auto-increment IDs
**Description**: Use database-generated sequential integers
**Pros**: Maximum performance, simple
**Cons**: Not globally unique, reveals record count, distributed system issues
**Why rejected**: Need globally unique IDs for distributed architecture

### ULID (Universally Unique Lexicographically Sortable Identifier)
**Description**: Similar to UUID v7 but different encoding
**Pros**: Lexicographically sortable as strings
**Cons**: Not UUID-compatible, less tooling support
**Why rejected**: UUID v7 provides same benefits with better ecosystem support

## References

- [Link to relevant documentation]
- [Link to research/benchmarks]
- [Related ADRs]

Example:

- [UUID v7 Specification](https://datatracker.ietf.org/doc/html/draft-peabody-dispatch-new-uuid-format)
- [Database Performance Benchmark](https://www.percona.com/blog/2019/11/22/uuids-are-popular-but-bad-for-performance/)
- Related: ADR-015 (Database indexing strategy)

## For LLM Context

**When to reference this ADR:**
- Implementing any new entity with an ID field
- Creating database migrations
- Reviewing code that generates UUIDs

**Key takeaway for LLMs:**
Always use UUID v7 for entity IDs. See `/service/kotlin/common/domain/UuidV7Generator.kt` for implementation.

---

**History:**
- YYYY-MM-DD: Proposed
- YYYY-MM-DD: Accepted after team review
- YYYY-MM-DD: Updated with implementation example
