package com.taskapi.taskmanager.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskapi.taskmanager.security.JwtFilter;
import com.taskapi.taskmanager.security.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Security configuration for the stateless JWT-based API.
 *
 * <ul>
 *   <li>CSRF disabled — stateless API, no browser sessions</li>
 *   <li>Session policy: STATELESS — no HTTP session created or used</li>
 *   <li>{@code /api/auth/**} is publicly accessible; all other paths require authentication</li>
 *   <li>{@link JwtFilter} runs before {@link UsernamePasswordAuthenticationFilter}</li>
 *   <li>401 and 403 responses use the same JSON error envelope:
 *       {@code { timestamp, status, error, message }}</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtFilter jwtFilter,
                          UserDetailsServiceImpl userDetailsService,
                          ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    // ── Filter chain ──────────────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless API — no CSRF protection needed
            .csrf(AbstractHttpConfigurer::disable)

            // No HTTP sessions
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated())

            // 401 for unauthenticated access
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler()))

            // Register JwtFilter before the standard username/password filter
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ── Exception handlers — shared JSON error envelope ───────────────────────

    /**
     * Returns HTTP 401 with {@code { timestamp, status, error, message }} when a request
     * arrives without valid authentication credentials.
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                objectMapper.writeValueAsString(
                    errorBody(HttpStatus.UNAUTHORIZED, "Authentication required")));
        };
    }

    /**
     * Returns HTTP 403 with {@code { timestamp, status, error, message }} when an
     * authenticated user lacks the required authority.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                objectMapper.writeValueAsString(
                    errorBody(HttpStatus.FORBIDDEN, "Access denied")));
        };
    }

    // ── Authentication beans ──────────────────────────────────────────────────

    /**
     * Wires the {@link UserDetailsServiceImpl} and {@link PasswordEncoder} into a
     * {@link DaoAuthenticationProvider} and exposes it as the primary provider.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the {@link AuthenticationManager} as a Spring bean so that
     * {@code AuthController} (and other services) can inject and use it directly.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * BCrypt password encoder bean used for hashing and verifying user passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, Object> errorBody(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
