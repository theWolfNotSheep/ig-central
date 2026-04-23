# Node Type Definitions — Demo Script

**Duration: 2:00**

**Goal:** Show how admins create custom taxonomy node types — extending the category model to match sector-specific terminology.

## Script

1. **Open with the limitation of flat taxonomies.** *"Most classification systems give you one kind of node — a category. But your organisation thinks in richer terms. A law firm has matters, clients, and engagements. A council has case files, complaints, and planning applications. Node types let you model that reality."*

2. **Navigate to Admin → Node Types.**

3. **Show the default types.** *"Out of the box — Category, Subcategory, Record Type. Enough for generic use. But your sector probably has more."*

4. **Create a custom node type.** *"Let's create a 'Matter' type for a legal taxonomy. Name it, pick a display icon and colour, define the metadata fields it carries — matter reference, client ID, opened date, responsible partner."*

5. **Show the schema editor.** *"Each node type has its own schema — fields you require, fields you enforce as unique, fields the AI should auto-populate. This is the type model your taxonomy tree respects."*

6. **Navigate to the taxonomy.** *"Now when creating categories, you can place a Matter node under a Client node under a Department node. The taxonomy becomes a real domain model, not just a flat tag list."*

7. **Tie it to metadata extraction.** *"Documents classified into a Matter inherit the Matter's metadata requirements — matter reference becomes a mandatory extracted field. Your records are structurally consistent across the estate."*

8. **Mention hub packs.** *"Packs in the Hub can ship their own node types — a UK Local Authority pack might define 'Ward', 'Complaint', 'Service Request' node types, already wired up."*

**Key message:** *"Your taxonomy stops being a flat folder structure and becomes a domain model — one that actually mirrors how your organisation works."*
