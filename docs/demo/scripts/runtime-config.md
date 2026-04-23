# Runtime Configuration (Config-Driven Design) — Demo Script

**Duration: 2:00**

**Goal:** Show admins that virtually everything in the UI — menus, labels, statuses, feature flags — is editable at runtime without redeployment.

## Script

1. **Open with the frustration customers know too well.** *"Raise a ticket to change a menu label. Wait four weeks for the next release. Pay consulting day rates for a two-minute change. That's not us. In IG Central, almost every piece of user-facing text, every menu item, every status name is configuration — editable live."*

2. **Navigate to Admin → Config.**

3. **Walk the configuration sections.**
   - **Navigation.** *"Sidebar items — order, labels, icons, visibility. Want to rename 'Review Queue' to 'Quality Control'? Edit here, save, refresh. Done."*
   - **Labels.** *"UI labels across the app — page titles, button text, empty states."*
   - **Status names.** *"Document status labels — keep the codes the pipeline uses but display 'Awaiting Review' as 'Pending Approval' if that's your terminology."*
   - **Feature flags.** *"Toggle features on or off — 'Show Google Drive integration', 'Enable PII auto-redaction' — instant effect, zero downtime."*
   - **Dropdowns and enums.** *"The sensitivity level picker, the PII type picker, the jurisdiction picker — all editable lists."*

4. **Make a change live.** Edit a menu label from 'Pipelines' to 'AI Workflows'. Save. Refresh the sidebar. *"Live. No restart, no deploy."*

5. **Show versioning.** *"Every config change is versioned and attributed — who changed what, when, from what to what. You can rollback. Your auditor can trace every UI change."*

6. **Show environment variants.** *"Different configs per environment — your production and test tenants can have different menu items, different feature flags. Promote a config change from test to production when you're ready."*

7. **Tie to the business case.** *"One mid-sized customer saved thirty-plus change requests to their development team in the first quarter just by using runtime config. Your change-management bill goes to zero for presentation tweaks."*

**Key message:** *"Stop raising tickets for button labels. Your admins own the presentation layer — and the developers stay focused on actual logic."*
