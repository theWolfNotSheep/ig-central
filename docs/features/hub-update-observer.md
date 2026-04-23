# Pack Update Observer — Hub Change Detection for IG Central

## Context

Currently, when a tenant imports a governance pack from the hub, there is no mechanism to detect when the hub publishes a newer version. The tenant must manually browse the hub and compare versions. This plan adds a **polling observer** that automatically checks the hub for updates to imported packs and surfaces notifications to admins via REST + SSE.

## Architecture

```
Hub (port 8090)                          IG Central (tenant)
┌─────────────────────┐                  ┌──────────────────────────┐
│ POST /check-updates │◄─── poll ────────│ PackUpdateObserver       │
│ (batch endpoint)    │────response──────│ (@Scheduled, 1hr)        │
└─────────────────────┘                  │                          │
                                         │ InstalledPack (MongoDB)  │
                                         │ PackUpdateAvailable (DB) │
                                         │                          │
                                         │ PipelineEventBroadcaster │
                                         │ (SSE → frontend)         │
                                         │                          │
                                         │ PackUpdatesController    │
                                         │ (REST → frontend)        │
                                         └──────────────────────────┘
```

## Implementation Steps

### 1. Hub-side: Batch check-updates endpoint

**File:** `backend/gls-governance-hub-app/src/main/java/co/uk/wolfnotsheep/hub/app/controllers/PackBrowseController.java`

Add to existing controller:

```java
record InstalledPackInfo(String slug, int currentVersion) {}
record PackUpdateInfo(String slug, String name, int latestVersion, String changelog,
                      Instant publishedAt, List<String> componentTypes) {}

@PostMapping("/check-updates")
public ResponseEntity<List<PackUpdateInfo>> checkUpdates(@RequestBody List<InstalledPackInfo> installed)
```

Logic: For each slug, look up the pack. If `pack.getLatestVersionNumber() > installed.currentVersion`, fetch the latest `PackVersion` and return its metadata (no component data). Uses existing `GovernancePackRepository.findBySlug()` and `PackVersionService.getVersion()`.

### 2. Tenant-side: InstalledPack model + repository

**New file:** `backend/gls-governance/src/main/java/co/uk/wolfnotsheep/governance/models/InstalledPack.java`

```java
@Document(collection = "installed_packs")
public class InstalledPack {
    @Id private String id;
    @Indexed(unique = true) private String packSlug;
    private String packName;
    private int installedVersion;
    private Instant importedAt;
    private List<String> componentTypesImported;
}
```

**New file:** `backend/gls-governance/src/main/java/co/uk/wolfnotsheep/governance/repositories/InstalledPackRepository.java`

```java
public interface InstalledPackRepository extends MongoRepository<InstalledPack, String> {
    Optional<InstalledPack> findByPackSlug(String packSlug);
}
```

### 3. Tenant-side: PackUpdateAvailable model + repository

**New file:** `backend/gls-governance/src/main/java/co/uk/wolfnotsheep/governance/models/PackUpdateAvailable.java`

```java
@Document(collection = "pack_updates_available")
public class PackUpdateAvailable {
    @Id private String id;
    @Indexed(unique = true) private String packSlug;
    private String packName;
    private int installedVersion;
    private int latestVersion;
    private String changelog;
    private Instant publishedAt;
    private List<String> componentTypes;
    private boolean dismissed;
    private Instant detectedAt;
}
```

**New file:** `backend/gls-governance/src/main/java/co/uk/wolfnotsheep/governance/repositories/PackUpdateAvailableRepository.java`

```java
public interface PackUpdateAvailableRepository extends MongoRepository<PackUpdateAvailable, String> {
    Optional<PackUpdateAvailable> findByPackSlug(String packSlug);
    List<PackUpdateAvailable> findByDismissedFalse();
    long countByDismissedFalse();
    void deleteByPackSlug(String packSlug);
}
```

### 4. Record installed packs on import

**Modify:** `backend/gls-app-assembly/src/main/java/co/uk/wolfnotsheep/infrastructure/services/PackImportService.java`

- Inject `InstalledPackRepository` and `PackUpdateAvailableRepository`
- After successful import (not PREVIEW mode), upsert an `InstalledPack` record with slug, version, timestamp, component types
- Also call `updateRepo.deleteByPackSlug(packSlug)` to clear any stale update notification

~15 lines added at the end of `importPack()`.

### 5. PackUpdateObserver — the scheduled polling service

**New file:** `backend/gls-app-assembly/src/main/java/co/uk/wolfnotsheep/infrastructure/services/PackUpdateObserver.java`

```java
@Component
public class PackUpdateObserver {
    private final GovernanceHubClient hubClient;
    private final InstalledPackRepository installedPackRepo;
    private final PackUpdateAvailableRepository updateRepo;
    private final PipelineEventBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${governance-hub.poll-interval-ms:3600000}",
               initialDelay = 60_000)
    public void checkForUpdates() { ... }
}
```

Logic:
1. Guard: if `!hubClient.isConfigured()` or no installed packs, return
2. Build JSON array of `{slug, currentVersion}` from `InstalledPack` records
3. POST to hub `/api/hub/packs/check-updates` via existing `hubClient.post()`
4. Deserialize response, upsert `PackUpdateAvailable` records (preserve `dismissed` if same version)
5. Remove stale `PackUpdateAvailable` for packs no longer needing update
6. If new updates detected, broadcast via `broadcaster.broadcast("pack-updates", ...)`
7. Wrap in try/catch — never crash the scheduled task

### 6. REST endpoint for frontend

**New file:** `backend/gls-app-assembly/src/main/java/co/uk/wolfnotsheep/infrastructure/controllers/admin/PackUpdatesController.java`

```
@RestController
@RequestMapping("/api/admin/governance/updates")

GET  /                → List<PackUpdateAvailable> (non-dismissed)
GET  /count           → {"count": N} (for badge)
POST /{slug}/dismiss  → mark dismissed
POST /check-now       → trigger observer manually, return results
```

### 7. SSE integration

No changes to `PipelineEventBroadcaster` — reuse existing `broadcast("pack-updates", data)` method. The observer calls this when new updates are detected.

## Files Summary

| Action | File |
|--------|------|
| Modify | `hub/.../controllers/PackBrowseController.java` — add `/check-updates` |
| New | `gls-governance/.../models/InstalledPack.java` |
| New | `gls-governance/.../repositories/InstalledPackRepository.java` |
| New | `gls-governance/.../models/PackUpdateAvailable.java` |
| New | `gls-governance/.../repositories/PackUpdateAvailableRepository.java` |
| Modify | `gls-app-assembly/.../services/PackImportService.java` — record install + clear updates |
| New | `gls-app-assembly/.../services/PackUpdateObserver.java` |
| New | `gls-app-assembly/.../controllers/admin/PackUpdatesController.java` |

## What is NOT duplicated

- Reuses `GovernanceHubClient.post()` for hub communication (no new HTTP client)
- Reuses `PipelineEventBroadcaster.broadcast()` for SSE push (no new SSE infra)
- Reuses `GovernancePackRepository.findBySlug()` and `PackVersionService.getVersion()` on the hub side
- Models in `gls-governance` follow existing patterns (same package, same style)
- Controller follows existing `GovernanceHubController` proxy pattern

## Verification

1. Start hub + tenant locally
2. Import a pack via existing flow → verify `installed_packs` collection gets a record
3. Publish a new version on the hub
4. Wait for poll (or hit `POST /api/admin/governance/updates/check-now`)
5. Verify `pack_updates_available` collection has a record
6. Hit `GET /api/admin/governance/updates` → see the update
7. Hit `GET /api/admin/governance/updates/count` → `{"count": 1}`
8. Import the new version → verify update record is cleared
