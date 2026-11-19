package com.example.githubsearch.mapper;

import com.example.githubsearch.client.dto.GitHubRepoItem;
import com.example.githubsearch.dto.RepositoryResponse;
import com.example.githubsearch.entity.RepositoryEntity;
import java.time.Instant;

/** Converts between the three shapes of a repository: GitHub JSON, database entity, API response. */
public final class RepositoryMapper {

    /** Must match the length of the "description" column in RepositoryEntity. */
    static final int MAX_DESCRIPTION_LENGTH = 1000;

    private RepositoryMapper() {
    }

    /** Copies GitHub's data onto an entity. Works for both a new entity and an existing one (update). */
    public static void applyGitHubData(RepositoryEntity entity, GitHubRepoItem item, Instant fetchedAt) {
        entity.setGithubId(item.id());
        entity.setFullName(item.fullName());
        entity.setName(item.name() != null ? item.name() : afterSlash(item.fullName()));
        entity.setDescription(truncate(item.description()));
        entity.setHtmlUrl(item.htmlUrl());
        entity.setLanguage(item.language());
        entity.setStargazersCount(zeroIfNull(item.stargazersCount()));
        entity.setForksCount(zeroIfNull(item.forksCount()));
        entity.setOpenIssuesCount(zeroIfNull(item.openIssuesCount()));
        entity.setOwnerLogin(item.owner() != null && item.owner().login() != null
                ? item.owner().login()
                : beforeSlash(item.fullName()));
        entity.setCreatedAt(item.createdAt());
        entity.setUpdatedAt(item.updatedAt());
        entity.setLastFetchedAt(fetchedAt);
    }

    public static RepositoryResponse toResponse(RepositoryEntity e) {
        return new RepositoryResponse(
                e.getId(), e.getGithubId(), e.getName(), e.getFullName(), e.getDescription(),
                e.getHtmlUrl(), e.getLanguage(), e.getStargazersCount(), e.getForksCount(),
                e.getOpenIssuesCount(), e.getOwnerLogin(), e.getCreatedAt(), e.getUpdatedAt(),
                e.getLastFetchedAt());
    }

    private static int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_DESCRIPTION_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_DESCRIPTION_LENGTH);
    }

    private static String afterSlash(String fullName) {
        int index = fullName.indexOf('/');
        return index < 0 ? fullName : fullName.substring(index + 1);
    }

    private static String beforeSlash(String fullName) {
        int index = fullName.indexOf('/');
        return index < 0 ? fullName : fullName.substring(0, index);
    }
}
