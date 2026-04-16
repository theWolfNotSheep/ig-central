# Governance Hub

Standalone stack for the governance pack marketplace. Runs independently of the main platform with its own Docker network, MongoDB, nginx, and Cloudflare tunnel.

## Services

| Service | Role |
|---|---|
| `hub-mongo` | MongoDB for pack/version/api-key storage |
| `governance-hub` | Spring Boot API (port 8090) |
| `web-hub` | Next.js admin UI (port 3001) |
| `hub-nginx` | Reverse proxy (port 8091 on host) — routes `/api/hub/*` to API, everything else to the UI |
| `hub-cloudflared` | Cloudflare tunnel — exposes the nginx to `hub.igcentral.com` |

All services live on the `hub-net` network; nothing touches the main platform network.

## Setup

1. **Create a Cloudflare tunnel** for `hub.igcentral.com`
   - Cloudflare Zero Trust → Networks → Tunnels → Create a tunnel
   - Public hostname: `hub.igcentral.com` → service `http://nginx:80`
   - Copy the tunnel token

2. **Create `.env`** from the template:
   ```bash
   cp .env.example .env
   # Edit .env — paste HUB_TUNNEL_TOKEN
   ```

3. **Start the stack**:
   ```bash
   docker compose up --build -d
   ```

4. **Verify**:
   - Local: `http://localhost:8091` (hub admin UI)
   - External: `https://hub.igcentral.com`
   - API: `curl -u admin:ChangeMe123! https://hub.igcentral.com/api/hub/admin/packs`

## Main platform integration

The main platform imports packs from this hub. Configure the URL in the platform's **Settings → Governance Hub** page, or set the compose env var:

```
GOVERNANCE_HUB_URL=https://hub.igcentral.com
GOVERNANCE_HUB_API_KEY=<generate from hub admin UI>
```

## Credentials

Default admin login (change in `.env`):
- Username: `admin`
- Password: `ChangeMe123!`
