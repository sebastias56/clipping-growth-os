package com.clippinggrowth.system;

import org.springframework.stereotype.Service;

@Service
public class SystemStatusService {

    private final MediaWorkerClient mediaWorkerClient;

    public SystemStatusService(MediaWorkerClient mediaWorkerClient) {
        this.mediaWorkerClient = mediaWorkerClient;
    }

    public SystemStatusResponse getStatus() {
        ServiceStatus mediaWorkerStatus = mediaWorkerClient.getStatus();
        SystemStatusResponse.AggregateStatus aggregateStatus = mediaWorkerStatus == ServiceStatus.UP
                ? SystemStatusResponse.AggregateStatus.UP
                : SystemStatusResponse.AggregateStatus.DEGRADED;

        return new SystemStatusResponse(
                aggregateStatus,
                new SystemStatusResponse.ComponentStatus(ServiceStatus.UP),
                new SystemStatusResponse.ComponentStatus(mediaWorkerStatus));
    }
}
