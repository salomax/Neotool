# Practice benchmark: starting an AI-driven initiative

**Question:** What patterns help a person and an agent move from an ambiguous idea to an informed next action, while keeping enough context for later work?

This is a qualitative comparison of documented practices, not a measurement of productivity or a ranking of products. We compared where each practice starts, how information reaches the agent, and how work can be verified or resumed.

| Reference | What it does and why | Neotool application |
| --- | --- | --- |
| [GOV.UK Service Manual: discovery](https://www.gov.uk/service-manual/agile-delivery/how-the-discovery-phase-works) | Investigates user needs, the current journey, and constraints before committing to a service. Findings support a decision about whether further work is worthwhile. | Make problem exploration an explicit option. Treat the solution in the opening brief as a candidate until supported. |
| [GitHub Spec Kit](https://github.github.com/spec-kit/) | Provides independent entry points for idea assessment and spec-driven implementation. Markdown outputs carry intent and evidence across steps. | Give orientation its own output and handoff. Introduce a delivery Spec when behavior is ready to define. |
| [OpenAI: Harness engineering](https://openai.com/index/harness-engineering/) | Uses a short repository map and deeper sources to help agents navigate. Executable checks and feedback help agents verify their work. | Link instructions to authoritative records. Connect implementation criteria to actual checks as an executable harness matures. |
| [Anthropic: Effective context engineering](https://www.anthropic.com/research/effective-context-engineering-for-ai-agents) and [Agent Skills specification](https://agentskills.io/specification) | Lightweight references support retrieval when needed. Skills expose discovery metadata first and load instructions and resources when relevant. | Keep skills short. Load the brief and relevant records first, then follow links for the chosen activity. |

## Patterns adopted in the current design

- **Selective context:** a short map points toward relevant records. Expand to details when a question crosses into them.
- **Linked knowledge:** stable IDs, states, and explicit relationships connect findings, decisions, intended behavior, and observations. Prefer an authoritative source plus a link over duplicated text.
- **Traceable evidence:** tie claims to source locations, distinguish observations from inferences, and recheck affected claims after changes.
- **Handoff by question:** each activity identifies the decision it informs, its expected result, and when to revisit the decision. A later agent starts from the latest record and changes since the last session.

These choices are design inferences from the references and established engineering practices. A useful pilot would try the same kickoff on a raw idea and an already researched initiative. Observe whether the agent chooses a defensible next move, asks for information it already has, or can resume from the brief without the original conversation.
