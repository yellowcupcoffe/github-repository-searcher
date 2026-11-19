package com.example.githubsearch.exception;

import org.springframework.http.HttpStatus;

/**
 * Anything that goes wrong while talking to GitHub. It carries the HTTP status that
 * OUR API should answer with (for example 502 Bad Gateway or 504 Gateway Timeout).
 */
public class GitHubApiException extends RuntimeException {

    private final HttpStatus status;

    public GitHubApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public GitHubApiException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
