package co.uk.wolfnotsheep.platformaudit.relay;

import co.uk.wolfnotsheep.platformaudit.envelope.Actor;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditDetails;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.Resource;
import co.uk.wolfnotsheep.platformaudit.envelope.ResourceType;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Sanity tests for {@link Tier1HashTransformer}. */
class Tier1HashTransformerTest {

    @Test
    void domain_tier_content_is_hashed_metadata_preserved() {
        AuditEvent input = envelope(Tier.DOMAIN, AuditDetails.of(
                Map.of("categoryId", "HR-LEAVE"),
                Map.of("rawText", "private content")));

        AuditEvent out = Tier1HashTransformer.toTier1(input);

        assertThat(out.details().metadata()).isEqualTo(input.details().metadata());
        assertThat(out.details().content()).hasSize(1);
        Object hashed = out.details().content().get("rawText");
        assertThat(hashed).isInstanceOf(String.class);
        assertThat((String) hashed).matches("^sha256:[0-9a-f]{64}$");
        assertThat(hashed).isNotEqualTo("private content");
    }

    @Test
    void domain_tier_with_no_content_is_pass_through() {
        AuditEvent input = envelope(Tier.DOMAIN,
                AuditDetails.metadataOnly(Map.of("decision", "APPROVED")));

        AuditEvent out = Tier1HashTransformer.toTier1(input);

        assertThat(out).isSameAs(input);
    }

    @Test
    void domain_tier_with_empty_content_is_pass_through() {
        AuditEvent input = envelope(Tier.DOMAIN, new AuditDetails(
                Map.of("k", "v"), new LinkedHashMap<>(), null, null));

        AuditEvent out = Tier1HashTransformer.toTier1(input);

        assertThat(out).isSameAs(input);
    }

    @Test
    void system_tier_is_pass_through_even_with_content() {
        AuditEvent input = envelope(Tier.SYSTEM, AuditDetails.of(
                Map.of("k", "v"),
                Map.of("rawText", "should not be hashed")));

        AuditEvent out = Tier1HashTransformer.toTier1(input);

        assertThat(out).isSameAs(input);
        assertThat(out.details().content().get("rawText")).isEqualTo("should not be hashed");
    }

    @Test
    void hashing_is_deterministic() {
        AuditDetails d1 = AuditDetails.of(Map.of(), Map.of("x", "value"));
        AuditDetails d2 = AuditDetails.of(Map.of(), Map.of("x", "value"));

        AuditEvent first = Tier1HashTransformer.toTier1(envelope(Tier.DOMAIN, d1));
        AuditEvent second = Tier1HashTransformer.toTier1(envelope(Tier.DOMAIN, d2));

        assertThat(first.details().content()).isEqualTo(second.details().content());
    }

    @Test
    void supersedes_links_are_preserved() {
        AuditDetails original = new AuditDetails(
                Map.of("k", "v"),
                Map.of("rawText", "content"),
                "01HMQX9V3K5T7Z9N2B4D6F8H0J",
                null);
        AuditEvent input = envelope(Tier.DOMAIN, original);

        AuditEvent out = Tier1HashTransformer.toTier1(input);

        assertThat(out.details().supersedes()).isEqualTo(original.supersedes());
        assertThat(out.details().supersededBy()).isNull();
    }

    private static AuditEvent envelope(Tier tier, AuditDetails details) {
        return new AuditEvent(
                "01HMQX9V3K5T7Z9N2B4D6F8H0J",
                "DOCUMENT_CLASSIFIED",
                tier,
                AuditEvent.CURRENT_SCHEMA_VERSION,
                Instant.parse("2026-04-26T22:00:00Z"),
                null, null, null, null,
                Actor.system("gls-app-assembly", "1.0.0", "pod-abc"),
                Resource.of(ResourceType.DOCUMENT, "doc_1"),
                "CLASSIFY",
                Outcome.SUCCESS,
                details,
                "7Y",
                null);
    }
}
