# Neotool Guideline

**Neotool is an AI-driven engineering framework for exploring, structuring, creating, and evolving an initiative.** An initiative can be a project, a product, a solution, or a change to something that already exists. Start where you are: a rough idea, a problem you have observed, or a proposal with research behind it.

## Start with product strategy

Open your product workspace and give your agent access to the [Product Strategy skill](../.agents/skills/neotool-product-strategy/SKILL.md) in the Neotool checkout. Bring any existing strategy, research, or observations. A short description of the idea is enough to start the conversation.

> Use the Neotool Product Strategy skill to help me define or review our strategy. Here is what I know about the audience, problem, or opportunity. Here are the sources I have so far.

The agent finds the relevant sources and summarizes the audience, intended improvement, and current focus. Together, review consequential choices and agree on how to measure progress. Use the [strategy template](../spec/templates/product-strategy.md) and [worked example](examples/product-strategy.md) to record the outcome.

## Connect the initiative to strategy

Look for an agreed product objective that the initiative can advance. If you find one, link its ID from the Initiative Brief. Read the relevant part of the strategy to understand the choice and the intended outcome.

When direction is missing or needs revision, use the Product Strategy skill in the same session. The agent summarizes the available sources, surfaces consequential trade-offs, and proposes ways to measure progress. The person confirms the choices and objectives that will guide the work. Record unresolved choices and missing baselines explicitly. An early discovery activity can help resolve them.

Keep the strategy short and add it to the Product Map or existing workspace index. Record the objective ID and link, or the unresolved strategy question, in the [Initiative Brief](../spec/templates/initiative-brief.md).

## Choose the next move

Choose based on the question that most affects the initiative. These are entry points you can revisit as you learn.

| Move | Use it when | Agree on what the work should produce |
| --- | --- | --- |
| **Explore the problem** | You are unsure who is affected, how they work today, or where the pain comes from. | A question about the current situation, who or what can answer it, and observations that would change your understanding. The agent can prepare interviews or analyze existing inputs; mark user evidence still to be collected. |
| **Research** | You need to answer a focused question about feasibility, constraints, or available approaches. | A sourced answer with the strength and limits of the evidence, and the decision it informs. Identify what needs a local experiment or expert input. |
| **Benchmark** | You need to compare existing options against a baseline or understand how others solve the same problem. | A comparison using agreed criteria, comparable conditions, and cited sources. Include the current workflow when relevant. Mark missing or incomparable data. |
| **Design the solution** | The problem, intended outcome, and important constraints are clear enough to explore a solution. | Alternatives, key trade-offs, and a sketch, prototype, or model that can test an assumption. Agree on what would make you refine or reconsider the design. |

For example, “We should build a room-booking app” could lead to **exploring the problem** if the team has no examples of scheduling failures, or to **benchmarking** if the problem is established and calendar tools are already available. Those are different next actions even though the initial idea is the same.

## Give the agent context it can use

Start from workspace instructions and a short Product Map that points to the brief and the records needed for the current question. Load those records first and follow only the relevant links. Deep research, architecture references, and detailed specs can stay where they are until needed.

- **One maintained record per topic.** Give each durable record a stable ID, kind, state, short summary, and an owner when responsibility needs to be explicit. Use a path or link alongside referenced IDs so the agent can resolve them. Records can be sections in one file.
- **Name what a link means.** An initiative *advances* an objective; a finding *supports* or *challenges* a hypothesis; a requirement *addresses* a need; work *targets* a requirement; a test *covers* a criterion; a result *checks* it against a specific implementation revision. For example, a decision can reference the finding by ID and link to its section rather than copying the research.
- **Keep evidence and intent distinguishable.** Cite a source at the point of claim, with the relevant locator, date or revision when useful, and limitations. Separate what the source says from what the agent infers. Distinguish an agreed Spec about intended behavior from a record of what the system does now.
- **Resume from the latest state.** Load the current brief, the last relevant result, and decisions or evidence added since then. Recheck affected links when upstream assumptions or sources change. Keep open questions visible.

## Continue through creation and implementation

Use the [product knowledge and traceability contract](product-knowledge.md) to classify records across six levels: strategy, opportunity and initiative, product definition, experience and technical design, delivery and implementation, and verification and operation. These levels describe the role of knowledge; the capabilities in the README support work across them. Follow typed links from needs to requirements, work, and observed results.

The brief links to the next activity. Give the agent the agreed question, inputs, expected output, and stopping condition for that activity. Each activity returns evidence, a decision, or a clearer question to the brief.

When you have behavior worth defining, write a PRD or product Spec with addressable requirements and observable acceptance criteria. Once accepted, it is authoritative for intended behavior in its scope. The [Spec template](../spec/templates/first-delivery.md) is a starting example. Link planned work to requirements, and link each validation result to its criterion, Spec revision, and examined implementation; preserve failed, blocked, and not-run outcomes. The [booking example](examples/first-delivery.md) shows those records at a later point in the initiative.

The harness is the execution environment for context loading, tool access, permissions, checks, and handoffs. You can start with your current agent and workspace tools. Neotool currently provides this guide, the Product Strategy skill, and templates. Specialized skills and a dedicated harness can build on these records.

See [the practice benchmark](research/initiative-orientation.md) for the evidence behind this design.
