package com.clippinggrowth.system;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

class SystemStatusControllerTests {

    private MockRestServiceServer mockServer;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl("http://media-worker.test");
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        MediaWorkerClient mediaWorkerClient = new MediaWorkerClient(restClientBuilder.build());
        SystemStatusService systemStatusService = new SystemStatusService(mediaWorkerClient);
        mockMvc = MockMvcBuilders.standaloneSetup(new SystemStatusController(systemStatusService)).build();
    }

    @Test
    void returnsUpContractWhenWorkerIsAvailable() throws Exception {
        mockServer.expect(requestTo("http://media-worker.test/health"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "status": "UP",
                          "service": "media-worker"
                        }
                        """, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.backend.status").value("UP"))
                .andExpect(jsonPath("$.mediaWorker.status").value("UP"));
        mockServer.verify();
    }

    @Test
    void returnsDegradedContractWithoutInternalDetailsWhenWorkerIsUnavailable() throws Exception {
        mockServer.expect(requestTo("http://media-worker.test/health"))
                .andExpect(method(GET))
                .andRespond(withException(new IOException("internal connection detail")));

        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.backend.status").value("UP"))
                .andExpect(jsonPath("$.mediaWorker.status").value("DOWN"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal connection detail"))));
        mockServer.verify();
    }
}
