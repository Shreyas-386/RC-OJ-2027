package com.oj.rc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.oj.rc.entity.Event;
import com.oj.rc.entity.User;
import com.oj.rc.repository.EventRepository;
import com.oj.rc.repository.ProblemRepository;
import com.oj.rc.repository.SubmissionRepository;
import com.oj.rc.repository.UserRepository;
import com.oj.rc.security.JwtService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Shared setup for the API tests.
 * The whole application starts (real security rules, real BCrypt and JWT, real JPA)
 * on an in-memory H2 database, and the tables are emptied before every test.
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class ApiTestBase {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected EventRepository eventRepository;
    @Autowired
    protected ProblemRepository problemRepository;
    @Autowired
    protected SubmissionRepository submissionRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JwtService jwtService;

    protected MockMvc mockMvc;

    @BeforeEach
    void buildMockMvcAndEmptyTheDatabase() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        submissionRepository.deleteAll();
        problemRepository.deleteAll();
        userRepository.deleteAll();
        eventRepository.deleteAll();
    }

    protected ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Same as postJson, but sends "Authorization: Bearer <token>" (the token text can be anything). */
    protected ResultActions postJson(String url, String json, String token) throws Exception {
        return mockMvc.perform(post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** token can be null for "no Authorization header". */
    protected ResultActions getWithToken(String url, String token) throws Exception {
        MockHttpServletRequestBuilder request = get(url);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions postWithToken(String url, String token) throws Exception {
        MockHttpServletRequestBuilder request = post(url);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    /** Reads "token" out of a login response. */
    protected String tokenFrom(ResultActions loginResult) throws Exception {
        String body = loginResult.andReturn().getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(body);
        assertThat(matcher.find()).as("a token in the response: " + body).isTrue();
        return matcher.group(1);
    }

    /** Saves a user straight into the database, with a properly hashed password. */
    protected User saveUser(String username, String teamname, String password, boolean junior, String role) {
        User user = new User();
        user.setUsername(username);
        user.setTeamname(teamname);
        user.setPassword(passwordEncoder.encode(password));
        user.setJunior(junior);
        user.setRole(role);
        user.setRc("rc-" + username);
        return userRepository.save(user);
    }

    protected Event saveEvent(String name, Instant start, Instant end) {
        Event event = new Event();
        event.setName(name);
        event.setStartTime(start);
        event.setEndTime(end);
        return eventRepository.save(event);
    }

    protected Event saveRunningEvent() {
        return saveEvent("Running contest", Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
    }

    /** A valid admin token, created directly (the login endpoint has its own tests). */
    protected String adminToken() {
        User admin = saveUser("admin-" + System.nanoTime(), "TEAM", "adminpass1", false, "admin");
        return jwtService.createToken(
                Map.of("id", admin.getId(), "username", admin.getUsername(), "role", "admin"),
                Duration.ofHours(1));
    }
}
