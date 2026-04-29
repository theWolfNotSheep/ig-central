package co.uk.wolfnotsheep.router.parse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Composes the active {@link CascadeService} bean. The cascade is
 * built outside-in: each tier wraps the next, with the inner-most
 * tier always being the LLM dispatch service (or the mock when LLM
 * isn't enabled).
 *
 * <p>Composition order, from outermost to innermost: BERT → SLM →
 * LLM (or mock). Each tier is independently activated by its own
 * feature flag, so the cascade can be turned on one tier at a time:
 *
 * <ul>
 *     <li>{@code bert.enabled=true} + {@code slm.enabled=true} +
 *         {@code llm.enabled=true} — production-like full cascade.
 *         BERT first; on {@code MODEL_NOT_LOADED} → SLM; on
 *         {@code SLM_NOT_CONFIGURED} → LLM.</li>
 *     <li>Any subset of those flags off — the matching tier is
 *         skipped; the cascade goes straight from the outer enabled
 *         tier to the next-inner enabled tier.</li>
 *     <li>Default (all flags off) — mock cascade only. Phase 1.2
 *         first cut shape.</li>
 * </ul>
 *
 * <p>The mock is always registered as a bean so it can be the
 * fallthrough target whether or not LLM is enabled. All three tier
 * services are conditional on their own feature flags.
 */
@Configuration
public class CascadeBackendConfig {

    @Bean
    public MockCascadeService mockCascadeService() {
        return new MockCascadeService();
    }

    @Bean
    @Primary
    public CascadeService cascadeService(
            MockCascadeService mock,
            ObjectProvider<LlmDispatchCascadeService> llmProvider,
            ObjectProvider<BertHttpDispatcher> bertDispatcherProvider,
            ObjectProvider<SlmHttpDispatcher> slmDispatcherProvider) {

        LlmDispatchCascadeService llm = llmProvider.getIfAvailable();
        CascadeService cascade = llm != null ? llm : mock;

        SlmHttpDispatcher slmDispatcher = slmDispatcherProvider.getIfAvailable();
        if (slmDispatcher != null) {
            cascade = new SlmOrchestratorCascadeService(slmDispatcher, cascade);
        }

        BertHttpDispatcher bertDispatcher = bertDispatcherProvider.getIfAvailable();
        if (bertDispatcher != null) {
            cascade = new BertOrchestratorCascadeService(bertDispatcher, cascade);
        }

        return cascade;
    }
}
