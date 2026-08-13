package com.clippinggrowth.sourcevideo;

import java.util.Optional;
import java.util.UUID;

import com.clippinggrowth.creator.CreatorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceVideoService {

    private final SourceVideoRepository sourceVideoRepository;
    private final CreatorService creatorService;

    public SourceVideoService(
            SourceVideoRepository sourceVideoRepository,
            CreatorService creatorService) {
        this.sourceVideoRepository = sourceVideoRepository;
        this.creatorService = creatorService;
    }

    @Transactional
    public Optional<SourceVideoResponse> create(
            UUID creatorId, CreateSourceVideoRequest request) {
        if (!creatorService.existsById(creatorId)) {
            return Optional.empty();
        }

        SourceVideo sourceVideo = sourceVideoRepository.saveAndFlush(
                new SourceVideo(creatorId, request.title(), request.originUrl()));
        return Optional.of(SourceVideoResponse.from(sourceVideo));
    }

    @Transactional(readOnly = true)
    public Optional<SourceVideoResponse> findById(UUID sourceVideoId) {
        return sourceVideoRepository.findById(sourceVideoId).map(SourceVideoResponse::from);
    }

    @Transactional(readOnly = true)
    public Optional<SourceVideoPageResponse> findAllByCreatorId(
            UUID creatorId, int page, int size) {
        if (!creatorService.existsById(creatorId)) {
            return Optional.empty();
        }

        Page<SourceVideo> sourceVideos = sourceVideoRepository
                .findAllByCreatorIdOrderByCreatedAtDescIdDesc(
                        creatorId, PageRequest.of(page, size));
        return Optional.of(SourceVideoPageResponse.from(sourceVideos));
    }
}
