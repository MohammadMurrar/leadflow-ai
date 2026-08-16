package com.mohammadmurrar.leadflow.service.api;

import com.mohammadmurrar.leadflow.service.ServiceOffering;
import java.time.Instant;
import java.util.UUID;

public record ServiceResponse(UUID id, long version, String name, String description,
                              boolean active, Instant createdAt, Instant updatedAt) {
    public static ServiceResponse from(ServiceOffering offering) {
        return new ServiceResponse(offering.getId(), offering.getVersion(), offering.getName(),
                offering.getDescription(), offering.isActive(), offering.getCreatedAt(), offering.getUpdatedAt());
    }
}
