package com.example.githubsearch.dto;

import java.util.List;

/** Result of one search: what GitHub returned plus what we did with it. */
public record SearchResponse(
        String githubQuery,
        int page,
        int perPage,
        long totalCount,
        int returnedCount,
        int inserted,
        int updated,
        List<RepositoryResponse> results) {
}
