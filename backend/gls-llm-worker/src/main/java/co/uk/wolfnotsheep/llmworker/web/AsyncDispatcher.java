package co.uk.wolfnotsheep.llmworker.web;

import co.uk.wolfnotsheep.llmworker.model.ClassifyRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AsyncDispatcher {

    private final ObjectProvider<ClassifyController> controllerProvider;

    public AsyncDispatcher(ObjectProvider<ClassifyController> controllerProvider) {
        this.controllerProvider = controllerProvider;
    }

    @Async("llmAsyncExecutor")
    public void dispatch(ClassifyRequest request, String traceparent) {
        controllerProvider.getObject().runAsync(request, traceparent);
    }
}
