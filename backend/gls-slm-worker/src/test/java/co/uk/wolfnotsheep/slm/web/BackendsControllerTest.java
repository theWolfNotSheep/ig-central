package co.uk.wolfnotsheep.slm.web;

import co.uk.wolfnotsheep.slm.backend.NotConfiguredSlmService;
import co.uk.wolfnotsheep.slm.backend.SlmBackendId;
import co.uk.wolfnotsheep.slm.backend.SlmService;
import co.uk.wolfnotsheep.slm.model.BackendInfo;
import co.uk.wolfnotsheep.slm.model.BackendsResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackendsControllerTest {

    @Test
    void not_configured_stub_reports_active_NONE_with_both_backends_listed_unready() {
        BackendsController controller = new BackendsController(new NotConfiguredSlmService());
        BackendsResponse body = controller.listBackends().getBody();

        assertThat(body.getActive()).isEqualTo(BackendsResponse.ActiveEnum.NONE);
        assertThat(body.getAvailable()).hasSize(2);
        assertThat(body.getAvailable())
                .extracting(BackendInfo::getId, BackendInfo::getReady)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(BackendInfo.IdEnum.ANTHROPIC_HAIKU, false),
                        org.assertj.core.groups.Tuple.tuple(BackendInfo.IdEnum.OLLAMA, false));
    }

    @Test
    void active_anthropic_translates_to_api_enum() {
        SlmService backend = mock(SlmService.class);
        when(backend.activeBackend()).thenReturn(SlmBackendId.ANTHROPIC_HAIKU);

        BackendsController controller = new BackendsController(backend);
        BackendsResponse body = controller.listBackends().getBody();
        assertThat(body.getActive()).isEqualTo(BackendsResponse.ActiveEnum.ANTHROPIC_HAIKU);
    }

    @Test
    void active_ollama_translates_to_api_enum() {
        SlmService backend = mock(SlmService.class);
        when(backend.activeBackend()).thenReturn(SlmBackendId.OLLAMA);

        BackendsController controller = new BackendsController(backend);
        BackendsResponse body = controller.listBackends().getBody();
        assertThat(body.getActive()).isEqualTo(BackendsResponse.ActiveEnum.OLLAMA);
    }
}
