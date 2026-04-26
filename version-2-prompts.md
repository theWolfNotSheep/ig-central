---
title: Prompts for Claude — IG-Central V2
lifecycle: forward
---

# Prompts for Claude — IG-Central V2

A copy-pasteable library of prompts for working with Claude on the v2 implementation. Pick the smallest one that fits the task.

## Why this file exists

The four v2 documents (`version-2-architecture.md`, `version-2-decision-tree.csv`, `version-2-implementation-plan.md`, `version-2-implementation-log.md`) plus `CLAUDE.md` are designed so Claude can pick up work from a fresh context. These prompts are the entry points.

**Good prompts share three properties:**

1. **They name the document(s) Claude should read first.** Without this, Claude hunts.
2. **They state the work item or decision explicitly.** Phase number, CSV row, sub-phase ID — not "what we were doing."
3. **They state the deliverable shape.** "Walk me through, then update the CSV" or "report under 200 words" or "don't act yet — just plan."

Avoid: *"Continue."* / *"Carry on."* / *"You know what to do."* These force Claude to guess.

---

## Cold-start prompts (after context reset)

### Minimum viable session-start

```
Read CLAUDE.md, version-2-implementation-plan.md, and the bottom of
version-2-implementation-log.md. Then tell me where we are.
```

### Start a specific sub-phase

```
Read CLAUDE.md and version-2-implementation-plan.md. We're at Phase X.Y.
Walk me through what gets built, identify open decisions that block it,
then proceed if clear.
```

### Continue mid-phase work across sessions

```
Read CLAUDE.md and version-2-implementation-plan.md, then tail the last
five entries of version-2-implementation-log.md. Continue from where the
log left off.
```

### Get unstuck

```
Read the four v2 docs. I think we're stuck on <topic>. Tell me what's
already decided about it, what's still open, and what the smallest next
move would be.
```

---

## Decision work

### Close out a single RECOMMENDED decision

```
Decision <ID> in version-2-decision-tree.csv is RECOMMENDED. Walk me
through it. If I confirm, flip it to DECIDED in the CSV.
```

### Close out a batch (e.g. phase-0 gate)

```
Phase 0.1 requires A1, A2, A5, A6 (CSV rows 13, 14, 17, 18) to be
DECIDED. Walk me through each in order. After each one I'll confirm or
push back; update the CSV as we go.
```

### Log a brand-new decision

```
We just decided <X>. Log it in version-2-decision-tree.csv with the
right category and section reference. Append a log entry to
version-2-implementation-log.md noting which sub-phase prompted it.
```

### Review what's open

```
List all OPEN and RECOMMENDED decisions in version-2-decision-tree.csv.
Group by category. Flag any that block an unstarted phase from starting.
Under 300 words.
```

### Reverse a decision

```
Decision <ID> needs reversing because <reason>. Append a new row marking
the old one SUPERSEDED with a pointer to the new ID. Update any doc
sections that reference the old decision. Don't act yet — show me the
plan first.
```

---

## Phase work

### Run a sub-phase end-to-end

```
Execute sub-phase X.Y from version-2-implementation-plan.md. Contract
first. Update the decision log if any new decisions arise. Append a log
entry when done. Stop and ask if a blocking open decision surfaces.
```

### Verify a phase acceptance gate

```
For Phase <N>, walk through each acceptance gate condition. Report which
pass, which fail, which haven't been measured. Don't change anything —
this is a status check.
```

### Close a sub-phase

```
Sub-phase X.Y is complete. Append a closing entry to
version-2-implementation-log.md. Update the per-phase status board if
applicable.
```

### Move to the next phase

```
Phase <N> acceptance gate is met. Update the status board in
version-2-implementation-log.md. List the first three sub-phases of
Phase <N+1> and the open decisions that need closing before any of them
can start.
```

---

## Architecture and planning

### Add an architectural piece (still planning)

```
Add <topic> to version-2-architecture.md in the appropriate section. If
the system map needs redrawing because of this, redraw it. Don't add a
CSV decision yet — we're still planning.
```

### Redraw a diagram

```
The <diagram name> in §<N> is out of date because <reason>. Redraw it
to reflect <change>. Update any cross-references in §<other section>.
```

### Capture a design conversation in the doc

```
The conversation we just had about <topic> needs to land in
version-2-architecture.md, in §<section> (or pick the right one if I'm
wrong). Don't decide anything yet — just narrative.
```

### Add a new rule to CLAUDE.md

```
Add a rule to CLAUDE.md: <rule>. Place it under the relevant existing
section — only introduce a new section if the rule genuinely doesn't fit
anywhere.
```

---

## Research

### Look at existing code before deciding

```
Before we decide <topic>, do read-only research on the existing code at
/Users/woollers/dev/ig-central. Specifically find: <list>. Report under
500 words. Don't propose decisions — just facts and locations.
```

### Spawn a research agent

```
Spawn an Explore agent to research <topic> at thoroughness <quick |
medium | very thorough>. Don't research yourself — use the agent and
synthesise its findings.
```

### Cross-check a claim

```
Earlier we asserted <X>. Verify against the current code before we act
on it.
```

---

## Implementation actions

### Draft a contract

```
Draft contracts/<service>/openapi.yaml v0.1.0 against the §11
conventions. Reference _shared/ schemas. Don't generate stubs yet —
let me review the spec first.
```

### Implement against an existing contract

```
Implement <service> against contracts/<service>/openapi.yaml. Generated
stub first; then fill in. Audit outbox writes for <events>. Health
probes per the §3 standard. Stop and ask if the contract has gaps.
```

### Open a PR for a sub-phase

```
Sub-phase X.Y is ready to ship. Stage the changes, draft a commit
message naming the sub-phase, append the log entry, and prepare the PR
description against the CSV decisions referenced.
```

### Run the contract-diff check locally

```
Run the contract-diff check on contracts/. Report any specs that
changed without a VERSION bump. Don't fix anything — just report.
```

---

## Status checks

### Fast read of project state

```
What's the state of v2? List phases in progress, decisions still open,
last three log entries. Under 200 words.
```

### Doc consistency audit

```
Audit the four v2 documents for inconsistencies: cross-references that
don't match, decisions referenced by section but not in the CSV,
sub-phases mentioned in the plan but not in the log, queue/exchange
names that drift between architecture and asyncapi. Report only real
issues, not stylistic preferences.
```

### Verify a CSV claim

```
CSV row <N> claims <X>. Verify that against the doc section it
references and against the current code if applicable. Report mismatches.
```

---

## Recovery / reversal

### Stop me

```
Stop. Don't proceed. Tell me what the last action was, what you were
about to do, and why I should be concerned.
```

### Roll back a code change

```
The change in <file> from <commit/PR> needs reverting because <reason>.
Show me the diff that would reverse it before you apply anything.
```

### Roll back a decision

```
Decision <N> was wrong. Walk me through reversing it: which CSV row to
mark SUPERSEDED, which doc sections need updating, which log entries to
append. Don't act yet — just plan.
```

---

## Mode-switches (use sparingly)

These shape *how* Claude responds rather than *what* it does.

```
Be terse. One sentence per update.
```

```
Stay on point. I'm asking a single question, not for a full essay.
```

```
Don't be swayed by my framing alone — push back if you disagree.
```

```
This is planning, not implementation. Don't write code or update
contracts.
```

```
This is implementation. No more conversation about whether — execute the
plan, log the work.
```

```
Read-only. Don't change any files; just report.
```

---

## Anti-patterns (avoid)

| Bad | Why it's bad | Use instead |
|---|---|---|
| *"Continue."* | Forces Claude to guess what's next | Name the sub-phase or work item |
| *"Carry on from yesterday."* | Yesterday isn't in context after a reset | "Tail the log; continue from there" |
| *"Build the whole thing."* | No scope = sprawl | Specify Phase X or sub-phase X.Y |
| *"Don't worry about the docs."* | Drift starts immediately | The docs are how future sessions work |
| *"You know what to do."* | Setup for a guess | State the deliverable shape |
| *"Make it nice."* | Unmeasurable | Name a concrete acceptance criterion |

---

## Worked example — first session of Phase 0

**Goal of session:** close out Phase 0.1 decisions and create the contracts skeleton.

**Open with:**

```
Read CLAUDE.md, version-2-implementation-plan.md, and the bottom of
version-2-implementation-log.md. Then tell me where we are.
```

**Then close decisions:**

```
Phase 0.1 requires A1, A2, A5, A6 (CSV rows 13, 14, 17, 18) to be
DECIDED. Walk me through each in order. After each one I'll confirm or
push back; update the CSV as we go.
```

**Once decisions are locked, run sub-phase 0.3:**

```
Execute sub-phase 0.3 from version-2-implementation-plan.md (contracts/
skeleton). Contract first. Append a log entry when done. Stop and ask
if a blocking open decision surfaces.
```

**Close the session:**

```
Phase 0.3 is complete. Append a closing log entry. List the next two
sub-phases that can start now and any decisions that gate them.
```

That sequence covers a productive 1–2 hour session without ambiguity at
any point.
