package com.consumoesperto.service;

import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.BankApiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Serviço para configuração automática do Mercado Pago
 * 
 * Este serviço configura automaticamente as credenciais do Mercado Pago
 * para todos os usuários, usando as credenciais padrão do banco de dados.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoAutoConfigService {

    private final BankApiConfigRepository bankApiConfigRepository;
    private final UsuarioRepository usuarioRepository;

    // Credenciais padrão do banco de dados
    private static final String DEFAULT_CLIENT_ID = "4223603750190943";
    private static final String DEFAULT_CLIENT_SECRET = "D3pZ1tvPtRXlo8m6QGXVmekh9jZsaxwP";
    private static final String DEFAULT_USER_ID = "209112973";

    /**
     * Configura automaticamente as credenciais do Mercado Pago para um usuário
     * 
     * @param userId ID do usuário
     * @return true se configurado com sucesso, false caso contrário
     */
    public boolean configurarCredenciaisAutomaticas(Long userId) {
        try {
            log.info("🔧 Configurando credenciais automáticas do Mercado Pago para usuário: {}", userId);
            
            // Verificar se usuário existe
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(userId);
            if (usuarioOpt.isEmpty()) {
                log.warn("❌ Usuário {} não encontrado", userId);
                return false;
            }
            
            Usuario usuario = usuarioOpt.get();
            
            // Verificar se já existe configuração
            Optional<BankApiConfig> existingConfig = bankApiConfigRepository
                .findByUsuarioIdAndTipoBanco(userId, "MERCADO_PAGO");
            
            BankApiConfig config;
            if (existingConfig.isPresent()) {
                // Atualizar configuração existente
                config = existingConfig.get();
                log.info("📝 Atualizando configuração existente para usuário: {}", userId);
            } else {
                // Criar nova configuração
                config = new BankApiConfig();
                config.setUsuario(usuario);
                config.setTipoBanco("MERCADO_PAGO");
                config.setNome("Mercado Pago - Auto Config");
                log.info("🆕 Criando nova configuração para usuário: {}", userId);
            }
            
            // Configurar credenciais padrão
            config.setClientId(DEFAULT_CLIENT_ID);
            config.setClientSecret(DEFAULT_CLIENT_SECRET);
            config.setApiUrl("https://api.mercadopago.com/v1");
            config.setAuthUrl("https://api.mercadopago.com/authorization");
            config.setTokenUrl("https://api.mercadopago.com/oauth/token");
            config.setScope("read,write");
            config.setSandbox(false); // Produção
            config.setAtivo(true);
            config.setDataCriacao(LocalDateTime.now());
            config.setDataAtualizacao(LocalDateTime.now());
            
            // Salvar configuração
            bankApiConfigRepository.save(config);
            
            log.info("✅ Credenciais automáticas configuradas com sucesso para usuário: {}", userId);
            log.info("🔑 Client ID: {}", DEFAULT_CLIENT_ID);
            log.info("🔐 Client Secret: {}...", DEFAULT_CLIENT_SECRET.substring(0, 8) + "***");
            log.info("👤 User ID: {}", DEFAULT_USER_ID);
            
            return true;
            
        } catch (Exception e) {
            log.error("❌ Erro ao configurar credenciais automáticas para usuário {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Configura credenciais automáticas para todos os usuários
     * 
     * @return Número de usuários configurados
     */
    public int configurarTodosUsuarios() {
        try {
            log.info("🔧 Configurando credenciais automáticas para todos os usuários...");
            
            int configurados = 0;
            var usuarios = usuarioRepository.findAll();
            
            for (Usuario usuario : usuarios) {
                if (configurarCredenciaisAutomaticas(usuario.getId())) {
                    configurados++;
                }
            }
            
            log.info("✅ Configuração automática concluída: {} usuários configurados", configurados);
            return configurados;
            
        } catch (Exception e) {
            log.error("❌ Erro ao configurar todos os usuários: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Verifica se um usuário tem configuração ativa
     * 
     * @param userId ID do usuário
     * @return true se tem configuração ativa, false caso contrário
     */
    public boolean temConfiguracaoAtiva(Long userId) {
        try {
            Optional<BankApiConfig> config = bankApiConfigRepository
                .findByUsuarioIdAndTipoBanco(userId, "MERCADO_PAGO");
            
            return config.isPresent() && config.get().getAtivo();
        } catch (Exception e) {
            log.error("❌ Erro ao verificar configuração para usuário {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Obtém as credenciais de um usuário
     * 
     * @param userId ID do usuário
     * @return Configuração do usuário ou null se não encontrada
     */
    public BankApiConfig obterConfiguracao(Long userId) {
        try {
            Optional<BankApiConfig> config = bankApiConfigRepository
                .findByUsuarioIdAndTipoBanco(userId, "MERCADO_PAGO");
            
            return config.orElse(null);
        } catch (Exception e) {
            log.error("❌ Erro ao obter configuração para usuário {}: {}", userId, e.getMessage());
            return null;
        }
    }
}
