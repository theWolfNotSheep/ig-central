# Clearance Levels & 3-Layer Access Control — Demo Script

**Duration: 2:30**

**Goal:** Show the multi-layer access model — role, clearance, and category grant — that enforces who can see what, even for classified documents.

## Script

1. **Open with the model.** *"Role-based access alone isn't enough for real governance. A manager can have a 'reviewer' role, but only the right to see RESTRICTED documents for certain departments. We use three layers — role, clearance, and category grant — combined at runtime."*

2. **Explain the three layers.** On-screen or in narration:
   - **Role** — what actions the user can perform (view, edit, approve, admin).
   - **Clearance** — the maximum sensitivity level the user can see (PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED).
   - **Category grants** — which parts of the taxonomy the user is permitted to access.

3. **Open a user in Admin → Users.**

4. **Show clearance.** *"This reviewer has clearance up to CONFIDENTIAL — they cannot open RESTRICTED documents even if they're in the right category. Their view is automatically filtered."*

5. **Show category grants.** *"They have grants for HR and Legal — Finance is not in their scope. Switching them to HR-only is one click."*

6. **Demonstrate the effect.** Log in as that user (or show a second browser). *"From their account, RESTRICTED documents are invisible in search, absent from the documents list, and blocked on direct URL access. Not 'hidden' — cryptographically not returned from the API."*

7. **Navigate to Admin → Access Matrix.** *"Here's the full picture — every user across every category, colour coded. Purple for admin, green for granted, amber when clearance blocks, grey for no access. Your DPO can audit access visually in seconds."*

8. **Tie it to audit.** *"Every access decision — grant or deny — is logged. Your security team can answer 'who viewed this document' and 'has anyone been denied access to something they expected to see' in one query."*

**Key message:** *"Access isn't just about role — it's role, clearance, and scope, combined. Defensible to your regulator and scalable to your organisation."*
