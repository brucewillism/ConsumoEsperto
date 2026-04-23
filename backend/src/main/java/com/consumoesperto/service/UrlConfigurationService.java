package com.consumoesperto.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

/**
 * Serviço para gerenciar URLs e configurações dinâmicas
 * Centraliza todas as URLs para facilitar manutenção
 */
@Service
@Slf4j
public class UrlConfigurationService {

    private final Environment environment;

    @Value("${ngrok.url:https://f03db4701c9f.ngrok-free.app}")
    private String ngrokUrl;

    @Value("${cors.allowed-origins:http://localhost:4200,https://*.ngrok-free.app}")
    private String corsAllowedOrigins;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    public UrlConfigurationService(Environment environment) {
        this.environment = environment;
    }

    /**
     * Obtém a URL base do ngrok
     */
    public String getNgrokUrl() {
        return ngrokUrl;
    }

    /**
     * Obtém a URL de callback do Mercado Pago
     */
    public String getMercadoPagoCallbackUrl() {
        return ngrokUrl + "/api/bank/oauth/mercadopago/callback";
    }

    /**
     * Obtém a URL de callback do Itaú
     */
    public String getItauCallbackUrl() {
        return ngrokUrl + "/api/auth/itau/callback";
    }

    /**
     * Obtém a URL de callback do Inter
     */
    public String getInterCallbackUrl() {
        return ngrokUrl + "/api/auth/inter/callback";
    }

    /**
     * Obtém a URL de callback do Nubank
     */
    public String getNubankCallbackUrl() {
        return ngrokUrl + "/api/auth/nubank/callback";
    }

    /**
     * Obtém as origens permitidas para CORS
     */
    public String[] getCorsAllowedOrigins() {
        return corsAllowedOrigins.split(",");
    }

    /**
     * Verifica se uma origem é permitida para CORS
     */
    public boolean isOriginAllowed(String origin) {
        String[] allowedOrigins = getCorsAllowedOrigins();
        return Arrays.stream(allowedOrigins)
                .anyMatch(allowed -> allowed.trim().equals(origin) || 
                         allowed.trim().equals("*") ||
                         (allowed.contains("*") && origin.matches(allowed.replace("*", ".*"))));
    }

    /**
     * Obtém a URL do frontend baseada no ambiente
     */
    public String getFrontendUrl() {
        if ("prod".equals(activeProfile)) {
            return "https://consumoesperto.com";
        }
        return "http://localhost:4200";
    }

    /**
     * Obtém a URL do backend baseada no ambiente
     */
    public String getBackendUrl() {
        if ("prod".equals(activeProfile)) {
            return "https://api.consumoesperto.com";
        }
        return "http://localhost:8080";
    }

    /**
     * Obtém a URL de callback baseada no banco
     */
    public String getCallbackUrl(String banco) {
        switch (banco.toUpperCase()) {
            case "MERCADO_PAGO":
                return getMercadoPagoCallbackUrl();
            case "ITAU":
                return getItauCallbackUrl();
            case "INTER":
                return getInterCallbackUrl();
            case "NUBANK":
                return getNubankCallbackUrl();
            default:
                log.warn("Banco não reconhecido para callback: {}", banco);
                return ngrokUrl + "/api/callback";
        }
    }

    /**
     * Atualiza a URL do ngrok dinamicamente
     */
    public void updateNgrokUrl(String newUrl) {
        this.ngrokUrl = newUrl;
        log.info("URL do ngrok atualizada para: {}", newUrl);
    }

    /**
     * Verifica se está em ambiente de desenvolvimento
     */
    public boolean isDevelopment() {
        return "dev".equals(activeProfile) || "development".equals(activeProfile);
    }

    /**
     * Verifica se está em ambiente de produção
     */
    public boolean isProduction() {
        return "prod".equals(activeProfile) || "production".equals(activeProfile);
    }
}
