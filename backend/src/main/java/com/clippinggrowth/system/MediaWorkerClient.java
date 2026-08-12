package com.clippinggrowth.system;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MediaWorkerClient {

    private static final String EXPECTED_SERVICE = "media-worker";

    private final RestClient restClient;

    public MediaWorkerClient(@Qualifier("mediaWorkerRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public ServiceStatus getStatus() {
        try {
            MediaWorkerHealthResponse response = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(MediaWorkerHealthResponse.class);

            if (response != null
                    && ServiceStatus.UP.name().equals(response.status())
                    && EXPECTED_SERVICE.equals(response.service())) {
                return ServiceStatus.UP;
            }
        } catch (RestClientException exception) {
            return ServiceStatus.DOWN;
        }

        return ServiceStatus.DOWN;
    }

    private record MediaWorkerHealthResponse(String status, String service) {
    }
}
