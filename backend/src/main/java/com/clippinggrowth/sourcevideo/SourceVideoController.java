package com.clippinggrowth.sourcevideo;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SourceVideoController {

    private final SourceVideoService sourceVideoService;

    public SourceVideoController(SourceVideoService sourceVideoService) {
        this.sourceVideoService = sourceVideoService;
    }

    @PostMapping("/creators/{creatorId}/source-videos")
    public ResponseEntity<?> create(
            @PathVariable UUID creatorId,
            @Valid @RequestBody CreateSourceVideoRequest request) {
        return sourceVideoService.create(creatorId, request)
                .<ResponseEntity<?>>map(sourceVideo -> {
                    URI location = URI.create("/api/source-videos/" + sourceVideo.id());
                    return ResponseEntity.created(location).body(sourceVideo);
                })
                .orElseGet(() -> creatorNotFound(creatorId));
    }

    @GetMapping("/creators/{creatorId}/source-videos")
    public ResponseEntity<?> findAllByCreatorId(
            @PathVariable UUID creatorId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page must be at least 0") int page,
            @RequestParam(defaultValue = "30")
            @Min(value = 1, message = "Size must be at least 1")
            @Max(value = 100, message = "Size must be at most 100") int size) {
        return sourceVideoService.findAllByCreatorId(creatorId, page, size)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> creatorNotFound(creatorId));
    }

    @GetMapping("/source-videos/{sourceVideoId}")
    public ResponseEntity<?> findById(@PathVariable UUID sourceVideoId) {
        return sourceVideoService.findById(sourceVideoId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> sourceVideoNotFound(sourceVideoId));
    }

    private ResponseEntity<ProblemDetail> creatorNotFound(UUID creatorId) {
        return notFound("Creator not found", "Creator " + creatorId + " was not found");
    }

    private ResponseEntity<ProblemDetail> sourceVideoNotFound(UUID sourceVideoId) {
        return notFound(
                "SourceVideo not found",
                "SourceVideo " + sourceVideoId + " was not found");
    }

    private ResponseEntity<ProblemDetail> notFound(String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detail);
        problem.setTitle(title);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
