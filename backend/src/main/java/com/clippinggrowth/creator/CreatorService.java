package com.clippinggrowth.creator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatorService {

    private final CreatorRepository creatorRepository;

    public CreatorService(CreatorRepository creatorRepository) {
        this.creatorRepository = creatorRepository;
    }

    @Transactional
    public CreatorResponse create(CreateCreatorRequest request) {
        Creator creator = creatorRepository.saveAndFlush(new Creator(request.name()));
        return CreatorResponse.from(creator);
    }

    @Transactional(readOnly = true)
    public List<CreatorResponse> findAll() {
        return creatorRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(CreatorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CreatorResponse> findById(UUID creatorId) {
        return creatorRepository.findById(creatorId).map(CreatorResponse::from);
    }
}
