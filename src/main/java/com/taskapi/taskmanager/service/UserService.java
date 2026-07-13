package com.taskapi.taskmanager.service;

import com.taskapi.taskmanager.dto.LoginRequest;
import com.taskapi.taskmanager.dto.RegisterRequest;
import com.taskapi.taskmanager.entity.Role;
import com.taskapi.taskmanager.entity.User;
import com.taskapi.taskmanager.exception.DuplicateUserException;
import com.taskapi.taskmanager.repository.RoleRepository;
import com.taskapi.taskmanager.repository.UserRepository;
import com.taskapi.taskmanager.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service handling user registration and login.
 *
 * <p>Registration validates username/email uniqueness, BCrypt-hashes the password,
 * assigns the {@code ROLE_USER} role (creating it in the DB if missing), persists the
 * new {@link User}, and returns a freshly generated JWT.
 *
 * <p>Login delegates to Spring Security's {@link AuthenticationManager}. On success a
 * JWT is returned; on {@link BadCredentialsException} the exception propagates to the
 * caller (→ HTTP 401).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       JwtUtil jwtUtil,
                       BCryptPasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    /**
     * Registers a new user.
     *
     * @param request contains username, email, and plain-text password
     * @return a signed JWT for the newly created user
     * @throws DuplicateUserException if the username or email is already taken
     */
    public String register(RegisterRequest request) {
        // Validate uniqueness
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new DuplicateUserException(
                    "Username already exists: " + request.username());
        }
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateUserException(
                    "Email already registered: " + request.email());
        }

        // Hash password
        String hashedPassword = passwordEncoder.encode(request.password());

        // Assign ROLE_USER — create in DB if missing
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));

        // Build and persist user
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(hashedPassword);
        user.getRoles().add(userRole);

        userRepository.save(user);

        return jwtUtil.generateToken(request.username());
    }

    /**
     * Authenticates a user and returns a JWT on success.
     *
     * @param request contains username and plain-text password
     * @return a signed JWT for the authenticated user
     * @throws BadCredentialsException if the credentials are incorrect (→ HTTP 401)
     */
    public String login(LoginRequest request) {
        // Delegates to Spring Security — throws BadCredentialsException on failure
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(), request.password()));

        String authenticatedUsername = authentication.getName();
        return jwtUtil.generateToken(authenticatedUsername);
    }
}
