package com.growthprint.backend.security;

import com.growthprint.backend.auth.entity.User;
import com.growthprint.backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    /**
     * Secret that used to be hard-coded as a fallback in application.yml (and is therefore public).
     * Refuse to start with it so a forgotten JWT_SECRET can never leave tokens forgeable.
     */
    static final String LEAKED_DEFAULT_SECRET = "default-dev-secret-key-for-growthprint-backend-min-32-chars";

    /** Claim holding users.token_version. Tokens issued before V20 have no claim and count as version 0. */
    public static final String TOKEN_VERSION_CLAIM = "ver";

    /** Reads the token-version claim; missing (pre-V20 tokens) means 0, malformed means -1 (never matches). */
    public static int tokenVersionOf(Claims claims) {
        Object raw = claims.get(TOKEN_VERSION_CLAIM);
        if (raw == null) {
            return 0;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return -1;
    }

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret must be configured: set the JWT_SECRET environment variable (min 32 chars)");
        }
        if (LEAKED_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET is set to a publicly known default value; generate one with: openssl rand -base64 64");
        }
        // Keys.hmacShaKeyFor also enforces the 256-bit minimum required for HS256.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiration = now.plus(jwtProperties.getExpiration());

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                // Token version (V20): bumping users.token_version revokes this token on the next request.
                .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
                .id(UUID.randomUUID().toString())
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    public String getEmailFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("email", String.class);
    }

    public String getRoleFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("role", String.class);
    }

    public boolean validateToken(String token) {
        return parseValidClaims(token).isPresent();
    }

    /**
     * Verifies signature, expiry and issuer in one pass.
     *
     * @return the token's claims, or empty if the token is missing, malformed, expired or forged
     */
    public Optional<Claims> parseValidClaims(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parseClaims(token));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
