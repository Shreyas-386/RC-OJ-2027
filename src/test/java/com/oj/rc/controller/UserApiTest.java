package com.oj.rc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oj.rc.entity.Event;
import com.oj.rc.entity.Submission;
import com.oj.rc.entity.User;
import com.oj.rc.security.JwtUser;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UserApiTest extends ApiTestBase {

    private static String registerBody(String username, String password, String teamname, Object isjunior, Integer eventId) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\""
                + (teamname == null ? "" : ",\"teamname\":\"" + teamname + "\"")
                + (isjunior == null ? "" : ",\"isjunior\":" + isjunior)
                + (eventId == null ? "" : ",\"event_id\":" + eventId) + "}";
    }

    private static String loginBody(String username, String password, String teamname, Object isjunior, Integer eventId) {
        return registerBody(username, password, teamname, isjunior, eventId);
    }

    // ---------- POST /user/register ----------

    @Test
    void register_createsUser_withHashedPassword_andNeverReturnsThePassword() throws Exception {
        Event event = saveRunningEvent();

        postJson("/user/register", registerBody("alice", "secret123", "Alpha", false, event.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User registered successfully!"))
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.user.teamname").value("Alpha"))
                .andExpect(jsonPath("$.user.isjunior").value(false))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.password").doesNotExist());

        User saved = userRepository.findFirstByUsername("alice").orElseThrow();
        assertThat(saved.getPassword()).startsWith("$2").isNotEqualTo("secret123");
        assertThat(saved.getRc()).isNotBlank(); // random team id, used later in the token
    }

    @Test
    void register_withoutTeamname_usesTEAM() throws Exception {
        postJson("/user/register", registerBody("alice", "secret123", null, true, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.teamname").value("TEAM"))
                .andExpect(jsonPath("$.user.isjunior").value(true));
    }

    @Test
    void register_giveEachUserADifferentTeamId() throws Exception {
        postJson("/user/register", registerBody("alice", "secret123", null, false, null));
        postJson("/user/register", registerBody("bob", "secret123", null, false, null));

        String aliceRc = userRepository.findFirstByUsername("alice").orElseThrow().getRc();
        String bobRc = userRepository.findFirstByUsername("bob").orElseThrow().getRc();
        assertThat(aliceRc).isNotEqualTo(bobRc);
    }

    @Test
    void register_withoutUsername_returns400() throws Exception {
        postJson("/user/register", "{\"password\":\"secret123\",\"isjunior\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username, password and isjunior are required"));
    }

    @Test
    void register_withoutIsjunior_returns400() throws Exception {
        postJson("/user/register", registerBody("alice", "secret123", null, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username, password and isjunior are required"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void register_withShortPassword_returns400() throws Exception {
        postJson("/user/register", registerBody("alice", "12345", null, false, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password must be at least 6 characters long"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void register_withUnknownEvent_returns400() throws Exception {
        postJson("/user/register", registerBody("alice", "secret123", null, false, 999999))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Event not found"));
    }

    @Test
    void register_withTakenUsername_returns400() throws Exception {
        saveUser("alice", "Alpha", "secret123", false, "USER");

        postJson("/user/register", registerBody("alice", "another123", null, false, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username already taken"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void register_ignoresABadTokenInTheHeader() throws Exception {
        postJson("/user/register", registerBody("alice", "secret123", null, false, null), "old-broken-token")
                .andExpect(status().isCreated());
    }

    @Test
    void register_withBrokenJson_returns400() throws Exception {
        postJson("/user/register", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // ---------- POST /user/login ----------

    @Test
    void login_withCorrectDetails_returnsTokenWithTheTeamId() throws Exception {
        Event event = saveRunningEvent();
        User alice = saveUser("alice", "Alpha", "secret123", false, "USER");

        var login = postJson("/user/login", loginBody("alice", "secret123", "Alpha", false, event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged in successfully"))
                .andExpect(jsonPath("$.isVerified").value(true))
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.user.isjunior").value(false))
                .andExpect(jsonPath("$.status").doesNotExist());

        JwtUser tokenContent = jwtService.parseToken(tokenFrom(login));
        assertThat(tokenContent.id()).isEqualTo(alice.getId());
        assertThat(tokenContent.username()).isEqualTo("alice");
        assertThat(tokenContent.teamId()).isEqualTo(alice.getRc());
    }

    @Test
    void login_withWrongPassword_returns400() throws Exception {
        Event event = saveRunningEvent();
        saveUser("alice", "Alpha", "secret123", false, "USER");

        postJson("/user/login", loginBody("alice", "wrong-password", "Alpha", false, event.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid username or password"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void login_withWrongTeamname_returns400() throws Exception {
        Event event = saveRunningEvent();
        saveUser("alice", "Alpha", "secret123", false, "USER");

        postJson("/user/login", loginBody("alice", "secret123", "Beta", false, event.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User not found. Please register first"));
    }

    @Test
    void login_withTheOtherJuniorSetting_returns400() throws Exception {
        Event event = saveRunningEvent();
        saveUser("alice", "Alpha", "secret123", false, "USER");

        postJson("/user/login", loginBody("alice", "secret123", "Alpha", true, event.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "password", "teamname", "isjunior", "event_id"})
    void login_withAMissingField_returns400(String missingField) throws Exception {
        Event event = saveRunningEvent();
        saveUser("alice", "Alpha", "secret123", false, "USER");
        Map<String, String> fields = new java.util.LinkedHashMap<>(Map.of(
                "username", "\"alice\"",
                "password", "\"secret123\"",
                "teamname", "\"Alpha\"",
                "isjunior", "false",
                "event_id", String.valueOf(event.getId())));
        fields.remove(missingField);
        String body = fields.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));

        postJson("/user/login", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username,Password,Teamname and isjunior are required"));
    }

    @Test
    void login_withUnknownEvent_returns400() throws Exception {
        saveUser("alice", "Alpha", "secret123", false, "USER");

        postJson("/user/login", loginBody("alice", "secret123", "Alpha", false, 999999))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Event not found"));
    }

    @Test
    void login_beforeTheEventStarts_returns400() throws Exception {
        Event event = saveEvent("Later", Instant.now().plus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(2)));
        saveUser("alice", "Alpha", "secret123", false, "USER");

        postJson("/user/login", loginBody("alice", "secret123", "Alpha", false, event.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Event has not started yet"));
    }

    @Test
    void login_whenTheEventHasNoStartTime_returns400() throws Exception {
        Event event = saveEvent("Not scheduled", null, null);
        saveUser("alice", "Alpha", "secret123", false, "USER");

        postJson("/user/login", loginBody("alice", "secret123", "Alpha", false, event.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Event has not started yet"));
    }

    @Test
    void login_afterTheEventEnded_returns501WithATokenAndTheTeamId() throws Exception {
        Event event = saveEvent("Finished", Instant.now().minus(Duration.ofHours(3)), Instant.now().minus(Duration.ofHours(1)));
        User alice = saveUser("alice", "Alpha", "secret123", false, "USER");

        postJson("/user/login", loginBody("alice", "secret123", "Alpha", false, event.getId()))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.status").value("ended"))
                .andExpect(jsonPath("$.message").value("Event has ended"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.isVerified").value(true))
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.user.team_id").value(alice.getRc()));
    }

    // ---------- POST /user/updatepassword ----------

    @Test
    void updatePassword_changesThePassword() throws Exception {
        Event event = saveRunningEvent();
        saveUser("alice", "Alpha", "oldpass123", false, "USER");

        postJson("/user/updatepassword", "{\"username\":\"alice\",\"newPassword\":\"newpass123\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully"));

        postJson("/user/login", loginBody("alice", "oldpass123", "Alpha", false, event.getId()))
                .andExpect(status().isBadRequest());
        postJson("/user/login", loginBody("alice", "newpass123", "Alpha", false, event.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void updatePassword_forUnknownUser_returns404() throws Exception {
        postJson("/user/updatepassword", "{\"username\":\"ghost\",\"newPassword\":\"newpass123\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    @Test
    void updatePassword_withShortPassword_returns400_andKeepsTheOldPassword() throws Exception {
        User alice = saveUser("alice", "Alpha", "oldpass123", false, "USER");

        postJson("/user/updatepassword", "{\"username\":\"alice\",\"newPassword\":\"123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("New password must be at least 6 characters long"));

        String stored = userRepository.findById(alice.getId()).orElseThrow().getPassword();
        assertThat(passwordEncoder.matches("oldpass123", stored)).isTrue();
    }

    @Test
    void updatePassword_withoutNewPassword_returns400_andDoesNotEchoTheBody() throws Exception {
        postJson("/user/updatepassword", "{\"username\":\"alice\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username and newPassword are required"))
                .andExpect(jsonPath("$.received").doesNotExist());
    }

    // ---------- token rules for logged-in endpoints (authMiddleware.js) ----------

    @ParameterizedTest
    @ValueSource(strings = {"/user/profile", "/user/gethistory"})
    void loggedInEndpoints_withoutToken_return403(String url) throws Exception {
        getWithToken(url, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/user/profile", "/user/gethistory"})
    void loggedInEndpoints_withGarbageToken_return403(String url) throws Exception {
        getWithToken(url, "not.a.jwt")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Invalid token. Please log in again."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/user/profile", "/user/gethistory"})
    void loggedInEndpoints_withExpiredToken_return401(String url) throws Exception {
        String expired = jwtService.createToken(Map.of("id", 1, "team_id", "x"), Duration.ofMinutes(-5));

        getWithToken(url, expired)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Token expired. Please log in again."));
    }

    // ---------- POST /user/logout ----------

    @Test
    void logout_withValidToken_returns200() throws Exception {
        String token = jwtService.createToken(Map.of("id", 1), Duration.ofHours(1));

        postWithToken("/user/logout", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User logged out successfully"));
    }

    @Test
    void logout_withoutToken_returns403() throws Exception {
        postWithToken("/user/logout", null).andExpect(status().isForbidden());
    }

    // ---------- GET /user/profile ----------

    @Test
    void profile_returnsTheLoggedInUser_withoutThePassword() throws Exception {
        Event event = saveRunningEvent();
        User alice = saveUser("alice", "Alpha", "secret123", false, "USER");
        String token = tokenFrom(postJson("/user/login", loginBody("alice", "secret123", "Alpha", false, event.getId())));

        getWithToken("/user/profile", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(alice.getId()))
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.user.teamname").value("Alpha"))
                .andExpect(jsonPath("$.user.rc").value(alice.getRc()))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void profile_forADeletedUser_returnsANullUser() throws Exception {
        String token = jwtService.createToken(Map.of("id", 424242), Duration.ofHours(1));

        getWithToken("/user/profile", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").isEmpty());
    }

    // ---------- GET /user/gethistory ----------

    @Test
    void history_returnsOnlyTheTeamsSubmissions_newestFirst() throws Exception {
        Event event = saveRunningEvent();
        User alice = saveUser("alice", "Alpha", "secret123", false, "USER");
        User bob = saveUser("bob", "Beta", "secret123", false, "USER");
        Instant now = Instant.now();
        saveSubmission(alice.getRc(), "older", now.minus(Duration.ofMinutes(30)));
        saveSubmission(alice.getRc(), "newer", now.minus(Duration.ofMinutes(5)));
        saveSubmission(bob.getRc(), "someone else's", now);
        String token = tokenFrom(postJson("/user/login", loginBody("alice", "secret123", "Alpha", false, event.getId())));

        getWithToken("/user/gethistory", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("newer"))
                .andExpect(jsonPath("$[1].code").value("older"))
                .andExpect(jsonPath("$[0].team_id").value(alice.getRc()))
                .andExpect(jsonPath("$[0].problem_id").value(1))
                .andExpect(jsonPath("$[0].language").value("python"))
                .andExpect(jsonPath("$[0].result").value("accepted"))
                .andExpect(jsonPath("$[0].submitted_at").isNotEmpty());
    }

    @Test
    void history_forATeamWithoutSubmissions_returnsAnEmptyList() throws Exception {
        String token = jwtService.createToken(Map.of("id", 1, "team_id", "team-without-submissions"), Duration.ofHours(1));

        getWithToken("/user/gethistory", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void history_withATokenThatHasNoTeamId_returns500_andDoesNotLeakOtherSubmissions() throws Exception {
        saveSubmission(null, "belongs to no team", Instant.now());
        String adminTokenWithoutTeam = jwtService.createToken(Map.of("id", 1, "role", "admin"), Duration.ofHours(1));

        getWithToken("/user/gethistory", adminTokenWithoutTeam)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Error fetching submission history"))
                .andExpect(jsonPath("$[0]").doesNotExist());
    }

    private void saveSubmission(String teamId, String code, Instant submittedAt) {
        Submission submission = new Submission();
        submission.setTeamId(teamId);
        submission.setTeamname("some team");
        submission.setProblemId(1);
        submission.setEventId(1);
        submission.setCode(code);
        submission.setLanguage("python");
        submission.setResult("accepted");
        submission.setScore(200);
        submission.setSubmittedAt(submittedAt);
        submission.setCreatedAt(submittedAt);
        submissionRepository.save(submission);
    }
}
