package com.consumoesperto.edith;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exceção de domínio da integração E.D.I.T.H. — sem stack trace exposto ao cliente.
 */
@Getter
public class EdithException extends RuntimeException {

    private final EdithErrorCode code;
    private final HttpStatus httpStatus;

    public EdithException(EdithErrorCode code, String message) {
        this(code, message, httpStatusFor(code));
    }

    public EdithException(EdithErrorCode code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    private static HttpStatus httpStatusFor(EdithErrorCode code) {
        return switch (code) {
            case EDITH_DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
            case EDITH_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case EDITH_AUTH_FAILED -> HttpStatus.UNAUTHORIZED;
            case EDITH_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case TASK_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case TASK_FAILED -> HttpStatus.BAD_GATEWAY;
            case TOOL_FAILED -> HttpStatus.BAD_GATEWAY;
            case TOOL_NOT_ALLOWED -> HttpStatus.FORBIDDEN;
            case FINANCE_DATA_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case FINANCE_RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FINANCE_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case INVALID_CONTEXT_REF -> HttpStatus.BAD_REQUEST;
            case CALLBACK_SIGNATURE_INVALID -> HttpStatus.UNAUTHORIZED;
            case CALLBACK_REPLAY_DETECTED -> HttpStatus.CONFLICT;
            case CONVERSATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TASK_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
        };
    }
}
