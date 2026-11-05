package com.oj.rc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A row of the existing "submissions" table.
 * teamId holds the user's "rc" value, problemId and eventId are foreign keys kept as plain numbers.
 * The Spring API only reads submissions, the Node/Flask services create and update them.
 */
@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "team_id")
    private String teamId;

    @Column(nullable = false)
    private String teamname = "TEAM";

    @Column(name = "problem_id", nullable = false)
    private Integer problemId;

    @Column(name = "event_id", nullable = false)
    private Integer eventId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String code;

    @Column(nullable = false)
    private String language;

    @Column(name = "failed_test_case", columnDefinition = "TEXT")
    private String failedTestCase;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String result = "Pending";

    private Integer score = 0;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    private String verdict;

    @Column(name = "created_at")
    private Instant createdAt;
}
