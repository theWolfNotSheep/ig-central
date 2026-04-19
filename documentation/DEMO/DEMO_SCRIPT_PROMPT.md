# Demo Script Authoring — Standing Instructions

**Load this document whenever the user asks you to "build a demo script", "write a demo for feature X", "add a new demo video", or anything equivalent.**

The user has curated the master feature inventory in `documentation/DEMO/FEATURE_LIST.md`. The existing 12-video master script is `documentation/DEMO_SCRIPT.md`. New standalone scripts live in `documentation/DEMO/scripts/`.

## Before writing anything

1. **Open `documentation/DEMO/FEATURE_LIST.md`.** Find the feature the user is asking about. If it isn't listed, ask whether to add it before writing a script.
2. **Check its status:**
   - ✅ **Covered** — the feature is already in `DEMO_SCRIPT.md`. Ask the user if they want a deeper standalone script or if the existing video is sufficient before duplicating work.
   - 🟡 **Partial** — a standalone deep-dive is appropriate. Reference the parent video and focus on the depth the master script skips.
   - ⬜ **Not covered** — write a new standalone script from scratch.
   - 🔒 **Planned / not shipped** — stop and confirm with the user. Do not script features that do not exist in the code yet.
3. **Read the relevant code before writing.** Check the actual frontend route, backend controller, and any recent commits. Do NOT invent UI elements, button labels, or flows. The script must match the real product.
4. **Read `documentation/DEMO_SCRIPT.md`** to match tone, pacing, and format — this is the reference style.

## Writing the script

Follow the structure used in `DEMO_SCRIPT.md`:

```markdown
# <Feature Name> — Demo Script

**Duration: X:XX**

**Goal:** <one sentence: what the viewer should walk away understanding>

## Script

1. **<Action>** — <description>. *"<spoken narration in italics>"*
2. **<Action>** — <description>. *"<spoken narration>"*
   - Sub-steps where a screen has multiple points to call out
...

**Key message:** *"<the one-line pitch for this feature>"*
```

### Rules

- **Duration target: 2:30–3:00.** Only go longer if the feature genuinely needs it and flag the excess to the user.
- **Every step should be executable in the real UI.** If you are unsure whether a button or page exists, grep the frontend code before writing.
- **Spoken narration in italics.** Keep it conversational, second person ("you can…"), and explain the *why* not just the *what*.
- **Include the unhappy path where relevant** — CLAUDE.md requires that both happy and failure paths are visible. For any workflow script, show at least one failure-and-recovery moment (e.g. a failed classification being retried).
- **Configuration-driven reminders.** CLAUDE.md is clear that labels, menus, and statuses come from MongoDB config. Don't script UI text as fixed — say "<label shown in your config>" when the exact wording is tenant-dependent.
- **Reference slugs not IDs.** The system is slug-based; narration and example URLs should use slugs (`/documents?doc=maternity-leave-confirmation-a3f2b1`) not raw ObjectIds.
- **Use admin creds from CLAUDE.md** for any login step: `admin@governanceledstore.co.uk` / `ChangeMe123!`.

### File & naming

- Path: `documentation/DEMO/scripts/<feature-slug>.md`
  - Slug example: `block-versioning.md`, `pack-update-observer.md`, `bert-training.md`
- If the script is a deep-dive companion to a master-script video, name it so the relationship is clear: `video-08-deepdive-block-feedback.md`.

## After writing

1. **Update `documentation/DEMO/FEATURE_LIST.md`** — flip the status from ⬜ or 🟡 to ✅ (or keep 🟡 if it is still only partial) and link to the new script file.
2. **Do not touch `DEMO_SCRIPT.md`** unless the user explicitly asks. That is the master 12-video script and should stay the canonical overview.
3. **Report back** with: feature covered, script path, duration, and any code details you verified (or could not verify) along the way.

## When the user asks to build scripts "for each feature"

If the user asks for multiple scripts in one go, work through `FEATURE_LIST.md` in order, prioritising ⬜ (not covered) over 🟡 (partial). Confirm scope first — 20+ scripts is a lot — and ask whether they want them all in one session or batched.
