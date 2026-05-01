package co.uk.wolfnotsheep.bert.inference;

/**
 * Block coordinates didn't resolve to a registered artefact. Mapped
 * to RFC 7807 422 / {@code BLOCK_UNKNOWN}.
 */
public class BlockUnknownException extends RuntimeException {
    public BlockUnknownException(String message) {
        super(message);
    }
}
