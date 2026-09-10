package com.consumoesperto.service;

/**
 * Envio de alerta por e-mail. Implementação SMTP; testes substituem por mock.
 */
@FunctionalInterface
public interface AlertaEmailSender {

    void enviar(String from, String to, String subject, String body);
}
