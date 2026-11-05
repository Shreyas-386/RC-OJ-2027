package com.oj.rc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oj.rc.entity.User;

/** A user as sent to the client. There is deliberately no password field. */
public record UserResponse(
        Integer id,
        String username,
        String teamname,
        String rc,
        @JsonProperty("isjunior") boolean junior,
        String role) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getTeamname(),
                user.getRc(),
                user.isJunior(),
                user.getRole());
    }
}
