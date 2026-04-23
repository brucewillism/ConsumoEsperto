package com.consumoesperto.exception;

/**
 * Exceção lançada quando há conflito de dados
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
public class DataConflictException extends RuntimeException {
    
    public DataConflictException(String message) {
        super(message);
    }
    
    public DataConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
