package com.consumoesperto.security;

import com.consumoesperto.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        
        log.debug("🔒 Interceptando requisição: {} {}", method, requestURI);

        // Endpoints públicos que não precisam de autenticação
        if (isPublicEndpoint(requestURI)) {
            log.debug("✅ Endpoint público: {}", requestURI);
            return true;
        }

        // Verificar token JWT
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("❌ Token não fornecido para: {}", requestURI);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Token de autenticação necessário\"}");
            return false;
        }

        String token = authHeader.substring(7);
        if (!authService.validateToken(token)) {
            log.warn("❌ Token inválido para: {}", requestURI);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Token inválido\"}");
            return false;
        }

        log.debug("✅ Token válido para: {}", requestURI);
        return true;
    }

    private boolean isPublicEndpoint(String requestURI) {
        return requestURI.startsWith("/api/auth/") ||
               requestURI.startsWith("/api/public/") ||
               requestURI.startsWith("/actuator/") ||
               requestURI.startsWith("/swagger-ui/") ||
               requestURI.startsWith("/v3/api-docs/") ||
               requestURI.startsWith("/swagger-resources/") ||
               requestURI.startsWith("/webjars/") ||
               requestURI.equals("/error") ||
               requestURI.equals("/favicon.ico");
    }
}
