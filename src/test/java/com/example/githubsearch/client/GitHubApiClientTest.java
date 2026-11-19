package com.example.githubsearch.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.githubsearch.client.dto.GitHubSearchResponse;
import com.example.githubsearch.exception.GitHubApiException;
import com.example.githubsearch.exception.GitHubRateLimitException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

/**
 * Uses MockRestServiceServer: a fake HTTP server built into Spring. The client really builds
 * URLs, sends requests and parses JSON, but nothing leaves the machine and GitHub is never called.
 */
class GitHubApiClientTest {

    private static final String BASE_URL = "https://api.github.test";

    private static final String SUCCESS_JSON = """
            {
              "total_count": 2,
              "incomplete_results": false,
              "items": [
                {
                  "id": 6296790,
                  "name": "spring-boot",
                  "full_name": "spring-projects/spring-boot",
                  "private": false,
                  "description": "Spring Boot helps you to create Spring-powered applications",
                  "html_url": "https://github.com/spring-projects/spring-boot",
                  "language": "Java",
                  "stargazers_count": 70000,
                  "forks_count": 40000,
                  "open_issues_count": 500,
                  "owner": { "login": "spring-projects", "id": 317776 },
                  "created_at": "2012-10-19T13:02:30Z",
                  "updated_at": "2025-01-01T00:00:00Z"
                }
              ]
            }
            """;

    private MockRestServiceServer server;
    private GitHubApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubApiClient(builder.build());
    }

    @AfterEach
    void verifyAllExpectedRequestsWereMade() {
        server.verify();
    }

    /** The decoded query string must contain the given text. */
    private RequestMatcher queryContains(String text) {
        return request -> assertThat(
                UriUtils.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8)).contains(text);
    }

    @Test
    void parsesGitHubJsonIntoJavaObjects() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/search/repositories")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryContains("q=spring boot language:Java"))
                .andExpect(queryContains("sort=stars"))
                .andExpect(queryContains("order=desc"))
                .andExpect(queryContains("page=2"))
                .andExpect(queryContains("per_page=10"))
                .andRespond(withSuccess(SUCCESS_JSON, MediaType.APPLICATION_JSON));

        GitHubSearchResponse response = client.searchRepositories("spring boot language:Java", "stars", "desc", 2, 10);

        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).fullName()).isEqualTo("spring-projects/spring-boot");
        assertThat(response.items().get(0).stargazersCount()).isEqualTo(70000);
        assertThat(response.items().get(0).owner().login()).isEqualTo("spring-projects");
        assertThat(response.items().get(0).createdAt()).isEqualTo(Instant.parse("2012-10-19T13:02:30Z"));
    }

    @Test
    void omitsSortAndOrderWhenNoSortWasRequested() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/search/repositories")))
                .andExpect(request -> assertThat(request.getURI().getRawQuery())
                        .doesNotContain("sort=").doesNotContain("order="))
                .andRespond(withSuccess(SUCCESS_JSON, MediaType.APPLICATION_JSON));

        client.searchRepositories("kafka", null, null, 1, 20);
    }

    @Test
    void encodesSpecialCharactersInTheQuery() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/search/repositories")))
                .andExpect(request -> assertThat(request.getURI().getRawQuery()).contains("C%2B%2B"))
                .andRespond(withSuccess(SUCCESS_JSON, MediaType.APPLICATION_JSON));

        client.searchRepositories("engine language:C++", null, null, 1, 20);
    }

    @Test
    void emptyResultIsReturnedNormally() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/search/repositories")))
                .andRespond(withSuccess("{\"total_count\":0,\"incomplete_results\":false,\"items\":[]}",
                        MediaType.APPLICATION_JSON));

        GitHubSearchResponse response = client.searchRepositories("zzzzzz", null, null, 1, 20);

        assertThat(response.totalCount()).isZero();
        assertThat(response.items()).isEmpty();
    }

    @Test
    void forbiddenWithRetryAfterHeaderIsTreatedAsRateLimit() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0")
                        .header("Retry-After", "30")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"API rate limit exceeded\"}"));

        assertThatThrownBy(() -> client.searchRepositories("q", null, null, 1, 20))
                .isInstanceOfSatisfying(GitHubRateLimitException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(30L));
    }

    @Test
    void rateLimitResetTimestampIsConvertedToSecondsToWait() {
        long resetInOneMinute = Instant.now().getEpochSecond() + 60;
        server.expect(requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0")
                        .header("X-RateLimit-Reset", String.valueOf(resetInOneMinute))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}"));

        assertThatThrownBy(() -> client.searchRepositories("q", null, null, 1, 20))
                .isInstanceOfSatisfying(GitHubRateLimitException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isBetween(1L, 60L));
    }

    @Test
    void tooManyRequestsStatusIsTreatedAsRateLimitEvenWithoutHeaders() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertThatThrownBy(() -> client.searchRepositories("q", null, null, 1, 20))
                .isInstanceOfSatisfying(GitHubRateLimitException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isNull());
    }

    @Test
    void forbiddenWithoutRateLimitSignalIsAPlainGitHubError() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertThatThrownBy(() -> client.searchRepositories("q", null, null, 1, 20))
                .isInstanceOf(GitHubApiException.class)
                .isNotInstanceOf(GitHubRateLimitException.class);
    }

    @Test
    void unprocessableQueryIsMappedTo422() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"Validation Failed\"}"));

        assertThatThrownBy(() -> client.searchRepositories("q", null, null, 1, 20))
                .isInstanceOfSatisfying(GitHubApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void invalidTokenIsReportedAsBadGateway() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertThatThrownBy(() -> client.searchRepositories("q", null, null, 1, 20))
                .isInstanceOfSatisfying(GitHubApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getMessage()).contains("GITHUB_TOKEN");
                });
    }

    @Test
    void githubServerErrorIsReportedAsBadGateway() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertThatThrownBy(() -> client.searchRepositories("q", null, null, 1, 20))
                .isInstanceOfSatisfying(GitHubApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void timeoutIsReportedAsGatewayTimeout() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.searchRepositories("q", null, null, 1, 20))
                .isInstanceOfSatisfying(GitHubApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
    }

    @Test
    void malformedJsonIsReportedAsBadGateway() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess("this is not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.searchRepositories("q", null, null, 1, 20))
                .isInstanceOfSatisfying(GitHubApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void responseWithoutItemsFieldIsReportedAsBadGateway() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess("{\"total_count\":0}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.searchRepositories("q", null, null, 1, 20))
                .isInstanceOfSatisfying(GitHubApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
