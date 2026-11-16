# First delivery: reserve a shared room

[Back to the Neotool Guideline](../guideline.md)

This is a fictional, filled example. The implementation references and check results illustrate what a team's records could look like after executing the delivery.

## CTX-001 · Shared room coordination

- **Owner:** Workplace Operations · **State:** Current · **Revision:** 1
- **Audience and problem:** Office staff book one shared room. Requests arrive by chat, and staff sometimes agree to overlapping times.
- **Intended outcome:** Staff can see right away whether a requested time is available. During a five-day pilot, Workplace Operations will review booking attempts and look for zero confirmed overlaps.
- **Constraints:** Use one room and one office time zone. Accept bookings only; keep cancellations out of this delivery.
- **Inputs and assumptions:** In this example, the operations lead confirmed the current chat workflow. The assumption that a single booking form is enough for the pilot still needs feedback from staff.

## DEC-001 · Start with a single room booking form

- **Owner and decision authority:** Workplace Operations lead · **State:** Accepted
- **Context:** CTX-001 revision 1
- **Choice:** Let staff enter a start and end time and get immediate confirmation or a conflict message.
- **Alternatives:** Keep routing requests through a coordinator; integrate with the company calendar.
- **Rationale and consequences:** Immediate feedback addresses overlapping requests without waiting for calendar integration. The form becomes the pilot's booking channel. Calendar sync and cancellations require later decisions.

## SPEC-001 · Submit a booking and receive a result

- **Owner:** Delivery lead · **State:** Ready for implementation · **Revision:** 1
- **Inputs:** CTX-001 revision 1, DEC-001
- **Scope:** A form and stored bookings for one shared room. End times are exclusive, so adjacent bookings are allowed. Multi-room support, calendar sync, and cancellations are outside this delivery.
- **Implementation authorization:** Delivery lead approved the agent to edit application and test files in the pilot workspace and run local checks against disposable data.

| Criterion | Expected behavior | Verification |
| --- | --- | --- |
| **AC-1** | Given an empty calendar, a valid 10:00–11:00 request is confirmed and still appears after reloading. | Submit through the form, reload, and confirm the saved interval. |
| **AC-2** | With 10:00–11:00 reserved, a request for 10:30–11:30 displays a conflict and creates no booking. | Seed the first booking, submit the overlap, and inspect the message and saved count. |
| **AC-3** | With 10:00–11:00 reserved, a request for 11:00–12:00 is accepted. | Seed the first booking, submit the adjacent interval, and confirm both appear. |
| **AC-4** | If two overlapping requests arrive together, exactly one is accepted. | Submit conflicting requests concurrently to an empty calendar; inspect both responses and the saved count. |

## EVD-001 · Booking flow verification

- **Owner:** Delivery lead · **State:** Recorded
- **Verified:** SPEC-001 revision 1 against illustrative build `room-booking/pilot.1`, in a local environment with a disposable database.
- **Checked by:** Coding agent using the project's verification suite on 2026-01-15 (illustrative).
- **Procedure:** Start the local build with a fresh database. Run the booking form scenarios and the concurrent request integration check, resetting fixtures between cases. The table captures the relevant observations.

| Criterion | Result | Observation |
| --- | --- | --- |
| **AC-1** | Passed | Confirmation appeared and 10:00–11:00 remained visible after reload. |
| **AC-2** | Passed | The form showed a conflict, and storage contained only the seeded booking. |
| **AC-3** | Passed | Confirmation appeared and both adjacent intervals were stored. |
| **AC-4** | Passed | One response confirmed the booking and one reported a conflict. Exactly one booking was stored. |

**Limits and next step:** These observations support the first delivery's acceptance criteria in the local environment. The five-day pilot outcome in CTX-001 is still unmeasured. Workplace Operations will collect that evidence after rollout and use it to decide whether to expand the booking flow.
