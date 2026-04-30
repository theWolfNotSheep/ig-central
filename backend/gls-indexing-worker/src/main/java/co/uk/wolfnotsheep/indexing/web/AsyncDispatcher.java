package co.uk.wolfnotsheep.indexing.web;

import co.uk.wolfnotsheep.indexing.model.ReindexRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AsyncDispatcher {

    private final ObjectProvider<ReindexController> controllerProvider;

    public AsyncDispatcher(ObjectProvider<ReindexController> controllerProvider) {
        this.controllerProvider = controllerProvider;
    }

    @Async("indexingAsyncExecutor")
    public void dispatch(ReindexRequest request) {
        controllerProvider.getObject().runAsync(request);
    }
}
