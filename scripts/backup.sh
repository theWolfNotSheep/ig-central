#!/usr/bin/env bash
#
# backup.sh — Back up MongoDB and (optionally) MinIO data for IGC
#
# Usage:
#   ./scripts/backup.sh [BACKUP_DIR]
#
# Examples:
#   ./scripts/backup.sh                          # default: ./backups/<timestamp>
#   ./scripts/backup.sh /mnt/nas/igc-backup      # custom directory
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_DIR="${1:-$PROJECT_DIR/backups/$TIMESTAMP}"

# Container names — match docker-compose.yml defaults
COMPOSE_PROJECT="${COMPOSE_PROJECT_NAME:-igc}"
MONGO_CONTAINER="${COMPOSE_PROJECT}-mongo"
MONGO_DB="${MONGO_DB_NAME:-governance_led_storage_main}"
MONGO_PASSWORD="${MONGO_PASSWORD:-example}"

MINIO_DATA_DIR="$PROJECT_DIR/data/minio"

# Colours for output
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ── Pre-flight checks ─────────────────────────────────────────

if ! command -v docker &>/dev/null; then
    error "docker is not installed or not on PATH."
    exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -q "^${MONGO_CONTAINER}$"; then
    error "MongoDB container '${MONGO_CONTAINER}' is not running."
    exit 1
fi

mkdir -p "$BACKUP_DIR"
info "Backup directory: $BACKUP_DIR"

# ── 1. MongoDB dump ───────────────────────────────────────────

MONGO_BACKUP_DIR="$BACKUP_DIR/mongo"
mkdir -p "$MONGO_BACKUP_DIR"

info "Dumping MongoDB database '${MONGO_DB}' from container '${MONGO_CONTAINER}'..."

docker exec "$MONGO_CONTAINER" mongodump \
    --username root \
    --password "$MONGO_PASSWORD" \
    --authenticationDatabase admin \
    --db "$MONGO_DB" \
    --out /tmp/mongodump \
    --quiet

info "Copying dump out of container..."
docker cp "$MONGO_CONTAINER":/tmp/mongodump/"$MONGO_DB" "$MONGO_BACKUP_DIR/"

# Clean up inside the container
docker exec "$MONGO_CONTAINER" rm -rf /tmp/mongodump

MONGO_SIZE=$(du -sh "$MONGO_BACKUP_DIR" 2>/dev/null | cut -f1)
info "MongoDB dump complete ($MONGO_SIZE)."

# ── 2. MinIO data (optional) ─────────────────────────────────

MINIO_BACKED_UP="no"
if [ -d "$MINIO_DATA_DIR" ]; then
    MINIO_SIZE_RAW=$(du -sm "$MINIO_DATA_DIR" 2>/dev/null | cut -f1)

    if [ "$MINIO_SIZE_RAW" -gt 0 ] 2>/dev/null; then
        read -r -p "Back up MinIO data directory (~${MINIO_SIZE_RAW}MB)? [y/N] " response </dev/tty || response="n"
        if [[ "$response" =~ ^[Yy]$ ]]; then
            MINIO_BACKUP_DIR="$BACKUP_DIR/minio"
            info "Copying MinIO data..."
            cp -a "$MINIO_DATA_DIR" "$MINIO_BACKUP_DIR"
            MINIO_BACKED_UP="yes"
            info "MinIO data copied (${MINIO_SIZE_RAW}MB)."
        else
            warn "Skipping MinIO backup."
        fi
    else
        warn "MinIO data directory is empty, skipping."
    fi
else
    warn "MinIO data directory not found at $MINIO_DATA_DIR, skipping."
fi

# ── Summary ───────────────────────────────────────────────────

TOTAL_SIZE=$(du -sh "$BACKUP_DIR" 2>/dev/null | cut -f1)

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
info "Backup complete"
echo "  Location:   $BACKUP_DIR"
echo "  Total size: $TOTAL_SIZE"
echo "  MongoDB:    $MONGO_BACKUP_DIR ($MONGO_SIZE)"
if [ "$MINIO_BACKED_UP" = "yes" ]; then
    echo "  MinIO:      $BACKUP_DIR/minio (${MINIO_SIZE_RAW}MB)"
else
    echo "  MinIO:      skipped"
fi
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
