package com.example.githubsearch.exception;

/** Thrown for invalid input that Bean Validation cannot express on its own (becomes HTTP 400). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
