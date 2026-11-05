package com.oj.rc.service;

import com.oj.rc.dto.EventRequest;
import com.oj.rc.dto.EventResponse;
import com.oj.rc.dto.JsonString;
import com.oj.rc.dto.LoginResponse;
import com.oj.rc.dto.ProblemResponse;
import com.oj.rc.dto.UserRequest;
import com.oj.rc.dto.UserResponse;
import com.oj.rc.entity.Event;
import com.oj.rc.entity.User;
import com.oj.rc.exception.ApiException;
import com.oj.rc.repository.EventRepository;
import com.oj.rc.repository.ProblemRepository;
import com.oj.rc.repository.UserRepository;
import com.oj.rc.security.JwtService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Business logic behind the /admin endpoints (adminController.js in the Node app). */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final ProblemRepository problemRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AdminService(UserRepository userRepository,
                        EventRepository eventRepository,
                        ProblemRepository problemRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.problemRepository = problemRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UserResponse registerAdmin(UserRequest request) {
        if (!StringUtils.hasText(request.username())
                || !StringUtils.hasText(request.email())
                || !StringUtils.hasText(request.password())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    Map.of("message", "Username, email, and password are required"));
        }

        // The email is required but not saved: the existing "user" table has no email column.
        User admin = new User();
        admin.setUsername(request.username());
        admin.setPassword(passwordEncoder.encode(request.password()));
        admin.setJunior(false);
        admin.setRole("admin");
        return UserResponse.from(userRepository.save(admin));
    }

    public LoginResponse loginAdmin(UserRequest request) {
        if (!StringUtils.hasText(request.username()) || !StringUtils.hasText(request.password())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Error logging in User");
        }

        // The Node app answers these two errors with a bare JSON string, not an object
        User user = userRepository.findFirstByUsername(request.username())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, new JsonString("User Not Found")));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, new JsonString("Invalid Password"));
        }

        String token = jwtService.createToken(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "isjunior", user.isJunior(),
                "role", user.getRole()), Duration.ofDays(1));
        return LoginResponse.forAdmin(token);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    public List<ProblemResponse> getAllProblems() {
        return problemRepository.findAll().stream().map(ProblemResponse::from).toList();
    }

    public EventResponse createEvent(EventRequest request) {
        if (!StringUtils.hasText(request.name())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Missing required fields");
        }
        if (eventRepository.existsByName(request.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "An event with this name already exists");
        }

        Event event = new Event();
        event.setName(request.name());
        return EventResponse.from(eventRepository.save(event));
    }

    /** The Node endpoint used a Team model that no longer exists, see the migration notes. */
    public void getAllTeams() {
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED,
                "Teams are not available: the Team model was removed from the Node backend");
    }
}
