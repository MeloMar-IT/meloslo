package com.example.meloslo.service;

/** Raised when a request conflicts with existing data, such as a duplicate name or a record in use (HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
