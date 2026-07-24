package com.consumoesperto.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Permite acesso a {@code /api/admin/**} via {@code X-Admin-Api-Key} (ops/VPS)
 * ou JWT já autenticado pelo filtro anterior.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
@Slf4j
public class AdminApiKeyFilter extends OncePerRequestFilter {

    @Value("${consumoesperto.admin.api-key:}")
    private String adminApiKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
            && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            filterChain.doFilter(request, response);
            return;
        }

        if (adminApiKey == null || adminApiKey.isBlank()) {
            log.debug("[AdminAuth] admin.api-key não configurada — exige JWT para {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String received = request.getHeader("X-Admin-Api-Key");
        if (received != null && adminApiKey.trim().equals(received.trim())) {
            UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                "admin-api-key",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
            SecurityContextHolder.getContext().setAuthentication(token);
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"unauthorized\",\"reason\":\"admin-api-key-required\"}");
    }
}
