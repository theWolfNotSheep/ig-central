package co.uk.wolfnotsheep.extraction.audio.sink;

public interface DocumentSink {
    ExtractedTextRef upload(String nodeRunId, String text);
}
