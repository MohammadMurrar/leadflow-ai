package com.mohammadmurrar.leadflow.service.api;

import com.mohammadmurrar.leadflow.service.ServiceOffering;
import java.util.UUID;

public record ServiceOptionResponse(UUID id, String name) {
    public static ServiceOptionResponse from(ServiceOffering offering) {
        return new ServiceOptionResponse(offering.getId(), offering.getName());
    }
}
