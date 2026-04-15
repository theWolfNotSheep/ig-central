package co.uk.wolfnotsheep.infrastructure.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DTOs for deserializing Hub pack data. These mirror the Hub's models
 * without creating a compile-time dependency on the Hub module.
 */
public class HubPackDto {

    public record PackVersionDto(
            String id,
            String packId,
            int versionNumber,
            String changelog,
            String publishedBy,
            String publishedAt,
            List<PackComponentDto> components,
            String compatibilityVersion
    ) {}

    public record PackComponentDto(
            String type,
            String name,
            String description,
            int itemCount,
            List<Map<String, Object>> data
    ) {}
}
