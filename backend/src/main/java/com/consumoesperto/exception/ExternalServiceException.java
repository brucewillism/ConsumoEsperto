package com.consumoesperto.exception;

/**
 * Exceção lançada quando há erro em serviços externos
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
public class ExternalServiceException extends RuntimeException {
    
    public ExternalServiceException(String message) {
        super(message);
    }
    
    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
