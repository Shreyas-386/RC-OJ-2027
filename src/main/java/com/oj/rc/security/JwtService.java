package com.oj.rc.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Creates and checks the JWT tokens (HS256, signed with JWT_SECRET).
 * The tokens have the same format as the ones made by the Node "jsonwebtoken" library,
 * so tokens from either backend are accepted by both.
 */
@Service
public class JwtService {

    private final byte[] secret;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String createToken(Map<String, Object> claims, Duration validFor) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(validFor)));
        claims.forEach(builder::claim);

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), builder.build());
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException e) {
            // Most likely JWT_SECRET is shorter than 32 characters
            throw new IllegalStateException("Could not sign the token, check JWT_SECRET", e);
        }
        return jwt.serialize();
    }

    /** Returns the user stored in the token, or throws TokenException if it is expired or invalid. */
    public JwtUser parseToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            boolean signatureOk = JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    && jwt.verify(new MACVerifier(secret));
            if (!signatureOk) {
                throw new TokenException(false);
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiry = claims.getExpirationTime();
            if (expiry != null && expiry.before(new Date())) {
                throw new TokenException(true);
            }

            Long id = claims.getLongClaim("id");
            return new JwtUser(
                    id == null ? null : id.intValue(),
                    claims.getStringClaim("username"),
                    claims.getStringClaim("role"),
                    claims.getStringClaim("team_id"));
        } catch (ParseException | JOSEException e) {
            throw new TokenException(false);
        }
    }
}
