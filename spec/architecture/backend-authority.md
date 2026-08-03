---
id: architecture.backend-authority
status: canonical
kind: architecture
source_of_truth: code
last_verified: 2026-08-02
schema: spec-metadata-v1
---

# Backend Authority

Backend services are the enforcement boundary for security and domain correctness.

## Backend Owns

- Authorization enforcement.
- Permission checks.
- Validation.
- Idempotency.
- Domain invariants.
- Persistence consistency.
- External side-effect boundaries.

## Frontend And Mobile Own

- User experience.
- Field visibility.
- Action visibility.
- Permission hints from token or user context.
- Client-side validation for responsiveness.

## Spec Rule

Feature specs must distinguish:

- `Backend enforcement`: what the service actually rejects or guarantees.
- `UX hints`: what the client hides, disables, or pre-validates to avoid unnecessary denies.

If a rule exists only in frontend or mobile, do not document it as an invariant until backend evidence exists.
