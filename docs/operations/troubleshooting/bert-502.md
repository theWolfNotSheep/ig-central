# Troubleshoot: 502 Bad Gateway on BERT Training Jobs Endpoint

## Problem

`GET /api/proxy/admin/bert/training-jobs` returns 502 Bad Gateway via Cloudflare.

## Request Chain

```
Browser → Cloudflare → nginx (:80) → Next.js (:3000) /api/proxy/[...path] → Spring Boot (:8080) /api/admin/bert/training-jobs
```

The 502 means one link in this chain is failing.

## Debug Steps

### 1. Check Spring Boot API is responding

```bash
docker exec gls-api curl -s http://localhost:8080/api/admin/bert/training-jobs
```

Expected: `{"error":"Unauthorized",...}` (401 is fine — means the endpoint exists and responds)

If this fails → API is down or endpoint doesn't exist. Check `docker logs gls-api --tail 50`.

### 2. Check Next.js can reach Spring Boot

```bash
docker exec gls-web curl -s http://api:8080/api/admin/bert/training-jobs
```

If this fails → DNS resolution or network issue between containers. Check both are on the same Docker network:
```bash
docker network inspect gls | grep -A2 "gls-api\|gls-web"
```

### 3. Check Next.js proxy route environment

The Next.js proxy at `web/src/app/api/proxy/[...path]/route.ts` uses:
```
BACKEND_BASE_URL or API_BASE_URL or "http://localhost:8080"
```

Check what the web container has:
```bash
docker exec gls-web env | grep -i "BASE_URL\|API_URL\|BACKEND"
```

It should be `http://api:8080` (Docker service name), NOT `http://localhost:8080`.

Check docker-compose.yml for the web service environment:
```yaml
# Look for:
BACKEND_BASE_URL: http://api:8080
# or
API_BASE_URL: http://api:8080
```

**If this env var is missing or set to localhost, that's the 502 cause.**

### 4. Check nginx is routing correctly

```bash
docker exec gls-nginx curl -s http://web:3000/api/proxy/admin/bert/training-jobs
```

### 5. Check MongoDB connectivity

The API had MongoDB timeouts in recent logs. If Mongo is slow:
```bash
docker exec gls-mongo mongosh --quiet -u root -p devpassword123 --authenticationDatabase admin --eval "db.adminCommand('ping')"
```

### 6. Rebuild everything cleanly

```bash
docker compose down
docker compose up --build -d
```

Wait 30 seconds for all health checks to pass, then test.

## BERT Training Pipeline Status

### What was built (all compiles, all deployed):
- `BertTrainingJobController.java` — POST to start, GET to list, POST to promote
- `BertTrainingJob.java` — model in MongoDB `bert_training_jobs`
- `train_worker.py` — subprocess worker for ModernBERT fine-tuning
- `main.py` — `/train` launches subprocess, `/train/{id}/status` polls, `/models/activate` hot-swaps

### Known issues fixed:
- HTTP/2 → HTTP/1.1 (Uvicorn doesn't support HTTP/2 upgrade from Java HttpClient)
- Stratified split fallback for categories with < 2 samples
- Subprocess-based training (was thread-based, blocked uvicorn, healthcheck killed container)
- Stderr from subprocess no longer treated as error (HuggingFace warnings leaked through)

### What to verify after 502 is fixed:
1. Clear old failed jobs: `db.bert_training_jobs.deleteMany({})`
2. Click "Train BERT" on `/ai/models` page
3. Watch logs: `docker logs -f gls-bert-classifier` and `docker logs -f gls-api | grep train`
4. Job should show TRAINING → COMPLETED with metrics
5. Click Promote → model hot-swapped → BERT status changes from "demo" to "onnx" or "transformers"

## Key Files

| File | What |
|------|------|
| `web/src/app/api/proxy/[...path]/route.ts` | Next.js proxy (line 6: API_BASE_URL) |
| `nginx/conf.d/default.conf` | nginx routing (line 51: /api/proxy/ → web:3000) |
| `docker-compose.yml` line ~150 | web service env vars |
| `BertTrainingJobController.java` | Training job endpoints |
| `bert-classifier/main.py` | Python training + inference |
| `bert-classifier/train_worker.py` | Subprocess training worker |
