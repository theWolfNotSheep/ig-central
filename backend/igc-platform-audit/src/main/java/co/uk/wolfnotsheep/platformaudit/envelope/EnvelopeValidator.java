package co.uk.wolfnotsheep.platformaudit.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runtime guard that validates an {@link AuditEvent} against
 * {@code contracts/audit/event-envelope.schema.json} before it leaves the
 * library.
 *
 * <p>The schema is bundled into the jar at build time (see this module's
 * {@code pom.xml}) so the validator works the same way in tests, in the
 * api container, and in any other consumer — no filesystem dependency on
 * the {@code contracts/} tree at runtime.
 *
 * <p>Construction parses and compiles the schema once. {@link #validate}
 * is cheap to call repeatedly. Instances are thread-safe — both the
 * compiled {@link JsonSchema} and the configured {@link ObjectMapper} are
 * safe to share across threads.
 */
public final class EnvelopeValidator {

    private static final String SCHEMA_RESOURCE = "/schemas/event-envelope.schema.json";

    private final Schema schema;
    private final ObjectMapper mapper;

    /**
     * Loads the bundled envelope schema and returns a validator ready to use.
     *
     * @throws IllegalStateException if the schema resource is missing from
     *                               the classpath — indicates a broken Maven
     *                               build, not a caller error.
     */
    public static EnvelopeValidator fromBundledSchema() {
        SchemaRegistry registry = SchemaRegistry.withDialect(Dialects.getDraft202012());
        try (InputStream in = EnvelopeValidator.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Bundled audit envelope schema not found on classpath at "
                                + SCHEMA_RESOURCE
                                + " — check the igc-platform-audit Maven build.");
            }
            Schema compiled = registry.getSchema(in);
            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .addMixIn(AuditEvent.class, AuditEventValidationMixin.class);
            return new EnvelopeValidator(compiled, mapper);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load bundled audit envelope schema", e);
        }
    }

    /**
     * Jackson mixin: forces {@code previousEventHash} to be serialised even
     * when null. The schema requires the property to be <em>present</em>
     * (value either null or a sha256 hash) for {@code tier=DOMAIN}
     * first-in-chain events; with the global NON_NULL inclusion we'd
     * otherwise strip it and fail validation.
     */
    @SuppressWarnings("unused")
    abstract static class AuditEventValidationMixin {
        @JsonInclude(JsonInclude.Include.ALWAYS)
        abstract String previousEventHash();
    }

    EnvelopeValidator(Schema schema, ObjectMapper mapper) {
        this.schema = schema;
        this.mapper = mapper;
    }

    /**
     * Validate an envelope against the schema.
     *
     * @throws IllegalArgumentException if the envelope fails validation. The
     *                                  message lists every violation (joined
     *                                  with {@code "; "}) so the caller can
     *                                  fix all of them in one pass.
     */
    public void validate(AuditEvent envelope) {
        JsonNode tree = mapper.valueToTree(envelope);
        List<Error> errors = schema.validate(tree);
        if (!errors.isEmpty()) {
            String summary = errors.stream()
                    .map(Error::getMessage)
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException(
                    "audit envelope failed schema validation: " + summary);
        }
    }
}
