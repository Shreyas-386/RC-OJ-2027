package com.oj.rc.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Plain unit tests, no Spring needed. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123";
    private final JwtService jwtService = new JwtService(SECRET);

    @Test
    void aTokenWeCreateCanBeReadBack() {
        String token = jwtService.createToken(
                Map.of("id", 5, "username", "alice", "role", "USER", "team_id", "team-1"), Duration.ofHours(2));

        JwtUser user = jwtService.parseToken(token);

        assertThat(user.id()).isEqualTo(5);
        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.role()).isEqualTo("USER");
        assertThat(user.teamId()).isEqualTo("team-1");
    }

    @Test
    void theTokenExpiresAfterTheRequestedTime() throws Exception {
        String token = jwtService.createToken(Map.of("id", 1), Duration.ofDays(1));

        var claims = SignedJWT.parse(token).getJWTClaimsSet();
        long seconds = (claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()) / 1000;
        assertThat(seconds).isEqualTo(Duration.ofDays(1).toSeconds());
    }

    @Test
    void aTokenWithoutOptionalClaimsGivesNullsInsteadOfFailing() {
        JwtUser user = jwtService.parseToken(jwtService.createToken(Map.of("id", 1), Duration.ofHours(1)));

        assertThat(user.role()).isNull();
        assertThat(user.teamId()).isNull();
    }

    @Test
    void anExpiredTokenIsReportedAsExpired() {
        String token = jwtService.createToken(Map.of("id", 1), Duration.ofSeconds(-10));

        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOfSatisfying(TokenException.class, e -> assertThat(e.isExpired()).isTrue());
    }

    @Test
    void aTokenSignedWithAnotherSecretIsInvalid() {
        String token = new JwtService("a-different-secret-a-different-secret-1").createToken(Map.of("id", 1), Duration.ofHours(1));

        assertInvalid(token);
    }

    @Test
    void aTokenWithChangedContentIsInvalid() {
        String token = jwtService.createToken(Map.of("id", 1, "role", "USER"), Duration.ofHours(1));
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"id\":1,\"role\":\"admin\"}".getBytes(StandardCharsets.UTF_8));

        assertInvalid(parts[0] + "." + forgedPayload + "." + parts[2]);
    }

    @Test
    void anUnsignedTokenWithAlgNoneIsInvalid() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString("{\"id\":1,\"role\":\"admin\"}".getBytes(StandardCharsets.UTF_8));

        assertInvalid(header + "." + payload + ".");
    }

    @Test
    void textThatIsNotAJwtIsInvalid() {
        assertInvalid("hello");
        assertInvalid("a.b.c");
        assertInvalid("");
    }

    @Test
    void aSecretThatIsTooShortCannotSignTokens() {
        JwtService weak = new JwtService("short");

        assertThatThrownBy(() -> weak.createToken(Map.of("id", 1), Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOfSatisfying(TokenException.class, e -> assertThat(e.isExpired()).isFalse());
    }
}
