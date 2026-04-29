package co.uk.wolfnotsheep.router.web;

import co.uk.wolfnotsheep.router.model.ClassifyRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Wraps the {@code @Async} call so the proxy boundary lives in a
 * separate bean from {@link ClassifyController} (Spring's AOP doesn't
 * advise self-invocations from within the same bean). Reads the
 * controller via {@link ObjectProvider} to avoid a constructor cycle.
 */
@Component
public class AsyncDispatcher {

    private final ObjectProvider<ClassifyController> controllerProvider;

    public AsyncDispatcher(ObjectProvider<ClassifyController> controllerProvider) {
        this.controllerProvider = controllerProvider;
    }

    @Async("routerAsyncExecutor")
    public void dispatch(ClassifyRequest request, String traceparent) {
        controllerProvider.getObject().runAsync(request, traceparent);
    }
}
