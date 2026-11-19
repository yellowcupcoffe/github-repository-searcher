package com.example.githubsearch.exception;

/** Thrown when a stored repository with the requested id does not exist (becomes HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
