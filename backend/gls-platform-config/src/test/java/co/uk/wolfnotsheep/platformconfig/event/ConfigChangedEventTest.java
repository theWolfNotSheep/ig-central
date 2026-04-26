package co.uk.wolfnotsheep.platformconfig.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigChangedEventTest {

    @Test
    void rejects_blank_entity_type() {
        assertThatThrownBy(() -> new ConfigChangedEvent(
                "", List.of(), ChangeType.UPDATED, Instant.now(), "test", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_null_change_type() {
        assertThatThrownBy(() -> new ConfigChangedEvent(
                "TAXONOMY", List.of(), null, Instant.now(), "test", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_null_actor() {
        assertThatThrownBy(() -> new ConfigChangedEvent(
                "TAXONOMY", List.of(), ChangeType.UPDATED, Instant.now(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_entity_ids_normalises_to_empty_list_and_isBulk_is_true() {
        ConfigChangedEvent event = new ConfigChangedEvent(
                "POLICY", null, ChangeType.UPDATED, Instant.now(), "test", null);
        assertThat(event.entityIds()).isEmpty();
        assertThat(event.isBulk()).isTrue();
    }

    @Test
    void single_factory_produces_targeted_event() {
        ConfigChangedEvent event = ConfigChangedEvent.single("POLICY", "p1", ChangeType.CREATED, "test");
        assertThat(event.entityIds()).containsExactly("p1");
        assertThat(event.isBulk()).isFalse();
    }

    @Test
    void roundtrips_through_jackson_with_jsr310() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ConfigChangedEvent original = new ConfigChangedEvent(
                "TAXONOMY", List.of("a", "b"), ChangeType.UPDATED,
                Instant.parse("2026-04-26T22:00:00Z"), "gls-app-assembly",
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

        String json = mapper.writeValueAsString(original);
        ConfigChangedEvent decoded = mapper.readValue(json, ConfigChangedEvent.class);

        assertThat(decoded).isEqualTo(original);
    }
}
