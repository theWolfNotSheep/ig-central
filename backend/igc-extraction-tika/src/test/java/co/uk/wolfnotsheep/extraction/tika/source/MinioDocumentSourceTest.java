package co.uk.wolfnotsheep.extraction.tika.source;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MinioDocumentSource} via a mocked
 * {@link MinioClient}. Real broker semantics are exercised in 0.5.4
 * integration tests once those land.
 */
class MinioDocumentSourceTest {

    private MinioClient minio;
    private MinioDocumentSource source;

    @BeforeEach
    void setUp() {
        minio = mock(MinioClient.class);
        source = new MinioDocumentSource(minio);
    }

    @Test
    void open_streams_object_bytes() throws Exception {
        DocumentRef ref = DocumentRef.of("bucket", "key");
        InputStream payload = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));
        when(minio.getObject(any(GetObjectArgs.class))).thenReturn(getObjectResponse(payload));

        try (InputStream in = source.open(ref)) {
            byte[] bytes = in.readAllBytes();
            assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("hello");
        }
    }

    @Test
    void missing_object_raises_DocumentNotFoundException() throws Exception {
        DocumentRef ref = DocumentRef.of("bucket", "missing");
        ErrorResponseException notFoundError = notFound();
        when(minio.getObject(any(GetObjectArgs.class))).thenThrow(notFoundError);

        assertThatThrownBy(() -> source.open(ref))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void other_minio_error_surfaces_as_unchecked_io() throws Exception {
        DocumentRef ref = DocumentRef.of("bucket", "key");
        when(minio.getObject(any(GetObjectArgs.class)))
                .thenThrow(new IOException("network borked"));

        assertThatThrownBy(() -> source.open(ref))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void etag_check_passes_when_storage_matches() throws Exception {
        DocumentRef ref = DocumentRef.withEtag("bucket", "key", "abc123");
        StatObjectResponse stat = statResponse("abc123", 5);
        GetObjectResponse get = getObjectResponse(
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        when(minio.statObject(any(StatObjectArgs.class))).thenReturn(stat);
        when(minio.getObject(any(GetObjectArgs.class))).thenReturn(get);

        try (InputStream in = source.open(ref)) {
            assertThat(in.readAllBytes()).hasSize(5);
        }
    }

    @Test
    void etag_check_fails_on_mismatch() throws Exception {
        DocumentRef ref = DocumentRef.withEtag("bucket", "key", "expected");
        StatObjectResponse stat = statResponse("actual", 5);
        when(minio.statObject(any(StatObjectArgs.class))).thenReturn(stat);

        assertThatThrownBy(() -> source.open(ref))
                .isInstanceOf(DocumentEtagMismatchException.class)
                .hasMessageContaining("expected")
                .hasMessageContaining("actual");
    }

    @Test
    void sizeOf_returns_storage_size() throws Exception {
        StatObjectResponse stat = statResponse("etag", 42);
        when(minio.statObject(any(StatObjectArgs.class))).thenReturn(stat);

        long size = source.sizeOf(DocumentRef.of("bucket", "key"));

        assertThat(size).isEqualTo(42);
    }

    @Test
    void sizeOf_returns_minus_one_for_missing_object() throws Exception {
        ErrorResponseException notFoundError = notFound();
        when(minio.statObject(any(StatObjectArgs.class))).thenThrow(notFoundError);

        long size = source.sizeOf(DocumentRef.of("bucket", "missing"));

        assertThat(size).isEqualTo(-1L);
    }

    @Test
    void document_ref_construction_validates_inputs() {
        assertThatThrownBy(() -> DocumentRef.of("", "k")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DocumentRef.of("b", "")).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- helpers --------------------------------------------------------

    /**
     * GetObjectResponse's only public constructor is package-private from
     * the consumer's perspective. We construct via reflection so the test
     * can supply an arbitrary stream.
     */
    private GetObjectResponse getObjectResponse(InputStream body) {
        try {
            return GetObjectResponse.class
                    .getDeclaredConstructor(Headers.class, String.class, String.class, String.class, InputStream.class)
                    .newInstance(Headers.of(), "bucket", null, "key", body);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("MinIO SDK GetObjectResponse signature changed", e);
        }
    }

    /**
     * StatObjectResponse setters are not public either; reflective field
     * writes keep the test independent of the SDK's internal API.
     */
    private StatObjectResponse statResponse(String etag, long size) {
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.etag()).thenReturn(etag);
        when(stat.size()).thenReturn(size);
        return stat;
    }

    private ErrorResponseException notFound() {
        ErrorResponse error = mock(ErrorResponse.class);
        when(error.code()).thenReturn("NoSuchKey");
        ErrorResponseException ere = mock(ErrorResponseException.class);
        when(ere.errorResponse()).thenReturn(error);
        when(ere.getMessage()).thenReturn("not found");
        return ere;
    }

    @SuppressWarnings("unused")
    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
