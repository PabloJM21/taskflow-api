package com.taskapi.taskmanager.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter that runs once per request.
 *
 * <p>Reads the {@code Authorization: Bearer <token>} header, validates the JWT, and — if
 * valid — populates the {@link SecurityContextHolder} with the authenticated user's details.
 * On any failure (missing header, invalid/expired token, unknown user) the security context
 * is cleared and the request continues unauthenticated; Spring Security will return 401.</p>
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                String token = authHeader.substring(BEARER_PREFIX.length());

                if (jwtUtil.validateToken(token)) {
                    String username = jwtUtil.extractUsername(token);

                    // Only set authentication if none is already present in the context
                    if (username != null
                            && SecurityContextHolder.getContext().getAuthentication() == null) {

                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities());

                        authToken.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                } else {
                    // Token present but invalid/expired — clear context to force 401
                    SecurityContextHolder.clearContext();
                }
            }
            // No Authorization header: leave context untouched; Spring Security handles 401
        } catch (Exception e) {
            // Any unexpected failure (e.g. UsernameNotFoundException) — clear context, do NOT rethrow
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
