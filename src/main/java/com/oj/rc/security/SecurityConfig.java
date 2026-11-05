package com.oj.rc.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security rules, copied from the two Express middlewares:
 *
 *  - Public: register/login for admins and users, and /user/updatepassword (as in the Node app).
 *  - /admin/**  needs a valid token whose role is "admin"          (adminMiddleware.js)
 *  - everything else needs any valid token                          (authMiddleware.js)
 *
 * The error codes and messages are the same as in Node, see the two handlers below.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // stateless API, no cookies or sessions
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/admin/register", "/admin/login",
                                "/user/register", "/user/login", "/user/updatepassword").permitAll()
                        .requestMatchers("/admin/**").hasAuthority("admin")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(this::writeNotAuthenticated)
                        .accessDeniedHandler(this::writeNotAnAdmin))
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** BCrypt with 10 rounds, so the existing password hashes from the Node app still work. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        cors.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    /** No valid login: missing, expired or invalid token. The answer depends on the URL, as in Node. */
    private void writeNotAuthenticated(HttpServletRequest request, HttpServletResponse response,
                                       AuthenticationException exception)
            throws IOException {
        TokenException tokenError = (TokenException) request.getAttribute(JwtAuthFilter.TOKEN_ERROR_ATTRIBUTE);
        boolean adminUrl = request.getRequestURI().startsWith("/admin/");

        if (adminUrl && tokenError == null) {
            writeError(response, 403, "Access denied. No token provided.");
        } else if (adminUrl) {
            writeError(response, 400, "Invalid token");
        } else if (tokenError == null) {
            writeError(response, 403, "Access denied");
        } else if (tokenError.isExpired()) {
            writeError(response, 401, "Token expired. Please log in again.");
        } else {
            writeError(response, 403, "Invalid token. Please log in again.");
        }
    }

    /** Valid token, but the role is not "admin" (only possible on /admin/**). */
    private void writeNotAnAdmin(HttpServletRequest request, HttpServletResponse response,
                                 AccessDeniedException exception)
            throws IOException {
        writeError(response, 403, "Access only For Admins");
    }

    // The messages above are fixed texts without quotes, so plain string joining is safe here
    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
