package com.consumoesperto.service;

import com.consumoesperto.config.AlertasOperacionaisProperties;
import com.consumoesperto.util.AppTimeZone;
import com.consumoesperto.util.LogSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sinalização de anomalias operacionais em produção: sempre log ERROR estruturado
 * (grepável por [ALERTA-OP]); webhook e e-mail opcionais. Nunca propaga excepção
 * para o fluxo chamador. Segredos não entram na mensagem (usar {@link LogSanitizer}).
 */
@Service
@Slf4j
public class AlertaOperacionalService {

    public static final String TIPO_DIVERGENCIA_SALDO = "DIVERGENCIA_SALDO";
    public static final String TIPO_WEBHOOK_AUTH_FALHA = "WEBHOOK_AUTH_FALHA";
    public static final String TIPO_MEMORIA_CAPTURA_FALHA = "MEMORIA_CAPTURA_FALHA";
    public static final String TIPO_WHATSAPP_DESCONECTADO = "WHATSAPP_DESCONECTADO";
    public static final String TIPO_WHATSAPP_RECUPERADO = "WHATSAPP_RECUPERADO";

    private final AlertasOperacionaisProperties properties;
    private final ObjectProvider<AlertaEmailSender> emailSender;
    private final RestTemplate restTemplate;
    private final Map<String, Instant> ultimoEnvioPorTipo = new ConcurrentHashMap<>();

    public AlertaOperacionalService(
        AlertasOperacionaisProperties properties,
        ObjectProvider<AlertaEmailSender> emailSender
    ) {
        this.properties = properties;
        this.emailSender = emailSender;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    public void alertar(String tipo, String mensagem) {
        alertar(tipo, mensagem, null, true);
    }

    /**
     * @param emailAssunto assunto do e-mail (se nulo, usa o tipo)
     * @param respeitarCooldown {@code false} para e-mail de recuperação (um disparo, sem bloquear)
     */
    public void alertar(String tipo, String mensagem, String emailAssunto, boolean respeitarCooldown) {
        String safeTipo = tipo == null || tipo.isBlank() ? "DESCONHECIDO" : tipo.trim();
        String safeMsg = LogSanitizer.sanitize(mensagem == null ? "" : mensagem);
        if (TIPO_WHATSAPP_RECUPERADO.equals(safeTipo)) {
            log.info("[ALERTA-OP] tipo={} mensagem={}", safeTipo, safeMsg);
        } else {
            log.error("[ALERTA-OP] tipo={} mensagem={}", safeTipo, safeMsg);
        }
        if (respeitarCooldown && emCooldown(safeTipo)) {
            return;
        }
        if (!respeitarCooldown) {
            ultimoEnvioPorTipo.put(safeTipo, Instant.now());
        }
        enviarWebhook(safeTipo, safeMsg);
        enviarEmail(safeTipo, safeMsg, emailAssunto);
    }

    private void enviarWebhook(String tipo, String mensagem) {
        if (!properties.isWebhookEnabled()
            || properties.getWebhookUrl() == null
            || properties.getWebhookUrl().isBlank()) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of(
                "tipo", tipo,
                "mensagem", mensagem,
                "timestamp", AppTimeZone.agora().toString(),
                "aplicacao", "consumo-esperto-backend"
            );
            restTemplate.postForEntity(properties.getWebhookUrl(), new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            log.warn("[ALERTA-OP] Falha ao enviar webhook de alerta ({}): {}",
                tipo, LogSanitizer.sanitize(e.getMessage()));
        }
    }

    private void enviarEmail(String tipo, String mensagem, String emailAssunto) {
        if (!properties.isEmailEnabled()) {
            return;
        }
        String destino = properties.getEmailDestino();
        String from = properties.getEmailFrom();
        if (destino == null || destino.isBlank() || from == null || from.isBlank()) {
            log.warn("[ALERTA-OP] E-mail ligado mas MAIL_FROM/ALERTA_EMAIL_DESTINO em falta (tipo={})", tipo);
            return;
        }
        AlertaEmailSender sender = emailSender == null ? null : emailSender.getIfAvailable();
        if (sender == null) {
            log.warn("[ALERTA-OP] E-mail ligado mas SMTP indisponível (tipo={})", tipo);
            return;
        }
        String subject = (emailAssunto == null || emailAssunto.isBlank())
            ? "[ConsumoEsperto] " + tipo
            : emailAssunto.trim();
        try {
            sender.enviar(from.trim(), destino.trim(), LogSanitizer.sanitize(subject), mensagem);
        } catch (Exception e) {
            log.warn("[ALERTA-OP] Falha ao enviar e-mail ({}): {}",
                tipo, LogSanitizer.sanitize(e.getMessage()));
        }
    }

    /** Um alerta por tipo dentro da janela de cooldown — evita tempestade de notificações. */
    boolean emCooldown(String tipo) {
        Instant agora = Instant.now();
        Duration cooldown = Duration.ofMinutes(Math.max(1, properties.getCooldownMinutes()));
        Instant anterior = ultimoEnvioPorTipo.get(tipo);
        if (anterior != null && anterior.plus(cooldown).isAfter(agora)) {
            return true;
        }
        ultimoEnvioPorTipo.put(tipo, agora);
        return false;
    }

    /** Visível para testes. */
    void limparCooldown() {
        ultimoEnvioPorTipo.clear();
    }
}
