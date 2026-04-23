package com.consumoesperto.service;

import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Serviço para renovação automática de tokens do Mercado Pago
 * 
 * Este serviço implementa o fluxo OAuth2 de refresh token para manter
 * os tokens sempre válidos e evitar problemas de autenticação.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoTokenRefreshService {

    private final RestTemplate restTemplate;
    private final AutorizacaoBancariaRepository autorizacaoRepository;
    private final AutorizacaoBancariaService autorizacaoBancariaService;

    // URLs do Mercado Pago
    private static final String MERCADOPAGO_TOKEN_URL = "https://api.mercadopago.com/oauth/token";
    private static final String MERCADOPAGO_CLIENT_ID = "4223603750190943";
    private static final String MERCADOPAGO_CLIENT_SECRET = "YOUR_MERCADOPAGO_CLIENT_SECRET"; // TODO: Configurar via properties

    /**
     * Renova o token de acesso usando o refresh token
     * 
     * @param userId ID do usuário
     * @return true se a renovação foi bem-sucedida
     */
    public boolean renovarToken(Long userId) {
        try {
            log.info("🔄 Iniciando renovação de token do Mercado Pago para usuário: {}", userId);
            
            // Busca a autorização do Mercado Pago
            Optional<AutorizacaoBancaria> authOpt = autorizacaoRepository
                    .findByUsuarioIdAndTipoBanco(userId, "MERCADO_PAGO");
            
            if (authOpt.isEmpty()) {
                log.warn("⚠️ Nenhuma autorização do Mercado Pago encontrada para usuário: {}", userId);
                return false;
            }
            
            AutorizacaoBancaria auth = authOpt.get();
            
            if (auth.getRefreshToken() == null || auth.getRefreshToken().trim().isEmpty()) {
                log.warn("⚠️ Refresh token não encontrado para usuário: {}", userId);
                return false;
            }
            
            // Verifica se o token ainda é válido (não expirou)
            if (auth.getDataExpiracao() != null && auth.getDataExpiracao().isAfter(LocalDateTime.now())) {
                log.info("ℹ️ Token ainda válido para usuário: {}, expira em: {}", userId, auth.getDataExpiracao());
                return true;
            }
            
            // Renova o token
            Map<String, Object> newTokenResponse = refreshAccessToken(auth.getRefreshToken());
            
            if (newTokenResponse != null && newTokenResponse.containsKey("access_token")) {
                // Atualiza a autorização com o novo token
                atualizarAutorizacaoComNovoToken(auth, newTokenResponse);
                log.info("✅ Token renovado com sucesso para usuário: {}", userId);
                return true;
            } else {
                log.error("❌ Falha ao renovar token para usuário: {}", userId);
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao renovar token para usuário {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Renova o token de acesso usando o refresh token
     * 
     * @param refreshToken Refresh token atual
     * @return Resposta com novo access token
     */
    private Map<String, Object> refreshAccessToken(String refreshToken) {
        try {
            log.info("🔄 Fazendo requisição de refresh token para o Mercado Pago");
            
            // Configura headers
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(MERCADOPAGO_CLIENT_ID, MERCADOPAGO_CLIENT_SECRET);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            // Monta corpo da requisição
            String body = String.format("grant_type=refresh_token&refresh_token=%s", refreshToken);
            
            // Cria entidade HTTP
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            
            // Faz a requisição
            ResponseEntity<Map> response = restTemplate.exchange(
                    MERCADOPAGO_TOKEN_URL, 
                    HttpMethod.POST, 
                    request, 
                    Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("✅ Refresh token executado com sucesso");
                return response.getBody();
            } else {
                log.error("❌ Falha na resposta do refresh token: {}", response.getStatusCode());
                return null;
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao executar refresh token: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Atualiza a autorização com o novo token
     * 
     * @param auth Autorização atual
     * @param newTokenResponse Nova resposta com tokens
     */
    private void atualizarAutorizacaoComNovoToken(AutorizacaoBancaria auth, Map<String, Object> newTokenResponse) {
        try {
            // Atualiza access token
            String newAccessToken = newTokenResponse.get("access_token").toString();
            auth.setAccessToken(newAccessToken);
            
            // Atualiza refresh token se fornecido
            if (newTokenResponse.containsKey("refresh_token")) {
                String newRefreshToken = newTokenResponse.get("refresh_token").toString();
                auth.setRefreshToken(newRefreshToken);
            }
            
            // Atualiza data de expiração
            if (newTokenResponse.containsKey("expires_in")) {
                int expiresIn = Integer.parseInt(newTokenResponse.get("expires_in").toString());
                LocalDateTime newExpiration = LocalDateTime.now().plusSeconds(expiresIn);
                auth.setDataExpiracao(newExpiration);
            }
            
            // Atualiza timestamp
            auth.setDataAtualizacao(LocalDateTime.now());
            
            // Salva no banco
            autorizacaoRepository.save(auth);
            
            log.info("✅ Autorização atualizada com novo token");
            
        } catch (Exception e) {
            log.error("❌ Erro ao atualizar autorização com novo token: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao atualizar autorização", e);
        }
    }

    /**
     * Verifica se o token precisa ser renovado
     * 
     * @param userId ID do usuário
     * @return true se precisa renovar
     */
    public boolean tokenPrecisaRenovar(Long userId) {
        try {
            Optional<AutorizacaoBancaria> authOpt = autorizacaoRepository
                    .findByUsuarioIdAndTipoBanco(userId, "MERCADO_PAGO");
            
            if (authOpt.isEmpty()) {
                return false;
            }
            
            AutorizacaoBancaria auth = authOpt.get();
            
            // Se não tem data de expiração, assume que precisa renovar
            if (auth.getDataExpiracao() == null) {
                return true;
            }
            
            // Renova se expira em menos de 5 minutos
            LocalDateTime cincoMinutosAntes = LocalDateTime.now().plusMinutes(5);
            return auth.getDataExpiracao().isBefore(cincoMinutosAntes);
            
        } catch (Exception e) {
            log.error("❌ Erro ao verificar se token precisa renovar: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Renova token se necessário
     * 
     * @param userId ID do usuário
     * @return true se renovou ou não precisava renovar
     */
    public boolean renovarTokenSeNecessario(Long userId) {
        if (tokenPrecisaRenovar(userId)) {
            log.info("🔄 Token precisa ser renovado para usuário: {}", userId);
            return renovarToken(userId);
        } else {
            log.debug("ℹ️ Token ainda válido para usuário: {}", userId);
            return true;
        }
    }
}
