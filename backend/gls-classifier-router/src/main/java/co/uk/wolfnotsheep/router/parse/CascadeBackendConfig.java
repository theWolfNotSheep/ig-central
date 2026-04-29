package co.uk.wolfnotsheep.router.parse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Composes the active {@link CascadeService} bean. Order of
 * preference:
 *
 * <ol>
 *     <li>BERT orchestrator wrapping LLM ({@code bert.enabled=true} +
 *         {@code llm.enabled=true}). Production-like: BERT tier first,
 *         falls through to LLM on {@code MODEL_NOT_LOADED}.</li>
 *     <li>BERT orchestrator wrapping the mock
 *         ({@code bert.enabled=true} + {@code llm.enabled=false}).
 *         Useful for local dev when the LLM worker isn't running.</li>
 *     <li>LLM-direct ({@code bert.enabled=false} +
 *         {@code llm.enabled=true}). Phase 1.2 / 1.3 cutover shape.</li>
 *     <li>Mock-only (default). Phase 1.2 first cut shape.</li>
 * </ol>
 *
 * <p>The mock is always registered as a bean so it can be the
 * fallthrough target whether or not LLM is enabled. The other two
 * tier services are conditional on their own feature flags.
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
            ObjectProvider<BertHttpDispatcher> bertDispatcherProvider) {
        LlmDispatchCascadeService llm = llmProvider.getIfAvailable();
        CascadeService inner = llm != null ? llm : mock;
        BertHttpDispatcher bertDispatcher = bertDispatcherProvider.getIfAvailable();
        if (bertDispatcher != null) {
            return new BertOrchestratorCascadeService(bertDispatcher, inner);
        }
        return inner;
    }
}
