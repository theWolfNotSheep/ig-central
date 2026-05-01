# AI-Powered Features: Taxonomy, Metadata & Hub Intelligence

## Overview

Five interconnected AI features that close the feedback loop between document classification in IG Central and governance pack authoring in the Hub.

```
IG Central (tenant)                          Hub (marketplace)
+---------------------------------+          +----------------------------------+
| 1. Taxonomy Gap Detection       |          | 3. Pack Authoring Assistant       |
|    - Low-confidence analysis    |          |    - Generate taxonomy from desc  |
|    - Correction pattern mining  |          |    - Auto-create schemas          |
|    - Suggests new categories    |          |    - Per-tab AI generation        |
|                                 |          |                                  |
| 2. Bulk Schema Generation       |   --->   | 5. Cross-Tenant Intelligence     |
|    - Sample docs from category  |  feedback|    - Pack quality scoring         |
|    - LLM identifies fields      |  pipeline|    - Improvement suggestions      |
|    - Frequency-based required   |          |    - Aggregated correction data   |
|                                 |          |                                  |
| 4. Feedback Pipeline            |          |    Feedback Tab (per pack)        |
|    - Admin-approved sharing     |          |    - Tenant correction patterns   |
|    - Anonymised data only       |          |    - Quality metrics              |
+---------------------------------+          +----------------------------------+
```

---

## Feature 1: AI Taxonomy Gap Detection

**Location:** IG Central governance page, Taxonomy tab

**Endpoint:** `POST /api/admin/governance/ai/taxonomy-gaps`

**Request:**
```json
{
  "confidenceThreshold": 0.7,
  "minCorrectionCount": 2
}
```

**How it works:**
1. Queries `classification_corrections` where `correctionType = CATEGORY_CHANGED`
2. Queries `classification_results` where `confidence < threshold`
3. Builds the current taxonomy tree via `GovernanceService.getTaxonomyAsText()`
4. Sends correction patterns + low-confidence stats + full taxonomy to LLM
5. LLM suggests new ISO 15489 categories (FUNCTION/ACTIVITY/TRANSACTION) with evidence
6. Also suggests metadata schemas for the proposed categories

**Response:** `TaxonomyGapReport` with `suggestedCategories[]`, `suggestedSchemas[]`, `analysisSummary`

**UI:** Collapsible "AI Taxonomy Gap Analysis" panel at the top of the Taxonomy tab. Each suggestion has an "Add to Taxonomy" button that pre-fills the category form.

**Files:**
- `backend/igc-app-assembly/.../services/ai/TaxonomyGapService.java`
- `backend/igc-app-assembly/.../services/ai/TaxonomyGapReport.java`
- `web/src/components/taxonomy-gap-panel.tsx`

---

## Feature 2: Bulk Metadata Schema Generation

**Location:** IG Central governance page, Metadata Schemas tab

**Endpoint:** `POST /api/admin/governance/ai/bulk-schema-suggest`

**Request:**
```json
{
  "categoryId": "60f1...",
  "sampleSize": 20
}
```

**How it works:**
1. Samples N documents classified into the target category
2. Extracts first 3000 chars of text from each
3. Includes existing schema (if any) for improvement context
4. Sends all text excerpts to LLM
5. LLM identifies common fields with `frequencyPercent` (how many docs had each field)
6. Fields with >80% frequency marked as `required`

**Response:** `BulkSchemaResult` with `fields[]` including `frequencyPercent`, `extractionHint`, `examples`

**UI:** "Generate from Documents" button opens a modal with category picker, sample size slider, and field selection. Admin reviews and deselects irrelevant fields before creating the schema.

**Files:**
- `backend/igc-app-assembly/.../services/ai/BulkSchemaGenerationService.java`
- `backend/igc-app-assembly/.../services/ai/BulkSchemaResult.java`
- `web/src/components/bulk-schema-modal.tsx`

---

## Feature 3: Hub Pack Authoring Assistant

**Location:** Hub admin, pack editor

**Architecture:** Uses JDK `HttpClient` for LLM calls (no Spring AI dependency). Zero new Maven dependencies.

**Endpoints:**
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/hub/admin/ai/status` | Check if AI is configured |
| POST | `/api/hub/admin/ai/generate-pack` | Generate full pack from description |
| POST | `/api/hub/admin/ai/generate-component` | Generate single component type |
| POST | `/api/hub/admin/ai/improve` | Refine data with natural language instruction |

**Configuration** (`application.yaml`):
```yaml
hub:
  ai:
    provider: ollama  # or anthropic
    ollama:
      base-url: http://host.docker.internal:11434
      model: qwen2.5:32b
    anthropic:
      api-key: ${HUB_AI_ANTHROPIC_API_KEY:}
      model: claude-sonnet-4-20250514
```

**UI components:**
- **AI Assistant Panel** — floating panel in pack editor header. Describe pack scope, select components, generate full pack.
- **AI Generate Button** — per-tab sparkle button on Taxonomy, Metadata, Retention, Sensitivity, Policies tabs. Generates additional items for that component type.

**Files:**
- `backend/igc-governance-hub-app/.../services/HubLlmService.java`
- `backend/igc-governance-hub-app/.../controllers/AiAssistantController.java`
- `backend/igc-governance-hub/.../models/HubAiUsageLog.java`
- `web-hub/app/packs/[id]/_components/AiAssistantPanel.tsx`
- `web-hub/app/packs/[id]/_components/AiGenerateButton.tsx`

---

## Feature 4: Central to Hub Feedback Pipeline

**Location:** IG Central governance hub page + Hub admin pack detail

**Flow:**
```
Central admin clicks "Share Feedback" for an installed pack
  -> PackFeedbackService aggregates corrections per pack
  -> Admin previews anonymised data (preview endpoint)
  -> Admin confirms sharing
  -> GovernanceHubClient POSTs to Hub
  -> Hub stores PackFeedback record
  -> Pack authors see feedback on their packs
```

**Central endpoints:**
| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/admin/governance/feedback/preview` | Preview what would be shared |
| POST | `/api/admin/governance/feedback/share` | Send to Hub (admin approval) |

**Hub endpoints:**
| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/hub/packs/{slug}/feedback` | Receive feedback (API-key auth) |
| GET | `/api/hub/admin/packs/{slug}/feedback` | View feedback (admin auth) |

**Anonymisation guarantees:**
- No document text, file names, or user names are ever sent
- `tenantId` is a hash of the API key (not org name)
- Only statistical patterns: correction counts, category pairs, confidence averages

**Files:**
- `backend/igc-app-assembly/.../services/PackFeedbackService.java`
- `backend/igc-app-assembly/.../services/ai/PackFeedbackSummary.java`
- `backend/igc-governance-hub/.../models/PackFeedback.java`
- `backend/igc-governance-hub-app/.../controllers/PackFeedbackController.java`

---

## Feature 5: Cross-Tenant Intelligence

**Location:** Hub admin, pack detail (Feedback tab)

The Hub aggregates `PackFeedback` from multiple tenants to surface:
- **Pack quality scoring** — correction rate, average confidence, trend across versions
- **Improvement suggestions** — most common reclassifications indicate taxonomy gaps
- **Tenant engagement** — how many tenants are actively using and correcting the pack

The `PackFeedbackController.getFeedback()` endpoint returns all feedback for a pack, sorted by date. Hub frontend displays this as a Feedback tab in the pack editor.

---

## Shared Infrastructure

### AiService (Central)
Reusable LLM call service extracted from `GovernanceAdminController`. Supports Anthropic and Ollama with automatic `AiUsageLog` logging.

**File:** `backend/igc-app-assembly/.../services/ai/AiService.java`

```java
@Service
public class AiService {
    public String callLlm(String usageType, String triggeredBy, String prompt)
    public String extractJson(String response)  // strips markdown fences
    public String getProvider()
    public String getModel(String provider)
}
```

### HubLlmService (Hub)
Identical pattern using JDK HttpClient. Logs to `hub_ai_usage_log` collection.

### AI Usage Logging
All AI calls in both Central and Hub are logged with:
- Usage type, provider, model
- Prompt (truncated to 2000 chars), response (truncated to 5000 chars)
- Duration, status, error message

---

## API Reference Summary

### Central AI Endpoints
| Method | Path | Feature |
|--------|------|---------|
| POST | `/api/admin/governance/ai/taxonomy-gaps` | Taxonomy gap analysis |
| POST | `/api/admin/governance/ai/bulk-schema-suggest` | Bulk schema generation |
| POST | `/api/admin/governance/feedback/preview` | Preview feedback |
| POST | `/api/admin/governance/feedback/share` | Share feedback with Hub |

### Hub AI Endpoints
| Method | Path | Feature |
|--------|------|---------|
| GET | `/api/hub/admin/ai/status` | AI configuration status |
| POST | `/api/hub/admin/ai/generate-pack` | Full pack generation |
| POST | `/api/hub/admin/ai/generate-component` | Single component generation |
| POST | `/api/hub/admin/ai/improve` | Refine component with instruction |
| POST | `/api/hub/packs/{slug}/feedback` | Receive tenant feedback |
| GET | `/api/hub/admin/packs/{slug}/feedback` | View pack feedback |
