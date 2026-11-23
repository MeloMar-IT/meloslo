package com.example.meloslo.service;

/** Raised when a record does not exist or is not visible to the caller (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
