package com.clippinggrowth.system;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class MediaWorkerClientConfiguration {

    @Bean("mediaWorkerRestClient")
    RestClient mediaWorkerRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${media-worker.base-url}") String baseUrl,
            @Value("${media-worker.connect-timeout}") Duration connectTimeout,
            @Value("${media-worker.read-timeout}") Duration readTimeout) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(connectTimeout, readTimeout);
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk()
                .build(settings);

        return restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
