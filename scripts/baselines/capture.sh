#!/usr/bin/env bash
#
# scripts/baselines/capture.sh — Phase 0.11 baseline capture script.
#
# What this does today:
#   1. Sanity-checks that the local stack is up.
#   2. Stubs the workload driver (drive_load) — no documents are pushed yet.
#   3. Samples the metrics it CAN sample without a workload (as a smoke).
#   4. Emits a single CSV row to stdout in the format documented in
#      baselines/README.md.
#
# What this does NOT do yet (the gap to "real" baselines):
#   - drive_load is a no-op. Wire it once representative content exists.
#   - PromQL queries are placeholders that may or may not match the metric
#     names emitted by the api today; verify by running
#     `curl -s localhost:9090/api/v1/label/__name__/values | jq` after a
#     real capture window to discover the actual metric names.
#
# Usage:
#   PHASE=0-pre-cutover WORKLOAD=mixed-100 SAMPLE_SIZE=100 \
#     scripts/baselines/capture.sh >> baselines/2026-04-baseline.csv
#
# Environment variables:
#   PHASE         Plan phase (e.g. "0-pre-cutover", "1.5"). Default: "unset".
#   WORKLOAD      Workload identifier; "none" if no driver is wired. Default: "none".
#   SAMPLE_SIZE   Number of documents the driver should push. Default: 0.
#   PROM_URL      Prometheus base URL. Default: http://localhost:9090.
#   API_URL       api base URL. Default: http://localhost:8080.

set -euo pipefail

PHASE="${PHASE:-unset}"
WORKLOAD="${WORKLOAD:-none}"
SAMPLE_SIZE="${SAMPLE_SIZE:-0}"
PROM_URL="${PROM_URL:-http://localhost:9090}"
API_URL="${API_URL:-http://localhost:8080}"

today() { date -u +%Y-%m-%d; }

log() { echo "[capture] $*" >&2; }

check_stack() {
  log "checking api at ${API_URL}/actuator/health ..."
  if ! curl -sf "${API_URL}/actuator/health" >/dev/null; then
    log "api not reachable. boot it first: docker compose up -d"
    exit 1
  fi
  log "checking prometheus at ${PROM_URL}/-/ready ..."
  if ! curl -sf "${PROM_URL}/-/ready" >/dev/null; then
    log "prometheus not reachable at ${PROM_URL}. ensure docker compose is up."
    exit 1
  fi
}

# Stub. Replace with a real loader (scripts/baselines/load.sh or similar)
# once representative content lives in the repo. See baselines/README.md
# "Driving load".
drive_load() {
  if [ "${SAMPLE_SIZE}" -gt 0 ]; then
    log "WARN: SAMPLE_SIZE=${SAMPLE_SIZE} but drive_load is a stub. Wire a real loader before relying on the captured numbers."
  fi
}

# Returns "" when the metric is missing (PromQL returns empty result).
prom_query() {
  local query="$1"
  curl -sf --get \
    --data-urlencode "query=${query}" \
    "${PROM_URL}/api/v1/query" 2>/dev/null \
    | python3 -c 'import sys, json; d=json.load(sys.stdin); r=d.get("data",{}).get("result",[]); print(r[0]["value"][1] if r else "")' \
    2>/dev/null || echo ""
}

# Placeholder histogram queries. Real metric names depend on what
# Micrometer is recording — verify against the live registry before trusting.
sample_latency_p50_ms() { prom_query 'histogram_quantile(0.50, sum(rate(pipeline_classification_duration_seconds_bucket[5m])) by (le)) * 1000'; }
sample_latency_p95_ms() { prom_query 'histogram_quantile(0.95, sum(rate(pipeline_classification_duration_seconds_bucket[5m])) by (le)) * 1000'; }
sample_latency_p99_ms() { prom_query 'histogram_quantile(0.99, sum(rate(pipeline_classification_duration_seconds_bucket[5m])) by (le)) * 1000'; }
sample_throughput_per_min() { prom_query 'sum(rate(pipeline_classification_total[5m])) * 60'; }
sample_error_rate_pct() { prom_query 'sum(rate(pipeline_classification_total{outcome="failure"}[5m])) / sum(rate(pipeline_classification_total[5m])) * 100'; }
sample_mcp_cache_hit_rate_pct() { prom_query 'sum(rate(mcp_cache_hits_total[5m])) / (sum(rate(mcp_cache_hits_total[5m])) + sum(rate(mcp_cache_misses_total[5m]))) * 100'; }
sample_mongo_queries_per_doc() { prom_query 'sum(rate(mongodb_driver_commands_seconds_count[5m])) / sum(rate(pipeline_classification_total[5m]))'; }

main() {
  check_stack
  drive_load

  local p50 p95 p99 throughput error_rate mcp_hit_rate mongo_qpd
  p50="$(sample_latency_p50_ms)"
  p95="$(sample_latency_p95_ms)"
  p99="$(sample_latency_p99_ms)"
  throughput="$(sample_throughput_per_min)"
  error_rate="$(sample_error_rate_pct)"
  mcp_hit_rate="$(sample_mcp_cache_hit_rate_pct)"
  mongo_qpd="$(sample_mongo_queries_per_doc)"

  local notes="captured by scripts/baselines/capture.sh (stub load driver)"

  printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
    "$(today)" \
    "${PHASE}" \
    "${WORKLOAD}" \
    "${SAMPLE_SIZE}" \
    "${p50}" \
    "${p95}" \
    "${p99}" \
    "${throughput}" \
    "${error_rate}" \
    "${mcp_hit_rate}" \
    "${mongo_qpd}" \
    "${notes}"
}

main "$@"
