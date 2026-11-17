# Product knowledge and traceability

This contract defines how Neotool represents product knowledge from strategy to observed results. It gives people and agents a shared way to find authoritative records, determine what they mean, and trace why a change exists and what happened after it shipped.

## Why separate levels?

Levels distinguish questions that have different answers and change at different rates. A product goal may outlive several initiatives. An accepted requirement may stay the same while its design changes. A release may ship a behavior before there is evidence that it improved the intended outcome. Keeping these statements addressable lets a team revise one without rewriting the others, and lets an agent load the context needed for its task.

Levels classify **the role of a record**, rather than its folder, file type, or place in a mandatory process. A document can contain several records at different levels. A small change can link a PRD requirement directly to an issue when no separate design record is needed. The [transversal capabilities](../README.md#capabilities-across-the-levels), including agent execution, support work across these levels.

## Levels and their records

| Level | Question and boundary | Records and concrete examples | Connections |
| --- | --- | --- | --- |
| **1. Strategy** | **Where are we going, for whom, and how will we judge progress?** Durable direction and desired outcomes. | **Vision:** “Make self-service booking reliable for small teams.” **Strategic choice:** focus on shared office resources. **Objective `OBJ-01`:** reduce scheduling conflicts in a pilot, measured against the pre-pilot baseline. | Initiatives **advance** objectives. Outcome measurements **assess** them. |
| **2. Opportunity and initiative** | **What problem or opportunity merits work now, and what supports that choice?** Discovery and investment scope. | **Initiative brief / PR/FAQ `INIT-01`:** pilot one shared room. **Need `NEED-01`:** staff need to know whether a slot is available before relying on a reservation. **Finding `FIND-01`:** staff reported conflicting chat confirmations; cite the interview notes. **Hypothesis:** immediate feedback will reduce conflicts. **Product decision `DEC-01`:** pilot immediate feedback instead of calendar integration. | The initiative **advances** an objective. Findings **support** or **challenge** a need or hypothesis. A product decision **is informed by** findings and records the chosen scope. |
| **3. Product definition** | **What must the product do, within which boundaries, and what would count as acceptable?** Intended behavior and qualities. | **PRD `PRD-01`:** Shared room booking. **Functional requirement `REQ-01`:** reject overlapping reservations. **Quality requirement:** a confirmation latency target, once agreed. **Domain rule:** adjacent intervals are allowed. **Constraint:** the pilot covers one room. **Acceptance criterion `AC-01`:** with 10:00–11:00 booked, a request for 10:30–11:30 shows a conflict and creates no reservation. | The PRD **defines scope for** an initiative. A requirement **addresses** a need or **follows from** a decision. A criterion **specifies acceptance for** a requirement. |
| **4. Experience and technical design** | **How will the experience and system realize the agreed behavior?** Proposed or accepted solution choices. | **UX flow or prototype `UX-01`:** show unavailable time and let staff adjust the request. **Technical design `DES-01`:** check overlaps within a transactional booking operation. **API contract:** a conflict response for an overlapping interval. **ADR `ADR-01`:** record the concurrency approach, why it was chosen, and alternatives. | Designs and contracts **address** requirements or criteria. An ADR **is informed by** the requirements and constraints that drive the choice. Delivery records reference the design they realize. |
| **5. Delivery and implementation** | **What work is planned, what changed, and where did it ship?** Work and implementation identity. | **Epic or issue `WORK-01`:** implement overlap rejection. **Execution plan:** change the booking operation, cover the conflict path in the UI, and prepare the agreed check. **Pull request `PR-01`:** reviewable change with links to code and work. **Release `REL-01`:** pilot build containing the merged revision. | Work **targets** requirements or criteria. A merged change **implements** a requirement where it actually provides the behavior and **completes** or **partially completes** the work item. A release **includes** the change. |
| **6. Verification and operation** | **What was observed, against which version, and did the outcome improve?** Checks, observations, and measurements. | **Test case `TEST-01`:** submit an overlapping booking and inspect the response and stored records. **Result `EVD-01`:** the check passed on the named pilot build in the stated environment. **Outcome measurement `MET-01`:** compare the pilot conflict rate with the baseline. | A test case **covers** a criterion. A result **records** a test execution, **checks** the criterion, and **examines** an implementation revision. A measurement **assesses** the objective. A test pass does not establish the product outcome. |

These examples describe a fictional product. See the [worked delivery](examples/first-delivery.md) for a filled example of context, decision, specification, and evidence.

## Make records addressable

A durable record has a **stable ID, kind, concise statement, state, and a resolvable location**. Include its source when it derives from another record or external material. For a sourced claim, link to the relevant section, revision, or passage. Keep ownership discoverable where a record needs a maintainer or decision authority. Add fields only when they affect navigation, interpretation, or verification.

The kind identifies the role and therefore the level. The state belongs to that kind: a requirement may be proposed, accepted, or superseded; work may be open or merged; a check result may be passed, failed, blocked, or not run. Keep those states distinct. A merged task does not establish acceptance, and an acceptance criterion describes intent even before a test exists.

A PRD is the authoritative record of intended behavior for its scope once accepted. Its requirements and criteria can be individually addressable sections or rows. Design owns the approach; verification owns the observations. If the PRD changes in a way that affects a criterion, evidence from the earlier revision remains historical until the team assesses its applicability or runs the check again.

## Link once; navigate both ways

Record a relationship where it is established, usually in the downstream record. A link identifies the **relationship**, the **target ID**, and a **location** the agent can resolve. Pin a revision when the link asserts what was examined, approved, or shipped. The reverse relationship can be found through backlinks, search, or an index; it does not need to be written twice.

Example relationship chain:

```text
INIT-01 advances OBJ-01
PRD-01 defines scope for INIT-01
REQ-01 addresses NEED-01 (within INIT-01)
AC-01 specifies acceptance for REQ-01 (within PRD-01)
DES-01 addresses REQ-01
WORK-01 targets REQ-01 and references DES-01
PR-01 implements REQ-01 and completes WORK-01
REL-01 includes PR-01
TEST-01 covers AC-01
EVD-01 records TEST-01, checks AC-01 at PRD revision 2,
       and examines REL-01 / implementation revision a1b2c3d
MET-01 assesses OBJ-01 against the pilot baseline
```

A link can cross levels and a record can have multiple links. Add links with meaning: use **targets** for planned work, **covers** for a test definition, and **checks** for an actual result. Record a missing target as a gap rather than inventing an upstream commitment or downstream proof.

## Give the agent a starting point

Use a short **Product Map** as a navigation view of the same records. For example, `Booking → Shared rooms → Prevent overlaps` points to the PRD or requirement. The hierarchy shows where to open a topic; the links show why the requirement exists and what implements or verifies it. A roadmap is another view: it references initiatives and planned releases by priority and sequence.

Start at the map, open the relevant record, and follow only the links needed for the question. For *“is overlap rejection verified?”*, load the criterion and the result for the relevant revision. For *“why do we require it?”*, follow the requirement to the need, initiative, and sources. For *“what changes if we allow overlaps?”*, query downstream designs, work, and checks, and flag gaps in those links.

Start with Markdown headings, IDs, and links. Trackers, generated indexes, and graph stores can implement the same contract. A harness can later check duplicate IDs, unresolved links, and missing revision references. It cannot infer that a result proves a requirement just because the link exists.
