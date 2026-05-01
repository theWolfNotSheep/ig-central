package co.uk.wolfnotsheep.extraction.ocr.source;

import java.io.InputStream;

public interface DocumentSource {
    InputStream open(DocumentRef ref);
    long sizeOf(DocumentRef ref);
}
