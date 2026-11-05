package com.oj.rc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oj.rc.entity.Submission;
import java.time.Instant;

public record SubmissionResponse(
        Integer id,
        @JsonProperty("team_id") String teamId,
        String teamname,
        @JsonProperty("problem_id") Integer problemId,
        @JsonProperty("event_id") Integer eventId,
        String code,
        String language,
        @JsonProperty("failed_test_case") String failedTestCase,
        String result,
        Integer score,
        @JsonProperty("submitted_at") Instant submittedAt,
        String verdict,
        @JsonProperty("created_at") Instant createdAt) {

    public static SubmissionResponse from(Submission s) {
        return new SubmissionResponse(
                s.getId(),
                s.getTeamId(),
                s.getTeamname(),
                s.getProblemId(),
                s.getEventId(),
                s.getCode(),
                s.getLanguage(),
                s.getFailedTestCase(),
                s.getResult(),
                s.getScore(),
                s.getSubmittedAt(),
                s.getVerdict(),
                s.getCreatedAt());
    }
}
