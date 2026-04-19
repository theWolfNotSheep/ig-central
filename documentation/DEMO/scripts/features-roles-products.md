# Features, Roles & Products Admin — Demo Script

**Duration: 2:30**

**Goal:** Show how the licensing model is managed — features, roles, and products as first-class entities so subscription tiers can be created and modified without code changes.

## Script

1. **Open with the commercial model.** *"Every feature in IG Central is a licensable unit. That means you can package and sell exactly what your customers need — a starter tier, an enterprise tier, an add-on for Google Drive, an add-on for the hub — without shipping code to turn features on and off."*

2. **Navigate to Admin → Features.**

3. **Walk the feature catalogue.** *"Every capability in the platform is a Feature, each with a unique `permissionKey`. `CAN_UPLOAD_DOCUMENTS`, `CAN_VIEW_ANALYTICS`, `CAN_IMPORT_HUB_PACKS`, `CAN_USE_BERT_TRAINING`. Fine-grained, not module-level."*

4. **Navigate to Admin → Roles.** *"Roles bundle features. 'Reviewer' might include classification view, PII flag, review queue actions. 'Admin' includes everything. You create new roles for tiered subscriptions — `SUB_PREMIUM`, `SUB_ENTERPRISE`."*

5. **Build a new role.** *"Name it 'Subscription — Professional Tier'. Tick the features: upload, classify, review, search, basic reporting. Leave off BERT training and hub import — those are Enterprise-only. Save."*

6. **Navigate to Admin → Products.** *"Products link Roles and direct Features to pricing. Think of this as the product catalogue in your billing system, mirrored here."*

7. **Create a product.** *"Name 'Professional', pick roles (the one we just made), set monthly and annual pricing, set the billing currency. Optionally link to Stripe SKUs."*

8. **Show the admin view.** *"The product is live. Existing and new customers can be subscribed to it — and their permissions auto-sync."*

9. **Tie it to the business.** *"When marketing wants to launch a new add-on — 'PII & SAR module' as an up-sell — it's a new Product definition, a new Role, a new price. Live in an afternoon, not a release cycle."*

**Key message:** *"Your product catalogue lives where it should — in the admin panel, not in code. Commercial flexibility without engineering bottlenecks."*
