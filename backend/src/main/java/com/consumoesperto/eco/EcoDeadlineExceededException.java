package com.consumoesperto.eco;

public class EcoDeadlineExceededException extends RuntimeException {

    public EcoDeadlineExceededException(String message) {
        super(message);
    }
}
