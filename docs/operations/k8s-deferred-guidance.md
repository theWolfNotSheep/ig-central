---
title: K8s adoption — deferred guidance
lifecycle: forward
status: deferred
---

# Kubernetes adoption — deferred guidance

**Status:** Deferred per CSV #38. v2 ships on Docker Compose. This document is the brief for whoever picks K8s up later.

## Why deferred

- v1 infra is already Compose-based (Cloudflare tunnel + nginx + Mongo + RabbitMQ + Elasticsearch + MinIO). Working, observable, debuggable.
- v2 introduces more services but no horizontal-scaling driver: traffic and tenancy don't change. The router cascade exists precisely so most documents resolve at BERT/SLM tier without elastic LLM capacity.
- K8s adds operational surface (RBAC, ingress controllers, secret managers, network policies, image registry, namespace strategy, HPA tuning) for benefits that aren't load-bearing yet.
- The seam is preserved so adoption is a deployment swap, not a refactor.

## Trigger conditions — when to revisit

Adopt K8s when **any** of these become true:

1. **Multi-tenant deployment** — separate orgs need network isolation, separate quotas, separate audit chains. Compose's flat networking stops being acceptable.
2. **Burst-scale ingestion** — sustained spikes that need sub-minute auto-scaling of `gls-extraction-*`, `gls-classifier-router`, or `gls-llm-worker`. Today's bottleneck is upstream LLM rate limits, not local compute.
3. **HA SLA tightens past Compose's reach** — single-host failover is no longer enough; we need rolling restarts with zero dropped Rabbit consumers and per-service liveness/readiness gates that route traffic away from unhealthy replicas mid-flight.
4. **Compliance demands network policies** — a Tier 1 audit collector that *cannot* be reached from anything outside its allow-list, enforced at the cluster level rather than at the application layer.
5. **The fleet exceeds ~12 long-running containers** — Compose stays sane; orchestration overhead grows. Empirically, between 12 and 20 services is where the operator-burden math flips.

If none of these are true, stay on Compose. The overhead is real and the rollback story from K8s back to Compose is messy.

## What is already in place (the seam)

Per Phase 0.5.5 of the implementation plan, the reference service `gls-extraction-tika` ships with **shell-only** K8s manifests:

- `Deployment` — image, port, command. **Missing:** resource requests/limits, replica count, anti-affinity rules, security context.
- `Service` — ClusterIP exposing the HTTP port. **Missing:** annotations for ingress, headless-service decisions for stateful peers.
- `HorizontalPodAutoscaler` — structure only, no metrics wired. **Missing:** target metrics (CPU, in-flight requests, queue depth), min/max replicas.

These exist as syntactic placeholders so a later PR can fill values without inventing structure. Re-use the same pattern for every new service.

## Adoption checklist (when the trigger fires)

### Cluster prerequisites

- [ ] Choose distribution: managed (GKE, EKS, AKS) vs self-hosted (k3s for small deployments). For a single-deployment ig-central, k3s on a small node pool is likely sufficient.
- [ ] Image registry: GHCR is already used in CI per Phase 0.5. Confirm pull-secret strategy (registry credentials vs IAM-bound nodes).
- [ ] Namespace strategy: `gls-prod`, `gls-staging`, `gls-dev` minimum. Consider per-component namespaces only if RBAC granularity requires it.
- [ ] Ingress: replace nginx + Cloudflare tunnel with an in-cluster ingress controller (Traefik / nginx-ingress) plus cert-manager for TLS. Cloudflare tunnel can stay as the external entrypoint.

### Service-level work (per deployable)

- [ ] Fill `resources.requests` and `resources.limits` from baseline (Phase 0.11 perf data).
- [ ] Set `replicas` from observed concurrency.
- [ ] Wire HPA metrics — start with CPU; layer custom metrics (Rabbit consumer lag, in-flight HTTP) via Prometheus Adapter once the metric surface exists.
- [ ] Configure liveness probe (process alive) vs readiness probe (dependencies reachable, model loaded) — these are *different* per CSV #21 / `GET /v1/capabilities`.
- [ ] Pod anti-affinity for any Class D singleton (`gls-audit-collector` Tier 1, `gls-scheduler`).
- [ ] `PodDisruptionBudget` for every horizontally-scalable service.

### Cross-cutting work

- [ ] **Secrets:** migrate `.env`-style config to a secret manager — Sealed Secrets, External Secrets Operator, or HashiCorp Vault. Mongo credentials, JWT signing keys, Anthropic API key, OAuth client secrets all need to move.
- [ ] **ConfigMaps** for non-secret config; mount as files where possible to reuse the existing config-loading code.
- [ ] **Persistent volumes** for Mongo, Elasticsearch, MinIO (or move them to managed services — RDS-equivalent for Mongo, Elastic Cloud, S3).
- [ ] **NetworkPolicies** — deny-all default, then allow the actual call graph. The implementation plan already has the call graph documented in `version-2-architecture.md` §3 Container topology.
- [ ] **ServiceAccount per deployable** — JWTs already carry service identity per CSV #18 (`/§11.A.6`); the K8s ServiceAccount is the issuer-side counterpart.
- [ ] **RBAC:** rules per ServiceAccount; default-deny on cluster resources; namespace-scoped where possible.
- [ ] **Observability:** swap Compose-mounted Prometheus / OTel for in-cluster equivalents (kube-prometheus-stack, OpenTelemetry Operator). Collector endpoints become DNS names inside the cluster.

### Migration sequence (suggested)

1. Stand up a parallel K8s cluster with **stateless** services only (`gls-extraction-*`, workers). Stateful stores (Mongo, ES, MinIO, Rabbit) stay where they are; cluster reaches them via internal DNS or `ExternalName` services.
2. Cut traffic over per service using the existing rollback flag (`pipeline.<service>.enabled` pattern from Phase 1 cutover plan).
3. Once stateless services are stable, migrate Rabbit (managed CloudAMQP or in-cluster operator), then Mongo (managed Atlas or Percona operator), then ES (Elastic Cloud or ECK), then MinIO (operator or replace with S3).
4. Decommission Compose path once parity is confirmed for ≥ 30 days.

## What NOT to do

- **Don't migrate stateful stores in the same PR as the application services.** Mongo/Rabbit/ES migrations are their own multi-week projects.
- **Don't ship `latest` tags.** Image tags must be the same VERSION values from `contracts/<service>/VERSION` per Phase 0.9 BOM-decoupling work.
- **Don't introduce a service mesh in the same migration.** mTLS is deferred per CSV #18; revisit when the JWT-only path proves insufficient (specifically: when service-to-service auth needs to be enforced at the network layer, not the application layer).
- **Don't drop the Compose path without a 30-day parallel run.** Treat Compose as the rollback target.

## Open questions to revisit at adoption time

- **Ingress strategy with Cloudflare tunnel:** does the tunnel keep terminating outside the cluster, or move into a `cloudflared` Deployment? Both work; preference depends on whether the cluster sits behind a dedicated VPN.
- **Audit Tier 1 storage:** if CSV #3 settles on S3 Object Lock, the cluster needs IAM-bound node identity (IRSA on EKS, Workload Identity on GKE). If it's Mongo with role-based deny, no extra cluster work.
- **GPU pool for `gls-bert-inference`:** CSV #12 is OPEN. CPU sufficient for ModernBERT-base at expected QPS. If GPUs land, that's a separate node-pool taint/toleration decision.
- **Per-environment branching strategy:** Helm with overlays vs Kustomize vs Argo CD application sets. Defer until the cluster is real.

## Cross-references

- `version-2-implementation-plan.md` — Phase 0.5.5 (shell manifests in reference service), Phase 1 cutover flags.
- `version-2-architecture.md` §3 — container topology, scaling profile by class, HPA signals.
- `version-2-decision-tree.csv` #38 — the deferral itself.
- `CLAUDE.md` — Build & Run section (current Compose-based path).
