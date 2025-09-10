package com.peter.authnservice.exception;


public class UserAccountDisabledException extends RuntimeException {
    public UserAccountDisabledException(String message) {
        super(message);
    }
}
