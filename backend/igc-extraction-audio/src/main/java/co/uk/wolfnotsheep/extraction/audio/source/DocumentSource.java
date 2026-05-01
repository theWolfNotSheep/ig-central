package co.uk.wolfnotsheep.extraction.audio.source;

import java.io.InputStream;

public interface DocumentSource {
    InputStream open(DocumentRef ref);
    long sizeOf(DocumentRef ref);
}
