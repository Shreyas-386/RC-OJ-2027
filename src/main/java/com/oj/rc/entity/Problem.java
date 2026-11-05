package com.oj.rc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A row of the existing "problems" table.
 * eventId is the foreign key to events.id, kept as a plain number to keep things simple.
 */
@Entity
@Table(name = "problems")
@Getter
@Setter
@NoArgsConstructor
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer score = 50;

    @Column(name = "input_format", columnDefinition = "TEXT")
    private String inputFormat;

    @Column(name = "output_format", columnDefinition = "TEXT")
    private String outputFormat;

    @Column(columnDefinition = "TEXT")
    private String constraints;

    @Column(name = "isjunior", nullable = false)
    private boolean junior;

    @Column(name = "time_limit")
    private Integer timeLimit = 2;

    @Column(name = "memory_limit")
    private Integer memoryLimit = 256;

    @Column(name = "event_id", nullable = false)
    private Integer eventId;
}
