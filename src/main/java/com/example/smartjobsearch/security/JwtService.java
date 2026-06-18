package com.example.smartjobsearch.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenTtlMs;
    private final String issuer;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.access-token-ttl-ms:3600000}") long accessTokenTtlMs,
            @Value("${security.jwt.issuer:smartjobsearch}") String issuer
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenTtlMs = accessTokenTtlMs;
        this.issuer = issuer;
    }

    public String generateAccessToken(Long userId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenTtlMs);

        return Jwts.builder()
                .setIssuer(issuer)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .setSubject(String.valueOf(userId))
                .addClaims(Map.of(
                        "username", username
                ))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }


    public Claims parseAndValidate(String token) {
        // Compatibility with older jjwt API: Jwts.parser() + setSigningKey + parseClaimsJws
        return Jwts.parser()
                .setSigningKey(signingKey)
                .parseClaimsJws(token)
                .getBody();
    }
}

