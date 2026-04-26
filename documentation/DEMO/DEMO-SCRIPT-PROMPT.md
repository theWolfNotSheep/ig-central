# Demo Script Authoring Prompt

**When the user asks to build a demo script for a feature, load this file first and follow it.**

This is a reminder-to-future-Claude on how IG Central demo scripts are structured, so every new script matches the house style established in `documentation/DEMO_SCRIPT.md`.

---

## Trigger phrases

Treat any of the following as a request to author a demo script using this template:
- "build a demo script for {feature}"
- "write the demo for {page/feature}"
- "script the {feature} video"
- "add demo video N for {feature}" / "next demo"
- "demo this" (when referring to a specific page or feature just discussed)

Before writing anything, open:
1. `documentation/DEMO/FEATURES-TO-DEMO.md` — confirm the feature is on the list; check if it's already marked ✅.
2. `documentation/DEMO_SCRIPT.md` — read the two or three videos closest in topic to match tone and pacing.
3. The actual page/controller in the codebase — **never invent UI elements**. Only narrate what is really on screen.

If the feature is marked ✅ already, ask the user whether they want a replacement, an extension, or a different framing before writing.

---

## Output format (must match the house style)

Each demo is a self-contained section with this exact shape:

```markdown
## Video N: {Title} ({M:SS})

**Goal:** {One sentence — what the viewer walks away knowing.}

### Script

1. **{Action in bold}**. {What they see}. *"{Verbatim narration in italics and quotes.}"*

2. **{Next action}**. {Observation}. *"{Narration.}"*

   - {Sub-bullet for UI elements to point at}
   - {Sub-bullet}

...

**Key message:** *"{One-line takeaway, quoted.}"*
```

Rules:
- **Target 2:30–3:00 per video.** Never longer than 3:00.
- **Numbered steps** for the presenter's actions, in order.
- **Bold for the clickable / visible action**; plain prose for what they observe; **italic + quotes** for words the presenter actually says.
- **Sub-bullets** for lists of UI fields to call out within one step.
- **End with a "Key message"** in italic quotes — the one sentence that should stick.
- Always reference the **real page path** (e.g. `/governance/hub`, `/admin/users`) so recording is unambiguous.
- Name **real button labels and field names** — verify them in `web/app/**` before writing.

---

## Pre-flight checklist before writing

For each new demo script, confirm the following and mention any that aren't yet true so the user can seed state:

- [ ] The page exists and the route is correct.
- [ ] Required demo data is seeded (e.g. some documents classified, a pack installed, a user with low-clearance).
- [ ] The narrated actions are actually possible in the current UI.
- [ ] Any referenced thresholds / numbers (e.g. "0.7", "30 days", "5 services") match production config.
- [ ] The video fits in ≤3 minutes at a conversational pace — cut if overstuffed.

If any check fails, flag it in the response before the script rather than writing fiction.

---

## Tone and voice

Match `DEMO_SCRIPT.md` exactly:
- **Second person, present tense.** "You can see…", "Click here…".
- **Confident, not salesy.** No marketing adjectives ("amazing", "revolutionary"). Describe what it does, let the feature sell itself.
- **Explain the why, not just the what.** Every step should connect to a business outcome (compliance, cost, speed, risk).
- **No jargon without glossing it.** "MCP tools (the way the AI asks our system for relevant context)" — once, then reuse.
- **UK English spelling** throughout (colour, behaviour, organisation, programme).

---

## Default narrative beats per video

Most good IG Central demos hit these beats. Use them as a mental checklist, not a rigid outline:

1. **Where are we and why.** One sentence framing the persona and the task.
2. **What they see on landing.** Orient the viewer before any clicks.
3. **The primary action.** The one thing this feature is for.
4. **One non-obvious capability.** The thing that differentiates IG Central.
5. **The feedback / audit / ops consequence.** Where does this action show up elsewhere in the system?
6. **Key message.** Tie it back to the business outcome.

---

## After writing

When you finish a script:

1. Append it to `documentation/DEMO_SCRIPT.md` (or create a new file under `documentation/DEMO/` if the user prefers separate files).
2. Update `documentation/DEMO/FEATURES-TO-DEMO.md`:
   - Flip the relevant row from 🆕 to ✅.
   - Add the video number in the Status column (e.g. `✅ Video 13`).
3. Update the Video Index table at the bottom of `DEMO_SCRIPT.md`.
4. Keep the running **Total runtime** figure at the top of `DEMO_SCRIPT.md` correct.
5. Tell the user **what was added and what state the system needs** for clean recording.

---

## Anti-patterns to avoid

- ❌ Inventing UI that doesn't exist ("click the Magic Wand icon"). Check the code.
- ❌ Multi-paragraph narration — viewers will skip. One or two sentences per step.
- ❌ Scripts longer than 3 minutes — split into two videos instead.
- ❌ Abstract feature descriptions with no on-screen action.
- ❌ Duplicating a key message across videos — each should earn its own.
- ❌ Forgetting the "Key message" line at the end.
- ❌ Using emojis in the script output (house style avoids them; the checklist icons in FEATURES-TO-DEMO.md are the only exception).
