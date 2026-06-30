package com.Laibin.SugarInventory.SpringSecurity;

import com.Laibin.SugarInventory.agent.security.AgentApiAuditFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;
    private final AgentApiAuditFilter agentApiAuditFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, AgentApiAuditFilter agentApiAuditFilter) {
        this.jwtFilter = jwtFilter;
        this.agentApiAuditFilter = agentApiAuditFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                                .requestMatchers("/api/auth/**",
                                        "/internal/agent/tools/**",
                                        "/v3/api-docs/**",
                                        "/doc.html",
                                        "/swagger-ui/**",
                                        "/webjars/**",
                                        "/swagger-ui.html").permitAll()
                                .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.getWriter().write("认证失败");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.getWriter().write("用户认证失败");
                        })
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(agentApiAuditFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
