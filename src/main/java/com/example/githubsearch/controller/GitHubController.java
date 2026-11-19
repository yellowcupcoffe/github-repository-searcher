package com.example.githubsearch.controller;

import com.example.githubsearch.dto.ApiErrorResponse;
import com.example.githubsearch.dto.PagedResponse;
import com.example.githubsearch.dto.RepositoryResponse;
import com.example.githubsearch.dto.SearchRequest;
import com.example.githubsearch.dto.SearchResponse;
import com.example.githubsearch.service.GitHubService;
import com.example.githubsearch.service.RepositoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP layer only: read the request, call a service, return a DTO. No business logic here. */
@RestController
@RequestMapping("/api/github")
@Validated // enables validation of @RequestParam / @PathVariable constraints
@Tag(name = "GitHub Repositories", description = "Search GitHub and manage the repositories stored in PostgreSQL")
public class GitHubController {

    private final GitHubService gitHubService;
    private final RepositoryService repositoryService;

    public GitHubController(GitHubService gitHubService, RepositoryService repositoryService) {
        this.gitHubService = gitHubService;
        this.repositoryService = repositoryService;
    }

    @PostMapping("/search")
    @Operation(summary = "Search GitHub and save the results",
            description = "Calls the GitHub Search API, then inserts new repositories and updates existing ones "
                    + "(matched by GitHub id). An empty search returns 200 with an empty list.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search completed (results may be empty)"),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "GitHub could not process the query",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "GitHub rate limit reached (see Retry-After header)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "GitHub failed or returned an unexpected response",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "504", description = "GitHub did not respond in time",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        return gitHubService.search(request);
    }

    @GetMapping("/repositories")
    @Operation(summary = "List stored repositories",
            description = "Reads from PostgreSQL (not GitHub). All filters are optional. Page numbers start at 0.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of stored repositories"),
            @ApiResponse(responseCode = "400", description = "Invalid query parameter",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PagedResponse<RepositoryResponse> getRepositories(
            @Parameter(description = "Exact language, case-insensitive", example = "Java")
            @RequestParam(required = false) String language,

            @Parameter(description = "Minimum number of stars", example = "100")
            @RequestParam(required = false)
            @Min(value = 0, message = "minStars must not be negative") Integer minStars,

            @Parameter(description = "Field to sort by", example = "stars",
                    schema = @Schema(allowableValues = {"stars", "forks", "name", "updated", "fetched"}))
            @RequestParam(defaultValue = "stars")
            @Pattern(regexp = "(?i)stars|forks|name|updated|fetched",
                    message = "sortBy must be one of: stars, forks, name, updated, fetched") String sortBy,

            @Parameter(description = "Sort direction", example = "desc",
                    schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(defaultValue = "desc")
            @Pattern(regexp = "(?i)asc|desc", message = "direction must be ASC or DESC") String direction,

            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must not be negative") int page,

            @Parameter(description = "Page size (1-100)", example = "10")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") int size) {
        return repositoryService.findAll(language, minStars, sortBy, direction, page, size);
    }

    @GetMapping("/repositories/{id}")
    @Operation(summary = "Get one stored repository by internal id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Repository found"),
            @ApiResponse(responseCode = "404", description = "No repository with this id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public RepositoryResponse getRepository(@Parameter(description = "Internal database id", example = "1")
                                            @PathVariable Long id) {
        return repositoryService.getById(id);
    }

    @DeleteMapping("/repositories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a stored repository by internal id")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "404", description = "No repository with this id",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public void deleteRepository(@Parameter(description = "Internal database id", example = "1")
                                 @PathVariable Long id) {
        repositoryService.deleteById(id);
    }
}
