package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.governance.models.PipelineDefinition;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition.PipelineSnapshot;
import co.uk.wolfnotsheep.governance.repositories.PipelineDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the pipeline-versioning slice of {@link PipelineAdminController}:
 * snapshot-on-update, list-versions, rollback. Mocks the repo only — the
 * controller's other dependencies are unused on these code paths and stay null.
 */
class PipelineAdminControllerVersioningTest {

    private PipelineDefinitionRepository pipelineRepo;
    private Authentication auth;
    private PipelineAdminController controller;

    @BeforeEach
    void setUp() {
        pipelineRepo = mock(PipelineDefinitionRepository.class);
        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin@example.com");
        controller = new PipelineAdminController(
                pipelineRepo, null, null, null, null, null);
        when(pipelineRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void update_archivesPreviousStateAndBumpsCurrentVersion() {
        PipelineDefinition existing = makePipeline("p-1", 3, "old name");
        existing.setSteps(List.of());
        when(pipelineRepo.findById("p-1")).thenReturn(Optional.of(existing));

        PipelineDefinition updates = makePipeline("p-1", 0, "new name");
        controller.update("p-1", updates, "tweak prompt threshold", auth);

        // The previous version (3) is archived; new currentVersion is 4.
        assertThat(existing.getCurrentVersion()).isEqualTo(4);
        assertThat(existing.getVersions()).hasSize(1);
        PipelineSnapshot archived = existing.getVersions().get(0);
        assertThat(archived.version()).isEqualTo(3);
        assertThat(archived.savedBy()).isEqualTo("admin@example.com");
        assertThat(archived.changelog()).isEqualTo("tweak prompt threshold");
        // Updates were applied AFTER archiving.
        assertThat(existing.getName()).isEqualTo("new name");
    }

    @Test
    void update_defaultsChangelogToSaveWhenBlank() {
        PipelineDefinition existing = makePipeline("p-1", 1, "old");
        when(pipelineRepo.findById("p-1")).thenReturn(Optional.of(existing));

        controller.update("p-1", makePipeline("p-1", 0, "new"), null, auth);

        assertThat(existing.getVersions().get(0).changelog()).isEqualTo("save");
    }

    @Test
    void update_treatsZeroVersionAsOne_initialSave() {
        // A pipeline created before the versioning feature has currentVersion=0
        // (Mongo default for int). The first save should archive it as v1.
        PipelineDefinition existing = makePipeline("p-old", 0, "legacy");
        when(pipelineRepo.findById("p-old")).thenReturn(Optional.of(existing));

        controller.update("p-old", makePipeline("p-old", 0, "updated"), null, auth);

        assertThat(existing.getVersions().get(0).version()).isEqualTo(1);
        assertThat(existing.getCurrentVersion()).isEqualTo(2);
    }

    @Test
    void listVersions_returnsCurrentVersionAndSnapshots() {
        PipelineDefinition p = makePipeline("p-1", 5, "name");
        p.setVersions(List.of(
                snap(1, "first save"),
                snap(2, "second save")));
        when(pipelineRepo.findById("p-1")).thenReturn(Optional.of(p));

        ResponseEntity<Map<String, Object>> resp = controller.listVersions("p-1");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("currentVersion", 5);
        assertThat((List<?>) resp.getBody().get("versions")).hasSize(2);
    }

    @Test
    void listVersions_returnsEmptyListWhenNoSnapshots() {
        PipelineDefinition p = makePipeline("p-1", 1, "name");
        when(pipelineRepo.findById("p-1")).thenReturn(Optional.of(p));

        ResponseEntity<Map<String, Object>> resp = controller.listVersions("p-1");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat((List<?>) resp.getBody().get("versions")).isEmpty();
    }

    @Test
    void listVersions_404OnMissingPipeline() {
        when(pipelineRepo.findById("ghost")).thenReturn(Optional.empty());
        assertThat(controller.listVersions("ghost").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void rollback_restoresWorkflowAndArchivesCurrentState() {
        PipelineDefinition p = makePipeline("p-1", 5, "current name");
        // Existing steps to confirm they're replaced.
        PipelineDefinition.PipelineStep currentStep = new PipelineDefinition.PipelineStep();
        currentStep.setName("current");
        p.setSteps(new ArrayList<>(List.of(currentStep)));
        // Snapshot for v3 has different steps.
        PipelineDefinition.PipelineStep v3Step = new PipelineDefinition.PipelineStep();
        v3Step.setName("v3-step");
        p.setVersions(new ArrayList<>(List.of(
                new PipelineSnapshot(3, java.time.Instant.now(), "alice", "v3 changes",
                        List.of(v3Step), List.of(), List.of()))));
        when(pipelineRepo.findById("p-1")).thenReturn(Optional.of(p));

        controller.rollback("p-1", 3, auth);

        // Workflow restored to v3.
        assertThat(p.getSteps()).hasSize(1);
        assertThat(p.getSteps().get(0).getName()).isEqualTo("v3-step");
        // Current state archived; currentVersion bumped.
        assertThat(p.getCurrentVersion()).isEqualTo(6);
        // Versions list now has 2 entries: original v3 + freshly-archived v5.
        assertThat(p.getVersions()).hasSize(2);
        PipelineSnapshot newest = p.getVersions().get(p.getVersions().size() - 1);
        assertThat(newest.version()).isEqualTo(5);
        assertThat(newest.changelog()).isEqualTo("Rolled back to v3");
        // Name is preserved across rollback.
        assertThat(p.getName()).isEqualTo("current name");
    }

    @Test
    void rollback_400OnUnknownVersion() {
        PipelineDefinition p = makePipeline("p-1", 5, "name");
        p.setVersions(List.of(snap(1, "first")));
        when(pipelineRepo.findById("p-1")).thenReturn(Optional.of(p));

        ResponseEntity<?> resp = controller.rollback("p-1", 99, auth);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().toString()).contains("versionNotFound").contains("99");
    }

    @Test
    void rollback_404OnMissingPipeline() {
        when(pipelineRepo.findById("ghost")).thenReturn(Optional.empty());
        assertThat(controller.rollback("ghost", 1, auth).getStatusCode().value()).isEqualTo(404);
    }

    private static PipelineDefinition makePipeline(String id, int version, String name) {
        PipelineDefinition p = new PipelineDefinition();
        p.setId(id);
        p.setName(name);
        p.setCurrentVersion(version);
        p.setActive(true);
        return p;
    }

    private static PipelineSnapshot snap(int version, String changelog) {
        return new PipelineSnapshot(version, java.time.Instant.now(), "tester", changelog,
                List.of(), List.of(), List.of());
    }
}
