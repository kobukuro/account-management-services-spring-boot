package com.peter.authnservice.exception;

public class InvalidAuthorizationCodeException extends RuntimeException {
    public InvalidAuthorizationCodeException(String message) {
        super(message);
    }
}
