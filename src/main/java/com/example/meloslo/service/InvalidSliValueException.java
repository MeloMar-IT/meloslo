package com.example.meloslo.service;

/** Raised when an SLI metric value or an SLO target is outside the supported scale. */
public class InvalidSliValueException extends RuntimeException {
    public InvalidSliValueException(String message) {
        super(message);
    }
}
