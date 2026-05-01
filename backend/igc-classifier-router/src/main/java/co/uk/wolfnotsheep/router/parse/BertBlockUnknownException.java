package co.uk.wolfnotsheep.router.parse;

/**
 * Thrown when bert-inference returns 422 — the BERT_CLASSIFIER block
 * coords don't resolve, or the loaded model doesn't match the
 * block's declared model version. Surfaced to the caller as 422
 * {@code ROUTER_BERT_BLOCK_UNKNOWN}; this is a configuration error
 * that should not silently fall through (the LLM tier can't make up
 * for a broken block).
 */
public class BertBlockUnknownException extends RuntimeException {

    public BertBlockUnknownException(String message) {
        super(message);
    }
}
