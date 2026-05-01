package co.uk.wolfnotsheep.extraction.ocr.sink;

public interface DocumentSink {
    ExtractedTextRef upload(String nodeRunId, String text);
}
