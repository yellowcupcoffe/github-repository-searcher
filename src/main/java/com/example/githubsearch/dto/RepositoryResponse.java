package com.example.githubsearch.dto;

import java.time.Instant;

/** What the API returns for one stored repository (never the JPA entity itself). */
public record RepositoryResponse(
        Long id,
        Long githubId,
        String name,
        String fullName,
        String description,
        String htmlUrl,
        String language,
        int stargazersCount,
        int forksCount,
        int openIssuesCount,
        String ownerLogin,
        Instant createdAt,
        Instant updatedAt,
        Instant lastFetchedAt) {
}
