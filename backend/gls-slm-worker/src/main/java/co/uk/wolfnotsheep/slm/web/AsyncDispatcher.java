package co.uk.wolfnotsheep.slm.web;

import co.uk.wolfnotsheep.slm.model.ClassifyRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AsyncDispatcher {

    private final ObjectProvider<ClassifyController> controllerProvider;

    public AsyncDispatcher(ObjectProvider<ClassifyController> controllerProvider) {
        this.controllerProvider = controllerProvider;
    }

    @Async("slmAsyncExecutor")
    public void dispatch(ClassifyRequest request, String traceparent) {
        controllerProvider.getObject().runAsync(request, traceparent);
    }
}
