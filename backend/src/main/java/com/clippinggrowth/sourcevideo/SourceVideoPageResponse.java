package com.clippinggrowth.sourcevideo;

import java.util.List;

import org.springframework.data.domain.Page;

public record SourceVideoPageResponse(
        List<SourceVideoResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static SourceVideoPageResponse from(Page<SourceVideo> sourceVideos) {
        return new SourceVideoPageResponse(
                sourceVideos.getContent().stream()
                        .map(SourceVideoResponse::from)
                        .toList(),
                sourceVideos.getNumber(),
                sourceVideos.getSize(),
                sourceVideos.getTotalElements(),
                sourceVideos.getTotalPages());
    }
}
