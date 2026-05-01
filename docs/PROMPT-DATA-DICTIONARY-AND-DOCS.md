# Prompt: Generate Data Dictionary, ERD, and Organise Documentation

Use this prompt with Claude Code in a new conversation with the target repo as the working directory.

---

## The Prompt

```
I need you to do three things for this project, in order:

---

### 1. Generate a Complete Data Dictionary

Build a complete data dictionary for this project. Document every data model / entity / schema used in the application. For each one, I need:

1. **Collection/table name** — the actual persistence name (e.g. MongoDB @Document value, SQL table name, Prisma model name)
2. **Class/model name** — the code-level name
3. **Purpose** — one-line description of what this entity represents
4. **Every field** — name, type, whether it's indexed (unique, sparse, composite), and a description
5. **Embedded/nested types** — if a field contains a sub-object, document its fields too
6. **Relations** — foreign keys, references to other entities, and the cardinality (1:1, 1:N, M:N)
7. **API endpoints** — every HTTP endpoint (method + path + one-line purpose) that reads or writes this entity. Include admin, user-facing, and public endpoints. Also note any internal/webhook/boot-seeder operations that create or modify records without a direct endpoint.

**How to approach this:**
- Search exhaustively across ALL modules/packages in the codebase. Don't stop at the first few — check every directory.
- For schemas: look for ORM annotations (@Document, @Entity, @Table), schema definitions (Prisma, Mongoose, Drizzle, SQLAlchemy), or type definitions used for persistence.
- For endpoints: look for controller/route annotations (@RestController, @GetMapping, app.get(), router.post(), etc.) and trace which entities they touch.
- For internal operations: look for event listeners, scheduled tasks, seeders/bootstrap runners, message queue consumers, and webhook handlers.

**Output format:**

Write the result to `docs/data-dictionary.md` as a single markdown document. Organise collections into logical domain groups. Use this structure for each collection:

#### `collection_name` — ClassName

One-line purpose.

| Field | Type | Indexed | Description |
|-------|------|---------|-------------|
| ... | ... | ... | ... |

**Embedded: SubTypeName** (if applicable)
| Field | Type | Description |
|-------|------|-------------|
| ... | ... | ... |

**Relations:**
- `fieldName` → `other_collection.id`
- `id` ← `other_collection.foreignKey`

**Endpoints:**
- `METHOD /path` — purpose
- (internal) description of non-HTTP operations

At the end, include an **Entity Relationship Summary** showing the full object graph as an ASCII tree.

---

### 2. Generate a Lucidchart-Importable ERD

From the data dictionary you just created, generate a draw.io XML file at `docs/igc-erd.drawio` containing:

- **Every entity** as a colour-coded rounded rectangle showing collection name (bold) and class name (italic)
- **Colour-code by domain** — use distinct fill colours to visually group related entities (e.g. blue for core domain, green for governance, orange for pipeline, purple for auth, etc.)
- **Every relationship** as an edge with:
  - Crow's foot notation (ERone/ERmany start/end arrows) showing cardinality (1:N, M:N, 1:1)
  - The FK field name as the edge label (small font)
- **Self-referential edges** where entities reference themselves (e.g. parent-child trees)
- **Domain group labels** as text annotations on the left margin
- **Thicker borders** on the 2-3 most central entities (the ones with the most relationships)
- **Grid layout** organised by domain group — rows of 5 entities with consistent spacing

Use orthogonal edge style for clean routing. The file must import cleanly into Lucidchart via File > Import > draw.io, and also open in diagrams.net.

---

### 3. Organise All Documentation

Find every `.md` file in the project (excluding `node_modules/`, `target/`, `.claude/`, and build output directories). Then:

**Leave in place** (standard convention):
- `CLAUDE.md` files (Claude Code project instructions)
- `README.md` files at module/project roots
- Any file referenced by a CLAUDE.md

**Move everything else** into `docs/` with this folder structure:

```
docs/
├── design/           # Architecture, strategy, technical decisions, security
├── implementation/   # Build guides, TODOs, how-to-implement documents
├── testing/          # Test plans, testing guides, QA procedures
├── features/         # Individual feature plans and specifications
├── operations/       # Deployment, getting started, troubleshooting
├── release/          # Roadmaps, changelogs, release plans
├── demo/             # Demo scripts, sales materials, presentations
│   └── scripts/      # Individual demo walkthrough scripts
└── feedback/         # User/stakeholder feedback
```

**Rules:**
- Use `git mv` for tracked files to preserve history. Use plain `mv` for untracked files.
- Rename files to lowercase-kebab-case (e.g. `MY_DOC.md` → `my-doc.md`)
- Delete the old directories once empty
- Non-markdown reference files (spreadsheets, .docx, etc.) go into the most relevant subfolder

---

### 4. Generate PDFs

Install `pandoc` and `weasyprint` if not already available (`brew install pandoc weasyprint`).

Then generate a PDF for every `.md` file under `docs/`, writing them to `docs/pdf/` with the same folder structure:

```bash
find docs/ -name "*.md" -type f | while read md_file; do
  rel="${md_file#docs/}"
  pdf_file="docs/pdf/${rel%.md}.pdf"
  mkdir -p "$(dirname "$pdf_file")"
  title=$(head -5 "$md_file" | grep -m1 "^#" | sed 's/^#\+\s*//')
  [ -z "$title" ] && title=$(basename "$md_file" .md)
  pandoc "$md_file" -o "$pdf_file" \
    --pdf-engine=weasyprint \
    --metadata title="$title" \
    -V margin-top=20mm -V margin-bottom=20mm \
    -V margin-left=15mm -V margin-right=15mm
done
```

Report the total count and size when done.

---

## Important

- Do NOT leave any entity, field, or endpoint out of the data dictionary. Be exhaustive.
- Search in parallel across modules where possible for speed.
- Verify field lists by reading the actual model source files, not just inferring from repository method names.
- For large codebases, work module by module and compile at the end.
- Do not commit anything — just create the files. I will review and commit myself.
```
