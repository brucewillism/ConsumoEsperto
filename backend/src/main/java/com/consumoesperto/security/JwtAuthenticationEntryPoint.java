package com.consumoesperto.security;

import com.consumoesperto.exception.ApiError;
import com.consumoesperto.exception.JarvisErrorCopy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest httpServletRequest,
                        HttpServletResponse httpServletResponse,
                        AuthenticationException e) throws IOException {
        httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        httpServletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        httpServletResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiError body = new ApiError(
            "Unauthorized",
            JarvisErrorCopy.AUTH_DENIED_MESSAGE,
            JarvisErrorCopy.AUTH_DENIED_INSTRUCAO,
            HttpServletResponse.SC_UNAUTHORIZED,
            httpServletRequest.getRequestURI() != null ? httpServletRequest.getRequestURI() : ""
        );
        objectMapper.writeValue(httpServletResponse.getOutputStream(), body);
    }
}
