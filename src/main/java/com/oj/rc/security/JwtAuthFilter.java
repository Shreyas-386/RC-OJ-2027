package com.oj.rc.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads "Authorization: Bearer <token>" on every request.
 * - Valid token: the request is marked as logged in, and the "role" claim becomes the authority.
 * - Bad token: the request stays anonymous and the reason is stored in a request attribute.
 *   SecurityConfig then answers with the right error for that URL.
 * Public endpoints ignore this, so an old token in the header never blocks login or register.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    static final String TOKEN_ERROR_ATTRIBUTE = "tokenError";

    // Same rule as the Node middleware: "Bearer" + space + token
    private static final Pattern BEARER = Pattern.compile("^Bearer\\s+(\\S+)$", Pattern.CASE_INSENSITIVE);

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        Matcher matcher = header == null ? null : BEARER.matcher(header);
        if (matcher != null && matcher.matches()) {
            authenticate(request, matcher.group(1));
        }
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            JwtUser user = jwtService.parseToken(token);
            List<GrantedAuthority> authorities = user.role() == null
                    ? List.of()
                    : List.of(new SimpleGrantedAuthority(user.role()));
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
        } catch (TokenException e) {
            request.setAttribute(TOKEN_ERROR_ATTRIBUTE, e);
        }
    }
}
