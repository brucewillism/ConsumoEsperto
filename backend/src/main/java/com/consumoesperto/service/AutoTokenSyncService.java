package com.consumoesperto.service;

import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import com.consumoesperto.repository.BankApiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
<<<<<<< HEAD
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import java.util.Map;
=======
>>>>>>> origin/main

/**
 * Serviço para verificar e sincronizar automaticamente tokens de APIs bancárias
 * quando o usuário faz login
 */
@Service
public class AutoTokenSyncService {
    
    private static final Logger log = LoggerFactory.getLogger(AutoTokenSyncService.class);
    
    @Autowired
    private AutorizacaoBancariaRepository autorizacaoBancariaRepository;
    
    @Autowired
    private BankApiConfigRepository bankApiConfigRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private MercadoPagoBankService mercadoPagoBankService;
    
    @Autowired
    private MercadoPagoService mercadoPagoService;
    
<<<<<<< HEAD
    @Autowired
    private RealMercadoPagoTokenService realMercadoPagoTokenService;
    
=======
>>>>>>> origin/main
    /**
     * Verifica e sincroniza automaticamente tokens bancários para o usuário
     * ATIVADO: Sistema sincroniza automaticamente todos os dados do Mercado Pago
     * 
     * @param userId ID do usuário que fez login
     */
    public void verificarESincronizarTokens(Long userId) {
        log.info("🔄 Verificando tokens bancários para usuário: {}", userId);
        
        try {
            // Buscar usuário
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(userId);
            if (!usuarioOpt.isPresent()) {
                log.warn("⚠️ Usuário não encontrado: {}", userId);
                return;
            }
            
            Usuario usuario = usuarioOpt.get();
            
            // Verificar configuração do Mercado Pago
<<<<<<< HEAD
            verificarMercadoPagoSimplificado(usuario);
=======
            verificarMercadoPago(usuario);
>>>>>>> origin/main
            
            // Aqui você pode adicionar verificações para outros bancos
            // verificarNubank(usuario);
            // verificarItau(usuario);
            // verificarInter(usuario);
            
            log.info("✅ Verificação de tokens concluída para usuário: {}", userId);
            
        } catch (Exception e) {
            log.error("❌ Erro ao verificar tokens para usuário {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
<<<<<<< HEAD
     * Verifica e sincroniza token do Mercado Pago (versão simplificada)
     */
    private void verificarMercadoPagoSimplificado(Usuario usuario) {
        try {
            log.info("🔍 Verificando Mercado Pago para usuário: {}", usuario.getId());
            
        // Usar o novo serviço de validação e renovação automática
        boolean tokenValido = realMercadoPagoTokenService.validateAndRenewToken(usuario.getId());
            
            if (tokenValido) {
                log.info("✅ Token do Mercado Pago válido/renovado para usuário: {}", usuario.getId());
            } else {
                log.warn("⚠️ Não foi possível validar/renovar token para usuário: {}", usuario.getId());
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao verificar Mercado Pago para usuário {}: {}", usuario.getId(), e.getMessage());
        }
    }

    /**
     * Verifica e sincroniza token do Mercado Pago (método antigo - mantido para compatibilidade)
=======
     * Verifica e sincroniza token do Mercado Pago
>>>>>>> origin/main
     */
    private void verificarMercadoPago(Usuario usuario) {
        try {
            log.info("🔍 Verificando Mercado Pago para usuário: {}", usuario.getId());
            
            // 1. Verificar se existe configuração ativa do Mercado Pago
            Optional<BankApiConfig> configOpt = bankApiConfigRepository
                .findByUsuarioIdAndTipoBanco(usuario.getId(), "MERCADO_PAGO");
            
            if (!configOpt.isPresent()) {
                log.info("ℹ️ Usuário {} não possui configuração do Mercado Pago", usuario.getId());
                return;
            }
            
            BankApiConfig config = configOpt.get();
            if (!config.getAtivo()) {
                log.info("ℹ️ Configuração do Mercado Pago inativa para usuário {}", usuario.getId());
                return;
            }
            
            // 2. Verificar se existe autorização bancária com token
            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(usuario.getId(), "MERCADO_PAGO");
            
            if (!authOpt.isPresent()) {
                log.info("ℹ️ Usuário {} não possui autorização bancária do Mercado Pago", usuario.getId());
                return;
            }
            
            AutorizacaoBancaria auth = authOpt.get();
            
            // 3. Verificar se o token existe e não está expirado
            if (auth.getAccessToken() == null || auth.getAccessToken().trim().isEmpty()) {
                log.info("ℹ️ Token do Mercado Pago vazio para usuário {}", usuario.getId());
                return;
            }
            
            if (auth.getDataExpiracao() != null && auth.getDataExpiracao().isBefore(LocalDateTime.now())) {
<<<<<<< HEAD
                log.info("⚠️ Token do Mercado Pago expirado para usuário {} - RENOVANDO AUTOMATICAMENTE", usuario.getId());
                
                // RENOVAR TOKEN AUTOMATICAMENTE
                try {
                    boolean renovado = renovarTokenMercadoPago(usuario.getId(), config);
                    if (renovado) {
                        log.info("✅ Token renovado com sucesso para usuário {}", usuario.getId());
                        // Após renovar, buscar nova autorização e continuar
                        authOpt = autorizacaoBancariaRepository
                            .findByUsuarioIdAndTipoBanco(usuario.getId(), "MERCADO_PAGO");
                        if (authOpt.isPresent()) {
                            auth = authOpt.get();
                        } else {
                            log.error("❌ Não foi possível buscar a nova autorização após renovação");
                            return;
                        }
                    } else {
                        log.error("❌ Falha ao renovar token para usuário {}", usuario.getId());
                        return;
                    }
                } catch (Exception e) {
                    log.error("❌ Erro ao renovar token para usuário {}: {}", usuario.getId(), e.getMessage(), e);
                    return;
                }
=======
                log.info("ℹ️ Token do Mercado Pago expirado para usuário {}", usuario.getId());
                return;
>>>>>>> origin/main
            }
            
            // 4. Token válido encontrado - testar conectividade
            log.info("✅ Token do Mercado Pago válido encontrado para usuário {}", usuario.getId());
            
            // 5. Sincronizar dados reais automaticamente
            log.info("🚀 Iniciando sincronização automática de dados reais do Mercado Pago...");
            try {
                mercadoPagoService.sincronizarDadosAutomaticamente(usuario.getId());
                log.info("✅ Sincronização automática concluída para usuário: {}", usuario.getId());
            } catch (Exception e) {
                log.error("❌ Erro na sincronização automática para usuário {}: {}", usuario.getId(), e.getMessage(), e);
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao verificar Mercado Pago para usuário {}: {}", usuario.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Força a sincronização de dados do Mercado Pago se o token estiver válido
     * 
     * @param userId ID do usuário
     * @return true se a sincronização foi executada, false caso contrário
     */
    public boolean sincronizarMercadoPagoSeValido(Long userId) {
        try {
            log.info("🔄 Verificando se deve sincronizar Mercado Pago para usuário: {}", userId);
            
            // Verificar se tem configuração ativa
            Optional<BankApiConfig> configOpt = bankApiConfigRepository
                .findByUsuarioIdAndTipoBanco(userId, "MERCADO_PAGO");
            
            if (!configOpt.isPresent() || !configOpt.get().getAtivo()) {
                log.info("ℹ️ Configuração do Mercado Pago inativa para usuário {}", userId);
                return false;
            }
            
            // Verificar se tem token válido
            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(userId, "MERCADO_PAGO");
            
            if (!authOpt.isPresent() || authOpt.get().getAccessToken() == null || 
                authOpt.get().getAccessToken().trim().isEmpty()) {
                log.info("ℹ️ Token do Mercado Pago não encontrado para usuário {}", userId);
                return false;
            }
            
            // Executar sincronização REAL
            log.info("🚀 Executando sincronização REAL do Mercado Pago para usuário: {}", userId);
            
            // Chamar o serviço de sincronização real
            try {
                log.info("✅ Sincronização REAL iniciada para usuário: {}", userId);
                
                // Sincronizar dados reais do Mercado Pago
                mercadoPagoService.sincronizarDadosAutomaticamente(userId);
                
                log.info("✅ Sincronização REAL concluída para usuário: {}", userId);
                return true;
                
            } catch (Exception e) {
                log.error("❌ Erro na sincronização REAL: {}", e.getMessage(), e);
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao sincronizar Mercado Pago para usuário {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }
<<<<<<< HEAD
    
    /**
     * Renova automaticamente o token do Mercado Pago usando OAuth2
     * 
     * @param userId ID do usuário
     * @param config Configuração do Mercado Pago
     * @return true se renovado com sucesso, false caso contrário
     */
    private boolean renovarTokenMercadoPago(Long userId, BankApiConfig config) {
        try {
            log.info("🔄 RENOVANDO TOKEN DO MERCADO PAGO para usuário: {}", userId);
            
            // Gerar novo token usando OAuth2
            String tokenUrl = "https://api.mercadopago.com/oauth/token";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded");
            
            String body = String.format(
                "grant_type=client_credentials&client_id=%s&client_secret=%s",
                config.getClientId(),
                config.getClientSecret()
            );
            
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.exchange(
                tokenUrl, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> tokenData = response.getBody();
                String newAccessToken = (String) tokenData.get("access_token");
                String tokenType = (String) tokenData.get("token_type");
                Integer expiresIn = (Integer) tokenData.get("expires_in");
                
                log.info("✅ NOVO TOKEN GERADO COM SUCESSO!");
                log.info("   - Access Token: {}...", newAccessToken.substring(0, Math.min(20, newAccessToken.length())));
                log.info("   - Token Type: {}", tokenType);
                log.info("   - Expires In: {} segundos", expiresIn);
                
                // Atualizar no banco de dados
                Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaRepository
                    .findByUsuarioIdAndTipoBanco(userId, "MERCADO_PAGO");
                
                if (authOpt.isPresent()) {
                    AutorizacaoBancaria auth = authOpt.get();
                    auth.setAccessToken(newAccessToken);
                    auth.setTokenType(tokenType);
                    auth.setDataExpiracao(LocalDateTime.now().plusSeconds(expiresIn));
                    auth.setDataAtualizacao(LocalDateTime.now());
                    autorizacaoBancariaRepository.save(auth);
                    
                    log.info("💾 Token atualizado no banco de dados");
                    return true;
                } else {
                    log.error("❌ Autorização bancária não encontrada para atualização");
                    return false;
                }
                
            } else {
                log.error("❌ Erro ao gerar novo token: {}", response.getStatusCode());
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Erro durante renovação automática: {}", e.getMessage(), e);
            // Não falha a aplicação, apenas loga o erro
            // O usuário pode continuar usando o sistema mesmo sem renovação automática
            return false;
        }
    }
=======
>>>>>>> origin/main
}
