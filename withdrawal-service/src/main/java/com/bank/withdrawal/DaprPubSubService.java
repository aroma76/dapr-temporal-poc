package com.bank.withdrawal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * What it does: Publishes withdrawal events to Dapr Pub/Sub topic 'withdrawal-requests'.
 * Why it exists: Abstracts the Dapr HTTP Pub/Sub publishing endpoint for clean separation.
 * Technology used: Spring @Service, Spring RestTemplate.
 * Communication: Called by WithdrawalActivityImpl; sends HTTP POST to Dapr sidecar (or direct service URL).
 */
@Service
public class DaprPubSubService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${dapr.http.host:localhost}")
    private String daprHttpHost;

    @Value("${dapr.http.port:3500}")
    private String daprHttpPort;

    @Value("${dapr.pubsub.name:pubsub}")
    private String pubsubName;

    @Value("${dapr.direct.fallback.url:http://localhost:8082/events/withdrawal-requests}")
    private String directFallbackUrl;

    public void publishWithdrawalRequest(WithdrawalRequest request) {
        String topic = "withdrawal-requests";
        String daprPublishUrl = "http://" + daprHttpHost + ":" + daprHttpPort + "/v1.0/publish/" + pubsubName + "/" + topic;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<WithdrawalRequest> entity = new HttpEntity<>(request, headers);

        try {
            // Attempt publishing through Dapr sidecar
            restTemplate.postForEntity(daprPublishUrl, entity, Void.class);
            System.out.println("[Withdrawal Service] Published withdrawal request to Dapr topic '" + topic + "': " + request.getRequestId());
        } catch (Exception daprException) {
            // If Dapr sidecar is not running (e.g. during standalone local development), fallback to direct HTTP call
            System.out.println("[Withdrawal Service] Dapr sidecar not reachable at " + daprPublishUrl + ". Attempting direct delivery: " + directFallbackUrl);
            try {
                restTemplate.postForEntity(directFallbackUrl, entity, Void.class);
                System.out.println("[Withdrawal Service] Delivered withdrawal request directly to Transaction Service: " + request.getRequestId());
            } catch (Exception directException) {
                throw new RuntimeException("Failed to publish withdrawal request via Dapr or direct delivery: " + directException.getMessage(), directException);
            }
        }
    }
}

