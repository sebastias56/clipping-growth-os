package com.clippinggrowth.creator;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorRepository extends JpaRepository<Creator, UUID> {

    List<Creator> findAllByOrderByCreatedAtDescIdDesc();
}
