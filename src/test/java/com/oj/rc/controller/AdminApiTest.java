package com.oj.rc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oj.rc.entity.Problem;
import com.oj.rc.security.JwtService;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdminApiTest extends ApiTestBase {

    // ---------- POST /admin/register ----------

    @Test
    void registerAdmin_createsAdmin_andNeverReturnsThePassword() throws Exception {
        postJson("/admin/register",
                "{\"username\":\"root\",\"email\":\"root@example.com\",\"password\":\"secret123\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Admin registered successfully"))
                .andExpect(jsonPath("$.admin.username").value("root"))
                .andExpect(jsonPath("$.admin.role").value("admin"))
                .andExpect(jsonPath("$.admin.isjunior").value(false))
                .andExpect(jsonPath("$.admin.password").doesNotExist());

        // Stored as a BCrypt hash, not as plain text
        String savedPassword = userRepository.findFirstByUsername("root").orElseThrow().getPassword();
        assertThat(savedPassword).startsWith("$2").isNotEqualTo("secret123");
    }

    @Test
    void registerAdmin_withMissingEmail_returns400() throws Exception {
        postJson("/admin/register", "{\"username\":\"root\",\"password\":\"secret123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username, email, and password are required"));

        assertThat(userRepository.count()).isZero();
    }

    // ---------- POST /admin/login ----------

    @Test
    void loginAdmin_withCorrectPassword_returnsATokenThatOpensAdminEndpoints() throws Exception {
        postJson("/admin/register",
                "{\"username\":\"root\",\"email\":\"root@example.com\",\"password\":\"secret123\"}");

        var login = postJson("/admin/login", "{\"username\":\"root\",\"password\":\"secret123\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User logged in successfully"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        getWithToken("/admin/users", tokenFrom(login)).andExpect(status().isOk());
    }

    @Test
    void loginAdmin_withWrongPassword_returns400AndABareJsonString() throws Exception {
        saveUser("root", "TEAM", "secret123", false, "admin");

        postJson("/admin/login", "{\"username\":\"root\",\"password\":\"wrong\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().string("\"Invalid Password\""));
    }

    @Test
    void loginAdmin_withUnknownUser_returns404AndABareJsonString() throws Exception {
        postJson("/admin/login", "{\"username\":\"nobody\",\"password\":\"secret123\"}")
                .andExpect(status().isNotFound())
                .andExpect(content().string("\"User Not Found\""));
    }

    @Test
    void loginAdmin_withoutPassword_returns400() throws Exception {
        postJson("/admin/login", "{\"username\":\"root\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Error logging in User"));
    }

    @Test
    void loginAdmin_ignoresABadTokenInTheHeader() throws Exception {
        // Public endpoint: an old or broken Authorization header must not block it
        saveUser("root", "TEAM", "secret123", false, "admin");

        postJson("/admin/login", "{\"username\":\"root\",\"password\":\"secret123\"}", "not-a-real-token")
                .andExpect(status().isOk());
    }

    // ---------- admin-only rules (adminMiddleware.js) ----------

    @ParameterizedTest
    @ValueSource(strings = {"/admin/users", "/admin/problems", "/admin/teams"})
    void adminEndpoints_withoutToken_return403(String url) throws Exception {
        getWithToken(url, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied. No token provided."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/users", "/admin/problems", "/admin/teams"})
    void adminEndpoints_withGarbageToken_return400(String url) throws Exception {
        getWithToken(url, "not.a.jwt")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid token"));
    }

    @Test
    void adminEndpoints_withExpiredAdminToken_return400() throws Exception {
        String expired = jwtService.createToken(Map.of("id", 1, "role", "admin"), Duration.ofMinutes(-5));

        getWithToken("/admin/users", expired)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid token"));
    }

    @Test
    void adminEndpoints_withTokenSignedByAnotherSecret_return400() throws Exception {
        JwtService otherService = new JwtService("a-completely-different-secret-0123456789");
        String forged = otherService.createToken(Map.of("id", 1, "role", "admin"), Duration.ofHours(1));

        getWithToken("/admin/users", forged).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/users", "/admin/problems", "/admin/teams"})
    void adminEndpoints_withNormalUserToken_return403(String url) throws Exception {
        String userToken = jwtService.createToken(Map.of("id", 1, "role", "USER"), Duration.ofHours(1));

        getWithToken(url, userToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access only For Admins"));
    }

    @Test
    void createEvent_withNormalUserToken_returns403_andCreatesNothing() throws Exception {
        String userToken = jwtService.createToken(Map.of("id", 1, "role", "USER"), Duration.ofHours(1));

        postJson("/admin/create-event", "{\"name\":\"Sneaky\"}", userToken)
                .andExpect(status().isForbidden());

        assertThat(eventRepository.count()).isZero();
    }

    // ---------- GET /admin/users ----------

    @Test
    void getAllUsers_returnsEveryUser_withoutPasswords() throws Exception {
        saveUser("alice", "Alpha", "secret123", false, "USER");
        String token = adminToken(); // creates one more user (the admin)

        getWithToken("/admin/users", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[1].password").doesNotExist())
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].role").value("USER"));
    }

    // ---------- GET /admin/problems ----------

    @Test
    void getAllProblems_returnsTheShortProblemList() throws Exception {
        Problem problem = new Problem();
        problem.setTitle("The Mysterious Number");
        problem.setDescription("Long text that the list does not need");
        problem.setJunior(true);
        problem.setEventId(7);
        problemRepository.save(problem);

        getWithToken("/admin/problems", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problems.length()").value(1))
                .andExpect(jsonPath("$.problems[0].title").value("The Mysterious Number"))
                .andExpect(jsonPath("$.problems[0].isjunior").value(true))
                .andExpect(jsonPath("$.problems[0].event_id").value(7))
                .andExpect(jsonPath("$.problems[0].description").doesNotExist());
    }

    @Test
    void getAllProblems_withNoProblems_returnsAnEmptyList() throws Exception {
        getWithToken("/admin/problems", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problems.length()").value(0));
    }

    // ---------- GET /admin/teams ----------

    @Test
    void getAllTeams_returns501_becauseTheTeamModelNoLongerExists() throws Exception {
        getWithToken("/admin/teams", adminToken())
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // ---------- POST /admin/create-event ----------

    @Test
    void createEvent_savesTheEvent() throws Exception {
        postJson("/admin/create-event", "{\"name\":\"Programming Contest 2026\"}", adminToken())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Event created"))
                .andExpect(jsonPath("$.event.id").isNumber())
                .andExpect(jsonPath("$.event.name").value("Programming Contest 2026"))
                .andExpect(jsonPath("$.event.is_active").value(false))
                .andExpect(jsonPath("$.event.start_time").isEmpty())
                .andExpect(jsonPath("$.event.end_time").isEmpty())
                .andExpect(jsonPath("$.event.created_at").isNotEmpty());

        assertThat(eventRepository.existsByName("Programming Contest 2026")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\"\"}", "{\"name\":\"   \"}"})
    void createEvent_withoutAName_returns400(String body) throws Exception {
        postJson("/admin/create-event", body, adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields"));

        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void createEvent_withADuplicateName_returns409() throws Exception {
        saveEvent("Programming Contest 2026", null, null);

        postJson("/admin/create-event", "{\"name\":\"Programming Contest 2026\"}", adminToken())
                .andExpect(status().isConflict());

        assertThat(eventRepository.count()).isEqualTo(1);
    }
}
