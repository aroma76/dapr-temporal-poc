package com.bank.transaction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * What it does: Publishes transaction results to Dapr Pub/Sub topic 'transaction-results'.
 * Why it exists: Transports the outcome back to Withdrawal Service via Dapr messaging.
 * Technology used: Spring @Service, Spring RestTemplate.
 * Communication: Called by DaprEventSubscriber; sends HTTP POST to Dapr sidecar (or direct fallback URL).
 */
@Service
public class DaprPubSubService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${dapr.http.host:localhost}")
    private String daprHttpHost;

    @Value("${dapr.http.port:3501}")
    private String daprHttpPort;

    @Value("${dapr.pubsub.name:pubsub}")
    private String pubsubName;

    @Value("${dapr.direct.fallback.url:http://localhost:8081/events/transaction-results}")
    private String directFallbackUrl;

    public void publishTransactionResult(TransactionResult result) {
        String topic = "transaction-results";
        String daprPublishUrl = "http://" + daprHttpHost + ":" + daprHttpPort + "/v1.0/publish/" + pubsubName + "/" + topic;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TransactionResult> entity = new HttpEntity<>(result, headers);

        try {
            // Attempt publishing through Dapr sidecar
            restTemplate.postForEntity(daprPublishUrl, entity, Void.class);
            System.out.println("[Transaction Service] Published transaction result to Dapr topic '" + topic + "': " + result.getRequestId());
        } catch (Exception daprException) {
            // Fallback for standalone local development when Dapr sidecar is not running
            System.out.println("[Transaction Service] Dapr sidecar not reachable at " + daprPublishUrl + ". Attempting direct delivery: " + directFallbackUrl);
            try {
                restTemplate.postForEntity(directFallbackUrl, entity, Void.class);
                System.out.println("[Transaction Service] Delivered transaction result directly to Withdrawal Service: " + result.getRequestId());
            } catch (Exception directException) {
                throw new RuntimeException("Failed to publish transaction result via Dapr or direct delivery: " + directException.getMessage(), directException);
            }
        }
    }
}

