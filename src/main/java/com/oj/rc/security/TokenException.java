package com.oj.rc.security;

/** A token could not be accepted: it is expired, or it is not a valid token signed with our secret. */
public class TokenException extends RuntimeException {

    private final boolean expired;

    public TokenException(boolean expired) {
        super(expired ? "Token expired" : "Invalid token");
        this.expired = expired;
    }

    public boolean isExpired() {
        return expired;
    }
}
