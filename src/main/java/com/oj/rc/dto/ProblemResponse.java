package com.oj.rc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oj.rc.entity.Problem;

/** Short version of a problem, used by the admin problem list. */
public record ProblemResponse(
        Integer id,
        String title,
        @JsonProperty("isjunior") boolean junior,
        @JsonProperty("event_id") Integer eventId) {

    public static ProblemResponse from(Problem problem) {
        return new ProblemResponse(
                problem.getId(),
                problem.getTitle(),
                problem.isJunior(),
                problem.getEventId());
    }
}
