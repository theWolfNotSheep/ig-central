# How n8n Makes Workflows User-Configurable — and What GLS Should Adopt

> A technical breakdown of the patterns that make n8n feel "easy" for non-developers, mapped to concrete implementation for GLS.

---

## The One Insight That Matters

n8n's entire UI is generated from a single JSON description per node. The node author writes a `description` object. The frontend reads that object and renders the form. There is no frontend code per node type. Zero. None.

This is the pattern to replicate.

---

## Pattern 1: Description-Driven Forms

### How n8n does it

Every node has a `description.properties` array — a declarative specification of what the user should configure. The frontend renders it into a form automatically.

```typescript
// This is ALL a node author writes to create a configurable node:
{
  displayName: 'HTTP Method',
  name: 'method',
  type: 'options',
  default: 'GET',
  options: [
    { name: 'GET', value: 'GET' },
    { name: 'POST', value: 'POST' },
    { name: 'PUT', value: 'PUT' },
    { name: 'DELETE', value: 'DELETE' },
  ],
},
{
  displayName: 'URL',
  name: 'url',
  type: 'string',
  default: '',
  placeholder: 'https://api.example.com/endpoint',
  required: true,
},
{
  displayName: 'Body',
  name: 'body',
  type: 'json',
  default: '{}',
  // THIS IS THE KEY — only show when method is POST or PUT:
  displayOptions: {
    show: { method: ['POST', 'PUT'] },
  },
},
```

The frontend receives this JSON and renders:
- A dropdown for `method`
- A text input for `url`
- A JSON editor for `body` — but ONLY when method is POST or PUT

No React component was written for this node. The form is entirely data-driven.

### What GLS has today

We already have `NodeTypeDefinition` with a `configSchema` field that drives `DynamicConfigForm`. Our schema supports `text`, `number`, `range`, `select`, `checkbox`, `readonly`, `textarea`, `password` widgets. We also have custom widget escape hatches (`custom:rulesEditor`, `custom:modelSelect`).

**What's missing vs n8n:**

| n8n Feature | GLS Status | Gap |
|-------------|-----------|-----|
| Conditional field visibility (`displayOptions`) | Not implemented | **Critical** — this is what makes complex nodes feel simple |
| Dynamic option loading (`loadOptionsMethod`) | Not implemented | Important for dropdowns populated from DB |
| Collection parameters (expandable field groups) | Not implemented | Needed for "Add condition", "Add header" patterns |
| Fixed collection (repeatable row groups) | Partial (`custom:rulesEditor`) | Should be generic, not per-widget |
| Expression toggle per field | Not implemented | Needed for dynamic values (`{{doc.fileName}}`) |
| Multi-value parameters (arrays) | Not implemented | Needed for tag lists, multiple assignees |
| Parameter validation (`validateType`) | Not implemented | Frontend validation before save |

### What to build

**Step 1: Add `displayOptions` to `NodeProperty` schema (~2 days)**

Extend the `configSchema` in `NodeTypeDefinition` to support conditional visibility:

```json
{
  "configSchema": {
    "method": {
      "type": "select",
      "label": "HTTP Method",
      "default": "GET",
      "options": [
        { "label": "GET", "value": "GET" },
        { "label": "POST", "value": "POST" }
      ]
    },
    "body": {
      "type": "json",
      "label": "Request Body",
      "default": "{}",
      "displayOptions": {
        "show": { "method": ["POST", "PUT"] }
      }
    },
    "retryCount": {
      "type": "number",
      "label": "Retry Count",
      "default": 3,
      "displayOptions": {
        "show": { "enableRetry": [true] }
      }
    }
  }
}
```

Frontend change in `DynamicConfigForm`: before rendering each field, evaluate its `displayOptions` against the current form values. If the condition isn't met, hide the field.

```tsx
function shouldShow(field: ConfigField, currentValues: Record<string, any>): boolean {
  if (!field.displayOptions) return true;
  
  const { show, hide } = field.displayOptions;
  
  if (show) {
    return Object.entries(show).every(([key, allowedValues]) =>
      allowedValues.includes(currentValues[key])
    );
  }
  if (hide) {
    return Object.entries(hide).every(([key, hiddenValues]) =>
      !hiddenValues.includes(currentValues[key])
    );
  }
  return true;
}
```

This single feature transforms complex node configuration from "a wall of 20 fields" to "3 relevant fields that expand as you make choices."

**Step 2: Add `collection` and `fixedCollection` parameter types (~3 days)**

`collection` = expandable optional fields (user clicks "Add Option" to reveal fields):
```json
{
  "additionalOptions": {
    "type": "collection",
    "label": "Additional Options",
    "fields": {
      "timeout": { "type": "number", "label": "Timeout (ms)", "default": 30000 },
      "followRedirects": { "type": "checkbox", "label": "Follow Redirects", "default": true },
      "proxy": { "type": "text", "label": "Proxy URL" }
    }
  }
}
```

Renders as a collapsed section with an "Add Option" button. User picks which fields they care about. Fields they don't add stay at defaults.

`fixedCollection` = repeatable row groups (user clicks "Add Row" to add another set of fields):
```json
{
  "conditions": {
    "type": "fixedCollection",
    "label": "Conditions",
    "fields": {
      "field": { "type": "select", "label": "Field", "options": [...] },
      "operator": { "type": "select", "label": "Operator", "options": [...] },
      "value": { "type": "text", "label": "Value" }
    },
    "typeOptions": { "multipleValues": true, "sortable": true }
  }
}
```

Renders as a list of condition rows with add/remove/reorder buttons. This replaces the custom `rulesEditor` widget with a generic component.

**Step 3: Add `loadOptionsMethod` for dynamic dropdowns (~1 day)**

```json
{
  "categoryId": {
    "type": "select",
    "label": "Category",
    "loadOptionsMethod": "getCategories"
  }
}
```

The frontend calls `GET /api/admin/node-types/{nodeType}/options/{methodName}` which dispatches to a registered method on the backend. Returns `[{label, value}]`. This eliminates hardcoded option lists.

---

## Pattern 2: The Resource/Operation Pattern

### How n8n does it

Almost every API node follows the same two-dropdown pattern:

```
┌─────────────────────┐
│ Resource: [Message ▾]│  ← What type of thing are you working with?
│ Operation: [Send   ▾]│  ← What do you want to do with it?
│                      │
│ Channel: [#general ▾]│  ← Fields specific to Message + Send
│ Text: [Hello world  ]│
└─────────────────────┘
```

All 400+ API integrations use this pattern. Users learn it once and can configure any node.

### How GLS should apply it

Our node types already have categories (TRIGGER, PROCESSING, ACCELERATOR, LOGIC, ACTION, ERROR_HANDLING), but within each node, the configuration is flat. We should adopt the resource/operation pattern for complex nodes:

**Example: The `aiClassification` node**

Current config: flat list of fields (provider, model, temperature, maxTokens, systemPrompt, blockId, blockVersion, injectTaxonomy, injectSensitivities, injectTraits...).

With resource/operation pattern:
```
┌──────────────────────────────────────────┐
│ Provider:  [Anthropic  ▾]                │
│ Model:     [Claude Sonnet ▾]             │  ← loaded dynamically
│                                          │
│ ▸ Prompt Configuration                   │  ← collapsed collection
│   Block: [Classification Prompt v3 ▾]    │
│   Temperature: [0.1]                     │
│                                          │
│ ▸ Context Injection                      │  ← collapsed collection
│   ☑ Inject Taxonomy                      │
│   ☑ Inject Sensitivities                 │
│   ☐ Inject Traits                        │
│   ☐ Inject PII Types                     │
│                                          │
│ ▸ Advanced                               │  ← collapsed collection
│   Max Tokens: [4096]                     │
│   Timeout: [120s]                        │
└──────────────────────────────────────────┘
```

When provider changes to "Ollama", the model dropdown reloads with local models. The "Context Injection" section might hide if the prompt mode is "Custom" instead of "Classification". All driven by `displayOptions`.

---

## Pattern 3: Items-Based Data Flow

### How n8n does it

Data flows between nodes as **items** — an array of JSON objects. Each node receives items from the previous node and produces items for the next node. This is how n8n handles both single documents and batch processing with the same visual graph.

```
[Trigger] → items: [{json: {file: "doc1.pdf"}}, {json: {file: "doc2.pdf"}}]
     ↓
[Extract] → items: [{json: {file: "doc1.pdf", text: "..."}}, {json: {file: "doc2.pdf", text: "..."}}]
     ↓
[IF confidence > 0.8]
     ↓ true                          ↓ false
items: [{json: {file: "doc1.pdf"}}]  items: [{json: {file: "doc2.pdf"}}]
     ↓                                ↓
[Auto-approve]                       [Human Review]
```

The IF node receives 2 items, evaluates the condition on each, and routes them to different outputs. Item-level branching, not pipeline-level branching.

### How GLS differs

GLS processes **one document per pipeline run**. Our `PipelineRun` tracks a single `documentId`. Nodes pass data via `sharedContext` (a mutable map) rather than structured items.

### What to adopt (and what to skip)

**Don't adopt full items-based processing** — our domain is "classify one document at a time." Batch processing is handled at the ingestion level (RabbitMQ queue with one message per document), not at the pipeline graph level. Adopting n8n's items model would add complexity without benefit.

**Do adopt structured node output** — replace the untyped `sharedContext` map with typed `NodeOutput` objects:

```java
public record NodeOutput(
    String nodeKey,
    Map<String, Object> data,      // node-specific output
    Instant completedAt,
    long durationMs
) {}

// After textExtraction:
NodeOutput {
  nodeKey: "extract_1",
  data: { "wordCount": 3200, "pageCount": 8, "hasHeadings": true, "language": "en" }
}

// After bertClassifier:
NodeOutput {
  nodeKey: "bert_1", 
  data: { "topCategory": "HR.ER.LR", "confidence": 0.87, "top3": [...] }
}
```

Expressions can then reference: `#node['bert_1'].data['confidence'] > 0.8`

---

## Pattern 4: Expression Toggle Per Field

### How n8n does it

Every parameter field has a small toggle icon. Click it to switch from "literal value" to "expression mode." In expression mode, the field becomes a code editor with autocomplete:

```
Literal mode:   [Hello World          ]
                                    ⚡ ← click to toggle

Expression mode: [={{ $json.greeting }}]  ← with autocomplete
```

This means ANY field can be dynamic. The URL field, the body field, the timeout — anything. Users start with literals and upgrade to expressions only when they need dynamic behaviour.

### What GLS should adopt

Add an expression toggle to `DynamicConfigForm` fields. When toggled, the text input becomes a SpEL expression editor with syntax highlighting and autocomplete for available variables (`#doc.fileName`, `#classification.confidence`, `#pipeline.bertTopCategory`).

```tsx
function ParameterField({ field, value, onChange }) {
  const [expressionMode, setExpressionMode] = useState(
    typeof value === 'string' && value.startsWith('=${')
  );

  if (expressionMode) {
    return (
      <div className="flex items-center gap-2">
        <ExpressionEditor 
          value={value} 
          onChange={onChange}
          variables={availableVariables}  // from pipeline context
        />
        <button onClick={() => setExpressionMode(false)} title="Switch to literal">
          ⚡
        </button>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2">
      <LiteralInput type={field.type} value={value} onChange={onChange} />
      <button onClick={() => setExpressionMode(true)} title="Switch to expression">
        ⚡
      </button>
    </div>
  );
}
```

On the backend, when executing a node, resolve any expression-prefixed values before passing config to the handler:

```java
public Map<String, Object> resolveConfig(Map<String, Object> rawConfig, NodeHandlerContext ctx) {
    Map<String, Object> resolved = new HashMap<>();
    for (var entry : rawConfig.entrySet()) {
        Object value = entry.getValue();
        if (value instanceof String s && s.startsWith("=${")) {
            String expr = s.substring(3, s.length() - 1); // strip =${ and }
            resolved.put(entry.getKey(), spelEvaluator.evaluate(expr, ctx));
        } else {
            resolved.put(entry.getKey(), value);
        }
    }
    return resolved;
}
```

---

## Pattern 5: Test Execution with Real Data

### How n8n does it

Users click "Test step" on any node. n8n:
1. Finds all upstream nodes that need to run first
2. Executes them (or uses pinned data if available)
3. Runs the selected node with real input
4. Shows the output immediately in the NDV panel

This is the fastest feedback loop possible. Users see immediately whether their configuration works.

### What GLS has today

We have a "Test Node" button in the inspector that fires a test request to the backend. But it doesn't chain upstream nodes or show real document data.

### What to build

**Pipeline preview mode** — execute the pipeline up to the selected node with a sample document:

1. Admin selects a document from a picker (or the system uses the most recent test document)
2. Admin clicks "Test to here" on any node
3. Backend runs the pipeline graph from trigger to selected node, skipping the async LLM boundary (use cached/mocked classification for speed)
4. Each node's output is returned to the frontend
5. The inspector shows the real output data for the selected node

This is the single biggest UX improvement for making the pipeline editor feel like n8n.

```
Frontend:
POST /api/admin/pipelines/{id}/test
{
  "targetNodeKey": "condition_1",
  "testDocumentId": "doc_abc123",
  "mockClassification": {
    "categoryId": "cat_hr_leave",
    "confidence": 0.72,
    "sensitivityLabel": "INTERNAL"
  }
}

Response:
{
  "nodeResults": {
    "trigger_1": { "status": "COMPLETED", "output": {...}, "durationMs": 1 },
    "extract_1": { "status": "COMPLETED", "output": {"wordCount": 3200}, "durationMs": 450 },
    "pii_1": { "status": "COMPLETED", "output": {"piiCount": 2}, "durationMs": 80 },
    "condition_1": { "status": "COMPLETED", "output": {"branch": "true"}, "durationMs": 1 }
  }
}
```

The canvas then shows green checkmarks on completed nodes and the output data in the inspector.

---

## Pattern 6: Pin Data

### How n8n does it

Users can "pin" sample output data on any node. When downstream nodes are tested, the pinned data is used instead of executing the upstream node. This lets users:

- Test downstream logic without waiting for slow upstream operations (like LLM calls)
- Work with consistent test data across multiple iterations
- Share test scenarios with teammates

### What GLS should adopt

Add a "Pin test data" feature to the node inspector:

1. After a successful test execution, the node's output is shown in the inspector
2. User clicks "Pin this data" — the output is saved to the `PipelineDefinition.visualNodes[].pinnedData` field
3. Future test executions skip pinned nodes and use their pinned output
4. Pinned nodes show a 📌 indicator on the canvas

This is especially valuable for the LLM classification node — pin a classification result and test all downstream governance/review logic without making LLM API calls.

---

## Pattern 7: Progressive Disclosure

### How n8n does it

n8n aggressively hides complexity. The default view of a node shows 2–3 essential fields. Advanced options are behind expandable sections. This is achieved through:

1. **`displayOptions`** — fields only appear when relevant
2. **`collection` parameters** — optional fields hidden behind "Add Option"
3. **Sensible defaults** — most fields have defaults that work for 80% of cases
4. **"Additional Options" pattern** — a collection at the bottom for power-user settings

### The GLS application

Every node type should be reviewed against this checklist:

| Question | Action |
|----------|--------|
| Does the user ALWAYS need to see this field? | Show it by default |
| Does this field only matter for certain configurations? | Add `displayOptions` |
| Does this field have a sensible default that works for most users? | Set a default, move to "Advanced" collection |
| Is this field only for power users? | Move to "Advanced Options" collection |
| Can this field's value be inferred from other fields? | Compute it automatically, show as readonly in Advanced |

**Example: `textExtraction` node**

Current: 5 fields all visible (maxTextLength, extractDublinCore, extractMetadata, ocrEnabled, ocrLanguage).

After progressive disclosure:
```
Essential (always visible):
  (nothing — extraction just works with defaults)

Shown when needed:
  ocrLanguage: only when ocrEnabled is true

Advanced Options (collapsed):
  maxTextLength: 100000 (default)
  extractDublinCore: true (default)
  extractMetadata: true (default)
  ocrEnabled: true (default)
  ocrLanguage: "eng" (default, shown when ocrEnabled is toggled)
```

The node goes from "5 fields to configure" to "0 fields — it just works" for 90% of users.

---

## Implementation Roadmap for GLS

### Phase 1: Description-driven forms (1 week)

1. **Add `displayOptions` to configSchema** — conditional field visibility
2. **Add `collection` parameter type** — expandable optional field groups
3. **Add `fixedCollection` parameter type** — repeatable row groups
4. **Update `DynamicConfigForm`** — evaluate displayOptions, render new types

This alone transforms the editor UX. Every existing node type can then have its configSchema enriched with displayOptions to hide irrelevant fields.

### Phase 2: Dynamic data + expressions (1 week)

4. **Add `loadOptionsMethod`** — dynamic dropdown loading from backend
5. **Add expression toggle** — per-field switch between literal and expression mode
6. **Add SpEL resolution** — backend resolves `=${...}` expressions in node config at runtime

### Phase 3: Test + preview (1 week)

7. **Pipeline test execution** — run pipeline to a target node with a sample document
8. **Node output display** — show real output data in inspector after test
9. **Pin data** — save test output as pinned data for downstream testing

### Phase 4: Polish (ongoing)

10. **Review all 17 node types** against progressive disclosure checklist
11. **Refactor existing custom widgets** (`rulesEditor`, `modelSelect`) to use generic `fixedCollection`/`loadOptionsMethod`
12. **Add expression autocomplete** — CodeMirror plugin with available variable completions

---

## What NOT to Copy from n8n

| n8n Feature | Why to skip it |
|-------------|---------------|
| Items-based data flow | GLS processes one document at a time. Items model adds complexity without benefit. |
| JavaScript expression engine | SpEL is safer and already on the classpath. JavaScript expressions require GraalVM or V8 isolates. |
| npm-based community nodes | Our nodes are Java Spring beans. A plugin JAR system would be better, but that's a future concern. |
| Credential types system | We handle credentials via `app_config` and `.env`. A full credential type system is over-engineering for current scale. |
| Webhook trigger system | We use RabbitMQ. HTTP webhook triggers add attack surface without clear benefit for records management. |
