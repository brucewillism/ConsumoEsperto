package com.consumoesperto.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Classe para representar erros padronizados da aplicação
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {
    
    private String error;
    private String message;
    private int status;
    private LocalDateTime timestamp;
    private String path;
    private Map<String, Object> details;
    
    public ApiError(String error, String message, int status) {
        this.error = error;
        this.message = message;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }
    
    public ApiError(String error, String message, int status, String path) {
        this.error = error;
        this.message = message;
        this.status = status;
        this.path = path;
        this.timestamp = LocalDateTime.now();
    }
    
    public ApiError(String error, String message, int status, String path, Map<String, Object> details) {
        this.error = error;
        this.message = message;
        this.status = status;
        this.path = path;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }
}
