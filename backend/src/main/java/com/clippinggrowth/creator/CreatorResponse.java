package com.clippinggrowth.creator;

import java.time.Instant;
import java.util.UUID;

public record CreatorResponse(
        UUID id,
        String name,
        Instant createdAt,
        Instant updatedAt) {

    static CreatorResponse from(Creator creator) {
        return new CreatorResponse(
                creator.getId(),
                creator.getName(),
                creator.getCreatedAt(),
                creator.getUpdatedAt());
    }
}
