package com.taskapi.taskmanager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Utility component for JWT token generation, validation, and claim extraction.
 * Uses HMAC-SHA256 (HS256) algorithm.
 */
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration:3600}") long expirationSeconds) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    /**
     * Generates a JWT token for the given username.
     * Sets subject=username, iat=now, exp=now+expiration.
     *
     * @param username the subject to embed in the token
     * @return signed JWT string
     */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000L);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates a JWT token: checks signature and expiry.
     *
     * @param token the JWT string to validate
     * @return true if the token has a valid signature and is not expired; false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Extracts the subject (username) claim from the token.
     * Caller should validate the token before calling this method.
     *
     * @param token the JWT string
     * @return the subject claim (username)
     */
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Extracts the expiration date claim from the token.
     * Caller should validate the token before calling this method.
     *
     * @param token the JWT string
     * @return the expiration {@link Date}
     */
    public Date extractExpiry(String token) {
        return extractClaims(token).getExpiration();
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
