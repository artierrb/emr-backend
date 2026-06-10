package com.emr.demo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret:emr-secret-key-must-be-at-least-32-chars-long}")
    private String secret;

    private Key getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generate(String userId, String auth, String name) {
        return Jwts.builder()
                .setSubject(userId)
                .claim("auth", auth)
                .claim("name", name)
                .setIssuedAt(new Date())
                // session-only: 12 hours แต่ frontend ล้างเมื่อปิด browser
                .setExpiration(new Date(System.currentTimeMillis() + 12 * 60 * 60 * 1000L))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String generateShortLived(String userId, String auth, String name, int minutes) {
        return Jwts.builder()
                .setSubject(userId)
                .claim("auth", auth)
                .claim("name", name)
                .claim("scan", true)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + (long)minutes * 60 * 1000L))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isValid(String token) {
        try { parse(token); return true; }
        catch (Exception e) { return false; }
    }
}
