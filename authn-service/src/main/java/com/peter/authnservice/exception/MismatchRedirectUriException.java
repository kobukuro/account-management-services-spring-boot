package com.peter.authnservice.exception;


public class MismatchRedirectUriException extends RuntimeException {
    public MismatchRedirectUriException(String message) {
        super(message);
    }
}
