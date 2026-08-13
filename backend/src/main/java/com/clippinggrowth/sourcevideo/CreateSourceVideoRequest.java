package com.clippinggrowth.sourcevideo;

import java.net.URI;
import java.net.URISyntaxException;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSourceVideoRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 300, message = "Title must be at most 300 characters")
        String title,
        @Size(max = 2048, message = "Origin URL must be at most 2048 characters")
        String originUrl) {

    public CreateSourceVideoRequest {
        if (title != null) {
            title = title.strip();
        }
        if (originUrl != null) {
            originUrl = originUrl.strip();
            if (originUrl.isEmpty()) {
                originUrl = null;
            }
        }
    }

    @AssertTrue(message = "Origin URL must be an absolute HTTP or HTTPS URL with a valid host")
    public boolean hasValidOriginUrl() {
        if (originUrl == null) {
            return true;
        }

        try {
            URI uri = new URI(originUrl).parseServerAuthority();
            String scheme = uri.getScheme();
            int port = uri.getPort();
            return uri.isAbsolute()
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && port <= 65535;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
