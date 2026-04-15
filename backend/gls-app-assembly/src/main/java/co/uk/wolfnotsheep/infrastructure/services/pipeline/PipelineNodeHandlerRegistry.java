package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators.AcceleratorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Collects all {@link AcceleratorHandler} and {@link PipelineNodeHandler} Spring beans
 * into lookup maps keyed by node type key.
 *
 * Follows the same pattern as StorageProviderRegistry.
 */
@Service
public class PipelineNodeHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(PipelineNodeHandlerRegistry.class);

    private final Map<String, AcceleratorHandler> accelerators;
    private final Map<String, PipelineNodeHandler> handlers;

    public PipelineNodeHandlerRegistry(List<AcceleratorHandler> acceleratorBeans,
                                        List<PipelineNodeHandler> handlerBeans) {
        this.accelerators = acceleratorBeans.stream()
                .collect(Collectors.toMap(AcceleratorHandler::getNodeTypeKey, Function.identity()));
        this.handlers = handlerBeans.stream()
                .collect(Collectors.toMap(PipelineNodeHandler::getNodeTypeKey, Function.identity()));

        log.info("Registered {} accelerator handlers: {}", accelerators.size(), accelerators.keySet());
        log.info("Registered {} node handlers: {}", handlers.size(), handlers.keySet());
    }

    public Optional<AcceleratorHandler> getAccelerator(String nodeTypeKey) {
        return Optional.ofNullable(accelerators.get(nodeTypeKey));
    }

    public Optional<PipelineNodeHandler> getHandler(String nodeTypeKey) {
        return Optional.ofNullable(handlers.get(nodeTypeKey));
    }
}
