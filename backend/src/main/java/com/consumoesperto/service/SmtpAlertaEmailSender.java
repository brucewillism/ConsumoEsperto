package com.consumoesperto.service;

import com.consumoesperto.util.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Canal SMTP dos alertas operacionais. Ausência de {@link JavaMailSender} não impede o boot.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpAlertaEmailSender implements AlertaEmailSender {

    private final ObjectProvider<JavaMailSender> mailSender;

    @Override
    public void enviar(String from, String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("SMTP não configurado (spring.mail.host vazio)");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
        log.info("[ALERTA-OP] E-mail enviado para destino configurado (assunto={})",
            LogSanitizer.sanitize(subject));
    }
}
