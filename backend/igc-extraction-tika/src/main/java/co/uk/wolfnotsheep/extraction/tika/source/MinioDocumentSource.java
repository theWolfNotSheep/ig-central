package co.uk.wolfnotsheep.extraction.tika.source;

import io.micrometer.observation.annotation.Observed;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * MinIO-backed {@link DocumentSource}. Wraps the official MinIO Java SDK.
 *
 * <p>The {@link MinioClient} bean is provided by Spring config (lives
 * separately so tests can supply a stub source without touching the
 * SDK at all). This class is intentionally thin — its job is the
 * mapping from contract-level errors (not-found, etag-mismatch) to the
 * exceptions the controller knows how to translate.
 */
public class MinioDocumentSource implements DocumentSource {

    private static final Logger log = LoggerFactory.getLogger(MinioDocumentSource.class);
    private static final String NOT_FOUND_CODE = "NoSuchKey";

    private final MinioClient minio;

    public MinioDocumentSource(MinioClient minio) {
        this.minio = minio;
    }

    @Override
    @Observed(name = "minio.fetch",
            contextualName = "minio-fetch",
            lowCardinalityKeyValues = {"component", "minio"})
    public InputStream open(DocumentRef ref) {
        if (ref.etag() != null && !ref.etag().isBlank()) {
            verifyEtag(ref);
        }
        try {
            return minio.getObject(GetObjectArgs.builder()
                    .bucket(ref.bucket())
                    .object(ref.objectKey())
                    .build());
        } catch (ErrorResponseException e) {
            if (NOT_FOUND_CODE.equals(e.errorResponse().code())) {
                throw new DocumentNotFoundException(ref);
            }
            throw new UncheckedIOException("MinIO get failed: " + ref, asIo(e));
        } catch (IOException e) {
            throw new UncheckedIOException("MinIO get failed: " + ref, e);
        } catch (Exception e) {
            throw new UncheckedIOException("MinIO get failed: " + ref, asIo(e));
        }
    }

    @Override
    public long sizeOf(DocumentRef ref) {
        try {
            StatObjectResponse stat = minio.statObject(StatObjectArgs.builder()
                    .bucket(ref.bucket())
                    .object(ref.objectKey())
                    .build());
            return stat.size();
        } catch (ErrorResponseException e) {
            if (NOT_FOUND_CODE.equals(e.errorResponse().code())) {
                return -1L;
            }
            log.warn("minio stat failed for {}: {}", ref, e.getMessage());
            return -1L;
        } catch (Exception e) {
            log.warn("minio stat failed for {}: {}", ref, e.getMessage());
            return -1L;
        }
    }

    private void verifyEtag(DocumentRef ref) {
        try {
            StatObjectResponse stat = minio.statObject(StatObjectArgs.builder()
                    .bucket(ref.bucket())
                    .object(ref.objectKey())
                    .build());
            String actual = stat.etag();
            if (!ref.etag().equals(actual)) {
                throw new DocumentEtagMismatchException(ref, actual);
            }
        } catch (ErrorResponseException e) {
            if (NOT_FOUND_CODE.equals(e.errorResponse().code())) {
                throw new DocumentNotFoundException(ref);
            }
            throw new UncheckedIOException("MinIO stat failed: " + ref, asIo(e));
        } catch (DocumentNotFoundException | DocumentEtagMismatchException pass) {
            throw pass;
        } catch (Exception e) {
            throw new UncheckedIOException("MinIO stat failed: " + ref, asIo(e));
        }
    }

    private static IOException asIo(Throwable t) {
        if (t instanceof IOException io) return io;
        return new IOException(t);
    }
}
