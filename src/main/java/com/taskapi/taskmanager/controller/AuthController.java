package com.taskapi.taskmanager.controller;

import com.taskapi.taskmanager.dto.AuthResponse;
import com.taskapi.taskmanager.dto.LoginRequest;
import com.taskapi.taskmanager.dto.RegisterRequest;
import com.taskapi.taskmanager.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 *
 * <ul>
 *   <li>{@code POST /api/auth/register} — create a new user account and return a JWT (HTTP 201)
 *   <li>{@code POST /api/auth/login}    — authenticate credentials and return a JWT (HTTP 200)
 * </ul>
 *
 * {@link BadCredentialsException} thrown during login is caught here and translated to HTTP 401
 * so the {@link com.taskapi.taskmanager.exception.GlobalExceptionHandler} fallback (HTTP 500)
 * is bypassed for this expected error case.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Register a new user.
     *
     * @param request validated registration payload
     * @return HTTP 201 with {@link AuthResponse} containing a JWT
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        String token = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token));
    }

    /**
     * Authenticate an existing user.
     *
     * @param request validated login payload
     * @return HTTP 200 with {@link AuthResponse} containing a JWT
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        String token = userService.login(request);
        return ResponseEntity.ok(new AuthResponse(token));
    }

    /**
     * Translates {@link BadCredentialsException} to HTTP 401 for this controller.
     * This keeps authentication failures out of the generic 500 fallback handler.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<AuthResponse> handleBadCredentials() {
        // AuthResponse token field is null — callers should inspect the status code
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
