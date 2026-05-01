package co.uk.wolfnotsheep.infrastructure.services;

import co.uk.wolfnotsheep.governance.models.PackImportHistory;
import co.uk.wolfnotsheep.governance.repositories.PackImportHistoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Phase 3 PR12 — covers the {@code recordImportHistory} branch added to
 * {@link PackImportService}. Reflection-driven so the test focuses on
 * exactly the new logic without instantiating the full Spring graph
 * (the service has 14 collaborators).
 *
 * <p>The reflection here is deliberate: mocking the entire constructor
 * argument list would be 14 nulls plus one mocked repo, with no payoff —
 * the rest of the service is not under test in this slice.
 */
class PackImportHistoryRecordingTest {

    private static final Method recordImportHistoryMethod;
    static {
        try {
            recordImportHistoryMethod = PackImportService.class.getDeclaredMethod(
                    "recordImportHistory",
                    String.class,
                    co.uk.wolfnotsheep.infrastructure.services.HubPackDto.PackVersionDto.class,
                    PackImportService.ImportMode.class,
                    Instant.class,
                    String.class,
                    List.class,
                    List.class,
                    int.class, int.class, int.class, int.class);
            recordImportHistoryMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final PackImportHistoryRepository historyRepo = mock(PackImportHistoryRepository.class);

    @Test
    void recordImportHistory_capturesAllRelevantFields() throws Exception {
        PackImportService svc = newServiceWithHistoryRepo();

        var version = makeVersion(7);
        invokeRecord(svc, "uk-hr", version, PackImportService.ImportMode.OVERWRITE,
                Instant.parse("2026-05-01T12:00:00Z"), "alice@example.com",
                null, // selectedItemKeys → not selective
                results("TAXONOMY_CATEGORIES", "RETENTION_SCHEDULES"),
                12, 3, 1, 0);

        ArgumentCaptor<PackImportHistory> captor = ArgumentCaptor.forClass(PackImportHistory.class);
        verify(historyRepo, times(1)).save(captor.capture());
        PackImportHistory entry = captor.getValue();
        assertThat(entry.getPackSlug()).isEqualTo("uk-hr");
        assertThat(entry.getVersion()).isEqualTo(7);
        assertThat(entry.getImportedAt()).isEqualTo(Instant.parse("2026-05-01T12:00:00Z"));
        assertThat(entry.getImportedBy()).isEqualTo("alice@example.com");
        assertThat(entry.getMode()).isEqualTo("OVERWRITE");
        assertThat(entry.getSelectedItemKeys()).isNull();
        assertThat(entry.getComponentTypes())
                .containsExactly("TAXONOMY_CATEGORIES", "RETENTION_SCHEDULES");
        assertThat(entry.getTotalCreated()).isEqualTo(12);
        assertThat(entry.getTotalUpdated()).isEqualTo(3);
        assertThat(entry.getTotalSkipped()).isEqualTo(1);
        assertThat(entry.getTotalFailed()).isZero();
    }

    @Test
    void recordImportHistory_marksAsSelectiveWhenSelectedKeysNonEmpty() throws Exception {
        PackImportService svc = newServiceWithHistoryRepo();

        invokeRecord(svc, "uk-hr", makeVersion(3), PackImportService.ImportMode.OVERWRITE,
                Instant.now(), "bob@example.com",
                List.of("TAXONOMY_CATEGORIES::HR-EMP"),
                results("TAXONOMY_CATEGORIES"),
                1, 0, 0, 0);

        ArgumentCaptor<PackImportHistory> captor = ArgumentCaptor.forClass(PackImportHistory.class);
        verify(historyRepo).save(captor.capture());
        PackImportHistory entry = captor.getValue();
        assertThat(entry.getMode()).isEqualTo("SELECTIVE");
        assertThat(entry.getSelectedItemKeys()).containsExactly("TAXONOMY_CATEGORIES::HR-EMP");
    }

    @Test
    void recordImportHistory_defaultsImportedByToADMINWhenNull() throws Exception {
        PackImportService svc = newServiceWithHistoryRepo();

        invokeRecord(svc, "x", makeVersion(1), PackImportService.ImportMode.MERGE,
                Instant.now(), null, null, results("STORAGE_TIERS"), 0, 1, 0, 0);

        ArgumentCaptor<PackImportHistory> captor = ArgumentCaptor.forClass(PackImportHistory.class);
        verify(historyRepo).save(captor.capture());
        assertThat(captor.getValue().getImportedBy()).isEqualTo("ADMIN");
    }

    @Test
    void recordImportHistory_swallowsRepoExceptions() throws Exception {
        // recordImportHistory is wrapped in try/catch — repo failures are
        // non-fatal because the import has already landed.
        org.mockito.Mockito.when(historyRepo.save(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("mongo down"));
        PackImportService svc = newServiceWithHistoryRepo();

        // Must not throw.
        invokeRecord(svc, "x", makeVersion(1), PackImportService.ImportMode.MERGE,
                Instant.now(), "u", null, results("STORAGE_TIERS"), 0, 1, 0, 0);

        verify(historyRepo, times(1)).save(org.mockito.ArgumentMatchers.any());
        // Sanity: no second save attempt or anything weird.
        verify(historyRepo, never()).deleteAll();
    }

    private PackImportService newServiceWithHistoryRepo() {
        // The 14-arg constructor: only historyRepo is non-null; the other
        // collaborators aren't touched by recordImportHistory.
        return new PackImportService(
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null,
                historyRepo);
    }

    private static co.uk.wolfnotsheep.infrastructure.services.HubPackDto.PackVersionDto makeVersion(int n) {
        return new co.uk.wolfnotsheep.infrastructure.services.HubPackDto.PackVersionDto(
                "v-" + n, "p", n, "changelog", "publisher",
                "2026-05-01T00:00:00Z", List.of(), null);
    }

    private static List<PackImportService.ComponentResult> results(String... types) {
        return java.util.Arrays.stream(types)
                .map(t -> new PackImportService.ComponentResult(t, t, 0, 0, 0, 0, List.of()))
                .toList();
    }

    private void invokeRecord(PackImportService svc,
                              String slug,
                              co.uk.wolfnotsheep.infrastructure.services.HubPackDto.PackVersionDto version,
                              PackImportService.ImportMode mode,
                              Instant importedAt,
                              String importedBy,
                              List<String> selectedItemKeys,
                              List<PackImportService.ComponentResult> results,
                              int created, int updated, int skipped, int failed) throws Exception {
        recordImportHistoryMethod.invoke(svc, slug, version, mode, importedAt, importedBy,
                selectedItemKeys, results, created, updated, skipped, failed);
    }
}
