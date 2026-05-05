package com.consumoesperto.controller;

import com.consumoesperto.dto.UsuarioDTO;
import com.consumoesperto.service.UsuarioService;
import com.consumoesperto.service.WhatsAppUserMappingService;
import com.consumoesperto.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Controller para operações relacionadas a usuários
 * 
 * Este controller gerencia operações de usuários como:
 * - Buscar perfil do usuário autenticado
 * - Atualizar dados do usuário
 * - Operações administrativas (para administradores)
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(originPatterns = {"http://localhost:4200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
@RequiredArgsConstructor
@Slf4j
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final SecurityService securityService;
    private final WhatsAppUserMappingService whatsAppUserMappingService;

    /**
     * Busca o perfil do usuário autenticado
     * 
     * @return Dados do usuário autenticado
     */
    @GetMapping("/perfil")
    public ResponseEntity<UsuarioDTO> getPerfil() {
        try {
            log.info("🔍 Buscando perfil do usuário autenticado");
            
            Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
            if (!usuarioOpt.isPresent()) {
                log.warn("❌ Usuário não autenticado");
                return ResponseEntity.status(401).build();
            }

            com.consumoesperto.model.Usuario usuario = usuarioOpt.get();
            log.info("✅ Perfil encontrado para usuário: {}", usuario.getEmail());
            
            // Converte para DTO
            UsuarioDTO usuarioDTO = new UsuarioDTO();
            usuarioDTO.setId(usuario.getId());
            usuarioDTO.setUsername(usuario.getUsername());
            usuarioDTO.setEmail(usuario.getEmail());
            usuarioDTO.setNome(usuario.getNome());
            usuarioDTO.setFotoUrl(usuario.getFotoUrl()); // Adiciona foto
            usuarioDTO.setWhatsappNumero(usuario.getWhatsappNumero());
            usuarioDTO.setDataCriacao(usuario.getDataCriacao());
            
            return ResponseEntity.ok(usuarioDTO);
            
        } catch (Exception e) {
            log.error("❌ Erro ao buscar perfil: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Atualiza dados do usuário autenticado
     * 
     * @param usuarioDTO Dados atualizados do usuário
     * @return Usuário atualizado
     */
    @PutMapping("/perfil")
    public ResponseEntity<UsuarioDTO> atualizarPerfil(@RequestBody UsuarioDTO usuarioDTO) {
        try {
            log.info("🔄 Atualizando perfil do usuário autenticado");
            
            Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
            if (!usuarioOpt.isPresent()) {
                log.warn("❌ Usuário não autenticado");
                return ResponseEntity.status(401).build();
            }

            // Atualiza apenas campos permitidos
            com.consumoesperto.model.Usuario usuario = usuarioOpt.get();
            if (usuarioDTO.getNome() != null) {
                usuario.setNome(usuarioDTO.getNome());
            }
            
            // Salva no banco
            com.consumoesperto.model.Usuario usuarioAtualizado = usuarioService.findById(usuario.getId());
            usuarioAtualizado.setNome(usuario.getNome());
            
            log.info("✅ Perfil atualizado para usuário: {}", usuario.getEmail());
            
            // Converte para DTO
            UsuarioDTO responseDTO = new UsuarioDTO();
            responseDTO.setId(usuarioAtualizado.getId());
            responseDTO.setUsername(usuarioAtualizado.getUsername());
            responseDTO.setEmail(usuarioAtualizado.getEmail());
            responseDTO.setNome(usuarioAtualizado.getNome());
            responseDTO.setWhatsappNumero(usuarioAtualizado.getWhatsappNumero());
            responseDTO.setDataCriacao(usuarioAtualizado.getDataCriacao());
            
            return ResponseEntity.ok(responseDTO);
            
        } catch (Exception e) {
            log.error("❌ Erro ao atualizar perfil: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/whatsapp/vincular")
    public ResponseEntity<Map<String, Object>> vincularWhatsapp(@RequestBody Map<String, String> payload) {
        try {
            Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
            if (!usuarioOpt.isPresent()) {
                return ResponseEntity.status(401).body(Map.of(
                    "status", "error",
                    "message", "Usuario nao autenticado"
                ));
            }

            String numero = payload.getOrDefault("numero", "");
            com.consumoesperto.model.Usuario usuario = usuarioOpt.get();
            com.consumoesperto.model.Usuario atualizado = whatsAppUserMappingService.linkWhatsAppNumber(usuario.getId(), numero);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Numero de WhatsApp vinculado com sucesso",
                "usuarioId", atualizado.getId(),
                "whatsappNumero", atualizado.getWhatsappNumero()
            ));
        } catch (Exception e) {
            log.error("Erro ao vincular WhatsApp: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/whatsapp/desvincular")
    public ResponseEntity<Map<String, Object>> desvincularWhatsapp() {
        try {
            Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
            if (!usuarioOpt.isPresent()) {
                return ResponseEntity.status(401).body(Map.of(
                    "status", "error",
                    "message", "Usuario nao autenticado"
                ));
            }

            com.consumoesperto.model.Usuario usuario = usuarioOpt.get();
            whatsAppUserMappingService.unlinkWhatsAppNumber(usuario.getId());

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Numero de WhatsApp desvinculado com sucesso"
            ));
        } catch (Exception e) {
            log.error("Erro ao desvincular WhatsApp: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/alterar-senha")
    public ResponseEntity<Map<String, String>> alterarSenha(@RequestBody Map<String, String> payload) {
        try {
            Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
            if (!usuarioOpt.isPresent()) {
                return ResponseEntity.status(401).body(Map.of("message", "Usuario nao autenticado"));
            }

            String senhaAtual = payload.getOrDefault("senhaAtual", "");
            String novaSenha = payload.getOrDefault("novaSenha", "");
            usuarioService.alterarSenha(usuarioOpt.get().getId(), senhaAtual, novaSenha);
            return ResponseEntity.ok(Map.of("message", "Senha alterada com sucesso"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/conta")
    public ResponseEntity<Map<String, String>> deletarConta() {
        Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
        if (!usuarioOpt.isPresent()) {
            return ResponseEntity.status(401).body(Map.of("message", "Usuario nao autenticado"));
        }
        usuarioService.excluirUsuario(usuarioOpt.get().getId());
        return ResponseEntity.ok(Map.of("message", "Conta excluida com sucesso"));
    }
}
