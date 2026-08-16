package com.mohammadmurrar.leadflow.qualification;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;

@Component
public class QualificationReliabilityScheduler {
    private final QualificationDispatchService dispatchService;
    private final QualificationAttemptRepository attemptRepository;
    private final QualificationAttemptService attemptService;
    private final QualificationReliabilityProperties properties;

    public QualificationReliabilityScheduler(QualificationDispatchService dispatchService,
            QualificationAttemptRepository attemptRepository,
            QualificationAttemptService attemptService,
            QualificationReliabilityProperties properties) {
        this.dispatchService = dispatchService;
        this.attemptRepository = attemptRepository;
        this.attemptService = attemptService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${leadflow.qualification-reliability.dispatcher-interval:PT5S}")
    public void dispatch() { dispatchService.dispatchAvailable(); }

    @Scheduled(fixedDelayString = "${leadflow.qualification-reliability.timeout-interval:PT1M}")
    public void reconcileTimeouts() {
        if (!properties.dispatcherEnabled()) return;
        Instant pendingCutoff = Instant.now().minus(properties.dispatchTimeout());
        Instant processingCutoff = Instant.now().minus(properties.processingTimeout());
        attemptRepository.findExpired(List.of(QualificationAttemptStatus.PENDING), pendingCutoff,
                PageRequest.of(0, properties.timeoutBatchSize())).forEach(a -> attemptService.timeOut(a.getId()));
        attemptRepository.findExpired(List.of(QualificationAttemptStatus.PROCESSING), processingCutoff,
                PageRequest.of(0, properties.timeoutBatchSize())).forEach(a -> attemptService.timeOut(a.getId()));
    }
}
