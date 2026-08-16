package com.mohammadmurrar.leadflow.qualification.api;

import jakarta.validation.constraints.PositiveOrZero;

public record QualificationRetryRequest(@PositiveOrZero long version) {
}
