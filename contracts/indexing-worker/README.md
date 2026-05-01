---
title: indexing-worker
lifecycle: forward
---

# `igc-indexing-worker` contract

The post-classification indexing stage of the pipeline. Today this
logic lives inside `igc-app-assembly` as `ElasticsearchIndexService`,
called in-process from `DocumentController` / `MonitoringController`
/ `PipelineWebhookController`. Phase 1.11 carves it out into a
standalone deployable that consumes `document.classified` from
RabbitMQ and writes searchable fields + `extractedMetadata` to
Elasticsearch.

## Surface

**Async (primary).** Subscribes to `document.classified` on the
`igc.documents` exchange. See `contracts/messaging/asyncapi.yaml`
operation `consumeDocumentClassified` — the worker is the third
consumer alongside `ClassificationEnforcementConsumer` and
`PipelineExecutionConsumer`. The Rabbit message envelope is the
existing `DocumentClassifiedEvent`; no new channel is introduced.

**REST (admin escape hatch + ops).**
- `POST /v1/index/{documentId}` — sync re-index a single document.
  `nodeRunId`-keyed idempotency for 24h.
- `DELETE /v1/index/{documentId}` — remove document from the index
  (e.g. after disposition).
- `POST /v1/reindex` — kick off async bulk reindex; returns 202 +
  `Location: /v1/jobs/{nodeRunId}`.
- `GET /v1/jobs/{nodeRunId}` — poll an async job (currently bulk
  reindex; future: bulk delete).
- `GET /v1/capabilities`, `GET /actuator/health`.

## What indexing does

For each classified document:

- Resolve the document by id from Mongo (canonical record);
- Build the Elasticsearch document body — searchable fields plus the
  flattened `extractedMetadata` map;
- `PUT /{indexName}/_doc/{documentId}` — idempotent upsert into the
  configured index;
- On mapping conflict (4xx with `mapper_parsing_exception` or
  similar), park the document in a `index_quarantine` Mongo
  collection for admin review;
- On transport failure / 5xx, set `INDEX_FAILED` status on the
  document and surface for retry from the admin monitoring page.

## Dependencies

- Mongo (read the canonical `DocumentModel` by id; write
  `index_quarantine` rows on mapping conflict).
- Elasticsearch (write target).
- RabbitMQ (consume `document.classified`; emit `INDEX_FAILED` audit
  via `igc-platform-audit` once that lands).

## Versioning

Per the API Contracts rule in `CLAUDE.md`. `VERSION` bumps on every
spec change; major bumps for breaking changes; `CHANGELOG.md` records
each version.
