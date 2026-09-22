package com.example.baget.jwt;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.Serial;
import java.io.Serializable;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtTokenUtil implements Serializable {

    @Serial
    private static final long serialVersionUID = -2550185165626007488L;
    public static final long JWT_TOKEN_VALIDITY = 20 * 60 * 60;

    @Value("${jwt.secret}")
    private String secret;
//    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
//    Key key = Keys.hmacShaKeyFor(keyBytes);
    // отримання юзернейму з токену

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public Claims getAllClaimsFromToken(String token) {
        Key signingKey = getSigningKey();
//        return Jwts.parser().verifyWith((SecretKey) signingKey).build().parseSignedClaims(token).getPayload();
        return Jwts.parser()
                .clockSkewSeconds(60) // дозволити 60 секунд різниці
                .verifyWith((SecretKey) signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    // отримання дати закінчення терміну дії з токену
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }


    // перевірка чи не закінчився термін дії токену
    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    // генерація токену для користувача
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return doGenerateToken(claims, userDetails.getUsername());
    }

    // під час створення токену -
    // 1. Визначаємо claims (емітент, термін дії, підпис і т.д.)
    // 2. Підписуємо JWT з використанням HS512 алгоритму і секретного ключа
    // 3. Compact JWT до URL-safe рядка
    private String doGenerateToken(Map<String, Object> claims, String subject) {
        Key signingKey = getSigningKey();
        return Jwts.builder().claims(claims).subject(subject).issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + JWT_TOKEN_VALIDITY * 1000))
                .signWith((SecretKey) signingKey, Jwts.SIG.HS512).compact();
    }

    // перевірка токену
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}