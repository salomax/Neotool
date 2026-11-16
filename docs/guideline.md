# Neotool Guideline

**Neotool is an AI-driven engineering framework for exploring, structuring, creating, and evolving an initiative.** An initiative can be a project, a product, a solution, or a change to something that already exists. Start where you are: a rough idea, a problem you have observed, or a proposal with research behind it.

## Start with a conversation

Open your existing repository or a workspace for the initiative. Give your coding agent access to the [Start Initiative skill](../.agents/skills/neotool-start-initiative/SKILL.md) in the Neotool checkout. Bring whatever you already have: notes, research, designs, existing code, or just an observation.

> Use the Neotool Start Initiative skill. I want to start an initiative around [idea, problem, or opportunity]. The material I already have is [links or paths, if any]. Help me organize what we know and choose what to do next.

The agent reads the relevant material, summarizes the situation in your terms, and separates sourced observations, assumptions, and proposed solutions. It asks about gaps that could change what you do next. You bring real examples, correct its reading, and decide which uncertainty is worth addressing.

Together, capture a short [Initiative Brief](../spec/templates/initiative-brief.md): what you know, what you still need to learn, and the next action you agree on. You can begin in the conversation and save the brief in your workspace when you need a durable handoff.

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

Start from a short, discoverable map: the workspace instructions, the brief, and links to records needed for the current question. Load those records first and follow only the relevant links. Deep research, architecture references, and detailed specs can stay where they are until needed.

- **One maintained record per topic.** Give each durable record a stable ID, kind, state, short summary, and an owner when responsibility needs to be explicit. Use a path or link alongside referenced IDs so the agent can resolve them. Records can be sections in one file.
- **Name what a link means.** A brief *raises* a question; a finding *answers* or *partially answers* it; a decision *uses* a finding; a Spec *implements* an accepted decision; a check *verifies* a criterion. For example, a decision can reference the finding by ID and link to its section rather than copying the research.
- **Keep evidence and intent distinguishable.** Cite a source at the point of claim, with the relevant locator, date or revision when useful, and limitations. Separate what the source says from what the agent infers. Distinguish an agreed Spec about intended behavior from a record of what the system does now.
- **Resume from the latest state.** Load the current brief, the last relevant result, and decisions or evidence added since then. Recheck affected links when upstream assumptions or sources change. Keep open questions visible.

## Continue through creation and implementation

The brief links to the next activity. Use an available [skill](../.agents/skills/neotool-start-initiative/SKILL.md) to guide repeated procedures; when no specialized skill exists, give the agent the agreed question, inputs, expected output, and stopping condition. Each activity returns evidence, a decision, or a clearer question to the brief.

When you have behavior worth defining, write a [Spec](../spec/templates/first-delivery.md) with observable criteria. The agent uses the Spec and relevant workspace conventions during implementation and links validation results back to the criteria. The [booking example](examples/first-delivery.md) shows those records at a later point in the initiative.

The harness is the execution environment for context loading, tool access, permissions, checks, and handoffs. You can start with your current agent and workspace tools. Neotool currently provides this guide, the orientation skill, and templates; specialized skills and a dedicated harness can build on these records.

See [the practice benchmark](research/initiative-orientation.md) for the evidence behind this design.
