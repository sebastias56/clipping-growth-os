package com.clippinggrowth.creator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCreatorRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name) {

    public CreateCreatorRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
