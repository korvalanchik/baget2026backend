package com.example.baget.qrcoding;

import org.springframework.stereotype.Service;
@Service
public class QrTokenService {
//
//    private final Algorithm algorithm;
//
//    public QrTokenService(@Value("${QR_TOKEN}") String secret) {
//        this.algorithm = Algorithm.HMAC256(secret);
//    }
//
//    public String createToken(Long orderId, Long branchId, long secondsValid) {
//        Instant now = Instant.now();
//        return JWT.create()
//                .withClaim("orderId", orderId)
//                .withClaim("branchId", branchId)
//                .withIssuedAt(Date.from(now))
//                .withExpiresAt(Date.from(now.plusSeconds(secondsValid)))
//                .withJWTId(java.util.UUID.randomUUID().toString())
//                .sign(algorithm);
//    }
}
