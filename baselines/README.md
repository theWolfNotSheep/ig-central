---
title: Performance + Correctness Baselines
lifecycle: forward
---

# Performance + Correctness Baselines

Per-phase baseline captures of the metrics that matter as the v2 cutover progresses. Each capture lands as a row in a dated CSV under this directory; the **methodology** is below; the **capture script** lives in `scripts/baselines/capture.sh`.

This is the cross-cutting Track C from `version-2-implementation-plan.md` and the original Phase 0.11 work item.

## Status

**Phase 0.11 — scaffold landed; no real data captured yet.**

The methodology, CSV format, and capture script all exist. There's no representative production traffic in this codebase yet (solo dev project on local Docker Compose), so the seed CSV row is structural rather than substantive. Real captures land once a load-test workload (or replay against representative content) is wired up — see "Driving load" below.

## Why baseline at all

The v2 cutover replaces the existing Spring Boot monolith with a constellation of contract-driven microservices. Without a baseline:

- Latency regressions in the new pipeline aren't visible until end users complain.
- Throughput claims for the new architecture have nothing to compare against.
- "We made it faster" is unfalsifiable.

The baseline pins one set of numbers per phase. Subsequent phases re-run the script against the same workload to show how the numbers move.

## Metrics

| Column | What it means | Where it comes from |
|---|---|---|
| `date` | Capture date (YYYY-MM-DD). | The shell script. |
| `phase` | Plan phase the system was at when captured (e.g. `0-pre-cutover`, `1.5`). | Manual — operator sets it. |
| `workload` | Short identifier for the workload the script drove. `none` for scaffold rows. | Manual or script-emitted. |
| `sample_size` | Number of documents pushed through the pipeline. | Script. |
| `p50_classification_ms` / `p95` / `p99` | Latency percentiles for the document classification step. | Prometheus histogram on `pipeline_classification_duration_seconds` (or equivalent — see "Metric sources"). |
| `throughput_docs_per_min` | Documents-classified divided by wall-clock minutes of capture window. | Prometheus counter delta + duration. |
| `error_rate_pct` | `CLASSIFICATION_FAILED` documents / total processed × 100. | Mongo count + Prometheus, or pure Mongo. |
| `mcp_cache_hit_rate_pct` | Hits / (hits + misses) for the MCP server's tool-call caches. | Prometheus or actuator metrics on the MCP service. |
| `mongo_queries_per_doc` | Average Mongo queries per document classified. | Mongo profiler aggregate over the capture window divided by sample size. |
| `notes` | Free-form. Document any anomalies, infrastructure quirks, or load-driver tweaks. | Manual. |

CSV column order is fixed — `scripts/baselines/capture.sh` emits in this order. Extending the format means adding a new column at the **end** so older capture rows still parse.

## Capture cadence

Capture **before** and **after** every phase that ships pipeline work. At minimum:

- End of Phase 0 (pre-cutover baseline against the existing monolith).
- End of Phase 1 (first per-service split — extraction).
- End of Phase 2 (classifier-router).
- End of Phase 3 (governance enforcement).

Out-of-cycle captures are welcome — append a row, set the `phase` column to whatever's accurate, write a sentence in `notes`.

## Methodology

1. Boot the local stack — `docker compose up --build -d`. Confirm `prometheus` and the api are healthy.
2. Drive the workload — `scripts/baselines/capture.sh` invokes the load driver. Today the load driver is a stub that does **no work**; once a real workload is wired (see "Driving load"), the script will push `sample_size` documents through `/api/documents/upload` and wait for them to reach `CLASSIFIED` (or `CLASSIFICATION_FAILED`).
3. Sample the metrics — script issues PromQL queries to `http://localhost:9090` and Mongo aggregations against `ig-central-mongo`.
4. Append a row to `baselines/<YYYY-MM>-baseline.csv` (one CSV per month; new captures append rows; never edit historical rows).
5. Commit the CSV change with a sentence describing the workload.

## Metric sources

- **Spring Boot actuator + Micrometer Prometheus** — already wired. The api exposes `/actuator/prometheus` in the `docker` profile (see `igc-app-assembly/src/main/resources/application-docker.yaml`). Prometheus container scrapes it per `monitoring/prometheus.yml`.
- **Mongo `audit_outbox` row counts** — give us classification success / failure tallies via `outcome` aggregation.
- **MCP server actuator** — exposed at `/actuator/prometheus` in docker profile (same pattern as api).
- **Mongo profiler** — enable temporarily during a capture window to measure per-doc query counts.

## Driving load (deferred until workload exists)

Today there is **no representative document corpus** in the repo. The capture script's `drive_load` function is a stub. Plans:

1. Pick a representative sample of documents (size to be decided — order of 100s, mixed mime types).
2. Add a small loader script (likely `scripts/baselines/load.sh` or a Python equivalent) that posts files to `/api/documents/upload` at a configurable rate.
3. Once the loader exists, `capture.sh` invokes it, then samples Prometheus over the capture window.

Until then, the script can be run end-to-end but the metrics will reflect whatever idle / hand-driven traffic happens to be flowing.

## Reading historical CSVs

Each row is a snapshot in time and **must not be edited**. If a capture is later found to be wrong (broken metric, wrong workload), append a new row with the correction and reference the original date in `notes`. The append-only log here matches the rule for `version-2-implementation-log.md`.
