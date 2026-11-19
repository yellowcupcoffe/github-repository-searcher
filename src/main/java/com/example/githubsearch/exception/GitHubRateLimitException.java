package com.example.githubsearch.exception;

import org.springframework.http.HttpStatus;

/** GitHub told us we are sending too many requests. Our API answers 429 Too Many Requests. */
public class GitHubRateLimitException extends GitHubApiException {

    /** Seconds the caller should wait, or null if GitHub did not tell us. */
    private final Long retryAfterSeconds;

    public GitHubRateLimitException(String message, Long retryAfterSeconds) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
