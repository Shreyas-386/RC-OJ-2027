package com.oj.rc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.oj.rc.entity.User;

/**
 * Response of the admin login and the user login.
 * Fields that are null are left out of the JSON, which gives the three different shapes
 * the Node API returned (admin login, user login, user login after the event ended).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        String status,
        String message,
        @JsonProperty("isVerified") Boolean verified,
        String token,
        LoginUser user) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginUser(
            @JsonProperty("isVerified") Boolean verified,
            String username,
            @JsonProperty("isjunior") boolean junior,
            @JsonProperty("team_id") String teamId) {
    }

    public static LoginResponse forAdmin(String token) {
        return new LoginResponse(null, "User logged in successfully", null, token, null);
    }

    public static LoginResponse forUser(String token, User user) {
        LoginUser loginUser = new LoginUser(null, user.getUsername(), user.isJunior(), null);
        return new LoginResponse(null, "Logged in successfully", true, token, loginUser);
    }

    public static LoginResponse forUserAfterEventEnded(String token, User user) {
        LoginUser loginUser = new LoginUser(true, user.getUsername(), user.isJunior(), user.getRc());
        return new LoginResponse("ended", "Event has ended", null, token, loginUser);
    }

    // Not named like a getter, so it is not written to the JSON
    public boolean eventHasEnded() {
        return "ended".equals(status);
    }
}
