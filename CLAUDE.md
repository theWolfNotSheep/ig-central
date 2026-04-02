# Governance-Led Storage — Project Guidelines

## Architecture

- **Backend:** Spring Boot 4.0.2 (Java 21), Maven multi-module monorepo, MongoDB
- **Frontend:** Next.js 16 (React 19, TypeScript, TailwindCSS 4)
- **Infra:** Docker Compose — Cloudflare tunnel, nginx, MongoDB
- **Auth:** JWT + OAuth2 (optional), CSRF via cookie

### Module structure

- `gls-platform` — Core platform: identity, security, JWT, OAuth2, config, products/licensing
- `gls-governance` — Governance rules and policy engine
- `gls-document` — Document domain: models, repositories, storage services
- `gls-document-processing` — Document processing pipeline
- `gls-governance-enforcement` — Governance enforcement services
- `gls-mcp-server` — MCP server integration
- `gls-llm-orchestration` — LLM orchestration layer (RabbitMQ, Anthropic)
- `gls-app-assembly` — Spring Boot entry point, config, seeders, controllers

## Configuration-Driven Design

**All application behaviour that is not core data infrastructure must be driven by configuration, not hardcoded.**

This means:

### Must be configuration (stored in MongoDB, editable at runtime)
- Menu items, navigation structure, sidebar links
- UI labels, display text, page titles
- Feature flags and toggles
- Role display names, permission labels, capability descriptions
- Status options, category lists, dropdown values
- Workflow states and transitions
- Anything that looks like an enum from a UI perspective

### May remain as code
- Security filter chain paths (`/api/auth/**`, `/actuator/**`)
- Database collection names and document schemas
- Spring config keys and profiles
- JWT claim structure and token mechanics
- Java enums used for **type safety in code** (e.g. `UserAccountType`) — but their display labels, ordering, and visibility must come from config

### How it works
- Config lives in a MongoDB collection (e.g. `app_config`)
- A `ConfigService` loads config on startup and supports refresh without restart
- Frontend fetches UI config via a public endpoint (e.g. `GET /api/public/config`)
- Java enums define what the code can handle; config defines what the user sees

### Why
- No redeployment for label changes, menu reordering, or toggling features
- Non-developers can manage application behaviour
- Keeps the codebase focused on logic, not presentation data

## Licensable Features & Subscription Model

**Every feature, workflow, and permission must be tracked as a licensable unit.** This enables an admin panel where subscriptions, products, and access tiers can be created and managed — matching the pattern from fractional-exchange.

### Entity hierarchy

```
Product (purchasable subscription tier)
  ├── roleIds → Role[]
  │     └── featureIds → Feature[]
  └── featureIds → Feature[] (direct)

Subscription (links a user/company to a Product)
  └── status: ACTIVE | TRIAL | EXPIRED | CANCELLED
```

### MongoDB collections

| Collection | Purpose |
|---|---|
| `app_features` | Individual permissions (e.g. `CAN_UPLOAD_DOCUMENTS`, `CAN_VIEW_ANALYTICS`) |
| `app_roles` | Bundles of features, scoped to account types (e.g. `SUB_PREMIUM`, `SUB_ENTERPRISE`) |
| `app_products` | Purchasable products linking roles + features with pricing |
| `app_subscriptions` | User/company subscriptions to products with billing intervals |
| `app_config` | Runtime configuration (menus, labels, flags, etc.) |

### How permissions flow

1. Admin creates Features (`app_features`) — each has a unique `permissionKey`
2. Admin creates Roles (`app_roles`) — each bundles feature IDs, has a unique `key` (prefix `SUB_` for subscription roles)
3. Admin creates Products (`app_products`) — links role IDs and feature IDs with pricing
4. Admin creates Subscription for user/company → triggers `SubscriptionPermissionSyncService`
5. Sync service loads Product → Roles → Features → extracts `permissionKey` values
6. User's `roles` and `permissions` sets are updated on their `UserModel`
7. Frontend checks permissions via `/api/user/me` response

### Rules for new features

- **Every new capability must have a corresponding Feature** with a `permissionKey`
- Group Features into Roles for subscription tiers
- Never hardcode permission checks against role names — check `permissionKey` values
- Subscription roles use `SUB_` prefix to distinguish from system roles (e.g. `ADMIN`)
- The admin panel will manage all CRUD for Features, Roles, Products, and Subscriptions

### Platform services (in `gls-platform`)

- `AppConfigService` — runtime config CRUD with in-memory cache + refresh
- `SubscriptionPermissionSyncService` — syncs subscription roles/permissions to user model
- `PublicConfigController` — `GET /api/public/config` for frontend config
- `AdminConfigController` — `GET/PUT /api/admin/config` for admin management

## Build & Run

```bash
# Local backend
cd backend && ./mvnw compile -DskipTests -pl gls-app-assembly -am

# Docker (all services)
docker compose up --build mongo api web nginx -d

# Access
http://localhost          # Homepage
http://localhost/login    # Login
http://localhost/dashboard # Dashboard (auth required)
```

## Admin credentials (dev)

Seeded on first startup from `.env`:
- Email: `admin@governanceledstore.co.uk`
- Password: `ChangeMe123!`
