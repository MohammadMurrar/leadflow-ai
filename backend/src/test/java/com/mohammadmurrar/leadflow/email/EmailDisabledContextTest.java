package com.mohammadmurrar.leadflow.email;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "leadflow.email-delivery.enabled=false")
class EmailDisabledContextTest {
    @Autowired ApplicationContext context;
    @Autowired EmailDeliveryProperties properties;

    @Test
    void disabledContextNeedsNoSmtpConfigurationAndHasNoScheduler() {
        assertThat(properties.enabled()).isFalse();
        assertThat(context.getBeansOfType(EmailDeliveryScheduler.class)).isEmpty();
        assertThat(context.getBean(SmtpEmailSender.class)).isNotNull();
    }
}
