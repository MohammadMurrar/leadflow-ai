package com.mohammadmurrar.leadflow.service.api;

import jakarta.validation.constraints.PositiveOrZero;

public record ServiceVersionRequest(@PositiveOrZero long version) {}
