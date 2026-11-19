package com.example.githubsearch.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Top-level JSON returned by GET https://api.github.com/search/repositories. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubSearchResponse(
        @JsonProperty("total_count") long totalCount,
        @JsonProperty("incomplete_results") boolean incompleteResults,
        List<GitHubRepoItem> items) {
}
