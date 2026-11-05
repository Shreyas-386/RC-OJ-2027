package com.oj.rc.service;

import com.oj.rc.dto.LoginResponse;
import com.oj.rc.dto.SubmissionResponse;
import com.oj.rc.dto.UserRequest;
import com.oj.rc.dto.UserResponse;
import com.oj.rc.entity.Event;
import com.oj.rc.entity.User;
import com.oj.rc.exception.ApiException;
import com.oj.rc.repository.EventRepository;
import com.oj.rc.repository.SubmissionRepository;
import com.oj.rc.repository.UserRepository;
import com.oj.rc.security.JwtService;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Business logic behind the /user endpoints (userController.js in the Node app). */
@Service
public class UserService {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final SubmissionRepository submissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository,
                       EventRepository eventRepository,
                       SubmissionRepository submissionRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.submissionRepository = submissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UserResponse register(UserRequest request) {
        if (!StringUtils.hasText(request.username())
                || !StringUtils.hasText(request.password())
                || request.junior() == null) {
            throw badRequest("Username, password and isjunior are required");
        }
        if (request.password().length() < MIN_PASSWORD_LENGTH) {
            throw badRequest("Password must be at least 6 characters long");
        }
        if (request.eventId() != null && !eventRepository.existsById(request.eventId())) {
            throw badRequest("Event not found");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw badRequest("Username already taken");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setTeamname(StringUtils.hasText(request.teamname()) ? request.teamname() : "TEAM");
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setJunior(request.junior());
        user.setRole("USER");
        user.setRc(UUID.randomUUID().toString());
        return UserResponse.from(userRepository.save(user));
    }

    public void updatePassword(UserRequest request) {
        if (!StringUtils.hasText(request.username()) || !StringUtils.hasText(request.newPassword())) {
            throw badRequest("Username and newPassword are required");
        }
        if (request.newPassword().length() < MIN_PASSWORD_LENGTH) {
            throw badRequest("New password must be at least 6 characters long");
        }

        User user = userRepository.findFirstByUsername(request.username())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    public LoginResponse login(UserRequest request) {
        if (!StringUtils.hasText(request.username())
                || !StringUtils.hasText(request.password())
                || !StringUtils.hasText(request.teamname())
                || request.junior() == null
                || request.eventId() == null) {
            throw badRequest("Username,Password,Teamname and isjunior are required");
        }

        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> badRequest("Event not found"));
        Instant now = Instant.now();
        if (event.getStartTime() == null || now.isBefore(event.getStartTime())) {
            throw badRequest("Event has not started yet");
        }
        // After the end time the user can still log in, but gets status 501 "ended" (see UserController)
        boolean eventEnded = event.getEndTime() != null && now.isAfter(event.getEndTime());

        User user = userRepository.findFirstByUsernameAndTeamname(request.username(), request.teamname())
                .orElseThrow(() -> badRequest("User not found. Please register first"));
        if (user.isJunior() != request.junior()) {
            throw badRequest("User not found");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw badRequest("Invalid username or password");
        }

        // HashMap, not Map.of: "rc" can be null for old users and Map.of does not accept null
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("username", user.getUsername());
        claims.put("event_id", request.eventId());
        claims.put("isjunior", user.isJunior());
        claims.put("team_id", user.getRc());
        claims.put("teamname", user.getTeamname());
        String token = jwtService.createToken(claims, Duration.ofHours(2));

        return eventEnded
                ? LoginResponse.forUserAfterEventEnded(token, user)
                : LoginResponse.forUser(token, user);
    }

    /** Returns null if the user no longer exists (the Node API answered {"user": null}). */
    public UserResponse getProfile(Integer userId) {
        return userRepository.findById(userId).map(UserResponse::from).orElse(null);
    }

    public List<SubmissionResponse> getHistory(String teamId) {
        // Admin tokens have no team_id. Node failed with a 500 here, and a null would
        // otherwise match submissions that have no team at all.
        if (!StringUtils.hasText(teamId)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error fetching submission history");
        }
        return submissionRepository.findByTeamIdOrderBySubmittedAtDesc(teamId).stream()
                .map(SubmissionResponse::from)
                .toList();
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
