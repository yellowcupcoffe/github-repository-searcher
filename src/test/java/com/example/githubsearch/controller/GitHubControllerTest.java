package com.example.githubsearch.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.githubsearch.TestData;
import com.example.githubsearch.dto.PagedResponse;
import com.example.githubsearch.dto.RepositoryResponse;
import com.example.githubsearch.dto.SearchRequest;
import com.example.githubsearch.dto.SearchResponse;
import com.example.githubsearch.exception.GitHubApiException;
import com.example.githubsearch.exception.GitHubRateLimitException;
import com.example.githubsearch.exception.ResourceNotFoundException;
import com.example.githubsearch.service.GitHubService;
import com.example.githubsearch.service.RepositoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * @WebMvcTest starts only the web layer (controller, JSON conversion, validation, our exception
 * handler). The services are replaced by Mockito mocks, so no database or GitHub is involved.
 */
@WebMvcTest(GitHubController.class)
class GitHubControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubService gitHubService;

    @MockitoBean
    private RepositoryService repositoryService;

    private static final String VALID_SEARCH_BODY = """
            {"query":"spring boot","language":"Java","sort":"stars","order":"desc","page":1,"perPage":20}
            """;

    // ---------- POST /api/github/search ----------

    @Test
    void searchReturnsResultsAndMetadata() throws Exception {
        RepositoryResponse repo = TestData.response(1L, 100L, "spring-projects/spring-boot", "Java", 70000);
        when(gitHubService.search(any(SearchRequest.class))).thenReturn(
                new SearchResponse("spring boot language:Java", 1, 20, 1234, 1, 1, 0, List.of(repo)));

        mockMvc.perform(post("/api/github/search").contentType(MediaType.APPLICATION_JSON).content(VALID_SEARCH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubQuery").value("spring boot language:Java"))
                .andExpect(jsonPath("$.totalCount").value(1234))
                .andExpect(jsonPath("$.inserted").value(1))
                .andExpect(jsonPath("$.results[0].fullName").value("spring-projects/spring-boot"));
    }

    @Test
    void searchWithBlankQueryIsRejectedWithValidationDetails() throws Exception {
        mockMvc.perform(post("/api/github/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/github/search"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details", hasItem("query: query must not be blank")));
        verifyNoInteractions(gitHubService);
    }

    @Test
    void searchWithOutOfRangePerPageAndUnknownSortIsRejected() throws Exception {
        String body = "{\"query\":\"x\",\"perPage\":500,\"sort\":\"popularity\"}";

        mockMvc.perform(post("/api/github/search").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("perPage: perPage must be at most 100")))
                .andExpect(jsonPath("$.details", hasItem("sort: sort must be one of: stars, forks, updated")));
    }

    @Test
    void searchWithMalformedJsonIsRejected() throws Exception {
        mockMvc.perform(post("/api/github/search").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }

    @Test
    void rateLimitFromGitHubBecomes429WithRetryAfterHeader() throws Exception {
        when(gitHubService.search(any(SearchRequest.class)))
                .thenThrow(new GitHubRateLimitException("GitHub API rate limit exceeded", 30L));

        mockMvc.perform(post("/api/github/search").contentType(MediaType.APPLICATION_JSON).content(VALID_SEARCH_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void githubFailureBecomes502WithoutLeakingInternals() throws Exception {
        when(gitHubService.search(any(SearchRequest.class)))
                .thenThrow(new GitHubApiException("GitHub returned an error (HTTP 500).", HttpStatus.BAD_GATEWAY));

        mockMvc.perform(post("/api/github/search").contentType(MediaType.APPLICATION_JSON).content(VALID_SEARCH_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("GitHub returned an error (HTTP 500)."));
    }

    @Test
    void unexpectedExceptionBecomes500WithGenericMessage() throws Exception {
        when(gitHubService.search(any(SearchRequest.class))).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(post("/api/github/search").contentType(MediaType.APPLICATION_JSON).content(VALID_SEARCH_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    // ---------- GET /api/github/repositories ----------

    @Test
    void listPassesFiltersSortingAndPagingToTheService() throws Exception {
        RepositoryResponse repo = TestData.response(1L, 100L, "a/one", "Java", 500);
        when(repositoryService.findAll("Java", 100, "stars", "desc", 0, 10))
                .thenReturn(new PagedResponse<>(List.of(repo), 0, 10, 1, 1));

        mockMvc.perform(get("/api/github/repositories")
                        .param("language", "Java").param("minStars", "100").param("sortBy", "stars")
                        .param("direction", "desc").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("a/one"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        verify(repositoryService).findAll("Java", 100, "stars", "desc", 0, 10);
    }

    @Test
    void listUsesDefaultsWhenNoParametersAreGiven() throws Exception {
        when(repositoryService.findAll(null, null, "stars", "desc", 0, 20))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/github/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void listRejectsNegativeMinStarsBadDirectionAndOversizedPage() throws Exception {
        mockMvc.perform(get("/api/github/repositories")
                        .param("minStars", "-1").param("direction", "sideways").param("size", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("minStars must not be negative")))
                .andExpect(jsonPath("$.details", hasItem("direction must be ASC or DESC")))
                .andExpect(jsonPath("$.details", hasItem("size must be at most 100")));
        verifyNoInteractions(repositoryService);
    }

    @Test
    void listRejectsUnsupportedSortField() throws Exception {
        mockMvc.perform(get("/api/github/repositories").param("sortBy", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details",
                        hasItem("sortBy must be one of: stars, forks, name, updated, fetched")));
    }

    // ---------- GET /api/github/repositories/{id} ----------

    @Test
    void getByIdReturnsTheRepository() throws Exception {
        when(repositoryService.getById(1L)).thenReturn(TestData.response(1L, 100L, "a/one", "Java", 5));

        mockMvc.perform(get("/api/github/repositories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.githubId").value(100));
    }

    @Test
    void getByIdReturns404ForUnknownRepository() throws Exception {
        when(repositoryService.getById(99L)).thenThrow(new ResourceNotFoundException("Repository with id 99 not found"));

        mockMvc.perform(get("/api/github/repositories/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Repository with id 99 not found"))
                .andExpect(jsonPath("$.path").value("/api/github/repositories/99"));
    }

    @Test
    void getByIdWithNonNumericIdIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/github/repositories/abc"))
                .andExpect(status().isBadRequest());
    }

    // ---------- DELETE /api/github/repositories/{id} ----------

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/github/repositories/5"))
                .andExpect(status().isNoContent());
        verify(repositoryService).deleteById(5L);
    }

    @Test
    void deleteReturns404ForUnknownRepository() throws Exception {
        doThrow(new ResourceNotFoundException("Repository with id 99 not found"))
                .when(repositoryService).deleteById(99L);

        mockMvc.perform(delete("/api/github/repositories/99"))
                .andExpect(status().isNotFound());
    }
}
