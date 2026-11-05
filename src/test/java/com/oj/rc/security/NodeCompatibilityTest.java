package com.oj.rc.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Checks that data made by the existing Node backend is accepted by the Spring backend.
 * The three values below were generated with the real Node libraries (jsonwebtoken 9 and bcryptjs 2):
 *
 *   jwt.sign({id:7, username:"root", isjunior:false, role:"admin"}, SECRET, {expiresIn:"36500d"})
 *   jwt.sign({id:3, username:"alice", event_id:1, isjunior:false, team_id:"team-uuid-1", teamname:"Alpha"}, SECRET, {expiresIn:"36500d"})
 *   bcrypt.hashSync("secret123", 10)
 */
class NodeCompatibilityTest {

    private static final String NODE_SECRET = "node-compat-test-secret-0123456789abcdef";

    private static final String NODE_ADMIN_TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6NywidXNlcm5hbWUiOiJyb290IiwiaXNqdW5pb3IiOmZhbHNlLCJyb2xlIjoiYWRtaW4iLCJpYXQiOjE3OTM3MDY3NDUsImV4cCI6NDk0NzMwNjc0NX0.b9zYbmE9iSSR3y08rlFjv2MupFrHkr8NDhCSaxvXLg0";

    private static final String NODE_USER_TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MywidXNlcm5hbWUiOiJhbGljZSIsImV2ZW50X2lkIjoxLCJpc2p1bmlvciI6ZmFsc2UsInRlYW1faWQiOiJ0ZWFtLXV1aWQtMSIsInRlYW1uYW1lIjoiQWxwaGEiLCJpYXQiOjE3OTM3MDY3NDUsImV4cCI6NDk0NzMwNjc0NX0.BEcMsf7fb0HUXGU9dS96_tWroqa_sF9fw_1yCJh3-As";

    private static final String NODE_BCRYPT_HASH = "$2a$10$qMprLHepDvbOw8n4obLvWewQDzIbg6rg2bqTGpI9DmoLY/tCLDkmy";

    private final JwtService jwtService = new JwtService(NODE_SECRET);

    @Test
    void anAdminTokenCreatedByNodeIsAccepted() {
        JwtUser user = jwtService.parseToken(NODE_ADMIN_TOKEN);

        assertThat(user.id()).isEqualTo(7);
        assertThat(user.username()).isEqualTo("root");
        assertThat(user.role()).isEqualTo("admin");
    }

    @Test
    void aUserTokenCreatedByNodeIsAccepted_andKeepsItsTeamId() {
        JwtUser user = jwtService.parseToken(NODE_USER_TOKEN);

        assertThat(user.id()).isEqualTo(3);
        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.teamId()).isEqualTo("team-uuid-1");
    }

    @Test
    void aBcryptHashCreatedByNodeCanBeChecked() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        assertThat(encoder.matches("secret123", NODE_BCRYPT_HASH)).isTrue();
        assertThat(encoder.matches("wrong-password", NODE_BCRYPT_HASH)).isFalse();
    }
}
