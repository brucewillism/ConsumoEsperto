package com.consumoesperto.service;

import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import com.consumoesperto.repository.BankApiConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Serviço para validação e renovação automática de tokens
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenValidationService {

    private final AutorizacaoBancariaRepository autorizacaoRepository;
    private final BankApiConfigRepository configRepository;
    private final MercadoPagoService mercadoPagoService;
    private final AutoTokenRenewalService autoTokenRenewalService;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Valida e renova token do Mercado Pago se necessário
     */
    public boolean validarERenovarTokenMercadoPago(Long usuarioId) {
        try {
            log.info("🔍 Validando token do Mercado Pago para usuário: {}", usuarioId);
            
            // Usar o MercadoPagoService para verificar e renovar token
            return mercadoPagoService.verificarERenovarToken(usuarioId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao validar token do Mercado Pago: {}", e.getMessage(), e);
            return false;
        }
    }
    
    
    /**
     * Valida token no login do usuário
     */
    public void validarTokenNoLogin(Long usuarioId) {
        try {
            log.info("🔐 Validando e renovando tokens no login do usuário: {}", usuarioId);
            
            // Usar o novo serviço aprimorado para renovação automática
            autoTokenRenewalService.renovarTokensNoLogin(usuarioId);
            
            log.info("✅ Validação de tokens no login concluída para usuário: {}", usuarioId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao validar tokens no login: {}", e.getMessage(), e);
        }
    }
}
