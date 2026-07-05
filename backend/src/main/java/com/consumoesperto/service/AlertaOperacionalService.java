package com.consumoesperto.service;

import com.consumoesperto.config.AlertasOperacionaisProperties;
import com.consumoesperto.util.AppTimeZone;
import lombok.extern.slf4j.Slf4j;
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
 * (grepável por [ALERTA-OP]); opcionalmente POST JSON num webhook configurável.
 * Nunca propaga exceção para o fluxo chamador.
 */
@Service
@Slf4j
public class AlertaOperacionalService {

    public static final String TIPO_DIVERGENCIA_SALDO = "DIVERGENCIA_SALDO";
    public static final String TIPO_WEBHOOK_AUTH_FALHA = "WEBHOOK_AUTH_FALHA";

    private final AlertasOperacionaisProperties properties;
    private final RestTemplate restTemplate;
    private final Map<String, Instant> ultimoEnvioPorTipo = new ConcurrentHashMap<>();

    public AlertaOperacionalService(AlertasOperacionaisProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    public void alertar(String tipo, String mensagem) {
        log.error("[ALERTA-OP] tipo={} mensagem={}", tipo, mensagem);
        if (!properties.isWebhookEnabled()
            || properties.getWebhookUrl() == null
            || properties.getWebhookUrl().isBlank()) {
            return;
        }
        if (emCooldown(tipo)) {
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
            log.warn("[ALERTA-OP] Falha ao enviar webhook de alerta ({}): {}", tipo, e.getMessage());
        }
    }

    /** Um alerta por tipo dentro da janela de cooldown — evita tempestade de notificações. */
    private boolean emCooldown(String tipo) {
        Instant agora = Instant.now();
        Duration cooldown = Duration.ofMinutes(Math.max(1, properties.getCooldownMinutes()));
        Instant anterior = ultimoEnvioPorTipo.get(tipo);
        if (anterior != null && anterior.plus(cooldown).isAfter(agora)) {
            return true;
        }
        ultimoEnvioPorTipo.put(tipo, agora);
        return false;
    }
}
