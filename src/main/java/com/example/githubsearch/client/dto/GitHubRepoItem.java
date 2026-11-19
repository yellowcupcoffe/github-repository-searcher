package com.example.githubsearch.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * One repository inside GitHub's search response. Only the fields we need are declared;
 * every other field in the JSON is ignored. @JsonProperty maps GitHub's snake_case names
 * to our camelCase Java names.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepoItem(
        Long id,
        String name,
        @JsonProperty("full_name") String fullName,
        String description,
        @JsonProperty("html_url") String htmlUrl,
        String language,
        @JsonProperty("stargazers_count") Integer stargazersCount,
        @JsonProperty("forks_count") Integer forksCount,
        @JsonProperty("open_issues_count") Integer openIssuesCount,
        Owner owner,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(String login) {
    }
}
