package com.oj.rc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body shared by the admin and user endpoints.
 * Each endpoint only reads the fields it needs (for example login uses username + password).
 * The JSON names are the same as in the Node API, so "isjunior" and "event_id" keep their names.
 */
public record UserRequest(
        String username,
        String email,
        String password,
        String teamname,
        @JsonProperty("isjunior") Boolean junior,
        @JsonProperty("event_id") Integer eventId,
        String newPassword) {
}
