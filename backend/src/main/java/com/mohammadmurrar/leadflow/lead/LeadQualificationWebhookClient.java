package com.mohammadmurrar.leadflow.lead;

import com.mohammadmurrar.leadflow.qualification.api.QualificationDispatchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class LeadQualificationWebhookClient {

    private final RestClient restClient;
    private final String webhookUrl;

    public LeadQualificationWebhookClient(
            @Value("${n8n.lead-qualification-webhook-url}") String webhookUrl) {

        this.webhookUrl = webhookUrl;

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public boolean send(QualificationDispatchRequest request) {
        var status = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode();
        if (status != HttpStatus.ACCEPTED) {
            throw new IllegalStateException("Unexpected webhook acknowledgement status: " + status.value());
        }
        return true;
    }
}
