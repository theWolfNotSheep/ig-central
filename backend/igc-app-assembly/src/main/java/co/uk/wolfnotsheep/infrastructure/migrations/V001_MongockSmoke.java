package co.uk.wolfnotsheep.infrastructure.migrations;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Smoke change-unit (Phase 0.10). Verifies that Mongock is wired into
 * the Spring Boot startup lifecycle and that the {@code mongockChangeLog}
 * tracking collection is being maintained.
 *
 * <p>This unit performs no schema work. The first real migration replaces
 * it as the canonical example of how to author a {@code @ChangeUnit}.
 *
 * <p>Convention (also in CLAUDE.md, Schema Migrations section):
 * <ul>
 *     <li>Class name: {@code V<order>_<short-name>}.</li>
 *     <li>{@code id} is immutable once landed — renaming breaks the tracking collection.</li>
 *     <li>Provide a {@code @RollbackExecution} for any non-trivial change. No-op is fine for additive ones.</li>
 *     <li>Failure halts startup loudly; fix-forward by writing a new {@code @ChangeUnit}.</li>
 * </ul>
 */
@ChangeUnit(id = "smoke-no-op", order = "001", author = "ig-central")
public class V001_MongockSmoke {

    private static final Logger log = LoggerFactory.getLogger(V001_MongockSmoke.class);

    @Execution
    public void execution() {
        log.info("Mongock smoke change-unit ran — schema-migration pipeline is wired.");
    }

    @RollbackExecution
    public void rollbackExecution() {
        log.info("Mongock smoke change-unit rolled back (no-op).");
    }
}
