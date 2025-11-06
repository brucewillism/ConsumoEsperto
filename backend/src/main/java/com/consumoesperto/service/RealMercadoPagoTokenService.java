package com.consumoesperto.service;

import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.model.Usuario;
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

import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Serviço para gerenciar tokens REAIS do Mercado Pago
 * 
 * Este serviço implementa o fluxo OAuth2 completo do Mercado Pago,
 * incluindo troca de código por token e renovação automática.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealMercadoPagoTokenService {

    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;
    private final BankApiConfigRepository bankApiConfigRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Valida e renova token do Mercado Pago se necessário
     * 
     * @param userId ID do usuário
     * @return true se o token está válido ou foi renovado com sucesso
     */
    public boolean validateAndRenewToken(Long userId) {
        try {
            log.info("🔍 Validando token do Mercado Pago para usuário: {}", userId);

            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(userId, "MERCADO_PAGO");

            if (authOpt.isPresent()) {
                AutorizacaoBancaria auth = authOpt.get();
                
                // Verificar se é um token simulado/fake
                if (isSimulatedToken(auth.getAccessToken())) {
                    log.warn("⚠️ Token simulado detectado para usuário: {}. Removendo autorização.", userId);
                    autorizacaoBancariaRepository.delete(auth);
                    return false;
                }
                
                if (auth.isTokenValido()) {
                    // Testar token real na API do Mercado Pago
                    if (testTokenWithAPI(auth)) {
                        log.info("✅ Token do Mercado Pago válido e testado na API para usuário: {}", userId);
                        return true;
                    } else {
                        log.warn("⚠️ Token inválido na API do Mercado Pago para usuário: {}", userId);
                        return false;
                    }
                } else if (auth.precisaRenovacao()) {
                    log.info("⚠️ Token do Mercado Pago precisa de renovação para usuário: {}", userId);
                    return renewTokenWithRefreshToken(auth);
                } else {
                    log.warn("❌ Token do Mercado Pago inválido ou expirado para usuário: {}", userId);
                    return false;
                }
                    } else {
                        log.warn("⚠️ Nenhuma autorização do Mercado Pago encontrada para usuário: {}. Usuário precisa fazer OAuth2.", userId);
                        return false;
                    }

        } catch (Exception e) {
            log.error("❌ Erro ao validar token para usuário {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Renova token usando refresh_token
     * 
     * @param auth Autorização bancária
     * @return true se renovado com sucesso
     */
    private boolean renewTokenWithRefreshToken(AutorizacaoBancaria auth) {
        try {
            log.info("🔄 Renovando token do Mercado Pago para usuário: {}", auth.getUsuario().getId());

            // Buscar configuração
            Optional<BankApiConfig> configOpt = bankApiConfigRepository
                .findByUsuarioIdAndTipoBanco(auth.getUsuario().getId(), "MERCADO_PAGO");

            if (configOpt.isEmpty()) {
                log.error("❌ Configuração do Mercado Pago não encontrada");
                return false;
            }

            BankApiConfig config = configOpt.get();

            // Fazer requisição de renovação
            Map<String, Object> tokenResponse = renewTokenViaAPI(config, auth.getRefreshToken());

            if (tokenResponse != null && tokenResponse.containsKey("access_token")) {
                // Atualizar autorização
                auth.setAccessToken((String) tokenResponse.get("access_token"));
                auth.setRefreshToken((String) tokenResponse.get("refresh_token"));
                auth.setDataAtualizacao(LocalDateTime.now());
                auth.setDataExpiracao(LocalDateTime.now().plusSeconds(
                    ((Number) tokenResponse.get("expires_in")).longValue()
                ));
                auth.setAtivo(true);
                
                autorizacaoBancariaRepository.save(auth);
                
                log.info("✅ Token renovado com sucesso para usuário: {}", auth.getUsuario().getId());
                return true;
            } else {
                log.error("❌ Falha ao renovar token para usuário: {}", auth.getUsuario().getId());
                return false;
            }

        } catch (Exception e) {
            log.error("❌ Erro ao renovar token para usuário {}: {}", auth.getUsuario().getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Renova token via API do Mercado Pago
     * 
     * @param config Configuração do Mercado Pago
     * @param refreshToken Refresh token
     * @return Resposta da API
     */
    private Map<String, Object> renewTokenViaAPI(BankApiConfig config, String refreshToken) {
        try {
            String tokenUrl = "https://api.mercadopago.com/oauth/token";
            
            // Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(config.getClientId(), config.getClientSecret());
            
            // Body da requisição
            String body = String.format(
                "grant_type=refresh_token&refresh_token=%s",
                refreshToken
            );
            
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            
            log.info("🔄 Fazendo requisição de renovação de token...");
            
            // Fazer requisição
            ResponseEntity<Map> response = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                request,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> tokenData = response.getBody();
                log.info("✅ Token renovado via API: {}", tokenData.keySet());
                return tokenData;
            } else {
                log.error("❌ Resposta inesperada na renovação: {}", response.getStatusCode());
                return null;
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao renovar token via API: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Salva nova autorização após OAuth2
     * 
     * @param userId ID do usuário
     * @param tokenResponse Resposta do OAuth2
     * @return true se salvo com sucesso
     */
    public boolean saveNewAuthorization(Long userId, Map<String, Object> tokenResponse) {
        try {
            log.info("💾 Salvando nova autorização para usuário: {}", userId);

            AutorizacaoBancaria auth = new AutorizacaoBancaria();
            Usuario usuario = new Usuario();
            usuario.setId(userId);
            auth.setUsuario(usuario);
            auth.setTipoBanco("MERCADO_PAGO");
            auth.setBanco("MERCADO_PAGO");
            auth.setTipoConta("CREDITO");
            auth.setAccessToken((String) tokenResponse.get("access_token"));
            auth.setRefreshToken((String) tokenResponse.get("refresh_token"));
            auth.setTokenType("Bearer");
            auth.setScope((String) tokenResponse.get("scope"));
            auth.setAtivo(true);
            auth.setDataCriacao(LocalDateTime.now());
            auth.setDataAtualizacao(LocalDateTime.now());
            auth.setDataExpiracao(LocalDateTime.now().plusSeconds(
                ((Number) tokenResponse.get("expires_in")).longValue()
            ));
            
            autorizacaoBancariaRepository.save(auth);
            
            log.info("✅ Nova autorização salva para usuário: {}", userId);
            return true;

        } catch (Exception e) {
            log.error("❌ Erro ao salvar nova autorização para usuário {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Verifica se o token é simulado/fake
     * 
     * @param accessToken Token de acesso
     * @return true se é simulado
     */
    private boolean isSimulatedToken(String accessToken) {
        if (accessToken == null) return true;
        
        // Tokens temporários são sempre simulados/fake
        if (accessToken.startsWith("TEMPORARY_AUTH_")) {
            return true; // Tokens temporários são sempre simulados
        }
        
        // Tokens simulados têm padrões específicos que indicam que são fake
        return accessToken.contains("FIXED_TOKEN") || 
               accessToken.contains("SIMULATED") ||
               accessToken.contains("TEST_TOKEN") ||
               accessToken.contains("FAKE_TOKEN") ||
               accessToken.contains("MOCK_TOKEN") ||
               // Tokens muito curtos que não são do Mercado Pago
               (accessToken.length() < 50 && !accessToken.startsWith("APP_USR_"));
    }

    /**
     * Cria uma autorização temporária para permitir que o usuário use o sistema
     * enquanto não faz o OAuth2 completo
     * 
     * @param userId ID do usuário
     * @return true se a autorização foi criada com sucesso
     */
    private boolean createTemporaryAuthorization(Long userId) {
        try {
            log.info("🔧 Criando autorização temporária do Mercado Pago para usuário: {}", userId);
            
            // Verificar se já existe uma autorização temporária
            Optional<AutorizacaoBancaria> existingAuthOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(userId, "MERCADO_PAGO");
                
            if (existingAuthOpt.isPresent()) {
                log.info("ℹ️ Autorização temporária já existe para usuário: {}", userId);
                return true;
            }
            
            // Não criar autorização temporária - usuário deve fazer OAuth2 para obter token real
            log.warn("⚠️ Usuário {} não possui autorização bancária válida. É necessário fazer OAuth2 para obter token real.", userId);
            return false;
            
        } catch (Exception e) {
            log.error("❌ Erro ao criar autorização temporária para usuário {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Testa o token real na API do Mercado Pago
     * 
     * @param auth Autorização bancária
     * @return true se o token é válido na API
     */
    private boolean testTokenWithAPI(AutorizacaoBancaria auth) {
        try {
            // Tokens temporários não são válidos para uso real
            if (auth.getAccessToken().startsWith("TEMPORARY_AUTH_")) {
                log.info("ℹ️ Token temporário detectado - não válido para uso real");
                return false;
            }
            
            log.info("🧪 Testando token na API do Mercado Pago...");
            
            // Fazer uma requisição simples para testar o token
            String testUrl = "https://api.mercadopago.com/v1/payments/search?limit=1";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(auth.getAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                testUrl,
                HttpMethod.GET,
                request,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Token válido na API do Mercado Pago");
                return true;
            } else {
                log.warn("⚠️ Token inválido na API: {}", response.getStatusCode());
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao testar token na API: {}", e.getMessage());
            return false;
        }
    }
}
