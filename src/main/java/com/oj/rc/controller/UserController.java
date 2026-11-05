package com.oj.rc.controller;

import com.oj.rc.dto.LoginResponse;
import com.oj.rc.dto.SubmissionResponse;
import com.oj.rc.dto.UserRequest;
import com.oj.rc.security.JwtUser;
import com.oj.rc.service.UserService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Replaces userRoutes.js. Which endpoints need a token is configured in SecurityConfig. */
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@RequestBody UserRequest request) {
        return Map.of(
                "message", "User registered successfully!",
                "user", userService.register(request));
    }

    @PostMapping("/updatepassword")
    public Map<String, Object> updatePassword(@RequestBody UserRequest request) {
        userService.updatePassword(request);
        return Map.of("message", "Password updated successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody UserRequest request) {
        LoginResponse response = userService.login(request);
        // Same as Node: a login after the event has ended still returns a token, but with status 501
        HttpStatus status = response.eventHasEnded() ? HttpStatus.NOT_IMPLEMENTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        // Tokens are stateless, so there is nothing to invalidate on the server
        return Map.of("message", "User logged out successfully");
    }

    @GetMapping("/profile")
    public Map<String, Object> getProfile(@AuthenticationPrincipal JwtUser currentUser) {
        // singletonMap because the value can be null
        return Collections.singletonMap("user", userService.getProfile(currentUser.id()));
    }

    @GetMapping("/gethistory")
    public List<SubmissionResponse> getHistory(@AuthenticationPrincipal JwtUser currentUser) {
        return userService.getHistory(currentUser.teamId());
    }
}
