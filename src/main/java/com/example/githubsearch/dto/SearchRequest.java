package com.example.githubsearch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of POST /api/github/search. Optional fields fall back to defaults. */
public record SearchRequest(
        @Schema(example = "spring boot", description = "Free-text search term")
        @NotBlank(message = "query must not be blank")
        @Size(max = 256, message = "query must be at most 256 characters")
        String query,

        @Schema(example = "Java", description = "Optional programming language filter")
        @Size(max = 50, message = "language must be at most 50 characters")
        @Pattern(regexp = "^[\\p{L}0-9 +#.\\-]*$", message = "language contains unsupported characters")
        String language,

        @Schema(example = "stars", allowableValues = {"stars", "forks", "updated"},
                description = "Omit for GitHub's best-match ranking")
        @Pattern(regexp = "stars|forks|updated", message = "sort must be one of: stars, forks, updated")
        String sort,

        @Schema(example = "desc", allowableValues = {"asc", "desc"})
        @Pattern(regexp = "(?i)asc|desc", message = "order must be asc or desc")
        String order,

        @Schema(example = "1", description = "1-based page number (GitHub convention)")
        @Min(value = 1, message = "page must be at least 1")
        Integer page,

        @Schema(example = "20", description = "Results per page, 1-100")
        @Min(value = 1, message = "perPage must be at least 1")
        @Max(value = 100, message = "perPage must be at most 100")
        Integer perPage) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PER_PAGE = 20;

    public int pageOrDefault() {
        return page == null ? DEFAULT_PAGE : page;
    }

    public int perPageOrDefault() {
        return perPage == null ? DEFAULT_PER_PAGE : perPage;
    }
}
