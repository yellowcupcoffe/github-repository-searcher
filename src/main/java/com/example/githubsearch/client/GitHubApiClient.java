package com.example.githubsearch.client;

import com.example.githubsearch.client.dto.GitHubSearchResponse;
import com.example.githubsearch.exception.GitHubApiException;
import com.example.githubsearch.exception.GitHubRateLimitException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;

/**
 * The ONLY class that talks to GitHub. It sends the request, turns the JSON into Java
 * objects and translates every kind of failure into a GitHubApiException.
 */
@Component
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);

    private final RestClient restClient;

    public GitHubApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * @param query   full GitHub query string, e.g. "spring boot language:Java"
     * @param sort    stars | forks | updated, or null for best match
     * @param order   asc | desc (only used when sort is given)
     * @param page    1-based page number
     * @param perPage results per page (max 100)
     */
    public GitHubSearchResponse searchRepositories(String query, String sort, String order, int page, int perPage) {
        GitHubSearchResponse body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> buildUri(uriBuilder, query, sort, order, page, perPage))
                    .retrieve()
                    .body(GitHubSearchResponse.class);
        } catch (RestClientResponseException e) {
            // GitHub answered with a 4xx/5xx status
            throw translateHttpError(e);
        } catch (ResourceAccessException e) {
            // network problem: connection refused, DNS failure, timeout ...
            if (isTimeout(e)) {
                log.warn("Timed out while calling GitHub");
                throw new GitHubApiException("GitHub did not respond in time", HttpStatus.GATEWAY_TIMEOUT, e);
            }
            log.warn("Could not reach GitHub: {}", e.getMessage());
            throw new GitHubApiException("Could not reach GitHub", HttpStatus.BAD_GATEWAY, e);
        } catch (RestClientException e) {
            // GitHub answered 2xx but the body could not be read as the expected JSON
            log.warn("Could not read GitHub response: {}", e.getMessage());
            throw new GitHubApiException("GitHub returned a response we could not understand",
                    HttpStatus.BAD_GATEWAY, e);
        }

        if (body == null || body.items() == null) {
            throw new GitHubApiException("GitHub returned an unexpected response", HttpStatus.BAD_GATEWAY);
        }
        return body;
    }

    private URI buildUri(UriBuilder uriBuilder, String query, String sort, String order, int page, int perPage) {
        // Values are passed as URI variables so Spring percent-encodes them strictly
        // (spaces, ':' and '+' in "language:C++" are all safe this way).
        Map<String, Object> variables = new HashMap<>();
        variables.put("q", query);
        variables.put("page", page);
        variables.put("perPage", perPage);

        uriBuilder.path("/search/repositories")
                .queryParam("q", "{q}")
                .queryParam("page", "{page}")
                .queryParam("per_page", "{perPage}");

        if (sort != null) {
            uriBuilder.queryParam("sort", "{sort}").queryParam("order", "{order}");
            variables.put("sort", sort);
            variables.put("order", order == null ? "desc" : order);
        }
        return uriBuilder.build(variables);
    }

    private GitHubApiException translateHttpError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        HttpHeaders headers = e.getResponseHeaders();

        if (status == 429 || (status == 403 && looksRateLimited(headers))) {
            Long retryAfter = retryAfterSeconds(headers);
            log.warn("GitHub rate limit reached (HTTP {}), retry after {} seconds", status, retryAfter);
            return new GitHubRateLimitException(
                    "GitHub API rate limit exceeded. Try again later, or configure GITHUB_TOKEN for a higher limit.",
                    retryAfter);
        }

        log.warn("GitHub returned HTTP {}", status);
        return switch (status) {
            case 401 -> new GitHubApiException(
                    "GitHub rejected the configured token (HTTP 401). Check GITHUB_TOKEN.", HttpStatus.BAD_GATEWAY);
            case 403 -> new GitHubApiException(
                    "GitHub refused the request (HTTP 403).", HttpStatus.BAD_GATEWAY);
            case 422 -> new GitHubApiException(
                    "GitHub could not process this search (HTTP 422). The query may be invalid "
                            + "or beyond the first 1000 results.", HttpStatus.UNPROCESSABLE_ENTITY);
            default -> new GitHubApiException(
                    "GitHub returned an error (HTTP " + status + ").", HttpStatus.BAD_GATEWAY);
        };
    }

    /** GitHub signals a rate limit with 403/429 plus X-RateLimit-Remaining: 0 or a Retry-After header. */
    private boolean looksRateLimited(HttpHeaders headers) {
        if (headers == null) {
            return false;
        }
        return "0".equals(headers.getFirst("X-RateLimit-Remaining")) || headers.getFirst("Retry-After") != null;
    }

    private Long retryAfterSeconds(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String retryAfter = headers.getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                return Long.parseLong(retryAfter.trim());
            } catch (NumberFormatException ignored) {
                // fall through to the reset header
            }
        }
        String reset = headers.getFirst("X-RateLimit-Reset"); // epoch seconds
        if (reset != null) {
            try {
                return Math.max(1L, Long.parseLong(reset.trim()) - Instant.now().getEpochSecond());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isTimeout(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
