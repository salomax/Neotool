# Neotool Guideline

Use this guide together with an AI agent to start a software initiative. Bring the problem in your own words. The agent leads the working session: it loads relevant context, asks questions that affect the direction, drafts the Spec with you, and helps turn the agreed scope into a first delivery.

## Start the session

Open your initiative workspace in a coding agent. Give it access to this guide, the [Spec template](../spec/templates/first-delivery.md), and the [Start Initiative skill](../.agents/skills/neotool-start-initiative/SKILL.md). You can point the agent to those files directly in a local checkout. If the workspace already has a context map or project instructions, point the agent there too.

> Use the Neotool Start Initiative skill and work through the Guideline with me. Here is the problem or opportunity I see: [describe it in your own words]. Here are the materials I already have: [links or paths, if any]. Help me decide what a useful first delivery would look like.

A short description is enough to begin. For example: “People book our shared room by chat, and sometimes two people show up at the same time. Help me figure out what to build first.”

## Work together from problem to delivery

| Moment | Person | Agent | What you leave with |
| --- | --- | --- | --- |
| **Understand the problem** | Share what happens today, who is affected, and what a better outcome would look like. | Read relevant materials, play back what you understood, and distinguish facts from assumptions. Ask only for missing information that changes the scope, a decision, or how success can be observed. | Shared context: audience, problem, outcome, constraints, and open questions. |
| **Choose a first delivery** | Choose or adjust the proposed scope and trade-offs. | Propose the smallest useful change. Compare alternatives when the choice matters. If uncertainty prevents a decision, frame one question and the evidence needed to resolve it. | A recorded decision linking the chosen slice to the problem. |
| **Write and agree on the Spec** | Review realistic scenarios and say what would make the first delivery acceptable. | Draft a versioned Spec with expected behavior, exclusions, and criteria with concrete checks. Bring any material disagreement back to the conversation. | An agreed Spec the agent can work from. |
| **Build and examine the result** | Authorize implementation and review the result in the intended context. | Use the initiative's relevant skills and tools to make the agreed change. Capture what was checked against each criterion, which revision was examined, and what is still blocked or unverified. | Working software and evidence linked to the Spec, or an explicit handoff describing what remains. |
| **Carry learning forward** | Decide whether to accept the delivery, gather more evidence, or change direction. | Record verified behavior and link its sources. Update the context with new findings and surface differences between intended and observed behavior. | A reliable starting point for the next session. |

The session can pause at any moment. To resume, start from the current context, the latest Spec and evidence, and decisions made since the last session. Keep links to authoritative sources so a new agent can resume without reconstructing the whole conversation.

## What the agent works with

- **Context** explains the problem, decisions, constraints, and where authoritative information lives. Start with a short map of relevant sources and expand it as the initiative grows.
- **Spec** describes what the team intends to build and how it will check it. Once the implementation exists, record verified behavior separately with references to code, tests, contracts, or runtime observations. Keep unverified claims visible as such.
- **Skills** provide procedures for particular moments. The bundled Start Initiative skill runs the collaborative kickoff through a draft and an agreed Spec. Use design exploration when the experience is still open and technical design when a consequential implementation choice needs analysis.
- **Harness** provides the agent's execution environment: context loading, tool access, permissions, checkpoints, and capture of results. For the starting workflow, use your coding agent and its existing workspace tools; record decisions and handoffs in the workspace. A dedicated Neotool runner, CLI, and adapters can automate those same responsibilities as the framework evolves.

Keep context, decisions, the Spec, and evidence linked. A small initiative can keep them in one document. As the product grows, split them where ownership or discovery calls for it. The [worked example](examples/first-delivery.md) shows how the four pieces fit together.
