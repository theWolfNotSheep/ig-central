package co.uk.wolfnotsheep.platformaudit.emit;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UlidTest {

    @Test
    void nextId_matches_envelope_schema_pattern() {
        for (int i = 0; i < 100; i++) {
            assertThat(Ulid.nextId()).matches("^[0-9A-HJKMNP-TV-Z]{26}$");
        }
    }

    @Test
    void nextId_does_not_use_excluded_characters() {
        for (int i = 0; i < 100; i++) {
            String id = Ulid.nextId();
            // Crockford excludes I, L, O, U
            assertThat(id).doesNotContain("I", "L", "O", "U");
        }
    }

    @Test
    void nextId_produces_distinct_values() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(Ulid.nextId());
        }
        // SecureRandom — collisions across 26 base32 chars are vanishingly unlikely
        assertThat(seen).hasSize(1000);
    }
}
