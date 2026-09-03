package com.mohammadmurrar.leadflow.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(prefix = "leadflow.email-delivery", name = "enabled", havingValue = "true")
public class EmailDeliveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryScheduler.class);
    private final EmailDispatchService dispatchService;

    public EmailDeliveryScheduler(EmailDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${leadflow.email-delivery.scheduler-interval:PT30S}")
    public void dispatch() {
        try {
            dispatchService.dispatchAvailable();
        } catch (Exception ignored) {
            log.error("Email dispatch cycle could not be completed");
        }
    }
}
