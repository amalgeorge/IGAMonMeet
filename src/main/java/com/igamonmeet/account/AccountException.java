package com.igamonmeet.account;

import org.springframework.http.HttpStatus;

/** A rule violation the client can show to the user; {@code code} is a stable key for the UI. */
public class AccountException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AccountException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
