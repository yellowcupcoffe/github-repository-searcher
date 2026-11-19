package com.example.githubsearch;

import com.example.githubsearch.client.dto.GitHubRepoItem;
import com.example.githubsearch.dto.RepositoryResponse;
import java.time.Instant;

/** Small factory methods so every test builds sample data the same way. */
public final class TestData {

    private TestData() {
    }

    /** A repository as GitHub would return it. fullName must look like "owner/name". */
    public static GitHubRepoItem item(long id, String fullName, String language, int stars) {
        int slash = fullName.indexOf('/');
        return new GitHubRepoItem(
                id,
                fullName.substring(slash + 1),
                fullName,
                "Description of " + fullName,
                "https://github.com/" + fullName,
                language,
                stars,
                7,
                3,
                new GitHubRepoItem.Owner(fullName.substring(0, slash)),
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:00Z"));
    }

    /** A repository as our API returns it. */
    public static RepositoryResponse response(Long id, long githubId, String fullName, String language, int stars) {
        int slash = fullName.indexOf('/');
        return new RepositoryResponse(
                id, githubId, fullName.substring(slash + 1), fullName, "Description of " + fullName,
                "https://github.com/" + fullName, language, stars, 7, 3, fullName.substring(0, slash),
                Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:00Z"));
    }
}
