package co.uk.wolfnotsheep.extraction.audio.web;

import co.uk.wolfnotsheep.extraction.audio.api.MetaApi;
import co.uk.wolfnotsheep.extraction.audio.model.GetCapabilities200Response;
import co.uk.wolfnotsheep.extraction.audio.model.HealthResponse;
import co.uk.wolfnotsheep.extraction.audio.parse.AudioTranscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetaControllerTest {

    @Test
    void capabilities_advertises_service_identity_and_default_tier() {
        AudioTranscriptionService backend = mock(AudioTranscriptionService.class);
        when(backend.isReady()).thenReturn(true);
        MetaController controller = new MetaController("gls-extraction-audio", "0.0.1-SNAPSHOT", backend);

        ResponseEntity<GetCapabilities200Response> resp = controller.getCapabilities();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        GetCapabilities200Response body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getService()).isEqualTo("gls-extraction-audio");
        assertThat(body.getVersion()).isEqualTo("0.0.1-SNAPSHOT");
        assertThat(body.getTiers()).containsExactly("default");
    }

    @Test
    void health_returns_UP_when_backend_is_ready() {
        AudioTranscriptionService backend = mock(AudioTranscriptionService.class);
        when(backend.isReady()).thenReturn(true);
        MetaController controller = new MetaController("gls-extraction-audio", "0.0.1", backend);

        ResponseEntity<HealthResponse> resp = controller.getHealth();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getStatus()).isEqualTo(HealthResponse.StatusEnum.UP);
    }

    @Test
    void health_returns_OUT_OF_SERVICE_503_when_backend_not_ready() {
        AudioTranscriptionService backend = mock(AudioTranscriptionService.class);
        when(backend.isReady()).thenReturn(false);
        MetaController controller = new MetaController("gls-extraction-audio", "0.0.1", backend);

        ResponseEntity<HealthResponse> resp = controller.getHealth();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody().getStatus()).isEqualTo(HealthResponse.StatusEnum.OUT_OF_SERVICE);
    }

    @Test
    void controller_implements_generated_meta_api() {
        assertThat(MetaApi.class.isAssignableFrom(MetaController.class)).isTrue();
    }
}
