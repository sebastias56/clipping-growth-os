package com.clippinggrowth.creator;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/creators")
public class CreatorController {

    private final CreatorService creatorService;

    public CreatorController(CreatorService creatorService) {
        this.creatorService = creatorService;
    }

    @PostMapping
    public ResponseEntity<CreatorResponse> create(@Valid @RequestBody CreateCreatorRequest request) {
        CreatorResponse creator = creatorService.create(request);
        URI location = URI.create("/api/creators/" + creator.id());
        return ResponseEntity.created(location).body(creator);
    }

    @GetMapping
    public List<CreatorResponse> findAll() {
        return creatorService.findAll();
    }

    @GetMapping("/{creatorId}")
    public ResponseEntity<?> findById(@PathVariable UUID creatorId) {
        return creatorService.findById(creatorId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> notFound(creatorId));
    }

    private ResponseEntity<ProblemDetail> notFound(UUID creatorId) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Creator " + creatorId + " was not found");
        problem.setTitle("Creator not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
