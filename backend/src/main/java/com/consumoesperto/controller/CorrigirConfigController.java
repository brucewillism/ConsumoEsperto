package com.consumoesperto.controller;

import com.consumoesperto.model.BankApiConfig;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.BankApiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/corrigir")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"})
public class CorrigirConfigController {

    private final BankApiConfigRepository bankApiConfigRepository;
    private final UsuarioRepository usuarioRepository;

    @PostMapping("/config-mercadopago")
    public ResponseEntity<Map<String, Object>> corrigirConfigMercadoPago() {
        log.info("🔧 CORRIGINDO CONFIGURAÇÃO DO MERCADO PAGO PARA USUÁRIO 1");
        
        try {
            // 1. Buscar usuário ID 1
            Usuario usuario = usuarioRepository.findById(1L).orElse(null);
            if (usuario == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Usuário ID 1 não encontrado"));
            }
            
            log.info("✅ Usuário encontrado: {}", usuario.getEmail());
            
            // 2. Buscar configuração existente do Mercado Pago
            var configsExistentes = bankApiConfigRepository.findAll();
            log.info("📋 Configurações existentes: {}", configsExistentes.size());
            
            BankApiConfig configMercadoPago = null;
            for (BankApiConfig config : configsExistentes) {
                log.info("   - Config ID: {}, Tipo: {}, Usuário: {}", 
                    config.getId(), config.getTipoBanco(), 
                    config.getUsuario() != null ? config.getUsuario().getId() : "NULL");
                
                if ("MERCADO_PAGO".equals(config.getTipoBanco())) {
                    configMercadoPago = config;
                    break;
                }
            }
            
            if (configMercadoPago == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Configuração do Mercado Pago não encontrada"));
            }
            
            log.info("✅ Configuração do Mercado Pago encontrada: ID {}", configMercadoPago.getId());
            
            // 3. Associar ao usuário ID 1
            configMercadoPago.setUsuario(usuario);
            configMercadoPago.setAtivo(true);
            
            BankApiConfig configSalva = bankApiConfigRepository.save(configMercadoPago);
            
            log.info("✅ Configuração corrigida e salva!");
            log.info("   - ID: {}", configSalva.getId());
            log.info("   - Usuário: {}", configSalva.getUsuario().getId());
            log.info("   - Ativo: {}", configSalva.getAtivo());
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Configuração do Mercado Pago corrigida com sucesso!",
                "configId", configSalva.getId(),
                "usuarioId", configSalva.getUsuario().getId(),
                "ativo", configSalva.getAtivo()
            ));
            
        } catch (Exception e) {
            log.error("❌ Erro ao corrigir configuração: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Erro interno: " + e.getMessage()));
        }
    }
}
