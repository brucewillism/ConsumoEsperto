package com.consumoesperto.exception;

import com.consumoesperto.exception.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler global para tratamento de exceções
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Trata exceções de recurso não encontrado
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());
        
        ApiError error = new ApiError(
            "RESOURCE_NOT_FOUND",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value(),
            request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Trata exceções de validação
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleValidationException(ConstraintViolationException ex, WebRequest request) {
        log.warn("Erro de validação: {}", ex.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            details.put(violation.getPropertyPath().toString(), violation.getMessage());
        });
        
        ApiError error = new ApiError(
            "VALIDATION_ERROR",
            "Erro de validação nos dados fornecidos",
            HttpStatus.BAD_REQUEST.value(),
            request.getDescription(false).replace("uri=", ""),
            details
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Trata exceções de autenticação
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        log.warn("Erro de autenticação: {}", ex.getMessage());
        
        ApiError error = new ApiError(
            "AUTHENTICATION_ERROR",
            ex.getMessage(),
            HttpStatus.UNAUTHORIZED.value(),
            request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Trata exceções de autorização
     */
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ApiError> handleAuthorizationException(AuthorizationException ex, WebRequest request) {
        log.warn("Erro de autorização: {}", ex.getMessage());
        
        ApiError error = new ApiError(
            "AUTHORIZATION_ERROR",
            ex.getMessage(),
            HttpStatus.FORBIDDEN.value(),
            request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Trata exceções de integração com APIs externas
     */
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiError> handleExternalApiException(ExternalApiException ex, WebRequest request) {
        log.error("Erro na integração com API externa: {}", ex.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        details.put("api", ex.getApiName());
        details.put("statusCode", ex.getStatusCode());
        details.put("response", ex.getResponseBody());
        
        ApiError error = new ApiError(
            "EXTERNAL_API_ERROR",
            "Erro na comunicação com serviço externo",
            HttpStatus.BAD_GATEWAY.value(),
            request.getDescription(false).replace("uri=", ""),
            details
        );
        
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    /**
     * Trata exceções de configuração
     */
    @ExceptionHandler(ConfigurationException.class)
    public ResponseEntity<ApiError> handleConfigurationException(ConfigurationException ex, WebRequest request) {
        log.error("Erro de configuração: {}", ex.getMessage());
        
        ApiError error = new ApiError(
            "CONFIGURATION_ERROR",
            ex.getMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Trata exceções genéricas
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex, WebRequest request) {
        log.error("Erro interno do servidor: {}", ex.getMessage(), ex);
        
        ApiError error = new ApiError(
            "INTERNAL_SERVER_ERROR",
            "Erro interno do servidor. Tente novamente mais tarde.",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Trata exceções de argumentos ilegais
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("Argumento ilegal: {}", ex.getMessage());
        
        ApiError error = new ApiError(
            "INVALID_ARGUMENT",
            ex.getMessage(),
            HttpStatus.BAD_REQUEST.value(),
            request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Trata exceções de estado ilegal
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalStateException(IllegalStateException ex, WebRequest request) {
        log.warn("Estado ilegal: {}", ex.getMessage());
        
        ApiError error = new ApiError(
            "INVALID_STATE",
            ex.getMessage(),
            HttpStatus.CONFLICT.value(),
            request.getDescription(false).replace("uri=", "")
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
}
