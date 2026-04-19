# Google OAuth Login — Demo Script

**Duration: 1:30**

**Goal:** Show the Google SSO login flow — how users authenticate with their existing Google Workspace account.

## Script

1. **Open with the SSO expectation.** *"For Google Workspace organisations — and that's most of our customer base — forcing staff to remember another password is unacceptable. We support Google SSO out of the box."*

2. **Navigate to the login page** at `http://localhost/login`.

3. **Point out the SSO button.** *"Alongside username/password, there's a 'Sign in with Google' button. If your organisation uses Google Workspace, that's the path."*

4. **Click 'Sign in with Google'.** The Google OAuth consent screen appears.

5. **Walk the consent.** *"First-time users see the consent screen — what IG Central is requesting from their Google account (basic profile, email). Your Workspace admin can pre-approve this at the domain level so staff never see it."*

6. **Complete sign-in.** *"Approve, and you're in. The platform creates or matches the user record by email, assigns the role based on your directory mapping rules, and logs the event to the audit trail."*

7. **Explain first-login provisioning.** *"If the email matches an existing invited user, they activate with Google credentials. If it's new and your domain allow-list is enabled, they're provisioned as a standard user with default role — you configure the policy."*

8. **Tie to security.** *"MFA, session management, device policies — all inherited from Google Workspace. You rotate a password in Google, sessions drop everywhere. Your IT team loves that answer."*

**Key message:** *"Login is a non-event. Your Workspace credentials get you in, your IT policies still apply, and provisioning is automatic."*
