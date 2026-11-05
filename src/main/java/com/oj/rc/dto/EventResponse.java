package com.oj.rc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oj.rc.entity.Event;
import java.time.Instant;

public record EventResponse(
        Integer id,
        String name,
        @JsonProperty("start_time") Instant startTime,
        @JsonProperty("end_time") Instant endTime,
        @JsonProperty("is_active") boolean active,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getStartTime(),
                event.getEndTime(),
                event.isActive(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }
}
