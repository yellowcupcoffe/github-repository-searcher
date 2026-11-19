package com.example.githubsearch.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.githubsearch.TestData;
import com.example.githubsearch.client.dto.GitHubRepoItem;
import com.example.githubsearch.entity.RepositoryEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RepositoryMapperTest {

    private static final Instant FETCHED_AT = Instant.parse("2025-06-01T10:00:00Z");

    @Test
    void copiesAllGitHubFieldsOntoTheEntity() {
        RepositoryEntity entity = new RepositoryEntity();

        RepositoryMapper.applyGitHubData(entity, TestData.item(5L, "acme/widget", "Java", 123), FETCHED_AT);

        assertThat(entity.getGithubId()).isEqualTo(5L);
        assertThat(entity.getName()).isEqualTo("widget");
        assertThat(entity.getFullName()).isEqualTo("acme/widget");
        assertThat(entity.getLanguage()).isEqualTo("Java");
        assertThat(entity.getStargazersCount()).isEqualTo(123);
        assertThat(entity.getOwnerLogin()).isEqualTo("acme");
        assertThat(entity.getLastFetchedAt()).isEqualTo(FETCHED_AT);
    }

    @Test
    void fillsMissingOptionalFieldsWithSafeDefaults() {
        GitHubRepoItem sparse = new GitHubRepoItem(9L, null, "acme/sparse", null,
                "https://github.com/acme/sparse", null, null, null, null, null, null, null);
        RepositoryEntity entity = new RepositoryEntity();

        RepositoryMapper.applyGitHubData(entity, sparse, FETCHED_AT);

        assertThat(entity.getName()).isEqualTo("sparse");      // derived from full_name
        assertThat(entity.getOwnerLogin()).isEqualTo("acme");  // derived from full_name
        assertThat(entity.getStargazersCount()).isZero();
        assertThat(entity.getLanguage()).isNull();
    }

    @Test
    void truncatesDescriptionsLongerThanTheColumn() {
        GitHubRepoItem item = new GitHubRepoItem(1L, "x", "a/x", "d".repeat(1500),
                "https://github.com/a/x", "Java", 1, 1, 1, null, null, null);
        RepositoryEntity entity = new RepositoryEntity();

        RepositoryMapper.applyGitHubData(entity, item, FETCHED_AT);

        assertThat(entity.getDescription()).hasSize(RepositoryMapper.MAX_DESCRIPTION_LENGTH);
    }

    @Test
    void updatingAnExistingEntityOverwritesOldValues() {
        RepositoryEntity entity = new RepositoryEntity();
        RepositoryMapper.applyGitHubData(entity, TestData.item(5L, "acme/widget", "Java", 10), FETCHED_AT);

        RepositoryMapper.applyGitHubData(entity, TestData.item(5L, "acme/widget", "Kotlin", 999), FETCHED_AT);

        assertThat(entity.getStargazersCount()).isEqualTo(999);
        assertThat(entity.getLanguage()).isEqualTo("Kotlin");
    }
}
