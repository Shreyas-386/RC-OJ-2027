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
 * A row of the existing "user" table. Both normal users and admins live here,
 * the "role" column tells them apart ("USER" or "admin").
 */
@Entity
@Table(name = "user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String username;

    @Column(nullable = false)
    private String teamname = "TEAM";

    // BCrypt hash, never sent to the client
    private String password;

    // Team id used by the submission tables (a UUID for registered users)
    private String rc;

    @Column(name = "isjunior", nullable = false)
    private boolean junior;

    @Column(nullable = false, length = 100)
    private String role = "USER";
}
