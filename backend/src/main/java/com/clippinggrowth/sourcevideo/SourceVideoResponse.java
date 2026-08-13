package com.clippinggrowth.sourcevideo;

import java.time.Instant;
import java.util.UUID;

public record SourceVideoResponse(
        UUID id,
        UUID creatorId,
        String title,
        String originUrl,
        Instant createdAt,
        Instant updatedAt) {

    static SourceVideoResponse from(SourceVideo sourceVideo) {
        return new SourceVideoResponse(
                sourceVideo.getId(),
                sourceVideo.getCreatorId(),
                sourceVideo.getTitle(),
                sourceVideo.getOriginUrl(),
                sourceVideo.getCreatedAt(),
                sourceVideo.getUpdatedAt());
    }
}
