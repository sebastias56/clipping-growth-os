package com.clippinggrowth.sourcevideo;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceVideoRepository extends JpaRepository<SourceVideo, UUID> {

    Page<SourceVideo> findAllByCreatorIdOrderByCreatedAtDescIdDesc(
            UUID creatorId, Pageable pageable);
}
