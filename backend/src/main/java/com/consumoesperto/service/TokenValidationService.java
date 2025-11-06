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
<<<<<<< HEAD
    private final MercadoPagoService mercadoPagoService;
    private final AutoTokenRenewalService autoTokenRenewalService;
=======
>>>>>>> origin/main
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Valida e renova token do Mercado Pago se necessário
     */
    public boolean validarERenovarTokenMercadoPago(Long usuarioId) {
        try {
            log.info("🔍 Validando token do Mercado Pago para usuário: {}", usuarioId);
            
<<<<<<< HEAD
            // Usar o MercadoPagoService para verificar e renovar token
            return mercadoPagoService.verificarERenovarToken(usuarioId);
=======
            // Buscar autorização bancária
            Optional<AutorizacaoBancaria> authOpt = autorizacaoRepository.findByUsuarioIdAndTipoBanco(usuarioId, "MERCADO_PAGO");
            if (authOpt.isEmpty()) {
                log.warn("⚠️ Nenhuma autorização do Mercado Pago encontrada para usuário: {}", usuarioId);
                return false;
            }
            
            AutorizacaoBancaria auth = authOpt.get();
            
            // Verificar se token expirou
            if (auth.getDataExpiracao() != null && auth.getDataExpiracao().isBefore(LocalDateTime.now())) {
                log.info("🔄 Token expirado, tentando renovar...");
                return renovarTokenMercadoPago(usuarioId, auth);
            }
            
            // Testar se token ainda é válido fazendo uma chamada de teste
            if (!testarTokenValido(auth)) {
                log.info("🔄 Token inválido, tentando renovar...");
                return renovarTokenMercadoPago(usuarioId, auth);
            }
            
            log.info("✅ Token do Mercado Pago válido para usuário: {}", usuarioId);
            return true;
>>>>>>> origin/main
            
        } catch (Exception e) {
            log.error("❌ Erro ao validar token do Mercado Pago: {}", e.getMessage(), e);
            return false;
        }
    }
    
<<<<<<< HEAD
=======
    /**
     * Testa se o token ainda é válido fazendo uma chamada de teste
     */
    private boolean testarTokenValido(AutorizacaoBancaria auth) {
        try {
            // Buscar configuração
            Optional<BankApiConfig> configOpt = configRepository.findByUsuarioIdAndTipoBanco(auth.getUsuario().getId(), "MERCADO_PAGO");
            if (configOpt.isEmpty()) {
                return false;
            }
            
            BankApiConfig config = configOpt.get();
            
            // Fazer chamada de teste para /users/me
            String testUrl = config.getApiUrl() + "/users/me";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + auth.getAccessToken());
            headers.set("Content-Type", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(testUrl, HttpMethod.GET, request, Map.class);
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.warn("⚠️ Token inválido: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Renova o token do Mercado Pago usando refresh token
     */
    private boolean renovarTokenMercadoPago(Long usuarioId, AutorizacaoBancaria auth) {
        try {
            // Buscar configuração
            Optional<BankApiConfig> configOpt = configRepository.findByUsuarioIdAndTipoBanco(usuarioId, "MERCADO_PAGO");
            if (configOpt.isEmpty()) {
                log.error("❌ Configuração do Mercado Pago não encontrada");
                return false;
            }
            
            BankApiConfig config = configOpt.get();
            
            // URL para renovar token
            String refreshUrl = "https://api.mercadopago.com/oauth/token";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded");
            
            // Parâmetros para renovar token
            String body = String.format(
                "grant_type=refresh_token&client_id=%s&client_secret=%s&refresh_token=%s",
                config.getClientId(),
                config.getClientSecret(),
                auth.getRefreshToken()
            );
            
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(refreshUrl, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> tokenData = response.getBody();
                
                // Atualizar token na base de dados
                auth.setAccessToken((String) tokenData.get("access_token"));
                if (tokenData.containsKey("refresh_token")) {
                    auth.setRefreshToken((String) tokenData.get("refresh_token"));
                }
                
                // Calcular nova data de expiração (geralmente 6 horas)
                auth.setDataExpiracao(LocalDateTime.now().plusHours(6));
                auth.setDataAtualizacao(LocalDateTime.now());
                
                autorizacaoRepository.save(auth);
                
                log.info("✅ Token do Mercado Pago renovado com sucesso para usuário: {}", usuarioId);
                return true;
            }
            
            log.error("❌ Falha ao renovar token: {}", response.getBody());
            return false;
            
        } catch (Exception e) {
            log.error("❌ Erro ao renovar token do Mercado Pago: {}", e.getMessage(), e);
            return false;
        }
    }
>>>>>>> origin/main
    
    /**
     * Valida token no login do usuário
     */
    public void validarTokenNoLogin(Long usuarioId) {
        try {
<<<<<<< HEAD
            log.info("🔐 Validando e renovando tokens no login do usuário: {}", usuarioId);
            
            // Usar o novo serviço aprimorado para renovação automática
            autoTokenRenewalService.renovarTokensNoLogin(usuarioId);
            
            log.info("✅ Validação de tokens no login concluída para usuário: {}", usuarioId);
=======
            log.info("🔐 Validando tokens no login do usuário: {}", usuarioId);
            
            // Validar token do Mercado Pago
            validarERenovarTokenMercadoPago(usuarioId);
            
            // Aqui pode adicionar validação de outros bancos se necessário
>>>>>>> origin/main
            
        } catch (Exception e) {
            log.error("❌ Erro ao validar tokens no login: {}", e.getMessage(), e);
        }
    }
}
