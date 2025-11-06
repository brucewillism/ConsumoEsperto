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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Serviço aprimorado para renovação automática de tokens no login
 * 
 * Este serviço garante que os tokens sejam sempre atualizados quando
 * o usuário fizer login, removendo tokens temporários e forçando
 * renovação quando necessário.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoTokenRenewalService {

    private final AutorizacaoBancariaRepository autorizacaoRepository;
    private final BankApiConfigRepository configRepository;
    private final RestTemplate restTemplate;

    /**
     * Renova automaticamente todos os tokens no login
     */
    public void renovarTokensNoLogin(Long usuarioId) {
        try {
            log.info("🔄 Iniciando renovação automática de tokens no login para usuário: {}", usuarioId);
            
            // Renovar token do Mercado Pago
            renovarTokenMercadoPago(usuarioId);
            
            // Aqui pode adicionar outros bancos no futuro
            // renovarTokenNubank(usuarioId);
            // renovarTokenItau(usuarioId);
            
            log.info("✅ Renovação de tokens concluída para usuário: {}", usuarioId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao renovar tokens no login: {}", e.getMessage(), e);
        }
    }

    /**
     * Renova especificamente o token do Mercado Pago
     */
    private void renovarTokenMercadoPago(Long usuarioId) {
        try {
            log.info("🔄 Renovando token do Mercado Pago para usuário: {}", usuarioId);
            
            // Buscar autorização bancária
            Optional<AutorizacaoBancaria> auth = autorizacaoRepository
                .findByUsuarioIdAndTipoBanco(usuarioId, "MERCADO_PAGO");
            
            if (!auth.isPresent()) {
                log.warn("⚠️ Usuário {} não possui autorização do Mercado Pago", usuarioId);
                return;
            }
            
            AutorizacaoBancaria autorizacao = auth.get();
            
            // Verificar se é token temporário
            if (isTokenTemporario(autorizacao.getAccessToken())) {
                log.warn("⚠️ Token temporário detectado para usuário: {}. Removendo...", usuarioId);
                removerTokenTemporario(autorizacao);
                return;
            }
            
            // Verificar se token está expirado
            if (autorizacao.isTokenExpirado()) {
                log.info("🔄 Token expirado detectado, renovando...");
                renovarTokenExpirado(autorizacao, usuarioId);
            } else {
                log.info("✅ Token válido para usuário: {}", usuarioId);
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao renovar token do Mercado Pago: {}", e.getMessage(), e);
        }
    }

    /**
     * Verifica se o token é temporário/simulado
     */
    private boolean isTokenTemporario(String token) {
        if (token == null || token.trim().isEmpty()) {
            return true;
        }
        
        // Tokens temporários têm padrões específicos que indicam que são fake
        return token.contains("FIXED_TOKEN") ||
               token.contains("SIMULATED") ||
               token.contains("TEST_TOKEN") ||
               token.contains("FAKE_TOKEN") ||
               token.contains("MOCK_TOKEN") ||
               // Tokens temporários criados pelo sistema têm formato específico
               (token.startsWith("TEMPORARY_AUTH_") && token.length() < 100) ||
               // Tokens muito curtos que não são do Mercado Pago
               (token.length() < 50 && !token.startsWith("APP_USR_"));
    }

    /**
     * Remove token temporário do banco
     */
    private void removerTokenTemporario(AutorizacaoBancaria autorizacao) {
        try {
            log.info("🗑️ Removendo autorização com token temporário: {}", autorizacao.getId());
            autorizacaoRepository.delete(autorizacao);
            log.info("✅ Token temporário removido com sucesso");
        } catch (Exception e) {
            log.error("❌ Erro ao remover token temporário: {}", e.getMessage());
        }
    }

    /**
     * Renova token expirado usando refresh token
     */
    private void renovarTokenExpirado(AutorizacaoBancaria autorizacao, Long usuarioId) {
        try {
            // Buscar configuração do Mercado Pago
            Optional<BankApiConfig> config = configRepository
                .findByUsuarioIdAndBanco(usuarioId, "MERCADOPAGO");
            
            if (!config.isPresent()) {
                log.error("❌ Configuração do Mercado Pago não encontrada para usuário: {}", usuarioId);
                return;
            }
            
            BankApiConfig mpConfig = config.get();
            
            // Verificar se tem refresh token
            if (autorizacao.getRefreshToken() == null || autorizacao.getRefreshToken().trim().isEmpty()) {
                log.warn("⚠️ Refresh token não disponível para usuário: {}. Necessário novo OAuth2", usuarioId);
                return;
            }
            
            // Tentar renovar usando refresh token
            String novoToken = renovarComRefreshToken(autorizacao.getRefreshToken(), mpConfig);
            
            if (novoToken != null) {
                // Atualizar token no banco
                autorizacao.setAccessToken(novoToken);
                autorizacao.setDataExpiracao(LocalDateTime.now().plusHours(6)); // 6 horas de validade
                autorizacao.setDataAtualizacao(LocalDateTime.now());
                
                autorizacaoRepository.save(autorizacao);
                
                log.info("✅ Token renovado com sucesso para usuário: {}", usuarioId);
            } else {
                log.warn("⚠️ Falha ao renovar token para usuário: {}. Necessário novo OAuth2", usuarioId);
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao renovar token expirado: {}", e.getMessage(), e);
        }
    }

    /**
     * Renova token usando refresh token do Mercado Pago
     */
    private String renovarComRefreshToken(String refreshToken, BankApiConfig config) {
        try {
            log.info("🔄 Renovando token usando refresh token...");
            
            String url = "https://api.mercadopago.com/oauth/token";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "refresh_token");
            body.add("client_id", config.getClientId());
            body.add("client_secret", config.getClientSecret());
            body.add("refresh_token", refreshToken);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                if (responseBody.containsKey("access_token")) {
                    String novoToken = responseBody.get("access_token").toString();
                    log.info("✅ Token renovado com sucesso");
                    return novoToken;
                }
            }
            
            log.warn("⚠️ Resposta inesperada da API: {}", response.getStatusCode());
            return null;
            
        } catch (Exception e) {
            log.error("❌ Erro ao renovar token com refresh token: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Força renovação de token (para casos especiais)
     */
    public boolean forcarRenovacaoToken(Long usuarioId) {
        try {
            log.info("🔄 Forçando renovação de token para usuário: {}", usuarioId);
            
            // Buscar autorização
            Optional<AutorizacaoBancaria> auth = autorizacaoRepository
                .findByUsuarioIdAndTipoBanco(usuarioId, "MERCADO_PAGO");
            
            if (!auth.isPresent()) {
                log.warn("⚠️ Usuário {} não possui autorização do Mercado Pago", usuarioId);
                return false;
            }
            
            AutorizacaoBancaria autorizacao = auth.get();
            
            // Buscar configuração
            Optional<BankApiConfig> config = configRepository
                .findByUsuarioIdAndBanco(usuarioId, "MERCADOPAGO");
            
            if (!config.isPresent()) {
                log.error("❌ Configuração do Mercado Pago não encontrada");
                return false;
            }
            
            // Tentar renovar
            String novoToken = renovarComRefreshToken(autorizacao.getRefreshToken(), config.get());
            
            if (novoToken != null) {
                autorizacao.setAccessToken(novoToken);
                autorizacao.setDataExpiracao(LocalDateTime.now().plusHours(6));
                autorizacao.setDataAtualizacao(LocalDateTime.now());
                
                autorizacaoRepository.save(autorizacao);
                
                log.info("✅ Token renovado com sucesso");
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("❌ Erro ao forçar renovação: {}", e.getMessage(), e);
            return false;
        }
    }
}
