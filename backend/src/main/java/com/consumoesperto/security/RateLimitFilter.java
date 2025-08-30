package com.consumoesperto.security;

import com.consumoesperto.config.RateLimitConfig;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Filtro de Rate Limiting para Proteção contra Ataques
 * 
 * Este filtro implementa rate limiting usando bucket4j para proteger
 * a aplicação contra ataques de força bruta, DDoS e abuso de API.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitConfig rateLimitConfig;
    private final SecurityAuditService auditService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String method = request.getMethod();
        String ipAddress = auditService.extractIpAddress(request);
        
        log.debug("RateLimitFilter - Processando requisição: {} {} de IP: {}", method, path, ipAddress);
        
        // Seleciona o bucket apropriado baseado no endpoint
        Bucket bucket = selectBucket(path, method);
        
        log.debug("RateLimitFilter - Bucket selecionado com {} tokens disponíveis", bucket.getAvailableTokens());
        log.debug("RateLimitFilter - Tentando consumir 1 token para: {} {}", method, path);
        
        // Verifica se a requisição pode ser processada
        if (bucket.tryConsume(1)) {
            log.debug("RateLimitFilter - Requisição permitida para: {} {}", method, path);
            log.debug("RateLimitFilter - Tokens disponíveis após consumo: {}", bucket.getAvailableTokens());
            // Log de acesso para auditoria
            if (path.startsWith("/api/")) {
                auditService.logResourceAccess("ANONYMOUS", path, method, ipAddress);
            }
            
            filterChain.doFilter(request, response);
        } else {
            // Rate limit excedido
            log.warn("🚫 Rate limit excedido para IP: {} | Path: {} | Método: {}", ipAddress, path, method);
            log.warn("RateLimitFilter - Tokens disponíveis: {}", bucket.getAvailableTokens());
            
            // Log de auditoria
            auditService.logUnauthorizedAccess("ANONYMOUS", path, method, ipAddress, "Rate limit excedido");
            
            // Resposta de erro
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Please try again later.\",\"retryAfter\":60}");
            
            // Adiciona headers de retry
            response.setHeader("Retry-After", "60");
            response.setHeader("X-RateLimit-Limit", String.valueOf(bucket.getAvailableTokens()));
            response.setHeader("X-RateLimit-Remaining", "0");
        }
    }

    /**
     * Seleciona o bucket apropriado baseado no endpoint e método
     */
    private Bucket selectBucket(String path, String method) {
        log.debug("RateLimitFilter - Selecionando bucket para: {} {}", method, path);
        
        // Endpoints de autenticação - mais restritivos
        if (path.startsWith("/api/auth/") || path.contains("/login") || path.contains("/register")) {
            log.debug("RateLimitFilter - Usando bucket de autenticação para: {}", path);
            return rateLimitConfig.authBucket();
        }
        
        // APIs bancárias - proteção especial
        if (path.startsWith("/api/bank") || path.startsWith("/api/financial")) {
            log.debug("RateLimitFilter - Usando bucket de API bancária para: {}", path);
            return rateLimitConfig.bankApiBucket();
        }
        
        // Endpoints administrativos - mais restritivos
        if (path.startsWith("/api/admin") || path.startsWith("/actuator")) {
            log.debug("RateLimitFilter - Usando bucket de autenticação para: {}", path);
            return rateLimitConfig.authBucket();
        }
        
        // Endpoints gerais
        log.debug("RateLimitFilter - Usando bucket geral para: {}", path);
        return rateLimitConfig.generalBucket();
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        
        log.debug("RateLimitFilter - Verificando se deve filtrar: {}", path);
        
        // Não aplica rate limiting para:
        // - Arquivos estáticos
        if (path.matches(".*\\.(css|js|png|jpg|jpeg|gif|svg|ico|woff|woff2|ttf|eot)$")) {
            log.debug("RateLimitFilter - Arquivo estático, não aplicando filtro: {}", path);
            return true;
        }
        
        // - Endpoints de saúde
        if (path.startsWith("/actuator/health") || path.startsWith("/actuator/info")) {
            log.debug("RateLimitFilter - Endpoint de saúde, não aplicando filtro: {}", path);
            return true;
        }
        
        // - Endpoints de documentação
        if (path.startsWith("/swagger-ui") || path.startsWith("/v2/api-docs") || path.startsWith("/swagger-resources")) {
            log.debug("RateLimitFilter - Endpoint de documentação, não aplicando filtro: {}", path);
            return true;
        }
        
        // - Rotas de SPA (não são endpoints reais do backend)
        if (path.equals("/login") || path.equals("/register") || path.equals("/dashboard") || 
            path.equals("/transacoes") || path.equals("/cartoes") || path.equals("/faturas") || 
            path.equals("/relatorios") || path.equals("/simulacoes") || path.equals("/bank-config")) {
            log.debug("RateLimitFilter - Rota de SPA, não aplicando filtro: {}", path);
            return true;
        }
        
        // - Endpoint de erro
        if (path.equals("/error")) {
            log.debug("RateLimitFilter - Endpoint de erro, não aplicando filtro: {}", path);
            return true;
        }
        
        log.debug("RateLimitFilter - Aplicando filtro para: {}", path);
        return false;
    }
}
