package co.uk.wolfnotsheep.extraction.ocr.parse;

import io.micrometer.observation.annotation.Observed;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Default OCR engine — Tesseract via Tess4J (CSV #45). Wraps the
 * {@code tesseract} binary installed on the runtime image (apt
 * package {@code tesseract-ocr} + the language packs in {@code tessdata}).
 *
 * <p>Tess4J's {@link Tesseract} loads the libtesseract JNA bridge
 * lazily on first use; this service is constructable in environments
 * without the binary, but {@link #run} fails with
 * {@link UnparseableImageException} on first invocation if it isn't
 * present. The companion health indicator surfaces the absence at
 * readiness probe time so callers don't get hit with the failure
 * cold.
 *
 * <p>Per-page handling: Tess4J accepts PDF input directly via
 * {@link Tesseract#doOCR(java.io.File)} on the materialised file —
 * each page is OCR'd in turn and the results are concatenated. The
 * {@code pageCount} we surface comes from the source's PDF page
 * count when known.
 */
@Service
public class Tess4JOcrExtractionService implements OcrExtractionService {

    private static final Logger log = LoggerFactory.getLogger(Tess4JOcrExtractionService.class);
    private static final List<String> DEFAULT_LANGUAGES = List.of("eng");

    private final Tika tika = new Tika();
    private final String tessdataPath;

    public Tess4JOcrExtractionService(
            @Value("${gls.extraction.ocr.tessdata-path:/usr/share/tesseract-ocr/4.00/tessdata}") String tessdataPath) {
        this.tessdataPath = tessdataPath;
    }

    @Override
    @Observed(name = "ocr.run", contextualName = "ocr-run",
            lowCardinalityKeyValues = {"component", "tesseract"})
    public OcrResult run(InputStream input, String fileName, List<String> languages) {
        List<String> effective = languages == null || languages.isEmpty()
                ? DEFAULT_LANGUAGES
                : new ArrayList<>(languages);

        Path temp;
        long byteCount;
        String detectedMime;
        try {
            temp = Files.createTempFile("gls-ocr-", suffixFor(fileName));
            try (input) {
                byteCount = Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            detectedMime = tika.detect(temp.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("OCR temp materialisation failed", e);
        }

        try {
            ITesseract tess = new Tesseract();
            tess.setDatapath(tessdataPath);
            tess.setLanguage(String.join("+", effective));
            String text;
            Integer pageCount = null;
            try {
                text = tess.doOCR(temp.toFile());
                if ("application/pdf".equals(detectedMime)) {
                    pageCount = countPdfPages(temp);
                }
            } catch (TesseractException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                if (msg.contains("language") || msg.contains("tessdata")) {
                    throw new OcrLanguageUnsupportedException(
                            "Tesseract rejected language(s) " + effective + ": " + e.getMessage());
                }
                throw new UnparseableImageException(
                        "Tesseract could not OCR the document: " + e.getMessage(), e);
            } catch (UnsatisfiedLinkError nativeMissing) {
                // libtesseract isn't loadable — this is a deploy-time
                // failure, surface as 503 via UncheckedIOException so
                // the readiness gate flips DOWN.
                throw new UncheckedIOException("Tesseract native library not loadable: "
                        + nativeMissing.getMessage(), new IOException(nativeMissing));
            }
            return new OcrResult(text == null ? "" : text, detectedMime, effective, pageCount, byteCount);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                log.warn("OCR temp file cleanup failed: {} ({})", temp, e.getMessage());
            }
        }
    }

    private static String suffixFor(String fileName) {
        if (fileName == null) return ".bin";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return ".bin";
        String ext = fileName.substring(dot);
        return ext.length() > 6 ? ".bin" : ext;
    }

    private static Integer countPdfPages(Path pdf) {
        // Avoid pulling pdfbox into this thin parser. Tika's mime
        // detect on a PDF surfaces nothing usable for page count
        // without parsing the body, and Tess4J doesn't expose a page
        // count directly. Best-effort: a follow-up adds pdfbox if
        // page-count metric is needed.
        return null;
    }
}
