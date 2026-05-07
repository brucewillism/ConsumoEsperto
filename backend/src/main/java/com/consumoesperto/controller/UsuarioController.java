package com.consumoesperto.controller;

import com.consumoesperto.dto.PerfilJarvisRequest;
import com.consumoesperto.dto.PreferenciaTratamentoRequest;
import com.consumoesperto.dto.UsuarioDTO;
import com.consumoesperto.service.JarvisProtocolService;
import com.consumoesperto.service.UsuarioService;
import com.consumoesperto.service.WhatsAppUserMappingService;
import com.consumoesperto.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
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
    private final JarvisProtocolService jarvisProtocolService;

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

            return ResponseEntity.ok(mapPerfilDto(usuario));
            
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

            com.consumoesperto.model.Usuario usuarioAtualizado = usuarioService.atualizarNomePerfil(usuario.getId(), usuarioDTO.getNome());

            log.info("✅ Perfil atualizado para usuário: {}", usuario.getEmail());

            return ResponseEntity.ok(mapPerfilDto(usuarioAtualizado));
            
        } catch (Exception e) {
            log.error("❌ Erro ao atualizar perfil: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PatchMapping("/preferencia-tratamento")
    public ResponseEntity<UsuarioDTO> atualizarPreferenciaTratamento(@Valid @RequestBody PreferenciaTratamentoRequest body) {
        try {
            Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
            if (!usuarioOpt.isPresent()) {
                return ResponseEntity.status(401).build();
            }
            com.consumoesperto.model.Usuario atualizado = usuarioService.atualizarPreferenciaTratamentoJarvis(
                usuarioOpt.get().getId(),
                body.getPreferenciaTratamento()
            );
            log.info("Preferência de tratamento J.A.R.V.I.S. atualizada para usuário {}", atualizado.getId());
            return ResponseEntity.ok(mapPerfilDto(atualizado));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao atualizar preferência de tratamento: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Calibragem J.A.R.V.I.S.: persiste título (Senhor, Doutora, NENHUM, etc.) e marca {@code jarvis_configurado = true}.
     */
    @PatchMapping("/perfil-jarvis")
    public ResponseEntity<UsuarioDTO> atualizarPerfilJarvis(@Valid @RequestBody PerfilJarvisRequest body) {
        try {
            Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
            if (!usuarioOpt.isPresent()) {
                return ResponseEntity.status(401).build();
            }
            com.consumoesperto.model.Usuario atualizado = usuarioService.atualizarPerfilJarvis(
                usuarioOpt.get().getId(),
                body.getTratamento());
            log.info("Perfil J.A.R.V.I.S. atualizado para usuário {}", atualizado.getId());
            return ResponseEntity.ok(mapPerfilDto(atualizado));
        } catch (IllegalArgumentException e) {
            log.warn("PATCH perfil-jarvis inválido: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao atualizar perfil J.A.R.V.I.S.: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private UsuarioDTO mapPerfilDto(com.consumoesperto.model.Usuario usuario) {
        UsuarioDTO usuarioDTO = new UsuarioDTO();
        usuarioDTO.setId(usuario.getId());
        usuarioDTO.setUsername(usuario.getUsername());
        usuarioDTO.setEmail(usuario.getEmail());
        usuarioDTO.setNome(usuario.getNome());
        usuarioDTO.setFotoUrl(usuario.getFotoUrl());
        usuarioDTO.setWhatsappNumero(usuario.getWhatsappNumero());
        usuarioDTO.setDataCriacao(usuario.getDataCriacao());
        usuarioDTO.setUltimoAcesso(usuario.getUltimoAcesso());
        com.consumoesperto.model.Usuario.PreferenciaTratamentoJarvis p = usuario.getPreferenciaTratamentoJarvis() != null
            ? usuario.getPreferenciaTratamentoJarvis()
            : com.consumoesperto.model.Usuario.PreferenciaTratamentoJarvis.AUTOMATICO;
        usuarioDTO.setPreferenciaTratamentoJarvis(p.name());
        usuarioDTO.setJarvisTratamentoResumo(jarvisProtocolService.montarVocativoCompleto(usuario));
        usuarioDTO.setGenero(usuario.getGenero() != null ? usuario.getGenero().name() : com.consumoesperto.model.Usuario.GeneroUsuario.UNKNOWN.name());
        usuarioDTO.setGeneroConfirmado(usuario.getGeneroConfirmado());
        usuarioDTO.setTratamento(usuario.getTratamento());
        // Evita null no JSON (coluna legada nullable / JDBC) — o front trata só === true como calibrado.
        usuarioDTO.setJarvisConfigurado(Boolean.TRUE.equals(usuario.getJarvisConfigurado()));
        return usuarioDTO;
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
