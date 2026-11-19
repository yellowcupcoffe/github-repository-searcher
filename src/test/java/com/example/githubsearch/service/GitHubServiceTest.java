package com.example.githubsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.githubsearch.TestData;
import com.example.githubsearch.client.GitHubApiClient;
import com.example.githubsearch.client.dto.GitHubRepoItem;
import com.example.githubsearch.client.dto.GitHubSearchResponse;
import com.example.githubsearch.dto.SearchRequest;
import com.example.githubsearch.dto.SearchResponse;
import com.example.githubsearch.exception.BadRequestException;
import com.example.githubsearch.exception.GitHubApiException;
import com.example.githubsearch.exception.GitHubRateLimitException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * GitHub and the database are both mocked. Without the mocks these tests would need internet
 * access, a GitHub rate-limit budget and a running database, and they would be slow and flaky.
 */
@ExtendWith(MockitoExtension.class)
class GitHubServiceTest {

    @Mock
    private GitHubApiClient client;

    @Mock
    private RepositoryService repositoryService;

    @InjectMocks
    private GitHubService service;

    @Test
    void searchCallsGitHubWithLanguageFilterAndSavesResults() {
        List<GitHubRepoItem> items = List.of(
                TestData.item(1L, "a/one", "Java", 10), TestData.item(2L, "b/two", "Java", 20));
        when(client.searchRepositories("spring boot language:Java", "stars", "desc", 1, 20))
                .thenReturn(new GitHubSearchResponse(150, false, items));
        when(repositoryService.upsertAll(items)).thenReturn(new RepositoryService.UpsertResult(
                List.of(TestData.response(1L, 1L, "a/one", "Java", 10), TestData.response(2L, 2L, "b/two", "Java", 20)),
                1, 1));

        SearchResponse response = service.search(new SearchRequest("spring boot", "Java", "stars", "DESC", null, null));

        assertThat(response.githubQuery()).isEqualTo("spring boot language:Java");
        assertThat(response.totalCount()).isEqualTo(150);
        assertThat(response.returnedCount()).isEqualTo(2);
        assertThat(response.inserted()).isEqualTo(1);
        assertThat(response.updated()).isEqualTo(1);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.perPage()).isEqualTo(20);
    }

    @Test
    void searchWithoutSortOrLanguageUsesPlainQueryAndGitHubDefaults() {
        when(client.searchRepositories("kafka", null, null, 3, 50))
                .thenReturn(new GitHubSearchResponse(0, false, List.of()));

        service.search(new SearchRequest("  kafka  ", null, null, null, 3, 50));

        verify(client).searchRepositories("kafka", null, null, 3, 50);
    }

    @Test
    void emptyGitHubResultIsReturnedGracefullyAndNothingIsSaved() {
        when(client.searchRepositories("nothing-matches-this", null, null, 1, 20))
                .thenReturn(new GitHubSearchResponse(0, false, List.of()));

        SearchResponse response = service.search(new SearchRequest("nothing-matches-this", null, null, null, null, null));

        assertThat(response.results()).isEmpty();
        assertThat(response.returnedCount()).isZero();
        assertThat(response.totalCount()).isZero();
        verifyNoInteractions(repositoryService);
    }

    @Test
    void malformedItemsAreSkippedAndValidOnesAreSaved() {
        GitHubRepoItem valid = TestData.item(1L, "a/one", "Java", 10);
        GitHubRepoItem noId = new GitHubRepoItem(null, "x", "a/x", null, "https://github.com/a/x",
                null, 1, 1, 1, null, null, null);
        when(client.searchRepositories("q", null, null, 1, 20))
                .thenReturn(new GitHubSearchResponse(2, false, List.of(noId, valid)));
        when(repositoryService.upsertAll(List.of(valid))).thenReturn(new RepositoryService.UpsertResult(
                List.of(TestData.response(1L, 1L, "a/one", "Java", 10)), 1, 0));

        SearchResponse response = service.search(new SearchRequest("q", null, null, null, null, null));

        assertThat(response.returnedCount()).isEqualTo(1);
        verify(repositoryService).upsertAll(List.of(valid));
    }

    @Test
    void languageNamesWithSpacesAreQuoted() {
        SearchRequest request = new SearchRequest("notebook", "Jupyter Notebook", null, null, null, null);

        assertThat(GitHubService.buildQuery(request)).isEqualTo("notebook language:\"Jupyter Notebook\"");
    }

    @Test
    void requestingBeyondGitHubsFirstThousandResultsIsRejectedWithoutCallingGitHub() {
        SearchRequest request = new SearchRequest("q", null, null, null, 11, 100);

        assertThatThrownBy(() -> service.search(request)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(client, repositoryService);
    }

    @Test
    void rateLimitErrorFromGitHubIsPropagatedAndNothingIsSaved() {
        when(client.searchRepositories("q", null, null, 1, 20))
                .thenThrow(new GitHubRateLimitException("rate limited", 30L));

        assertThatThrownBy(() -> service.search(new SearchRequest("q", null, null, null, null, null)))
                .isInstanceOf(GitHubRateLimitException.class);
        verifyNoInteractions(repositoryService);
    }

    @Test
    void otherGitHubErrorsArePropagatedAndNothingIsSaved() {
        when(client.searchRepositories("q", null, null, 1, 20))
                .thenThrow(new GitHubApiException("GitHub down", HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> service.search(new SearchRequest("q", null, null, null, null, null)))
                .isInstanceOf(GitHubApiException.class)
                .hasMessage("GitHub down");
        verifyNoInteractions(repositoryService);
    }
}
