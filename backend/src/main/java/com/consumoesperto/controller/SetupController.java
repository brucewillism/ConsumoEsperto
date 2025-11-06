package com.consumoesperto.controller;

import com.consumoesperto.model.AutorizacaoBancaria;
import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.AutorizacaoBancariaRepository;
import com.consumoesperto.repository.BankApiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.security.UserPrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
@Slf4j
public class SetupController {

    private final AutorizacaoBancariaRepository autorizacaoBancariaRepository;
    private final BankApiConfigRepository bankApiConfigRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * Configura dados iniciais do Mercado Pago para o usuário
     */
    @PostMapping("/mercadopago")
    public ResponseEntity<Map<String, Object>> setupMercadoPago(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            log.info("🔧 Configurando dados iniciais do Mercado Pago para usuário: {}", currentUser.getId());
            
            // 1. Verificar se usuário existe
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(currentUser.getId());
            if (usuarioOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Usuário não encontrado",
                    "success", false
                ));
            }
            
            Usuario usuario = usuarioOpt.get();
            
            // 2. Configurar BankApiConfig
            Optional<BankApiConfig> configOpt = bankApiConfigRepository.findByUsuarioIdAndBanco(
                currentUser.getId(), "MERCADOPAGO");
            
            if (configOpt.isEmpty()) {
                BankApiConfig config = new BankApiConfig();
                config.setUsuario(usuario);
                config.setTipoBanco("MERCADOPAGO");
                config.setNome("Mercado Pago");
                config.setClientId("4223603750190943");
                config.setClientSecret("CONFIGURAR_MERCADOPAGO_CLIENT_SECRET");
                config.setApiUrl("https://api.mercadopago.com/v1");
<<<<<<< HEAD
                config.setRedirectUri("https://0d723f1e294f.ngrok-free.app/api/bank/oauth/mercadopago/callback");
=======
                config.setRedirectUri("https://85766d45517b.ngrok-free.app/api/auth/mercadopago/callback");
>>>>>>> origin/main
                config.setScope("read write");
                config.setAtivo(true);
                config.setDataCriacao(LocalDateTime.now());
                config.setDataAtualizacao(LocalDateTime.now());
                bankApiConfigRepository.save(config);
                log.info("✅ Configuração da API criada");
            } else {
                log.info("✅ Configuração da API já existe");
            }
            
            // 3. Configurar AutorizacaoBancaria
            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(currentUser.getId(), "MERCADO_PAGO");
            
            if (authOpt.isEmpty()) {
                AutorizacaoBancaria auth = new AutorizacaoBancaria();
                auth.setUsuario(usuario);
                auth.setTipoBanco("MERCADO_PAGO");
                auth.setBanco("Mercado Pago");
                auth.setTipoConta("CONTA_CORRENTE");
<<<<<<< HEAD
                auth.setAccessToken("REAL_TOKEN_PLACEHOLDER");
                auth.setRefreshToken("REAL_REFRESH_TOKEN_PLACEHOLDER");
=======
                auth.setAccessToken("APP_USR_4223603750190943-091315-1234567890abcdef1234567890abcdef-123456789");
                auth.setRefreshToken("TG-1234567890abcdef1234567890abcdef-123456789");
>>>>>>> origin/main
                auth.setTokenType("Bearer");
                auth.setScope("read write");
                auth.setDataExpiracao(LocalDateTime.now().plusHours(6));
                auth.setAtivo(true);
                auth.setDataCriacao(LocalDateTime.now());
                auth.setDataAtualizacao(LocalDateTime.now());
                autorizacaoBancariaRepository.save(auth);
                log.info("✅ Autorização bancária criada");
            } else {
                log.info("✅ Autorização bancária já existe");
            }
            
            Map<String, Object> resultado = new HashMap<>();
            resultado.put("success", true);
            resultado.put("message", "Dados iniciais do Mercado Pago configurados com sucesso!");
            resultado.put("timestamp", LocalDateTime.now());
            
            log.info("🎉 Setup do Mercado Pago concluído para usuário: {}", currentUser.getId());
            
            return ResponseEntity.ok(resultado);
            
        } catch (Exception e) {
            log.error("❌ Erro ao configurar dados iniciais: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", "Falha na configuração: " + e.getMessage(),
                "success", false
            ));
        }
    }
    
    /**
     * Verifica status da configuração
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        try {
            Map<String, Object> status = new HashMap<>();
            
            // Verificar configuração da API
            Optional<BankApiConfig> configOpt = bankApiConfigRepository.findByUsuarioIdAndBanco(
                currentUser.getId(), "MERCADOPAGO");
            status.put("hasApiConfig", configOpt.isPresent());
            
            // Verificar autorização bancária
            Optional<AutorizacaoBancaria> authOpt = autorizacaoBancariaRepository
                .findByUsuarioIdAndTipoBanco(currentUser.getId(), "MERCADO_PAGO");
            status.put("hasAuth", authOpt.isPresent());
            
            if (authOpt.isPresent()) {
                AutorizacaoBancaria auth = authOpt.get();
                status.put("isTokenExpired", auth.getDataExpiracao().isBefore(LocalDateTime.now()));
                status.put("isActive", auth.getAtivo());
            }
            
            status.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("❌ Erro ao verificar status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("erro", e.getMessage()));
        }
    }
}
