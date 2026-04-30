# Changelog — `indexing-worker/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

(Entries land alongside the PRs that touch the spec.)

## [0.1.0] — 2026-04-30

### Added

- Initial contract for `gls-indexing-worker` — Phase 1.11 PR1.
  Greenfield deployable that consumes `document.classified` from
  RabbitMQ and writes searchable fields + `extractedMetadata` to
  Elasticsearch. Replaces the in-process `ElasticsearchIndexService`
  in `gls-app-assembly` (PR3 cuts callers over).
- **Async primary surface** — declared in
  `contracts/messaging/asyncapi.yaml` as the third consumer on
  `documentClassified` (alongside `ClassificationEnforcementConsumer`
  and `PipelineExecutionConsumer`). No new channel; reuses the
  existing event shape.
- **REST admin surface** for operators:
  - `POST /v1/index/{documentId}` — sync re-index a single document
    (admin escape hatch; idempotent via `nodeRunId`).
  - `DELETE /v1/index/{documentId}` — remove document from the index
    (e.g. after disposition).
  - `POST /v1/reindex` — kick off async bulk reindex; returns 202 +
    `Location: /v1/jobs/{nodeRunId}`.
  - `GET /v1/jobs/{nodeRunId}` — poll bulk reindex job state.
  - `GET /v1/capabilities`, `GET /actuator/health` — standard meta
    surface.
- RFC 7807 problem+json error envelope (CSV #17), service-account JWT
  (CSV #18), `traceparent` + `Idempotency-Key` headers (CSV #16 / #20),
  capability advertisement (CSV #21).
