package com.example.githubsearch.service;

import com.example.githubsearch.client.GitHubApiClient;
import com.example.githubsearch.client.dto.GitHubRepoItem;
import com.example.githubsearch.client.dto.GitHubSearchResponse;
import com.example.githubsearch.dto.SearchRequest;
import com.example.githubsearch.dto.SearchResponse;
import com.example.githubsearch.exception.BadRequestException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The search workflow: validate -> build GitHub query -> call GitHub -> save results.
 *
 * This class is deliberately NOT @Transactional. The slow HTTP call to GitHub must not hold a
 * database connection open; only the saving step (RepositoryService.upsertAll) is transactional.
 */
@Service
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);

    /** GitHub's Search API never returns more than the first 1000 results of a query. */
    static final int GITHUB_MAX_RESULTS = 1000;

    private final GitHubApiClient gitHubApiClient;
    private final RepositoryService repositoryService;

    public GitHubService(GitHubApiClient gitHubApiClient, RepositoryService repositoryService) {
        this.gitHubApiClient = gitHubApiClient;
        this.repositoryService = repositoryService;
    }

    public SearchResponse search(SearchRequest request) {
        int page = request.pageOrDefault();
        int perPage = request.perPageOrDefault();
        if ((long) page * perPage > GITHUB_MAX_RESULTS) {
            throw new BadRequestException("GitHub only returns the first " + GITHUB_MAX_RESULTS
                    + " results of a search; reduce page or perPage");
        }

        String githubQuery = buildQuery(request);
        String order = request.order() == null ? null : request.order().toLowerCase();

        GitHubSearchResponse githubResponse =
                gitHubApiClient.searchRepositories(githubQuery, request.sort(), order, page, perPage);

        // Ignore (and log) items that lack the fields we cannot store without.
        List<GitHubRepoItem> usable = githubResponse.items().stream().filter(this::isUsable).toList();
        if (usable.size() < githubResponse.items().size()) {
            log.warn("Skipped {} malformed repositories from GitHub", githubResponse.items().size() - usable.size());
        }

        if (usable.isEmpty()) {
            // Empty result is a normal outcome, not an error.
            return new SearchResponse(githubQuery, page, perPage, githubResponse.totalCount(), 0, 0, 0, List.of());
        }

        RepositoryService.UpsertResult saved = repositoryService.upsertAll(usable);
        log.info("Search '{}' page {}: {} inserted, {} updated", githubQuery, page, saved.inserted(), saved.updated());

        return new SearchResponse(githubQuery, page, perPage, githubResponse.totalCount(),
                saved.repositories().size(), saved.inserted(), saved.updated(), saved.repositories());
    }

    /** "spring boot" + language Java  ->  "spring boot language:Java". Names with spaces get quotes. */
    static String buildQuery(SearchRequest request) {
        StringBuilder query = new StringBuilder(request.query().trim());
        String language = request.language();
        if (language != null && !language.isBlank()) {
            String trimmed = language.trim();
            query.append(" language:");
            if (trimmed.contains(" ")) {
                query.append('"').append(trimmed).append('"');
            } else {
                query.append(trimmed);
            }
        }
        return query.toString();
    }

    private boolean isUsable(GitHubRepoItem item) {
        return item != null && item.id() != null && item.fullName() != null && item.htmlUrl() != null;
    }
}
