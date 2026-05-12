package com.consumoesperto.security;

import com.consumoesperto.exception.ApiError;
import com.consumoesperto.exception.JarvisErrorCopy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Resposta JSON alinhada ao {@link ApiError} quando o utilizador autenticado não tem permissão (403).
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiError body = new ApiError(
            "Forbidden",
            JarvisErrorCopy.AUTH_DENIED_MESSAGE,
            JarvisErrorCopy.AUTH_DENIED_INSTRUCAO,
            HttpServletResponse.SC_FORBIDDEN,
            request.getRequestURI() != null ? request.getRequestURI() : ""
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
