package co.uk.wolfnotsheep.llmworker.backend;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads PROMPT block content from the {@code pipeline_blocks} Mongo
 * collection without pulling in the igc-governance domain shape.
 * The collection is the same one the orchestrator and admin UI write
 * to (architecture §3 — block library is the source of truth); we
 * just read it through a minimal projection so the LLM worker stays
 * decoupled from governance class evolution.
 *
 * <p>A {@code PROMPT} block's content carries (at least)
 * {@code systemPrompt} and {@code userPromptTemplate} string keys.
 * Extra keys are ignored. The version selected is either the pinned
 * {@code blockVersion} from the request, or {@code activeVersion}
 * from the block document.
 */
@Component
public class PromptBlockResolver {

    private static final String COLLECTION = "pipeline_blocks";

    private final MongoTemplate mongo;

    public PromptBlockResolver(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * Resolve a prompt block to its system / user prompt pair.
     *
     * @throws BlockUnknownException if the block id doesn't resolve, isn't a
     *         PROMPT block, or the requested version doesn't exist.
     */
    public ResolvedPrompt resolve(String blockId, Integer pinnedVersion) {
        if (blockId == null || blockId.isBlank()) {
            throw new BlockUnknownException("blockId is required");
        }
        Query q = new Query(Criteria.where("_id").is(blockId));
        Document doc = mongo.findOne(q, Document.class, COLLECTION);
        if (doc == null) {
            throw new BlockUnknownException("no pipeline_blocks row for id=" + blockId);
        }
        String type = doc.getString("type");
        if (type != null && !"PROMPT".equalsIgnoreCase(type)) {
            throw new BlockUnknownException(
                    "block " + blockId + " is type=" + type + ", expected PROMPT");
        }

        int targetVersion = pinnedVersion != null
                ? pinnedVersion
                : Optional.ofNullable(doc.getInteger("activeVersion")).orElse(0);

        Map<String, Object> content = contentForVersion(doc, targetVersion);
        if (content == null) {
            throw new BlockUnknownException(
                    "block " + blockId + " has no content for version=" + targetVersion);
        }

        String systemPrompt = stringField(content, "systemPrompt");
        String userTemplate = stringField(content, "userPromptTemplate");
        if (systemPrompt == null && userTemplate == null) {
            throw new BlockUnknownException(
                    "block " + blockId + " v" + targetVersion
                            + " carries neither systemPrompt nor userPromptTemplate");
        }
        return new ResolvedPrompt(blockId, targetVersion, systemPrompt, userTemplate);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> contentForVersion(Document doc, int targetVersion) {
        List<Document> versions = (List<Document>) doc.get("versions");
        if (versions != null) {
            for (Document v : versions) {
                Integer ver = v.getInteger("version");
                if (ver != null && ver == targetVersion) {
                    Object content = v.get("content");
                    if (content instanceof Document d) {
                        return d;
                    }
                    if (content instanceof Map<?, ?> m) {
                        return (Map<String, Object>) m;
                    }
                }
            }
        }
        // Some legacy rows hold {draftContent} when no versions exist yet —
        // accept that as a fallback so dev / test data isn't blocked.
        Object draft = doc.get("draftContent");
        if (draft instanceof Document d) return d;
        if (draft instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }

    private static String stringField(Map<String, Object> content, String key) {
        Object v = content.get(key);
        if (v == null) return null;
        String s = v.toString();
        return s.isBlank() ? null : s;
    }

    public record ResolvedPrompt(
            String blockId,
            int blockVersion,
            String systemPrompt,
            String userPromptTemplate) {
    }
}
