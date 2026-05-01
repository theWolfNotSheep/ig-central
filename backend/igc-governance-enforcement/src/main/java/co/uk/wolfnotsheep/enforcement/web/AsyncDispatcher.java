package co.uk.wolfnotsheep.enforcement.web;

import co.uk.wolfnotsheep.enforcement.model.EnforceRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Indirection so {@link EnforceController#runAsync} can be invoked
 * via Spring's {@code @Async} proxy without the controller
 * self-injecting. Mirrors the slm-worker / classifier-router pattern.
 */
@Component
public class AsyncDispatcher {

    private final ObjectProvider<EnforceController> controllerProvider;

    public AsyncDispatcher(ObjectProvider<EnforceController> controllerProvider) {
        this.controllerProvider = controllerProvider;
    }

    @Async("enforcementAsyncExecutor")
    public void dispatch(EnforceRequest request) {
        controllerProvider.getObject().runAsync(request);
    }
}
