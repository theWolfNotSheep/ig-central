package co.uk.wolfnotsheep.extraction.audio.web;

import co.uk.wolfnotsheep.extraction.audio.model.ExtractRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Wraps the {@code @Async} call so the proxy boundary lives in a
 * separate bean from {@link ExtractController} (Spring's AOP doesn't
 * advise self-invocations from within the same bean). Reads the
 * controller via {@link ObjectProvider} to avoid a constructor cycle.
 */
@Component
public class AsyncDispatcher {

    private final ObjectProvider<ExtractController> controllerProvider;

    public AsyncDispatcher(ObjectProvider<ExtractController> controllerProvider) {
        this.controllerProvider = controllerProvider;
    }

    @Async("audioAsyncExecutor")
    public void dispatch(ExtractRequest request, String traceparent) {
        controllerProvider.getObject().runAsync(request, traceparent);
    }
}
