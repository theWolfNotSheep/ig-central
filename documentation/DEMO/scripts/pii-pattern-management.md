# PII Pattern Management — Demo Script

**Duration: 2:30**

**Goal:** Show how admins define and tune PII detection patterns — both generic and organisation-specific — as versioned regex blocks.

## Script

1. **Open with why generic PII detection fails.** *"Out of the box, most tools detect emails and credit cards. But in your organisation, there are things only you know are PII — internal staff IDs, supplier reference numbers, patient record numbers, matter codes. Generic detection misses them. Here's how you fix that."*

2. **Navigate to AI → Blocks → filter by REGEX_SET.**

3. **Open the default UK PII Patterns block.** *"Built in — National Insurance numbers, UK postcodes, NHS numbers, UTR codes, passport numbers, driving licence numbers. Each with a regex pattern, a confidence score, and an example."*

4. **Add a custom pattern.** *"Your organisation uses staff IDs in the format `EMP-######`. Add a pattern — name it 'Internal Staff ID', regex `EMP-\d{6}`, confidence 0.95, PII type `STAFF_ID`. Save to draft."*

5. **Preview the pattern.** *"Run the draft against a sample document — the preview highlights matches in context. Tune the regex if you get false positives."*

6. **Publish as a new version.** *"Click Publish with a changelog. The active version is now the one with your custom pattern. Next PII scan picks it up immediately."*

7. **Show feedback integration.** *"Feedback tab shows reviewer-flagged false positives and missed-PII reports. Each one is tied back to the pattern that caused it — you know exactly which regex to tune."*

8. **Show the `get_org_pii_patterns` MCP tool.** *"Beyond regex, the platform also learns patterns from reviewer flagging. If reviewers flag 'employee number 4422' as PII repeatedly, the LLM-side PII scanner picks that up via the MCP tool — even without a formal regex."*

9. **Hub packs.** *"Mentioned earlier — Hub packs can ship jurisdiction-specific PII pattern sets. Import a 'UK Healthcare' pack and you get NHS numbers, CHI numbers, and patient identifiers pre-defined and maintained."*

**Key message:** *"Your PII detection matches *your* data — not some vendor's generic baseline. And every tune is versioned, previewable, and reversible."*
