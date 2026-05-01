package co.uk.wolfnotsheep.extraction.audio.sink;

import java.net.URI;

public record ExtractedTextRef(URI uri, long contentLength, String contentType) {
}
