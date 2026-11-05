package com.oj.rc.security;

/** The data we read from a valid token. Controllers receive it with @AuthenticationPrincipal. */
public record JwtUser(Integer id, String username, String role, String teamId) {
}
