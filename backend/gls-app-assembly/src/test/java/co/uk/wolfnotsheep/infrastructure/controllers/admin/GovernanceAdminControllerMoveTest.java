package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory.TaxonomyLevel;
import co.uk.wolfnotsheep.governance.repositories.ClassificationCategoryRepository;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@code POST /api/admin/governance/taxonomy/{id}/move}
 * endpoint — focused on cycle detection and the level-recompute rules.
 * The controller has a large dependency list; the move endpoint only
 * touches {@code categoryRepository} and {@code governanceService},
 * the rest stay null.
 */
class GovernanceAdminControllerMoveTest {

    private ClassificationCategoryRepository categoryRepository;
    private GovernanceService governanceService;
    private GovernanceAdminController controller;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(ClassificationCategoryRepository.class);
        governanceService = mock(GovernanceService.class);
        controller = new GovernanceAdminController(
                governanceService, null, categoryRepository, null, null, null,
                null, null, null, null, null, null);
    }

    @Test
    void moveToRoot_setsParentNullAndLevelFunction() {
        ClassificationCategory cat = makeCategory("cat-1", "parent-x", TaxonomyLevel.ACTIVITY);
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(cat));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> resp = controller.moveCategory("cat-1", body(null));

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        ClassificationCategory result = (ClassificationCategory) resp.getBody();
        assertThat(result).isNotNull();
        assertThat(result.getParentId()).isNull();
        assertThat(result.getLevel()).isEqualTo(TaxonomyLevel.FUNCTION);
        verify(governanceService).rebuildPaths();
    }

    @Test
    void moveUnderFunction_setsLevelActivity() {
        ClassificationCategory parent = makeCategory("parent-1", null, TaxonomyLevel.FUNCTION);
        ClassificationCategory cat = makeCategory("cat-1", null, TaxonomyLevel.FUNCTION);
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(cat));
        when(categoryRepository.findById("parent-1")).thenReturn(Optional.of(parent));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> resp = controller.moveCategory("cat-1", body("parent-1"));

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        ClassificationCategory result = (ClassificationCategory) resp.getBody();
        assertThat(result.getParentId()).isEqualTo("parent-1");
        assertThat(result.getLevel()).isEqualTo(TaxonomyLevel.ACTIVITY);
    }

    @Test
    void moveUnderActivity_setsLevelTransaction() {
        ClassificationCategory parent = makeCategory("parent-1", "grand-1", TaxonomyLevel.ACTIVITY);
        ClassificationCategory cat = makeCategory("cat-1", null, TaxonomyLevel.FUNCTION);
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(cat));
        when(categoryRepository.findById("parent-1")).thenReturn(Optional.of(parent));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> resp = controller.moveCategory("cat-1", body("parent-1"));

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        ClassificationCategory result = (ClassificationCategory) resp.getBody();
        assertThat(result.getLevel()).isEqualTo(TaxonomyLevel.TRANSACTION);
    }

    @Test
    void moveOntoSelf_returns400() {
        when(categoryRepository.findById("cat-1"))
                .thenReturn(Optional.of(makeCategory("cat-1", null, TaxonomyLevel.FUNCTION)));

        ResponseEntity<?> resp = controller.moveCategory("cat-1", body("cat-1"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().toString()).contains("cycle");
        verify(governanceService, never()).rebuildPaths();
    }

    @Test
    void moveUnderOwnDescendant_returns400() {
        // Tree: A → B → C. Move A under C should fail.
        ClassificationCategory a = makeCategory("a", null, TaxonomyLevel.FUNCTION);
        ClassificationCategory b = makeCategory("b", "a", TaxonomyLevel.ACTIVITY);
        ClassificationCategory c = makeCategory("c", "b", TaxonomyLevel.TRANSACTION);
        when(categoryRepository.findById("a")).thenReturn(Optional.of(a));
        when(categoryRepository.findById("b")).thenReturn(Optional.of(b));
        when(categoryRepository.findById("c")).thenReturn(Optional.of(c));

        ResponseEntity<?> resp = controller.moveCategory("a", body("c"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().toString()).contains("cycle").contains("descendant");
        verify(governanceService, never()).rebuildPaths();
    }

    @Test
    void moveSourceNotFound_returns404() {
        when(categoryRepository.findById("nope")).thenReturn(Optional.empty());

        ResponseEntity<?> resp = controller.moveCategory("nope", body("anything"));

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void moveToMissingParent_returns400() {
        when(categoryRepository.findById("cat-1"))
                .thenReturn(Optional.of(makeCategory("cat-1", null, TaxonomyLevel.FUNCTION)));
        when(categoryRepository.findById("ghost")).thenReturn(Optional.empty());

        ResponseEntity<?> resp = controller.moveCategory("cat-1", body("ghost"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().toString()).contains("newParentNotFound");
    }

    @Test
    void successfulMove_incrementsVersionAndCallsSaveOnce() {
        ClassificationCategory cat = makeCategory("cat-1", "p1", TaxonomyLevel.ACTIVITY);
        cat.setVersion(3);
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(cat));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        controller.moveCategory("cat-1", body(null));

        assertThat(cat.getVersion()).isEqualTo(4);
        verify(categoryRepository, times(1)).save(any());
    }

    private static ClassificationCategory makeCategory(String id, String parentId, TaxonomyLevel level) {
        ClassificationCategory c = new ClassificationCategory();
        c.setId(id);
        c.setParentId(parentId);
        c.setLevel(level);
        c.setName(id);
        c.setVersion(1);
        return c;
    }

    private static Map<String, String> body(String newParentId) {
        Map<String, String> b = new HashMap<>();
        b.put("newParentId", newParentId);
        return b;
    }
}
