# Payments End-to-End Implementation Guide

A staged plan for taking IG Central from "signup creates an empty user" to "self-serve subscription with Stripe billing". Each phase has a self-contained prompt designed to be pasted into a fresh Claude Code conversation — no prior context required.

---

## Current State (as of writing)

**What exists:**
- `SignupController` at `/api/auth/public/signup` — creates a user with an encoded password, no email verification, account immediately enabled
- `OAuth2LoginSuccessHandler` — auto-provisions Google users
- `Product`, `Subscription`, `Role`, `Feature` models in `gls-platform/src/main/java/co/uk/wolfnotsheep/platform/products/`
- `Product.monthlyPriceInPence` and `annualPriceInPence` fields (unused)
- `Subscription.status` supports `"ACTIVE"`, `"TRIAL"` (no `endDate` enforcement)
- `RolePermissionSyncService.getNewUserDefaults()` — returns roles where `defaultForNewUsers = true`
- `SubscriptionPermissionSyncService.syncPermissionsForUser(userId)` — applies subscription product permissions to the user
- Admin UI for users, roles, features at `/admin/users`

**What's broken or missing:**
- Public signup and OAuth signup **don't apply default roles** — new users get empty `roles[]` and `permissions[]`
- No subscription auto-created on signup (trial or otherwise)
- No admin UI for products or subscriptions
- No payment integration (Stripe etc.)
- No email service / verification / welcome emails
- No subscription expiry / trial-ending logic
- `MeResponse` doesn't expose subscription state to the frontend

---

## Target Architecture

**Payment processor:** Stripe (industry standard, good developer experience, handles compliance).
**Email provider:** Postmark, Resend, or AWS SES (any one — pick by cost/preference).
**Subscription model:**
- Each user has 0 or 1 active subscription to a Product
- Trial = `Subscription.status = TRIAL` with `endDate` in future
- Active paid = `status = ACTIVE`, Stripe webhook keeps in sync
- Expired = `status = EXPIRED`, permissions revoked, banner shown

**Permission resolution chain (existing, keep using):**
```
Role.featureIds → Feature.permissionKey → User.permissions (snapshot)
Product.roleIds + featureIds → User.permissions (via SubscriptionPermissionSyncService)
```

**Resync strategy:** Re-run `SubscriptionPermissionSyncService.syncPermissionsForUser()` on:
- User signup
- Subscription created/updated/cancelled
- Stripe webhook events
- Login (cheap insurance against drift)

---

## Phase 1 — Foundation: Fix the Signup Hole

**Goal:** Every signup produces a user with sensible default roles and permissions, so they can use the app immediately. No payments yet.

**Why first:** The product is unusable today for self-serve signups. This fix takes 1-2 hours and unblocks internal trials.

**Changes:**
- `SignupController.signup()` — call `RolePermissionSyncService.getNewUserDefaults()` after saving user
- `OAuth2LoginSuccessHandler` — same
- Ensure seed data has at least one Role with `defaultForNewUsers = true` (e.g. `STANDARD_USER`)
- Add a dashboard banner if `user.permissions.size() == 0` so you notice if signup ever drops permissions

**Files to touch:**
- `backend/gls-platform/src/main/java/co/uk/wolfnotsheep/platform/identity/controllers/SignupController.java`
- `backend/gls-platform/src/main/java/co/uk/wolfnotsheep/platform/identity/configs/OAuth2LoginSuccessHandler.java`
- `backend/gls-platform/src/main/java/co/uk/wolfnotsheep/platform/products/services/RolePermissionSyncService.java` (verify `syncForUser()` exists)
- `backend/gls-app-assembly/src/main/java/co/uk/wolfnotsheep/infrastructure/bootstrap/PermissionDataSeeder.java` (add STANDARD_USER role with defaults)

### 🤖 Prompt — Phase 1

```
I need to fix the signup flow in this Spring Boot + Next.js app. Currently new users
sign up successfully but get zero roles and zero permissions, so they hit 403 on
everything they try.

Tasks:

1. Read backend/gls-platform/src/main/java/co/uk/wolfnotsheep/platform/identity/controllers/SignupController.java
   and backend/gls-platform/src/main/java/co/uk/wolfnotsheep/platform/identity/configs/OAuth2LoginSuccessHandler.java
   and identify where the user is saved.

2. Read backend/gls-platform/src/main/java/co/uk/wolfnotsheep/platform/products/services/RolePermissionSyncService.java
   to find the existing method that applies default roles. There's `getNewUserDefaults()`
   that returns the roles+permissions to apply, and likely a `syncForUser(userId)` method
   that does it directly. Use whichever fits cleanly.

3. After the user is saved in BOTH SignupController and OAuth2LoginSuccessHandler, call
   the sync service to apply default roles. Reload the user from the repo before
   responding so the JWT/session reflects the new permissions.

4. Read backend/gls-app-assembly/src/main/java/co/uk/wolfnotsheep/infrastructure/bootstrap/PermissionDataSeeder.java
   and check whether a STANDARD_USER role exists with `defaultForNewUsers = true` and
   sensible feature keys (DOCUMENT_READ, DOCUMENT_CREATE, DOCUMENT_DOWNLOAD, SEARCH_USE,
   TAXONOMY_READ at minimum). If not, add it. Run idempotently — only create if missing.

5. In web/src/app/(protected)/dashboard/page.tsx, add a small banner that shows when
   the current user (from /api/user/me) has zero permissions. Text: "Your account has
   no permissions assigned. Contact an administrator." This is a safety net that
   surfaces the problem if the sync ever drops a user.

Verify:
- Compile: `cd backend && ./mvnw compile -DskipTests -pl gls-app-assembly -am`
- Frontend: `cd web && npx tsc --noEmit`
- Wipe and re-seed the platform mongo, sign up a new user via /api/auth/public/signup,
  check the user document has roles + permissions populated
- Sign in as the new user, /api/user/me returns non-empty roles/permissions
```

---

## Phase 2 — Trial Subscriptions

**Goal:** Every new signup gets a 14-day trial of the default Product. When the trial expires the subscription becomes EXPIRED and a banner prompts the user to upgrade.

**Why second:** Validates the subscription model end-to-end without payment processor complexity.

**Changes:**
- Add `trialDays` (Integer, default 14) and `defaultForNewSignups` (boolean) to `Product`
- Seed a "Starter" / "Trial" Product with one role bundle and `defaultForNewSignups = true`
- On signup (after Phase 1's role sync), find the default product and create a `Subscription` row with `status = TRIAL`, `startDate = now`, `endDate = now + trialDays days`
- Call `SubscriptionPermissionSyncService.syncPermissionsForUser()` to merge product permissions on top of role permissions
- Add a scheduled job (`@Scheduled` daily) that flips `TRIAL` subs past `endDate` to `EXPIRED` and re-syncs permissions
- Extend `MeController.MeResponse` with `activeSubscription` (productName, status, endDate, daysRemaining)
- Add a dashboard banner showing trial countdown / expired state

**Files to touch:**
- `backend/gls-platform/src/main/java/co/uk/wolfnotsheep/platform/products/models/Product.java`
- `backend/gls-platform/src/main/java/co/uk/wolfnotsheep/platform/products/models/Subscription.java` (verify `endDate` field exists; add if not)
- `backend/gls-platform/src/main/java/co/uk/wolfnotsheep/platform/identity/controllers/SignupController.java`
- `backend/gls-platform/src/main/java/co/uk/wolfnotsheep/platform/identity/configs/OAuth2LoginSuccessHandler.java`
- `backend/gls-app-assembly/src/main/java/co/uk/wolfnotsheep/infrastructure/bootstrap/PermissionDataSeeder.java` (seed Starter product)
- New: `backend/gls-app-assembly/src/main/java/co/uk/wolfnotsheep/infrastructure/services/SubscriptionExpiryJob.java`
- `backend/gls-platform/src/main/java/co/uk/wolfnotsheep/platform/identity/controllers/MeController.java`
- `web/src/app/(protected)/dashboard/page.tsx`

### 🤖 Prompt — Phase 2

```
This Spring Boot + Next.js app has Product and Subscription models in
backend/gls-platform/src/main/java/co/uk/wolfnotsheep/platform/products/. Phase 1 of a
broader payments work has already been completed (signup applies default roles via
RolePermissionSyncService). Now I need to add trial subscriptions.

Tasks:

1. Read Product.java and add two new fields:
   - Integer trialDays (default 14)
   - boolean defaultForNewSignups (default false)
   Add getters/setters.

2. Read Subscription.java. Verify it has `endDate` (Instant) and `status` (String).
   The status field should support: TRIAL, ACTIVE, EXPIRED, CANCELLED.

3. Read SubscriptionPermissionSyncService.java in the same package — note its
   `syncPermissionsForUser(userId)` method which we'll call from new signups.

4. In PermissionDataSeeder.java (or wherever roles are seeded), add a "Starter" Product
   with defaultForNewSignups=true, trialDays=14, linked to a STARTER_BUNDLE Role that
   includes more generous permissions than the default STANDARD_USER (e.g. add
   DOCUMENT_DOWNLOAD if not already in default).

5. Create a SubscriptionService.createTrialForUser(userId) method that:
   - Finds the Product where defaultForNewSignups=true
   - Creates a Subscription with status=TRIAL, startDate=now, endDate=now+trialDays
   - Saves it
   - Calls SubscriptionPermissionSyncService.syncPermissionsForUser(userId) to merge
     product permissions onto the user

6. Call this from SignupController and OAuth2LoginSuccessHandler after the role-sync
   from Phase 1 (chain: save user → sync default roles → create trial → sync subscription
   permissions). Don't fail signup if trial creation fails — log and continue.

7. Create SubscriptionExpiryJob with @Scheduled(cron = "0 0 * * * *") that runs hourly:
   - Find all subscriptions where status=TRIAL and endDate < now
   - Set status=EXPIRED, save
   - Call SubscriptionPermissionSyncService.syncPermissionsForUser() for each affected
     user to revoke product permissions
   - Log how many were expired

8. Extend MeController.MeResponse to add `activeSubscription` with these fields:
   - productId, productName
   - status (TRIAL/ACTIVE/EXPIRED/CANCELLED)
   - startDate, endDate (ISO strings)
   - daysRemaining (computed; null if no active sub)
   Resolve via SubscriptionRepository.findByUserIdAndStatusIn([TRIAL, ACTIVE]) ordered
   by endDate desc. Return null if none.

9. In web/src/app/(protected)/dashboard/page.tsx, read the activeSubscription from
   /api/user/me and show:
   - If status=TRIAL with daysRemaining < 7: amber banner "Trial ends in N days. Upgrade
     to keep access."
   - If status=EXPIRED: red banner "Your trial has ended. Upgrade to restore access."
   - Otherwise: nothing (or a small indicator in the sidebar)

Verify:
- Compile both backend and frontend
- Sign up a new user → verify a TRIAL Subscription row exists in Mongo
  (collection: subscriptions, fields: userId, productId, status=TRIAL, endDate ~14 days
  ahead)
- /api/user/me returns activeSubscription with daysRemaining ≈ 14
- Manually set a Subscription's endDate to yesterday in Mongo and trigger the job (or
  wait an hour) → status flips to EXPIRED, user's permissions reflect only the role
  defaults, dashboard shows the red banner
```

---

## Phase 3 — Subscriptions Admin UI

**Goal:** Admins can create/edit Products and assign Subscriptions to users via the admin UI. No more direct Mongo edits for ops.

**Why third:** Lets ops teams manually onboard paying customers before Stripe is wired up. The admin UI also makes Phase 4 testing easier.

**Changes:**
- `/api/admin/products` — full CRUD for Products (name, description, prices, trialDays, defaultForNewSignups, roleIds[], featureIds[])
- `/api/admin/subscriptions` — list, create, cancel; filter by user
- Frontend pages: `/admin/products`, `/admin/subscriptions`
- On user profile page (`/admin/users/[id]`), add a **Subscriptions** card showing user's subs + assign/cancel buttons

**Files to create:**
- `backend/gls-app-assembly/.../controllers/admin/ProductAdminController.java`
- `backend/gls-app-assembly/.../controllers/admin/SubscriptionAdminController.java`
- `web/src/app/(protected)/admin/products/page.tsx`
- `web/src/app/(protected)/admin/subscriptions/page.tsx`

**Files to modify:**
- `web/src/app/(protected)/admin/users/[id]/page.tsx` — add Subscriptions card

### 🤖 Prompt — Phase 3

```
This Spring Boot + Next.js app has Product, Subscription, Role, Feature models in
backend/gls-platform/.../products/. Phases 1-2 are done (default roles on signup,
auto-trial subscription).

I need full admin UI for Products and Subscriptions so ops can manage them without
touching Mongo directly.

Tasks:

1. Backend — ProductAdminController at /api/admin/products:
   - GET (list all)
   - GET /{id}
   - POST (create) — body: name, description, monthlyPriceInPence, annualPriceInPence,
     trialDays, defaultForNewSignups, roleIds, featureIds, status
   - PUT /{id} (update)
   - DELETE /{id} — block if any active subscriptions reference it
   Place in backend/gls-app-assembly/src/main/java/co/uk/wolfnotsheep/infrastructure/controllers/admin/

2. Backend — SubscriptionAdminController at /api/admin/subscriptions:
   - GET ?userId=&productId=&status= (list with filters)
   - POST (create) — body: userId, productId, status (default ACTIVE),
     billingInterval (MONTHLY/ANNUAL), startDate, endDate, optionally trialDays
     to compute endDate
   - PUT /{id}/cancel — set status=CANCELLED, endDate=now, call
     SubscriptionPermissionSyncService.syncPermissionsForUser
   - DELETE /{id} — hard delete (admin override only)
   - GET /{userId}/active — convenience endpoint for the user profile card
   Wire up SubscriptionPermissionSyncService on every state change.

3. Frontend — /admin/products page:
   Mirror the pattern from web/src/app/(protected)/admin/users/page.tsx (list + create
   modal + edit). Show columns: Name, Pricing (monthly/annual), Trial Days,
   Default For Signups, Status. Form fields: name, description, prices in pounds
   (convert to pence on save), trialDays, defaultForNewSignups checkbox, role picker
   (multi-select from /admin/users/roles), feature picker (multi-select from
   /admin/users/features), status dropdown.

4. Frontend — /admin/subscriptions page:
   Table with: User (email + link to /admin/users/{id}), Product, Status, Billing
   Interval, Start Date, End Date, Days Remaining, Actions (Cancel button).
   Filters: user search, product filter, status filter.
   "Create Subscription" button opens modal: user picker (search), product picker,
   billing interval, optionally trial days to set endDate.

5. Frontend — extend web/src/app/(protected)/admin/users/[id]/page.tsx:
   Add a new card in the right column titled "Subscriptions" that shows the user's
   subscriptions (loaded from /api/admin/subscriptions?userId={id}). Each row shows
   product name, status badge (color-coded TRIAL=amber, ACTIVE=green, EXPIRED=gray,
   CANCELLED=red), days remaining, cancel button. "Assign Subscription" button at
   the top opens a modal with product picker + billing interval + optional trial.

6. Make sure all the new controllers respect the existing security:
   /api/admin/** is already protected by an admin role (see DefaultSecurityConfig.java +
   AdminRoleProvider) — no extra auth annotations needed.

Verify:
- Compile both backend and frontend
- As admin, create a new Product via /admin/products
- As admin, assign that Product as a subscription to an existing user via /admin/subscriptions
- Confirm the user's permissions update immediately (sync runs on create)
- Cancel the subscription, confirm status=CANCELLED and permissions reduce
- Check user profile page shows the subscription history
```

---

## Phase 4 — Stripe Integration

**Goal:** Self-serve "Choose a plan" page. Users can upgrade from trial to paid via Stripe Checkout. Stripe webhooks keep `Subscription` status in sync.

**Why fourth:** This is the largest single change. Stripe sandbox lets you test the whole flow without real cards.

**Changes:**
- Add `stripePriceIdMonthly` and `stripePriceIdAnnual` to `Product`
- Add `stripeCustomerId` to `UserModel`
- Add `stripeSubscriptionId` to `Subscription`
- New `StripeService` wrapping the Stripe Java SDK
- New `/api/billing/checkout-session` endpoint — creates a Stripe Checkout session for a chosen Product + interval, redirects to Stripe
- New `/api/billing/portal-session` endpoint — Stripe Customer Portal redirect for self-serve cancel/update
- New `/api/webhooks/stripe` endpoint — handles `customer.subscription.created|updated|deleted`, `invoice.paid`, `invoice.payment_failed`, `checkout.session.completed`
- Frontend: `/billing/plans` page showing Products with "Choose Monthly / Annual" buttons
- Frontend: `/billing` page with current sub + "Manage Subscription" (portal) button
- Add `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, `STRIPE_WEBHOOK_SECRET` to `.env`

**Files to create:**
- `backend/gls-platform/.../products/services/StripeService.java`
- `backend/gls-app-assembly/.../controllers/BillingController.java`
- `backend/gls-app-assembly/.../controllers/StripeWebhookController.java`
- `web/src/app/(protected)/billing/page.tsx`
- `web/src/app/(protected)/billing/plans/page.tsx`

**Files to modify:**
- `backend/gls-platform/.../products/models/Product.java`
- `backend/gls-platform/.../products/models/Subscription.java`
- `backend/gls-platform/.../identity/models/UserModel.java`
- `backend/gls-app-assembly/pom.xml` — add `com.stripe:stripe-java`
- `.env.example` and docker-compose.yml — Stripe env vars
- `nginx/conf.d/default.conf` — exclude `/api/webhooks/stripe` from CSRF (raw body for signature verification)

### 🤖 Prompt — Phase 4

```
This Spring Boot + Next.js app needs Stripe integration. Phases 1-3 already done:
- Default roles on signup
- Auto-trial subscription
- Admin UI for Products and Subscriptions

I need self-serve Stripe checkout, Stripe Customer Portal for management, and
webhook-driven status sync.

Setup first:
- I'll provide STRIPE_SECRET_KEY, STRIPE_PUBLISHABLE_KEY, STRIPE_WEBHOOK_SECRET
  via .env (test mode). Add them to .env.example with empty values and to
  docker-compose.yml as env vars on the api service.

Tasks:

1. Add com.stripe:stripe-java (latest stable) to backend/gls-app-assembly/pom.xml.
   Run `./mvnw compile -DskipTests -pl gls-app-assembly -am` to verify.

2. Extend models:
   - Product: add stripePriceIdMonthly (String), stripePriceIdAnnual (String).
     File: backend/gls-platform/src/main/java/co/uk/wolfnotsheep/platform/products/models/Product.java
   - Subscription: add stripeSubscriptionId (String), stripeStatus (String — Stripe's
     own status string like "active", "trialing", "past_due", "canceled" — kept for
     reference; our local `status` enum stays the source of truth).
     File: backend/gls-platform/.../products/models/Subscription.java
   - UserModel: add stripeCustomerId (String).
     File: backend/gls-platform/src/main/java/co/uk/wolfnotsheep/platform/identity/models/UserModel.java

3. Create StripeService at backend/gls-platform/.../products/services/StripeService.java:
   - Inject StripeKey via @Value("${stripe.secret-key}")
   - getOrCreateCustomer(userId, email, name) → returns stripeCustomerId, persists on
     UserModel
   - createCheckoutSession(userId, productId, interval, successUrl, cancelUrl) → returns
     Stripe Session URL. Use Stripe.session.create with mode=subscription, customer=
     stripeCustomerId, line_items=[{ price: stripePriceId, quantity: 1 }], trial period
     ONLY if user has no prior subscription (use customer.metadata to track this)
   - createPortalSession(userId, returnUrl) → returns Stripe Portal URL
   - handleWebhookEvent(payload, signature) → delegates to handlers per event type

4. Create BillingController at backend/gls-app-assembly/.../controllers/BillingController.java:
   - GET /api/billing/plans → returns list of active Products with prices and
     stripePriceId IDs (no secrets). Public to authenticated users.
   - POST /api/billing/checkout-session → body: { productId, interval: "MONTHLY"|"ANNUAL" },
     returns { url } from StripeService.createCheckoutSession. Frontend redirects
     window.location to that URL.
   - POST /api/billing/portal-session → returns { url } from
     StripeService.createPortalSession.

5. Create StripeWebhookController at backend/gls-app-assembly/.../controllers/StripeWebhookController.java:
   - POST /api/webhooks/stripe — accepts raw body + Stripe-Signature header
   - Verifies signature with stripe.webhook-secret
   - Handles events:
     * checkout.session.completed → look up Product by Session.metadata.productId,
       create or upgrade Subscription with status=ACTIVE, stripeSubscriptionId=
       session.subscription, endDate=null (Stripe manages renewal). Call
       SubscriptionPermissionSyncService.
     * customer.subscription.updated → find local Subscription by stripeSubscriptionId,
       map Stripe status → local status (active→ACTIVE, trialing→TRIAL, past_due→ACTIVE
       with warning, canceled→CANCELLED). Update endDate from current_period_end.
     * customer.subscription.deleted → set local status=CANCELLED, endDate=now.
     * invoice.payment_failed → don't change subscription status (Stripe retries),
       but log it for visibility.
   IMPORTANT: This endpoint must NOT require CSRF — add to nginx config and Spring
   security config exemptions. Webhooks come from Stripe servers, not the browser.

6. Update nginx config (nginx/conf.d/default.conf): the /api/ block already proxies to
   the api service. Webhooks need raw body — verify nginx doesn't strip or modify it
   (default behavior is fine but worth a check). No special routing needed.

7. Update Spring security to exclude /api/webhooks/stripe from CSRF. Find
   DefaultSecurityConfig.java in gls-platform and add:
     .csrf(csrf -> csrf.ignoringRequestMatchers("/api/webhooks/stripe"))
   And under authorizeHttpRequests:
     .requestMatchers("/api/webhooks/stripe").permitAll()

8. Frontend — /billing/plans page (web/src/app/(protected)/billing/plans/page.tsx):
   Loads /api/billing/plans, renders cards for each product with monthly + annual
   pricing toggle. Each card has a "Choose Monthly" / "Choose Annual" button that
   posts to /api/billing/checkout-session and redirects to the returned URL.

9. Frontend — /billing page (web/src/app/(protected)/billing/page.tsx):
   Shows the current activeSubscription from /api/user/me with: product name,
   status, billing interval, next renewal date (endDate from Stripe), price.
   "Manage Subscription" button → posts to /api/billing/portal-session, redirects
   to Stripe Portal. "Change Plan" button → /billing/plans. If user has no
   subscription (rare after auto-trial in Phase 2): "Choose a Plan" button →
   /billing/plans.

10. Update Phase 2's MeController.MeResponse if needed: subscription should now
    include billingInterval and a flag like `manageable: boolean` indicating whether
    Stripe Portal can be used (true when stripeSubscriptionId is set).

11. Add a "Billing" item to the user's settings or sidebar.

Verify:
- Compile both backend and frontend
- Use Stripe test mode. Set up two test Products in Stripe dashboard (or via API).
  Copy their price IDs into your local Product records via /admin/products UI.
- Trigger the test card flow (4242 4242 4242 4242) — confirm:
  * Stripe Checkout opens, payment succeeds
  * Webhook fires (use `stripe listen --forward-to localhost/api/webhooks/stripe`
    locally, or Cloudflare tunnel for the public URL)
  * Local Subscription created with status=ACTIVE, stripeSubscriptionId set
  * User's permissions reflect the paid product
  * /billing page shows the subscription
- Test cancellation via Stripe Portal → webhook flips local status to CANCELLED →
  permissions reduce.
- Test upgrade (change plan in Portal) → webhook updates productId, permissions resync.

Out of scope for this phase (will come later):
- Email notifications on payment events
- Dunning / retry logic beyond Stripe defaults
- Annual discount logic (Stripe handles)
- Tax (Stripe Tax can be enabled later)
```

---

## Phase 5 — Email Verification + Welcome Emails

**Goal:** New signups must verify their email before logging in. Send transactional emails on key events (welcome, trial ending, payment failed, password reset).

**Why fifth:** Required for any paid SaaS, but not blocking for internal trials. Easier to add after subscription state exists since "trial ending" emails have something to fire on.

**Changes:**
- Add `emailVerified` (boolean) and `emailVerificationToken` (String, sparse indexed) to `UserModel`
- Add `EmailService` interface and a Postmark/Resend/SES implementation
- Signup → save user with `emailVerified=false`, generate token, send verification email
- Login → block if `emailVerified=false` (unless OAuth user)
- New `/api/auth/public/verify-email?token=...` endpoint
- Welcome email after verification
- Trial-ending email (3 days, 1 day before)
- Payment-failed email
- Password reset emails (admin-triggered or self-serve forgot-password)

**Files to create:**
- `backend/gls-platform/.../identity/services/EmailService.java`
- `backend/gls-platform/.../identity/services/PostmarkEmailService.java` (or chosen provider)
- `backend/gls-platform/.../identity/controllers/EmailVerificationController.java`
- Email templates (Java text or external HTML files)

### 🤖 Prompt — Phase 5

```
This Spring Boot + Next.js app needs email verification on signup and transactional
emails on key events. Phases 1-4 done (default roles, auto-trial, admin UI for
Products/Subs, Stripe integration).

Setup first:
- Pick an email provider — Postmark, Resend, or AWS SES. (User: please specify, or
  I'll default to Postmark which has the simplest setup.)
- Add EMAIL_PROVIDER_API_KEY and EMAIL_FROM_ADDRESS to .env, .env.example,
  docker-compose.yml.
- Domain DNS verification is out of scope for this prompt — assume the user has
  set up SPF/DKIM/return path before testing real sends.

Tasks:

1. Extend UserModel (backend/gls-platform/.../identity/models/UserModel.java):
   - boolean emailVerified (default false)
   - @Indexed(sparse=true) String emailVerificationToken
   - Instant emailVerificationTokenExpiresAt

2. Create EmailService interface (backend/gls-platform/.../identity/services/EmailService.java)
   with methods:
   - sendVerificationEmail(toEmail, token, displayName)
   - sendWelcomeEmail(toEmail, displayName)
   - sendTrialEndingEmail(toEmail, displayName, daysRemaining)
   - sendPaymentFailedEmail(toEmail, displayName)
   - sendPasswordResetEmail(toEmail, token, displayName)

3. Create implementation, e.g. PostmarkEmailService implementing EmailService.
   Use the Postmark Java SDK (com.wildbit.java:postmark) or simple HTTP client.
   Inject @Value("${email.provider.api-key}") and @Value("${email.from}").
   Email bodies can start as simple text — HTML templates are a polish item.

4. Update SignupController (and OAuth2LoginSuccessHandler if relevant — note OAuth
   users have already verified email with the provider, so set emailVerified=true
   for them):
   - On web-form signup: generate UUID token, set emailVerificationTokenExpiresAt=
     now+24h, emailVerified=false. Save user. Call EmailService.sendVerificationEmail.
   - Don't apply default roles or trial subscription until email is verified
     (OR apply them but block login — your choice; blocking login is simpler).
     Recommendation: apply roles + trial NOW so the user is fully set up by the
     time they verify; just block login until verified.

5. Add EmailVerificationController (backend/gls-platform/.../identity/controllers/):
   - GET /api/auth/public/verify-email?token=... → look up user, check token not
     expired, set emailVerified=true, clear token. Send welcome email. Redirect to
     /login?verified=true (frontend shows success banner).
   - POST /api/auth/public/resend-verification → body: { email }. If user exists
     and not verified, generate new token, send verification email. Always returns
     200 to avoid revealing whether the email exists.

6. Update AuthController.login (or the security config's authentication flow) to
   reject login if emailVerified=false (unless signUpMethod is GOOGLE/GITHUB/LINKEDIN).
   Return error code EMAIL_NOT_VERIFIED so frontend can show the right message and
   a "Resend verification" button.

7. Frontend changes:
   - web/src/app/(auth)/login/page.tsx: handle EMAIL_NOT_VERIFIED error — show
     "Check your inbox" message with a "Resend" button calling
     /api/auth/public/resend-verification
   - new page web/src/app/(auth)/verify/page.tsx — shows on the redirect after
     successful verification with a "Sign in" button

8. Trial-ending emails — extend SubscriptionExpiryJob (from Phase 2) to also:
   - Find subs where status=TRIAL and endDate is between now+3d and now+3d+1h →
     send "trial ends in 3 days" email
   - Find subs where status=TRIAL and endDate is between now+24h and now+25h →
     send "trial ends tomorrow" email
   Track sent reminders on the Subscription (e.g. Set<String> remindersSent) to
   avoid duplicates.

9. Payment-failed emails — in StripeWebhookController, on invoice.payment_failed,
   look up the user and call EmailService.sendPaymentFailedEmail.

10. Password reset (basic):
    - POST /api/auth/public/forgot-password — body { email }. Generates token,
      sends email. Always returns 200.
    - POST /api/auth/public/reset-password — body { token, newPassword }. Validates,
      updates password, clears token.
    - Frontend: web/src/app/(auth)/forgot-password/page.tsx + reset-password/page.tsx

Verify:
- Compile both backend and frontend
- Sign up a new user → verification email sent (check provider's test inbox or local
  smtp test mode)
- Try logging in before verification → 401 with EMAIL_NOT_VERIFIED, frontend shows
  resend button
- Click verification link → user.emailVerified=true, welcome email sent, redirect to
  login, can now log in
- Manually set a Subscription endDate to ~3 days from now and trigger the job → trial
  ending email sent (only once)
- Trigger a Stripe test webhook for invoice.payment_failed → payment failed email
  sent
- Test forgot-password flow end to end
```

---

## Phase 6 — Subscription Enforcement Hardening

**Goal:** Subscription state actually enforces feature access. Permission resolution is fresh on every request (or every login at minimum), so revocation is immediate.

**Why last:** Earlier phases assume `User.permissions` is always correct via sync calls. This phase makes that assumption robust.

**Changes:**
- On every successful login, re-run `SubscriptionPermissionSyncService.syncPermissionsForUser()`
- Optionally: live permission resolution on each request (slower, but truthful)
- Add `SubscriptionGuard` aspect / interceptor for endpoints that require an active subscription (vs just a permission)
- Audit-log subscription state changes
- Add a `Subscription.lastSyncedAt` field to detect drift

**Files to touch:**
- `backend/gls-platform/.../identity/controllers/AuthController.java` (post-login hook)
- `backend/gls-platform/.../products/services/SubscriptionPermissionSyncService.java`
- `backend/gls-app-assembly/.../services/AuditService.java` (if exists, add subscription events)

### 🤖 Prompt — Phase 6

```
This Spring Boot app has Subscription-driven permissions. Phases 1-5 done (default
roles on signup, auto-trial, admin UI, Stripe, email verification). Right now
User.permissions is a snapshot kept in sync via SubscriptionPermissionSyncService —
but if the sync gets missed for any reason, a user could keep accessing features
after their subscription is cancelled.

Tasks:

1. On every successful login (find AuthController.login or the equivalent in
   gls-platform/.../identity/), call SubscriptionPermissionSyncService
   .syncPermissionsForUser(userId) before returning the JWT response. This is the
   primary safety net — a freshly-issued token always reflects the latest
   subscription state.

2. Add lastSyncedAt (Instant) to Subscription model. Set it inside the sync service
   so we can audit drift.

3. Add a scheduled job SubscriptionDriftCheck @Scheduled(cron = "0 0 3 * * *")
   (3am daily): re-syncs every user with a non-CANCELLED subscription. Logs how many
   were updated. Catches any drift from Stripe webhook misses.

4. Audit subscription state changes:
   - Find existing AuditService / audit-events code (search for "audit" in
     backend/gls-app-assembly)
   - Emit audit events on: subscription created, status changed, cancelled.
     Source = "Stripe webhook" / "Admin UI" / "Trial expiry job" / "Drift check".

5. Add a SubscriptionRequired check for endpoints that should be blocked when no
   active subscription exists:
   - Create annotation @SubscriptionRequired (no params) or @SubscriptionRequired(
     productKeys = {"PRO", "ENTERPRISE"})
   - Implement as Spring AOP @Around aspect that:
     * Reads the authenticated user
     * Calls SubscriptionRepository.findByUserIdAndStatusIn([ACTIVE, TRIAL])
     * If empty (or productKey doesn't match required), throw 402 Payment Required
   - Apply selectively — start with just one endpoint to prove the flow, e.g.
     /api/documents (POST) for upload.
   - Frontend should handle 402 by redirecting to /billing/plans.

6. Confirm that existing endpoints work fine without the annotation — by default
   permissions still control access. The annotation is an additional check on top
   for premium features.

Verify:
- Compile backend
- Cancel a user's subscription via Stripe Portal → webhook fires → status=CANCELLED
- That user logs in again → JWT issued, but their roles/permissions reflect the
  now-CANCELLED state (only role-based defaults remain)
- Endpoints with @SubscriptionRequired return 402; frontend redirects to /billing/plans
- Run the drift-check job manually → confirm log output shows 0 changes (since
  webhook-sync should be current)
- Manually corrupt a Subscription state in Mongo, run drift check → catches and
  reverts it
```

---

## Quick Reference — File Locations

| Concept | Location |
|---|---|
| Signup endpoint | `backend/gls-platform/.../identity/controllers/SignupController.java` |
| OAuth callback | `backend/gls-platform/.../identity/configs/OAuth2LoginSuccessHandler.java` |
| User model | `backend/gls-platform/.../identity/models/UserModel.java` |
| Login endpoint | `backend/gls-platform/.../identity/controllers/AuthController.java` |
| `/me` endpoint | `backend/gls-platform/.../identity/controllers/MeController.java` |
| Product / Subscription / Role / Feature models | `backend/gls-platform/.../products/models/` |
| Role permission sync | `backend/gls-platform/.../products/services/RolePermissionSyncService.java` |
| Subscription permission sync | `backend/gls-platform/.../products/services/SubscriptionPermissionSyncService.java` |
| Permission seeder | `backend/gls-app-assembly/.../bootstrap/PermissionDataSeeder.java` |
| Admin user controller | `backend/gls-app-assembly/.../controllers/admin/AdminUserController.java` |
| Security config | `backend/gls-platform/.../identity/configs/DefaultSecurityConfig.java` |
| Admin role provider | `backend/gls-app-assembly/.../config/DefaultAdminRoleProvider.java` |
| Frontend login page | `web/src/app/(auth)/login/page.tsx` |
| Frontend dashboard | `web/src/app/(protected)/dashboard/page.tsx` |
| Frontend admin users | `web/src/app/(protected)/admin/users/page.tsx` |
| Frontend user profile | `web/src/app/(protected)/admin/users/[id]/page.tsx` |
| Auth context | `web/src/contexts/auth-context.tsx` |
| Build platform: | `cd backend && ./mvnw compile -DskipTests -pl gls-app-assembly -am` |
| TypeScript check: | `cd web && npx tsc --noEmit` |
| Restart api: | `docker compose up --build -d api web` |

## Schema Migration Summary (Mongo)

When implementing each phase, these fields are added to existing documents. New docs get them automatically; existing docs default to null/false until updated.

| Phase | Collection | Fields added |
|---|---|---|
| 2 | `app_products` | `trialDays`, `defaultForNewSignups` |
| 4 | `app_products` | `stripePriceIdMonthly`, `stripePriceIdAnnual` |
| 4 | `app_security_user_account` | `stripeCustomerId` |
| 4 | `subscriptions` | `stripeSubscriptionId`, `stripeStatus` |
| 5 | `app_security_user_account` | `emailVerified`, `emailVerificationToken`, `emailVerificationTokenExpiresAt` |
| 6 | `subscriptions` | `lastSyncedAt`, `remindersSent` |

## Order of operations on a clean install (after all phases)

```
User signs up
  ↓
Save user (emailVerified=false, no roles, no permissions)
  ↓
Apply default roles (Phase 1)
  ↓
Create TRIAL subscription on default Product (Phase 2)
  ↓
Sync product permissions (Phase 2)
  ↓
Send verification email (Phase 5)
  ↓
[wait for user to click link]
  ↓
Mark emailVerified=true, send welcome email
  ↓
User logs in
  ↓
Re-sync subscription permissions (Phase 6 safety)
  ↓
Issue JWT, redirect to /dashboard
  ↓
Dashboard shows trial countdown
  ↓
Day 11 of trial → "trial ends in 3 days" email (Phase 5)
  ↓
User clicks "Upgrade" → /billing/plans → Stripe Checkout (Phase 4)
  ↓
Stripe webhook upgrades Subscription to ACTIVE
  ↓
Permissions re-sync, dashboard banner clears
  ↓
Stripe handles renewal automatically; webhooks keep our state in sync
```
