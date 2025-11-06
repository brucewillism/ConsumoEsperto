package com.consumoesperto.controller;

import com.consumoesperto.dto.MercadoPagoConfigDTO;
import com.consumoesperto.dto.MercadoPagoCartaoDTO;
import com.consumoesperto.dto.MercadoPagoFaturaDTO;
import com.consumoesperto.service.MercadoPagoService;
import com.consumoesperto.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.service.UsuarioService;

/**
 * Controller para integração com Mercado Pago
 * 
 * Este controller gerencia a integração com a API do Mercado Pago,
 * incluindo configuração de credenciais, busca de cartões e faturas.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/mercadopago")
@RequiredArgsConstructor
@Slf4j
<<<<<<< HEAD
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app", "https://*.ngrok-free.app"})
=======
@CrossOrigin(origins = {"http://localhost:4200", "https://85766d45517b.ngrok-free.app", "https://ngrok-free.app"})
>>>>>>> origin/main
public class MercadoPagoController {

    private final MercadoPagoService mercadoPagoService;
    private final SecurityService securityService;
    private final UsuarioService usuarioService;

    /**
     * Sincroniza dados do Mercado Pago (endpoint chamado pelo frontend)
     */
    @PostMapping("/sync-data")
    public ResponseEntity<Map<String, Object>> syncData() {
        try {
            log.info("🔄 Sincronização de dados Mercado Pago solicitada");
            
<<<<<<< HEAD
            // Buscar usuário autenticado atual
            Optional<Usuario> usuarioOpt = securityService.getCurrentUser();
            if (usuarioOpt.isEmpty()) {
                log.warn("❌ Usuário não autenticado");
                return ResponseEntity.status(401).body(Map.of(
                    "status", "error",
                    "message", "Usuário não autenticado"
                ));
            }
            
            Usuario usuario = usuarioOpt.get();
            Long usuarioId = usuario.getId();
=======
            // Buscar usuário atual (assumindo que está autenticado)
            Long usuarioId = 1L; // TODO: Pegar do contexto de segurança
>>>>>>> origin/main
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Sincronização iniciada",
                "usuarioId", usuarioId
            ));
        } catch (Exception e) {
            log.error("❌ Erro na sincronização: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Erro na sincronização: " + e.getMessage()
            ));
        }
    }

    /**
     * Configura credenciais do Mercado Pago para o usuário
     * 
     * @param configDTO Dados de configuração (access token)
     * @return Confirmação da configuração
     */
    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> configurarCredenciais(@RequestBody MercadoPagoConfigDTO configDTO) {
        try {
            log.info("🔄 Iniciando configuração de credenciais Mercado Pago");
            
            // Validação básica
            if (configDTO.getAccessToken() == null || configDTO.getAccessToken().trim().isEmpty()) {
                log.warn("❌ Token de acesso não fornecido");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Token de acesso é obrigatório");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            if (configDTO.getPublicKey() == null || configDTO.getPublicKey().trim().isEmpty()) {
                log.warn("❌ Public Key não fornecida");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Public Key é obrigatória");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            if (configDTO.getClientId() == null || configDTO.getClientId().trim().isEmpty()) {
                log.warn("❌ Client ID não fornecido");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Client ID é obrigatório");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            if (configDTO.getClientSecret() == null || configDTO.getClientSecret().trim().isEmpty()) {
                log.warn("❌ Client Secret não fornecido");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Client Secret é obrigatório");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Buscar usuário autenticado atual
            Optional<Usuario> usuarioOpt = securityService.getCurrentUser();
            if (usuarioOpt.isEmpty()) {
                log.warn("❌ Usuário não autenticado");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Usuário não autenticado");
                return ResponseEntity.status(401).body(errorResponse);
            }

            Usuario usuario = usuarioOpt.get();
            String accessToken = configDTO.getAccessToken().trim();
            String publicKey = configDTO.getPublicKey().trim();
            String clientId = configDTO.getClientId().trim();
            String clientSecret = configDTO.getClientSecret().trim();
            String userId = configDTO.getUserId() != null ? configDTO.getUserId().trim() : null;
            
            // Configurar credenciais
            mercadoPagoService.configurarCredenciais(usuario.getId(), accessToken, publicKey, clientId, clientSecret, userId);
            
            log.info("✅ Credenciais configuradas para usuário: {}", usuario.getEmail());
            
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("success", true);
            successResponse.put("message", "Credenciais do Mercado Pago configuradas com sucesso");
            successResponse.put("userId", usuario.getId());
            successResponse.put("email", usuario.getEmail());
            successResponse.put("configurado", true);
            successResponse.put("accessToken", accessToken.substring(0, 10) + "..."); // Mostrar apenas parte do token por segurança
            successResponse.put("publicKey", publicKey.substring(0, 10) + "...");   // Mostrar apenas parte da chave por segurança
            successResponse.put("clientId", clientId);
            
            return ResponseEntity.ok(successResponse);
            
        } catch (Exception e) {
            log.error("❌ Erro ao configurar credenciais: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Erro ao configurar credenciais: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Busca cartões de crédito do usuário no Mercado Pago
     * 
     * @return Lista de cartões de crédito
     */
    @GetMapping("/cartoes")
    public ResponseEntity<List<MercadoPagoCartaoDTO>> buscarCartoes() {
        try {
            log.info("💳 Buscando cartões de crédito no Mercado Pago");
            
            Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
            if (usuarioOpt.isEmpty()) {
                log.warn("❌ Usuário não autenticado");
                return ResponseEntity.status(401).build();
            }

            com.consumoesperto.model.Usuario usuario = usuarioOpt.get();
            List<MercadoPagoCartaoDTO> cartoes = mercadoPagoService.buscarCartoes(usuario.getId());
            
            log.info("✅ {} cartões encontrados para usuário: {}", cartoes.size(), usuario.getEmail());
            return ResponseEntity.ok(cartoes);
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar cartões: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Busca faturas dos cartões do usuário no Mercado Pago
     * 
     * @return Lista de faturas
     */
    @GetMapping("/faturas")
    public ResponseEntity<List<MercadoPagoFaturaDTO>> buscarFaturas() {
        try {
            log.info("📄 Buscando faturas no Mercado Pago");
            
            Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
            if (usuarioOpt.isEmpty()) {
                log.warn("❌ Usuário não autenticado");
                return ResponseEntity.status(401).build();
            }

            com.consumoesperto.model.Usuario usuario = usuarioOpt.get();
            List<MercadoPagoFaturaDTO> faturas = mercadoPagoService.buscarFaturas(usuario.getId());
            
            log.info("✅ {} faturas encontradas para usuário: {}", faturas.size(), usuario.getEmail());
            return ResponseEntity.ok(faturas);
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar faturas: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Testa a conexão com a API do Mercado Pago
     * 
     * @return Status da conexão
     */
    @GetMapping("/teste-conexao")
    public ResponseEntity<String> testarConexao() {
        try {
            log.info("🔍 Testando conexão com Mercado Pago");
            
            Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
            if (usuarioOpt.isEmpty()) {
                log.warn("❌ Usuário não autenticado");
                return ResponseEntity.status(401).build();
            }

            com.consumoesperto.model.Usuario usuario = usuarioOpt.get();
            boolean conectado = mercadoPagoService.testarConexao(usuario.getId());
            
            if (conectado) {
                log.info("✅ Conexão com Mercado Pago OK para usuário: {}", usuario.getEmail());
                return ResponseEntity.ok("Conexão OK");
            } else {
                log.warn("⚠️ Falha na conexão com Mercado Pago para usuário: {}", usuario.getEmail());
                return ResponseEntity.status(400).body("Falha na conexão");
            }
            
        } catch (Exception e) {
            log.error("❌ Erro ao testar conexão: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Erro ao testar conexão: " + e.getMessage());
        }
    }

    /**
     * Verifica se o usuário possui configuração ativa
     * 
     * @return Status da configuração
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> verificarStatus() {
        try {
            log.info("🔍 Verificando status da configuração do Mercado Pago");
            
            Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
            if (usuarioOpt.isEmpty()) {
                log.warn("❌ Usuário não autenticado");
                return ResponseEntity.status(401).build();
            }

            com.consumoesperto.model.Usuario usuario = usuarioOpt.get();
            boolean configurado = mercadoPagoService.possuiConfiguracaoAtiva(usuario.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("configurado", configurado);
            response.put("usuarioId", usuario.getId());
            response.put("timestamp", System.currentTimeMillis());
            
            log.info("✅ Status verificado para usuário {}: configurado={}", usuario.getEmail(), configurado);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Erro ao verificar status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("erro", "Falha ao verificar status"));
        }
    }
}
