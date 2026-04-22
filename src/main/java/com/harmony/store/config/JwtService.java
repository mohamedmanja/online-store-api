package com.harmony.store.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiry-days}")
    private long expiryDays;

    @Value("${app.jwt.pending-expiry-minutes}")
    private long pendingExpiryMinutes;

    private SecretKey key() {
        byte[] bytes = secret.getBytes();
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(String userId, String email, String role) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryDays * 86_400_000L))
                .signWith(key())
                .compact();
    }

    public String generatePendingToken(String userId, String email, String role) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("role", role)
                .claim("type", "two_factor_pending")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + pendingExpiryMinutes * 60_000L))
                .signWith(key())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractEmail(String token) {
        return extractClaim(token, c -> c.get("email", String.class));
    }

    public String extractRole(String token) {
        return extractClaim(token, c -> c.get("role", String.class));
    }

    public String extractType(String token) {
        return extractClaim(token, c -> c.get("type", String.class));
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public boolean isPendingToken(String token) {
        try {
            return "two_factor_pending".equals(extractType(token));
        } catch (Exception e) {
            return false;
        }
    }
}
