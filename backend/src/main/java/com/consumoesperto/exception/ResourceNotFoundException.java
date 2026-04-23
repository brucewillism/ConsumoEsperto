package com.consumoesperto.exception;

/**
 * Exceção lançada quando um recurso não é encontrado
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
