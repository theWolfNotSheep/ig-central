package co.uk.wolfnotsheep.infrastructure.services.drives;

import co.uk.wolfnotsheep.document.models.ConnectedDrive;
import co.uk.wolfnotsheep.document.models.StorageProviderType;
import co.uk.wolfnotsheep.document.services.StorageProviderService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of all available storage provider implementations.
 * Provides lookup by provider type or connected drive.
 */
@Service
public class StorageProviderRegistry {

    private final Map<StorageProviderType, StorageProviderService> providers;

    public StorageProviderRegistry(List<StorageProviderService> services) {
        this.providers = services.stream()
                .collect(Collectors.toMap(StorageProviderService::getType, Function.identity()));
    }

    public StorageProviderService get(StorageProviderType type) {
        StorageProviderService provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No storage provider registered for type: " + type);
        }
        return provider;
    }

    public StorageProviderService get(ConnectedDrive drive) {
        StorageProviderType type = drive.getProviderType();
        if (type == null) {
            // Fallback for legacy drives with string-only provider
            type = StorageProviderType.valueOf(drive.getProvider());
        }
        return get(type);
    }

    public boolean isSupported(StorageProviderType type) {
        return providers.containsKey(type);
    }
}
