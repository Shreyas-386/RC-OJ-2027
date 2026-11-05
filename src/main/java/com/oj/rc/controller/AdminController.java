package com.oj.rc.controller;

import com.oj.rc.dto.EventRequest;
import com.oj.rc.dto.LoginResponse;
import com.oj.rc.dto.UserRequest;
import com.oj.rc.dto.UserResponse;
import com.oj.rc.service.AdminService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Replaces adminRoutes.js. Which endpoints need an admin token is configured in SecurityConfig. */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@RequestBody UserRequest request) {
        return Map.of(
                "message", "Admin registered successfully",
                "admin", adminService.registerAdmin(request));
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody UserRequest request) {
        return adminService.loginAdmin(request);
    }

    @GetMapping("/users")
    public List<UserResponse> getAllUsers() {
        return adminService.getAllUsers();
    }

    @GetMapping("/problems")
    public Map<String, Object> getAllProblems() {
        return Map.of("problems", adminService.getAllProblems());
    }

    @GetMapping("/teams")
    public void getAllTeams() {
        adminService.getAllTeams(); // always answers 501, see AdminService
    }

    @PostMapping("/create-event")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createEvent(@RequestBody EventRequest request) {
        return Map.of(
                "message", "Event created",
                "event", adminService.createEvent(request));
    }
}
