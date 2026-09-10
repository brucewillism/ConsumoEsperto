package com.consumoesperto.ingest.notificacao.security;

import com.consumoesperto.config.IngestNotificacaoProperties;
import com.consumoesperto.model.IngestToken;
import com.consumoesperto.repository.IngestTokenRepository;
import com.consumoesperto.security.ForwardedHttps;
import com.consumoesperto.util.AppTimeZone;
import com.consumoesperto.util.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class IngestTokenFilter extends OncePerRequestFilter {

    public static final String TOKEN_HEADER = "X-Ingest-Token";

    private final IngestNotificacaoProperties properties;
    private final IngestTokenRepository tokenRepository;
    private final IngestNotificacaoRateLimiter rateLimiter;
    private final ForwardedHttps forwardedHttps;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/ingest/notificacao");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (properties.isRequireHttps() && !ForwardedHttps.isHttps(request)) {
            forwardedHttps.rejectRequired(request, response);
            return;
        }
        int max = properties.getMaxPayloadBytes();
        String lengthHeader = request.getHeader("Content-Length");
        if (lengthHeader != null) {
            try {
                if (Long.parseLong(lengthHeader) > max) {
                    response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                    return;
                }
            } catch (NumberFormatException ignored) {
                // segue; o controller trunca o texto
            }
        }
        String rawToken = request.getHeader(TOKEN_HEADER);
        if (rawToken == null || rawToken.isBlank()) {
            log.warn("ingest_token_ausente path={}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String hash = IngestTokenHasher.hashToken(rawToken);
        Optional<IngestToken> token = tokenRepository.findByTokenHashAndRevogadoFalse(hash);
        if (token.isEmpty() || token.get().getUsuario() == null) {
            log.warn("ingest_token_invalido path={}", LogSanitizer.sanitize(request.getRequestURI()));
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        IngestToken entity = token.get();
        try {
            rateLimiter.checkOrThrow(entity.getId());
        } catch (IngestNotificacaoException ex) {
            response.sendError(429, "Limite de requisições excedido");
            return;
        }
        entity.setUltimoUsoEm(AppTimeZone.agora());
        tokenRepository.save(entity);
        SecurityContextHolder.getContext().setAuthentication(new IngestTokenAuthentication(entity));
        filterChain.doFilter(request, response);
    }
}
