package com.mohammadmurrar.leadflow.qualification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties("leadflow.qualification-reliability")
public record QualificationReliabilityProperties(
        boolean dispatcherEnabled,
        boolean legacyCallbackEnabled,
        boolean retryEnabled,
        Duration dispatcherInterval,
        int batchSize,
        int maximumDeliveryAttempts,
        Duration initialBackoff,
        Duration maximumBackoff,
        Duration leaseDuration,
        Duration dispatchTimeout,
        Duration processingTimeout,
        int timeoutBatchSize) {
}
