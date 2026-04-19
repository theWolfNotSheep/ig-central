# Hub App — API Keys — Demo Script

**Duration: 2:00**

**Goal:** Show pack authors and programmatic integrators how to manage API access to the Hub.

## Script

1. **Open with the use case.** *"Pack authoring doesn't have to be all UI. If your firm generates retention schedules from a master spreadsheet, or pulls taxonomy updates from a source-of-truth database, you can publish to the Hub programmatically. You'll need an API key."*

2. **Navigate to Hub → API Keys** (`/api-keys` in the Hub app).

3. **Show the key list.** *"Every key has a name, a scope — publisher, consumer, read-only — a creation date, a last-used timestamp, and an expiry."*

4. **Create a new key.** *"Name it — 'Pack CI Pipeline'. Scope it to the minimum needed — Publisher for your packs only, not the whole Hub. Set an expiry — 90 days is a sensible default. Click Generate."*

5. **Capture the secret.** *"The key is shown exactly once. Copy it into your secrets manager — we never show it again, and we only store a hash. That's table stakes for security teams."*

6. **Demonstrate scope enforcement.** *"Try a read-only key against a publish endpoint — it's rejected with a clear error. Least-privilege by default."*

7. **Show rotation and revocation.** *"Revoke a key with one click if it's compromised. Rotation flow lets you generate a new key, transition your systems, then revoke the old one without downtime."*

8. **Audit trail.** *"Every API call made with every key is logged — endpoint, payload summary, response code, timestamp. Your security team gets visibility without having to build it."*

**Key message:** *"First-class programmatic access with the security controls your IT team expects — no manual copy-pasting of packs."*
