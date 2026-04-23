# Subscription Creation & Permission Sync — Demo Script

**Duration: 2:30**

**Goal:** Show how customer subscriptions are created and managed — and how the permission sync service propagates product entitlements down to every user instantly.

## Script

1. **Open with the lifecycle.** *"A new customer signs up on Enterprise. Ten users get invited. Six months later, they upgrade to Enterprise Plus — they want the hub add-on. Every user should gain access the moment that upgrade completes. Let me show you that flow."*

2. **Navigate to Admin → Subscriptions.**

3. **Show the subscription list.** *"Every tenant subscription — customer name, product, status (Active, Trial, Expired, Cancelled), billing interval, start date, renewal date."*

4. **Create a new subscription.** *"Pick the customer — or company if multi-company — select the product, pick the interval (monthly or annual), pick status (Trial, Active), set the term. Save."*

5. **Watch the sync happen.** *"The moment you save, the Subscription Permission Sync Service runs. It reads the product, expands the roles, extracts the feature permission keys, and writes them to every user on the customer's tenant. Sub-second."*

6. **Show a user's updated permissions.** Click through to a user on that tenant. *"Permissions tab — the feature flags are now active. Their sidebar includes the newly licensed modules. They can refresh and see the change immediately."*

7. **Demonstrate a downgrade.** *"Change the subscription to a lower tier. Save. Permissions re-sync — features they no longer have access to disappear from their UI. Their historical data isn't touched, but action capabilities are pulled."*

8. **Show trial handling.** *"Trials have an end date. The platform emails reminders at 7 days, 3 days, 1 day. When the trial expires, the tenant converts or downgrades automatically based on your policy."*

9. **Tie it to billing.** *"Each subscription links to a Stripe subscription ID (when you enable billing). Subscription state changes trigger billing webhooks and vice versa — no reconciliation spreadsheets."*

**Key message:** *"Your commercial ops and your product access stay in lockstep — automatically. Sell it, sync it, no manual provisioning."*
