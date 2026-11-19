package com.example.githubsearch.dto;

import java.time.Instant;
import java.util.List;

/** Every error returned by the API has this shape. */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details) {
}
