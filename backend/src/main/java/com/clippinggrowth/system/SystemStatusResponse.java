package com.clippinggrowth.system;

public record SystemStatusResponse(
        AggregateStatus status,
        ComponentStatus backend,
        ComponentStatus mediaWorker) {

    public enum AggregateStatus {
        UP,
        DEGRADED
    }

    public record ComponentStatus(ServiceStatus status) {
    }
}
