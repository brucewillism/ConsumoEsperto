package com.consumoesperto.eco;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Erro no contrato do ecossistema (seção 6). Só os códigos fechados.
 */
@Getter
public class EcoException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;
    private final boolean retryable;

    public EcoException(String code, String message, HttpStatus httpStatus, boolean retryable) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public static EcoException unauthorized(String message) {
        return new EcoException("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED, false);
    }

    public static EcoException scopeDenied(String message) {
        return new EcoException("SCOPE_DENIED", message, HttpStatus.FORBIDDEN, false);
    }

    public static EcoException invalidInput(String message) {
        return new EcoException("INVALID_INPUT", message, HttpStatus.BAD_REQUEST, false);
    }

    public static EcoException capabilityNotFound(String message) {
        return new EcoException("CAPABILITY_NOT_FOUND", message, HttpStatus.NOT_FOUND, false);
    }
}
