package com.clippinggrowth.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MediaWorkerClientTests {

    private MockRestServiceServer mockServer;
    private MediaWorkerClient mediaWorkerClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl("http://media-worker.test");
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        mediaWorkerClient = new MediaWorkerClient(restClientBuilder.build());
    }

    @Test
    void mapsWorkerHealthContractToUp() {
        mockServer.expect(requestTo("http://media-worker.test/health"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "status": "UP",
                          "service": "media-worker"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(mediaWorkerClient.getStatus()).isEqualTo(ServiceStatus.UP);
        mockServer.verify();
    }

    @Test
    void mapsWorkerConnectionFailureToDown() {
        mockServer.expect(requestTo("http://media-worker.test/health"))
                .andExpect(method(GET))
                .andRespond(withException(new IOException("internal connection detail")));

        assertThat(mediaWorkerClient.getStatus()).isEqualTo(ServiceStatus.DOWN);
        mockServer.verify();
    }
}
