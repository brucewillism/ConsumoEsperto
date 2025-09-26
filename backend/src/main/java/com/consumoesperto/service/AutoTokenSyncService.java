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
            verificarMercadoPago(usuario);
            
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
     * Verifica e sincroniza token do Mercado Pago
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
                log.info("ℹ️ Token do Mercado Pago expirado para usuário {}", usuario.getId());
                return;
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
}
