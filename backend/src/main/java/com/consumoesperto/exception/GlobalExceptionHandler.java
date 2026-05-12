package com.consumoesperto.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Handler global — respostas com persona J.A.R.V.I.S. ({@code message} + {@code instrucao}).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Pattern CAMEL = Pattern.compile("([a-z])([A-Z])");

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatus status,
        WebRequest request
    ) {
        String path = pathFrom(request);
        FieldError fe = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String campoRotulo = fe != null ? humanizarNomeCampo(fe.getField()) : "entrada";
        String msg = "Identifiquei uma inconsistência nos dados de " + campoRotulo + "." + JarvisErrorCopy.VALIDATION_SUFFIX;
        log.warn("Validação Bean: {}", ex.getBindingResult().getAllErrors());

        ApiError error = new ApiError(
            "VALIDATION_ERROR",
            msg,
            JarvisErrorCopy.VALIDATION_INSTRUCAO,
            HttpStatus.BAD_REQUEST.value(),
            path
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
        HttpMessageNotReadableException ex,
        HttpHeaders headers,
        HttpStatus status,
        WebRequest request
    ) {
        String path = pathFrom(request);
        ApiError error = new ApiError(
            "BAD_REQUEST",
            "Não consegui interpretar os dados enviados. Verifique se o formato (JSON) está correto.",
            JarvisErrorCopy.GENERIC_BAD_REQUEST_INSTRUCAO,
            HttpStatus.BAD_REQUEST.value(),
            path
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Integridade de dados: {}", ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage());
        String path = pathFrom(request);
        if (isUniqueOrDuplicate(ex)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(
                "DUPLICATE_RECORD",
                JarvisErrorCopy.DUPLICATE_RECORD_MESSAGE,
                JarvisErrorCopy.DUPLICATE_RECORD_INSTRUCAO,
                HttpStatus.BAD_REQUEST.value(),
                path
            ));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
            "DATA_INTEGRITY",
            "Senhor, esta operação conflita com dados já existentes no sistema.",
            JarvisErrorCopy.CONFLICT_INSTRUCAO,
            HttpStatus.CONFLICT.value(),
            path
        ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());
        String path = pathFrom(request);
        ApiError error = new ApiError(
            "RESOURCE_NOT_FOUND",
            ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "Não localizei o recurso solicitado.",
            JarvisErrorCopy.NOT_FOUND_INSTRUCAO,
            HttpStatus.NOT_FOUND.value(),
            path
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        log.warn("ConstraintViolation: {}", ex.getMessage());
        String path = pathFrom(request);
        ConstraintViolation<?> first = ex.getConstraintViolations().stream().findFirst().orElse(null);
        String campo = first != null ? humanizarNomeCampo(extractPropertyName(first.getPropertyPath().toString())) : "entrada";
        Map<String, Object> details = new HashMap<>();
        ex.getConstraintViolations().forEach(v ->
            details.put(v.getPropertyPath().toString(), v.getMessage()));

        String msg = "Identifiquei uma inconsistência nos dados de " + campo + "." + JarvisErrorCopy.VALIDATION_SUFFIX;
        ApiError error = new ApiError(
            "VALIDATION_ERROR",
            msg,
            JarvisErrorCopy.VALIDATION_INSTRUCAO,
            HttpStatus.BAD_REQUEST.value(),
            path,
            details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        log.warn("Autenticação: {}", ex.getMessage());
        String path = pathFrom(request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(
            "AUTHENTICATION_ERROR",
            JarvisErrorCopy.AUTH_DENIED_MESSAGE,
            JarvisErrorCopy.AUTH_DENIED_INSTRUCAO,
            HttpStatus.UNAUTHORIZED.value(),
            path
        ));
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ApiError> handleAuthorizationException(AuthorizationException ex, WebRequest request) {
        log.warn("Autorização: {}", ex.getMessage());
        String path = pathFrom(request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(
            "AUTHORIZATION_ERROR",
            JarvisErrorCopy.AUTH_DENIED_MESSAGE,
            JarvisErrorCopy.AUTH_DENIED_INSTRUCAO,
            HttpStatus.FORBIDDEN.value(),
            path
        ));
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiError> handleExternalApiException(ExternalApiException ex, WebRequest request) {
        log.error("API externa: {}", ex.getMessage());
        Map<String, Object> details = new HashMap<>();
        details.put("api", ex.getApiName());
        details.put("statusCode", ex.getStatusCode());
        details.put("response", ex.getResponseBody());
        String path = pathFrom(request);
        ApiError error = new ApiError(
            "EXTERNAL_API_ERROR",
            "Houve uma falha temporária ao contactar um serviço externo.",
            "Aguarde um momento e tente de novo. Se insistir, verifique a sua ligação e as configurações de integração.",
            HttpStatus.BAD_GATEWAY.value(),
            path,
            details
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    @ExceptionHandler(ConfigurationException.class)
    public ResponseEntity<ApiError> handleConfigurationException(ConfigurationException ex, WebRequest request) {
        log.error("Configuração: {}", ex.getMessage());
        String path = pathFrom(request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(
            "CONFIGURATION_ERROR",
            JarvisErrorCopy.SERVER_INSTABILITY_MESSAGE,
            JarvisErrorCopy.SERVER_INSTABILITY_INSTRUCAO,
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            path
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("Argumento ilegal: {}", ex.getMessage());
        String path = pathFrom(request);
        String raw = ex.getMessage() != null ? ex.getMessage() : "";

        if (pareceDuplicidadeOuUnicidade(raw)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(
                "DUPLICATE_OR_CONFLICT",
                JarvisErrorCopy.DUPLICATE_RECORD_MESSAGE,
                JarvisErrorCopy.DUPLICATE_RECORD_INSTRUCAO,
                HttpStatus.BAD_REQUEST.value(),
                path
            ));
        }

        String msg = raw.isBlank() ? "Não consegui concluir esta operação com os dados recebidos." : raw;
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(
            "INVALID_ARGUMENT",
            msg,
            JarvisErrorCopy.GENERIC_BAD_REQUEST_INSTRUCAO,
            HttpStatus.BAD_REQUEST.value(),
            path
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalStateException(IllegalStateException ex, WebRequest request) {
        log.warn("Estado ilegal: {}", ex.getMessage());
        String path = pathFrom(request);
        String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
            ? ex.getMessage()
            : "O sistema não pode prosseguir neste momento.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
            "INVALID_STATE",
            msg,
            JarvisErrorCopy.CONFLICT_INSTRUCAO,
            HttpStatus.CONFLICT.value(),
            path
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex, WebRequest request) {
        log.error("Erro não previsto: {}", ex.getMessage(), ex);
        String path = pathFrom(request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(
            "internal_server_error",
            JarvisErrorCopy.SERVER_INSTABILITY_MESSAGE,
            JarvisErrorCopy.SERVER_INSTABILITY_INSTRUCAO,
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            path
        ));
    }

    private static String pathFrom(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }

    private static boolean isUniqueOrDuplicate(DataIntegrityViolationException ex) {
        StringBuilder sb = new StringBuilder();
        Throwable t = ex;
        while (t != null) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage().toLowerCase()).append(' ');
            }
            t = t.getCause();
        }
        String joined = sb.toString();
        return joined.contains("unique")
            || joined.contains("duplicate")
            || joined.contains("already exists");
    }

    private static boolean pareceDuplicidadeOuUnicidade(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String n = raw.toLowerCase();
        return n.contains("já existe") || n.contains("ja existe")
            || n.contains("semelhante")
            || n.contains("duplicate")
            || n.contains("unique")
            || n.contains("already exists");
    }

    private static String extractPropertyName(String propertyPath) {
        if (propertyPath == null) {
            return "";
        }
        int last = propertyPath.lastIndexOf('.');
        return last >= 0 ? propertyPath.substring(last + 1) : propertyPath;
    }

    private static String humanizarNomeCampo(String field) {
        if (field == null || field.isBlank()) {
            return "entrada";
        }
        String spaced = CAMEL.matcher(field).replaceAll("$1 $2").trim();
        if (!spaced.isEmpty()) {
            spaced = spaced.replace('_', ' ');
            return spaced.substring(0, 1).toUpperCase() + spaced.substring(1).toLowerCase();
        }
        return field;
    }
}
