package com.consumoesperto.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);
            
            log.debug("JWT Filter - Request URI: {}, JWT present: {}", request.getRequestURI(), StringUtils.hasText(jwt));

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                Long userId = tokenProvider.getUserIdFromJWT(jwt);
                log.debug("JWT Filter - Valid token for user ID: {}", userId);

                UserDetails userDetails = customUserDetailsService.loadUserById(userId);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("JWT Filter - Authentication set for user: {}", userDetails.getUsername());
            } else {
                log.debug("JWT Filter - No valid JWT found for request: {}", request.getRequestURI());
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        
<<<<<<< HEAD
        // Não aplica filtro JWT para endpoints públicos de autenticação
        if (path.startsWith("/api/auth/") || 
            path.startsWith("/api/oauth2/") ||
            path.startsWith("/api/public/") ||
            path.startsWith("/api/mercadopago/oauth/") ||
            path.startsWith("/swagger-ui/") ||
            path.startsWith("/v3/api-docs/") ||
            path.startsWith("/actuator/") ||
            path.equals("/login") || 
            path.equals("/register") || 
            path.equals("/dashboard") || 
            path.equals("/transacoes") || 
            path.equals("/cartoes") || 
            path.equals("/faturas") || 
            path.equals("/relatorios") || 
            path.equals("/simulacoes") || 
            path.equals("/bank-config") ||
=======
        // Não aplica filtro JWT para rotas de SPA e arquivos estáticos
        if (path.equals("/login") || path.equals("/register") || path.equals("/dashboard") || 
            path.equals("/transacoes") || path.equals("/cartoes") || path.equals("/faturas") || 
            path.equals("/relatorios") || path.equals("/simulacoes") || path.equals("/bank-config") ||
>>>>>>> origin/main
            path.equals("/error") ||
            path.matches(".*\\.(css|js|png|jpg|jpeg|gif|svg|ico|woff|woff2|ttf|eot)$")) {
            return true;
        }
        
        return false;
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
