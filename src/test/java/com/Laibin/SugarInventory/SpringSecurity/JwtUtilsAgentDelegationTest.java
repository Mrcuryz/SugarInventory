package com.Laibin.SugarInventory.SpringSecurity;

import com.Laibin.SugarInventory.domain.po.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsAgentDelegationTest {
    @Test
    void generatedDelegationTokenContainsAgentClaims() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", "xVqyO+0aawRF/LNpEjDDe1w/VXjnbgjJsm43uq0dbpTTt5EHbldZ3FLohntYJfRlXAhW2ZNww27q04Ja2C9kNw==");
        ReflectionTestUtils.setField(jwtUtils, "expiration", 86400000L);
        User user = new User();
        user.setId(7);
        user.setRoleCode("ADMIN");
        LoginUser loginUser = new LoginUser(user, List.of(new SimpleGrantedAuthority("record:query")));

        String token = jwtUtils.generateAgentDelegationToken(loginUser, "session-1", List.of("mcp:warehouse:read"), Duration.ofMinutes(15));
        Claims claims = jwtUtils.parseClaims(token);

        assertThat(jwtUtils.isAgentDelegationToken(claims)).isTrue();
        assertThat(jwtUtils.hasWarehouseMcpAudience(claims)).isTrue();
        assertThat(jwtUtils.getAgentSessionId(claims)).isEqualTo("session-1");
        assertThat(jwtUtils.getUserIdFromClaims(claims)).isEqualTo(7);
        assertThat(jwtUtils.getScopes(claims)).containsExactly("mcp:warehouse:read");
    }
}
