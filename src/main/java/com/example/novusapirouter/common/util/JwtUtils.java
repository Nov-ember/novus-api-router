package com.example.novusapirouter.common.util;

import com.example.novusapirouter.common.property.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final JwtProperties jwtProperties;

    public String createToken(Long uid) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + jwtProperties.getExpireHours() *
                3600 * 1000);
        return Jwts.builder()
                .subject(String.valueOf(uid))
                .issuedAt(now)
                .expiration(expire)
                .signWith(Keys.hmacShaKeyFor(secretBytes()))
                .compact();
    }

    public Long parseUid(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secretBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    private byte[] secretBytes() {
        return jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
    }
}
