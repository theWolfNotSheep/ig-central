# IGC Deployment Guide

Deployment guide for Governance-Led Storage on a single server using Docker Compose with Cloudflare tunnel for public access.

---

## Prerequisites

| Requirement | Details |
|---|---|
| Docker Engine | 24+ with Compose v2 |
| Domain name | Pointed at Cloudflare (for tunnel) |
| Cloudflare account | Free tier is sufficient; create a tunnel in Zero Trust dashboard |
| Google Cloud project | Optional; required for Google Drive connector and Google login |
| Anthropic API key | Required for LLM-based document classification |
| Server resources | Minimum 4 CPU / 8 GB RAM / 50 GB disk |

---

## Architecture Overview

The platform runs as a set of Docker containers on a single host. Cloudflare tunnel provides public HTTPS access without opening inbound ports.

| Service | Container | Port | Description |
|---|---|---|---|
| **nginx** | igc-nginx | 80 | Reverse proxy; routes `/api/` to API, everything else to frontend |
| **api** | igc-api | 8080 | Main Spring Boot API, pipeline execution, governance enforcement |
| **llm-worker** | igc-llm-worker | 8082 | LLM classification worker; consumes from RabbitMQ, calls Anthropic/Ollama |
| **mcp-server** | igc-mcp-server | 8081 | MCP tool server; provides correction history and metadata schemas to LLM |
| **web** | igc-web | 3000 | Next.js frontend |
| **mongo** | igc-mongo | 27017 | MongoDB 7 document database (system of record) |
| **elasticsearch** | igc-elasticsearch | 9200 | Elasticsearch 8.17 search index |
| **rabbitmq** | igc-rabbitmq | 5672/15672 | RabbitMQ 4 message queue (management UI on 15672) |
| **minio** | igc-minio | 9000/9001 | MinIO object storage (console on 9001) |
| **bert-classifier** | igc-bert-classifier | 8000 | BERT accelerator for fast pre-classification |
| **cloudflared** | igc-cloudflared | -- | Cloudflare tunnel agent |
| **governance-hub** | igc-governance-hub | 8090 | Governance pack marketplace API |
| **web-hub** | igc-web-hub | 3002 | Governance Hub frontend |
| **hub-mongo** | igc-hub-mongo | 27017 | Separate MongoDB for Governance Hub |

---

## Environment Variables

Copy the example file and fill in your values:

```bash
cp .env.example .env
```

### Required

| Variable | Description | Example |
|---|---|---|
| `MONGO_PASSWORD` | MongoDB root password | `strongpassword123` |
| `JWT_SECRET` | JWT signing key (min 256 bits). Generate with `openssl rand -base64 64` | (64-char base64 string) |
| `ADMIN_EMAIL` | Initial admin account email | `admin@example.com` |
| `ADMIN_PASSWORD` | Initial admin account password | `ChangeMe123!` |
| `TUNNEL_TOKEN` | Cloudflare tunnel token from Zero Trust dashboard | (long token string) |
| `PUBLIC_URL` | Public-facing URL for the platform | `https://igc.example.com` |

### Recommended

| Variable | Description | Default |
|---|---|---|
| `ANTHROPIC_API_KEY` | Anthropic API key for LLM classification. Can also be set post-deploy via Settings > AI Configuration | (empty) |
| `RABBITMQ_USER` | RabbitMQ username | `guest` |
| `RABBITMQ_PASSWORD` | RabbitMQ password | `guest` |
| `MINIO_ACCESS_KEY` | MinIO root user | `minioadmin` |
| `MINIO_SECRET_KEY` | MinIO root password | `minioadmin` |
| `MINIO_BUCKET` | MinIO bucket name for documents | `igc-documents` |

### Optional

| Variable | Description | Default |
|---|---|---|
| `COMPOSE_PROJECT_NAME` | Docker Compose project prefix | `igc` |
| `APP_ENV` | Environment label (`DEV`, `STAGING`, `PROD`) | `DEV` |
| `MONGO_DB_NAME` | MongoDB database name | `governance_led_storage_main` |
| `BACKEND_BASE_URL` | Backend URL (used internally) | `http://localhost:8080` |
| `GOOGLE_OAUTH_CLIENT_ID` | Google OAuth client ID for Drive connector | (empty) |
| `GOOGLE_OAUTH_CLIENT_SECRET` | Google OAuth client secret | (empty) |
| `LINKEDIN_CLIENT_ID` | LinkedIn OAuth client ID | (empty) |
| `LINKEDIN_CLIENT_SECRET` | LinkedIn OAuth client secret | (empty) |
| `GIT_HUB_CLIENT_ID` | GitHub OAuth client ID | (empty) |
| `GIT_HUB_CLIENT_SECRET` | GitHub OAuth client secret | (empty) |
| `EMAIL_SMTP_HOST` | SMTP server for outbound email | `smtp.office365.com` |
| `EMAIL_SMTP_PORT` | SMTP port | `587` |
| `EMAIL_ADDRESS` | SMTP sender address | (empty) |
| `EMAIL_PASSWORD` | SMTP password | (empty) |
| `HUB_MONGO_PASSWORD` | Governance Hub MongoDB password | `hubpassword123` |
| `HUB_ADMIN_USERNAME` | Governance Hub admin username | `admin` |
| `HUB_ADMIN_PASSWORD` | Governance Hub admin password | `ChangeMe123!` |

---

## Quick Start (Development)

```bash
cd /path/to/governance-led-storage

# 1. Create env file
cp .env.example .env

# 2. Edit .env — at minimum set MONGO_PASSWORD, JWT_SECRET, ADMIN_PASSWORD
#    Generate a JWT secret:
openssl rand -base64 64

# 3. Start all services
docker compose up --build -d

# 4. Wait for health checks (2-3 minutes for first build)
docker compose ps

# 5. Access the platform
#    Local:  http://localhost
#    Login:  http://localhost/login
```

Default admin credentials are defined by `ADMIN_EMAIL` and `ADMIN_PASSWORD` in your `.env` file.

---

## Production Deployment

### 1. Prepare the environment file

```bash
cp .env.example .env
```

Set production values:

```env
COMPOSE_PROJECT_NAME=igc
APP_ENV=PROD
PUBLIC_URL=https://igc.example.com
BACKEND_BASE_URL=https://igc.example.com

MONGO_PASSWORD=<strong-random-password>
JWT_SECRET=<openssl rand -base64 64>
ADMIN_EMAIL=admin@yourcompany.com
ADMIN_PASSWORD=<strong-initial-password>

TUNNEL_TOKEN=<cloudflare-tunnel-token>

RABBITMQ_USER=igc_rabbit
RABBITMQ_PASSWORD=<strong-random-password>
MINIO_ACCESS_KEY=igc_minio
MINIO_SECRET_KEY=<strong-random-password>

ANTHROPIC_API_KEY=sk-ant-...
```

### 2. Set up Cloudflare Tunnel

1. Go to Cloudflare Zero Trust > Networks > Tunnels
2. Create a new tunnel, copy the token into `TUNNEL_TOKEN`
3. Add a public hostname pointing to `http://nginx:80`
4. The tunnel container connects outbound to Cloudflare -- no inbound ports required

### 3. Deploy

```bash
docker compose up --build -d
```

### 4. Verify

```bash
# Check all containers are healthy
docker compose ps

# Check API health
curl -sf http://localhost/actuator/health

# Check logs for errors
docker compose logs --tail=50 api
docker compose logs --tail=50 llm-worker
```

---

## Post-Deployment Checklist

1. **Change admin password** -- Log in at `/login` with your `ADMIN_EMAIL`/`ADMIN_PASSWORD` and change the password immediately.

2. **Configure AI provider** -- Go to Settings > AI Configuration. Set the Anthropic API key (or configure Ollama for local LLM). This can also be set via the `ANTHROPIC_API_KEY` env var.

3. **Configure Google OAuth** (optional) -- Go to Settings > Integrations. Enter your Google OAuth client ID and secret. Register redirect URIs in Google Cloud Console:
   - Drive: `${PUBLIC_URL}/api/drives/google/callback`
   - Login: `${PUBLIC_URL}/api/auth/public/google/callback`

4. **Import a governance pack** (optional) -- Go to Governance Hub and import a pack to bootstrap your taxonomy, retention rules, and PII patterns.

5. **Verify service health** -- Check the Monitoring page in the admin UI, or hit these endpoints:
   - API: `http://localhost:8080/actuator/health`
   - MCP Server: `http://localhost:8081/actuator/info`
   - LLM Worker: `http://localhost:8082/actuator/health`
   - RabbitMQ Management: `http://localhost:15672` (with RabbitMQ credentials)
   - MinIO Console: `http://localhost:9001` (with MinIO credentials)
   - Elasticsearch: `http://localhost:9200/_cluster/health`

6. **Test the pipeline** -- Upload a test document and verify it flows through: UPLOADED > PROCESSING > PROCESSED > CLASSIFYING > CLASSIFIED > GOVERNANCE_APPLIED.

---

## Data Volumes

All persistent data is stored under `./data/` on the host:

| Directory | Service | Contents |
|---|---|---|
| `./data/mongo` | MongoDB | Document database |
| `./data/minio` | MinIO | Uploaded document files |
| `./data/elasticsearch` | Elasticsearch | Search indices |
| `./data/rabbitmq` | RabbitMQ | Queue state |
| `./data/hub-mongo` | Hub MongoDB | Governance Hub data |
| `./data/bert-models` | BERT classifier | Model files |

---

## Backup and Restore

### MongoDB (primary data store)

```bash
# Backup
docker exec igc-mongo mongodump \
  --uri="mongodb://root:<MONGO_PASSWORD>@localhost:27017/governance_led_storage_main?authSource=admin" \
  --out=/tmp/backup

docker cp igc-mongo:/tmp/backup ./backups/mongo-$(date +%Y%m%d)

# Restore
docker cp ./backups/mongo-20260415 igc-mongo:/tmp/restore
docker exec igc-mongo mongorestore \
  --uri="mongodb://root:<MONGO_PASSWORD>@localhost:27017/governance_led_storage_main?authSource=admin" \
  --drop /tmp/restore/governance_led_storage_main
```

### MinIO (document files)

MinIO data lives at `./data/minio`. Back up this directory:

```bash
tar czf backups/minio-$(date +%Y%m%d).tar.gz ./data/minio
```

### Elasticsearch (search index)

Elasticsearch is a secondary index built from MongoDB data. It does not need to be backed up -- it can be rebuilt by re-indexing documents. If needed, you can snapshot:

```bash
# Register a snapshot repo (one-time)
curl -X PUT "localhost:9200/_snapshot/backup" -H 'Content-Type: application/json' -d'{
  "type": "fs",
  "settings": { "location": "/usr/share/elasticsearch/data/snapshots" }
}'

# Create snapshot
curl -X PUT "localhost:9200/_snapshot/backup/snap-$(date +%Y%m%d)"
```

---

## Upgrading

```bash
# 1. Pull latest code
git pull origin main

# 2. Rebuild and restart (zero-downtime is not supported in single-server mode)
docker compose up --build -d

# 3. Verify health
docker compose ps
curl -sf http://localhost/actuator/health
```

MongoDB migrations (if any) run automatically on startup via Spring Boot seeders.

---

## Troubleshooting

### Services not starting

```bash
# Check which containers are unhealthy
docker compose ps

# View logs for the failing service
docker compose logs --tail=100 <service-name>

# Common causes:
# - mongo: MONGO_PASSWORD mismatch between first run and subsequent runs
#   Fix: Remove ./data/mongo and restart (destroys data)
# - api: MongoDB connection timeout — ensure mongo is healthy first
# - llm-worker: mcp-server not healthy yet — wait for startup
```

### API returns 502

The API container takes 1-2 minutes to start (Spring Boot + health check warm-up). Check:

```bash
docker compose logs --tail=50 api
docker compose logs --tail=50 nginx
```

If the API is healthy but nginx returns 502, restart nginx: `docker compose restart nginx`

### LLM classification not working

1. Check the Anthropic API key is set -- either in `.env` or via Settings > AI Configuration in the UI.
2. Check llm-worker logs: `docker compose logs --tail=50 llm-worker`
3. Check RabbitMQ for queued messages: `http://localhost:15672` (default: guest/guest)
4. Documents stuck in CLASSIFYING for >10 minutes are stale -- use Monitoring > Reset Stale in the admin UI.

### Documents stuck in PROCESSING or CLASSIFYING

These are likely stale. The Monitoring page in the admin UI shows stuck documents and provides "Reset Stale" to re-queue them. Alternatively:

```bash
# Check RabbitMQ queues
docker exec igc-rabbitmq rabbitmqctl list_queues name messages consumers
```

### Queue buildup (messages not being consumed)

```bash
# Check consumer count
docker exec igc-rabbitmq rabbitmqctl list_queues name consumers

# If consumers = 0, the llm-worker may have crashed
docker compose restart llm-worker
```

### Elasticsearch index out of sync

Elasticsearch is a secondary index. If search results are stale or missing, re-index from the admin Monitoring page or by restarting the API (which triggers startup indexing).

### Disk space

```bash
# Check data directory sizes
du -sh ./data/*

# MongoDB and MinIO grow with document volume
# Elasticsearch indices can be large -- check index size:
curl -s "localhost:9200/_cat/indices?v&h=index,store.size"
```

### Container resource limits

For production, consider adding resource limits in a `docker-compose.override.yml`:

```yaml
services:
  api:
    deploy:
      resources:
        limits:
          memory: 2G
  llm-worker:
    deploy:
      resources:
        limits:
          memory: 2G
  elasticsearch:
    deploy:
      resources:
        limits:
          memory: 1G
```

---

## Ports Reference

These ports are exposed to the host by default:

| Port | Service | Notes |
|---|---|---|
| 80 | nginx | Main entry point |
| 9000 | MinIO API | S3-compatible API |
| 9001 | MinIO Console | Web UI |
| 9200 | Elasticsearch | REST API |
| 15672 | RabbitMQ | Management UI |
| 8090 | Governance Hub API | Hub REST API |
| 3002 | Governance Hub UI | Hub frontend |

In production behind Cloudflare tunnel, only port 80 is used by the tunnel. Consider binding other ports to `127.0.0.1` only by adding `127.0.0.1:` prefix in docker-compose override.

---

## Security Considerations

- **Change all default passwords** before production deployment (MongoDB, RabbitMQ, MinIO, admin account, Hub admin).
- **JWT_SECRET** must be a strong random value (256+ bits). Generate with `openssl rand -base64 64`.
- **Cloudflare tunnel** means no inbound ports are open on the server. Access to management UIs (RabbitMQ, MinIO, Elasticsearch) should be restricted to localhost or VPN.
- **CSRF protection** is enabled via cookie-based tokens on all state-changing API endpoints.
- **File upload limit** is 120 MB (configured in nginx).
