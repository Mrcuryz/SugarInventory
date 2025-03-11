package com.Laibin.SugarInventory.SpringSecurity;

import com.Laibin.SugarInventory.common.BusinessException;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        response.setCharacterEncoding("UTF-8");
        String token = jwtUtils.parseToken(request);
        if (token != null) {
            try {
                Integer userId = jwtUtils.getUserIdFromToken(token);

                System.out.println("JWT Token :" + token);
                System.out.println("User ID :" + userId);
                // 检查用户是否存在
                UserDetails userDetails = null;
                if (userId != null) {
                    try {
                        userDetails = userDetailsService.loadUserByUsername(userId.toString());
                        System.out.println("User Details :" + userDetails.toString());
                    } catch (BusinessException e) {
                        // 捕获用户不存在异常，返回 401 Unauthorized
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("用户不存在");
                        return;
                    }
                }

                if (userDetails == null) {
                    // 如果用户未找到，返回 401 Unauthorized
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("用户不存在");
                    return;
                }

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        )
                );
                SecurityContextHolder.setContext(context);
            } catch (ExpiredJwtException e) {
                // 处理 JWT 过期异常
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("JWT Token已过期");
                return;
            } catch (Exception e) {
                // 处理其他异常
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("认证失败");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}