package com.Laibin.SugarInventory.SpringSecurity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtUtils {
    public static final String TOKEN_TYPE_AGENT_DELEGATION = "AGENT_DELEGATION";
    public static final String AUDIENCE_WAREHOUSE_MCP = "warehouse-mcp";

    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration}")
    private Long expiration;

    public String generateToken(UserDetails user) {
        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("authorities", user.getAuthorities())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    public String generateAgentDelegationToken(UserDetails user, String agentSessionId, Collection<String> scopes, Duration ttl) {
        List<String> authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        long ttlMillis = ttl == null || ttl.isZero() || ttl.isNegative() ? 15 * 60 * 1000L : ttl.toMillis();
        return Jwts.builder()
                .setSubject(user.getUsername())
                .setAudience(AUDIENCE_WAREHOUSE_MCP)
                .claim("tokenType", TOKEN_TYPE_AGENT_DELEGATION)
                .claim("agentSessionId", agentSessionId)
                .claim("scopes", scopes == null ? List.of() : scopes)
                .claim("authorities", authorities)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    public String parseToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    public Integer getUserIdFromToken(String token) {
        return getUserIdFromClaims(parseClaims(token));
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }

    public Integer getUserIdFromClaims(Claims claims) {
        if (claims == null || claims.getSubject() == null) {
            return null;
        }
        return Integer.parseInt(claims.getSubject());
    }

    public String getTokenType(Claims claims) {
        return claims == null ? null : claims.get("tokenType", String.class);
    }

    public String getAgentSessionId(Claims claims) {
        return claims == null ? null : claims.get("agentSessionId", String.class);
    }

    public boolean isAgentDelegationToken(Claims claims) {
        return TOKEN_TYPE_AGENT_DELEGATION.equals(getTokenType(claims));
    }

    public boolean hasWarehouseMcpAudience(Claims claims) {
        return AUDIENCE_WAREHOUSE_MCP.equals(claims == null ? null : claims.getAudience());
    }

    @SuppressWarnings("unchecked")
    public List<String> getScopes(Claims claims) {
        if (claims == null) {
            return List.of();
        }
        Object value = claims.get("scopes");
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toList());
        }
        return List.of();
    }
}
