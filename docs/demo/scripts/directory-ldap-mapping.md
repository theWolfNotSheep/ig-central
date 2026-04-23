# Directory / LDAP Mapping — Demo Script

**Duration: 2:30**

**Goal:** Show how enterprise customers sync users, groups, and org hierarchy from Active Directory or Azure AD — so IG Central inherits the identity model they already manage.

## Script

1. **Open with the enterprise concern.** *"For any organisation above two hundred staff, identity lives in Active Directory or Azure AD. You don't want a second user database to maintain — and your IT team definitely doesn't want to onboard users twice. Let me show you the sync."*

2. **Navigate to Admin → Directory Mapping.**

3. **Configure the connection.** *"Pick your provider — Azure AD, Active Directory via LDAP, Okta, Google Workspace. Enter the tenant details, upload the certificate or provide the service principal. Test the connection — green tick, we see N users and M groups."*

4. **Walk the attribute mapping.** *"Map AD attributes to IG Central fields — `displayName` to full name, `mail` to email, `department` to department, `title` to job title. Custom attributes too — your security clearance attribute maps straight to our clearance field."*

5. **Walk the group mapping.** *"Map AD groups to IG Central roles and category grants. 'HR Team' in AD becomes 'HR Reviewers' in IG Central, automatically granting category access to HR."*

6. **Show the sync settings.** *"Sync on a schedule — hourly, daily — or on-demand. Handle leavers automatically — when a user is deactivated in AD, they're deactivated in IG Central within the sync window."*

7. **Show inheritance.** *"Your org hierarchy — departments, teams, reporting lines — flows in from AD. Useful for approvals workflows, SAR routing, and reporting: 'show me all documents owned by staff reporting into this director'."*

8. **Show the dry-run preview.** *"Every sync run can preview the changes before applying — users to be added, updated, deactivated. Your IT team sees the impact before hitting Apply."*

9. **Close with the audit angle.** *"Every sync run is logged — what changed, when, by what source. If HR asks 'why does this person have access', the trail points straight back to AD membership."*

**Key message:** *"Your identity platform stays the source of truth. We stay in sync, automatically, with full audit — no parallel user database, no onboarding duplication."*
