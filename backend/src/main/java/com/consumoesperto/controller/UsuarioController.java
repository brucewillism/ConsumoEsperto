package com.consumoesperto.controller;

import com.consumoesperto.dto.EvolutionPairingOutcomeDTO;
import com.consumoesperto.dto.PerfilJarvisRequest;
import com.consumoesperto.dto.PreferenciaTratamentoRequest;
import com.consumoesperto.dto.UsuarioDTO;
import com.consumoesperto.service.EvolutionInstanceLifecycleService;
import com.consumoesperto.service.EvolutionInstanceSettingsService;
import com.consumoesperto.service.EvolutionPairingService;
import com.consumoesperto.service.JarvisProtocolService;
import com.consumoesperto.service.UsuarioService;
import com.consumoesperto.service.WhatsAppUserMappingService;
import com.consumoesperto.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.LinkedHashMap;
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
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
@RequiredArgsConstructor
@Slf4j
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final SecurityService securityService;
    private final WhatsAppUserMappingService whatsAppUserMappingService;
    private final JarvisProtocolService jarvisProtocolService;
    private final EvolutionPairingService evolutionPairingService;
    private final EvolutionInstanceLifecycleService evolutionInstanceLifecycleService;
    private final EvolutionInstanceSettingsService evolutionInstanceSettingsService;

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

            EvolutionInstanceLifecycleService.PrepareInstanceResult prep =
                evolutionInstanceLifecycleService.prepareInstanceForPairing(usuario.getId());
            evolutionInstanceLifecycleService.resetSessionBeforePairing(
                usuario.getId(), prep.skipLogoutBeforeConnect());

            evolutionPairingService.invalidatePairingCredCache(usuario.getId());
            EvolutionPairingOutcomeDTO pairing = evolutionPairingService.invokeInstanceConnect(usuario.getId());
            boolean waConnected = evolutionPairingService.isInstanceConnectedForUser(usuario.getId());

            boolean temQrOuCodigo = (pairing.getQrCodeDataUri() != null && !pairing.getQrCodeDataUri().isBlank())
                || (pairing.getPairingCode() != null && !pairing.getPairingCode().isBlank());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "success");
            body.put("evolutionWaConnected", waConnected);
            String message;
            if (waConnected) {
                message = "Número vinculado. A sessão WhatsApp está ligada à Evolution nesta instância.";
            } else if (temQrOuCodigo) {
                message = "Número gravado na app. Escaneie o QR (ou use o código de pareamento) para ligar o WhatsApp — "
                    + "a sessão Evolution não está activa.";
            } else {
                message = "Número gravado na app. O WhatsApp não está ligado à Evolution — use Desvincular e vincule de novo "
                    + "ou abra o Manager da Evolution para o QR.";
            }
            body.put("message", message);
            body.put("usuarioId", atualizado.getId());
            body.put("whatsappNumero", atualizado.getWhatsappNumero());
            aplicarCamposEvolutionPairing(body, pairing, waConnected);
            prep.getSetupWarning().ifPresent(w -> appendEvolutionSetupWarning(body, w));

            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("Erro ao vincular WhatsApp: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Novo GET do QR/pairing sem gravar número (permite polling no modal após vínculo).
     */
    @PostMapping("/whatsapp/evolution-pairing-refresh")
    public ResponseEntity<Map<String, Object>> evolutionPairingRefresh() {
        try {
            Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
            if (!usuarioOpt.isPresent()) {
                return ResponseEntity.status(401).body(Map.of(
                    "status", "error",
                    "message", "Usuario nao autenticado"
                ));
            }
            Long uid = usuarioOpt.get().getId();
            // Sem logout aqui: o modal faz polling a cada 5 s e logout repetido impede o QR de aparecer.
            EvolutionInstanceLifecycleService.PrepareInstanceResult prep =
                evolutionInstanceLifecycleService.prepareInstanceForPairing(uid);
            evolutionPairingService.invalidatePairingCredCache(uid);
            EvolutionPairingOutcomeDTO pairing = evolutionPairingService.invokeInstanceConnect(uid);
            boolean waConnected = evolutionPairingService.isInstanceConnectedForUser(uid);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "success");
            body.put("evolutionWaConnected", waConnected);
            aplicarCamposEvolutionPairing(body, pairing, waConnected);
            prep.getSetupWarning().ifPresent(w -> appendEvolutionSetupWarning(body, w));
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("Evolution pairing refresh: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    private static void appendEvolutionSetupWarning(Map<String, Object> body, String setupWarning) {
        if (setupWarning == null || setupWarning.isBlank()) {
            return;
        }
        Object cur = body.get("evolutionWarning");
        String merged = cur instanceof String && !((String) cur).isBlank()
            ? ((String) cur) + " " + setupWarning
            : setupWarning;
        body.put("evolutionWarning", merged);
    }

    private static void aplicarCamposEvolutionPairing(
        Map<String, Object> body, EvolutionPairingOutcomeDTO pairing, boolean waConnected
    ) {
        body.put("evolutionInstanceName", pairing.getResolvedInstanceName());
        body.put("evolutionAlreadyConnected", waConnected);
        body.put("evolutionHasAlternativePairingHints", pairing.isHasAlternativePairingHints());
        if (pairing.getQrCodeDataUri() != null && !pairing.getQrCodeDataUri().isBlank()) {
            body.put("evolutionQrCodeDataUri", pairing.getQrCodeDataUri());
        }
        if (pairing.getPairingCode() != null && !pairing.getPairingCode().isBlank()) {
            body.put("evolutionPairingCode", pairing.getPairingCode());
        }
        if (pairing.getEvolutionWarning() != null && !pairing.getEvolutionWarning().isBlank()) {
            body.put("evolutionWarning", pairing.getEvolutionWarning());
        }
    }

    /**
     * Polling pelo frontend durante o pareamento: consulta Evolution {@code /instance/connectionState/:instance}.
     */
    /**
     * Reaplica modo fantasma na instância Evolution do utilizador (readMessages/alwaysOnline/sync off).
     * Útil quando notificações no telemóvel sumiram ou o perfil fica sempre online.
     */
    @PostMapping("/whatsapp/evolution-privacy-settings")
    public ResponseEntity<Map<String, Object>> applyEvolutionPrivacySettings() {
        Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
        if (!usuarioOpt.isPresent()) {
            return ResponseEntity.status(401).body(Map.of(
                "status", "error",
                "message", "Usuario nao autenticado"
            ));
        }
        Long usuarioId = usuarioOpt.get().getId();
        evolutionInstanceLifecycleService.prepareInstanceForPairing(usuarioId);
        String instance = evolutionPairingService.resolvedInstanceDisplayName(usuarioId);
        Map<String, Object> body = evolutionInstanceSettingsService.applyGhostPrivacySettingsDetailed(instance);
        String st = String.valueOf(body.getOrDefault("status", "error"));
        if ("error".equals(st)) {
            return ResponseEntity.badRequest().body(body);
        }
        return ResponseEntity.ok(body);
    }

    /**
     * Correcção em todas as instâncias conhecidas (Evolution + BD). Operação de manutenção.
     */
    @PostMapping("/whatsapp/evolution-privacy-settings-all")
    public ResponseEntity<Map<String, Object>> applyEvolutionPrivacySettingsAll() {
        Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
        if (!usuarioOpt.isPresent()) {
            return ResponseEntity.status(401).body(Map.of(
                "status", "error",
                "message", "Usuario nao autenticado"
            ));
        }
        Map<String, Object> report = evolutionInstanceSettingsService.applyGhostPrivacyToAllKnownInstances();
        report.put(
            "message",
            "Correcção aplicada. Confira settingsVerified em cada instância. Se o telemóvel ainda não notificar, desvincule e escaneie o QR de novo."
        );
        return ResponseEntity.ok(report);
    }

    @GetMapping("/whatsapp/evolution-connection-status")
    public ResponseEntity<Map<String, Object>> evolutionWhatsAppConnectionStatus() {
        Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
        if (!usuarioOpt.isPresent()) {
            return ResponseEntity.status(401).body(Map.of(
                "status", "error",
                "message", "Usuario nao autenticado"
            ));
        }
        Long usuarioId = usuarioOpt.get().getId();
        evolutionPairingService.invalidatePairingCredCache(usuarioId);
        boolean connected = evolutionPairingService.isInstanceConnectedForUser(usuarioId);
        String numero = usuarioOpt.get().getWhatsappNumero();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connected", connected);
        body.put("evolutionWaConnected", connected);
        body.put("numeroCadastrado", numero != null && !numero.isBlank());
        body.put("whatsappNumero", numero != null ? numero : "");
        body.put("instanceName", evolutionPairingService.resolvedInstanceDisplayName(usuarioId));
        return ResponseEntity.ok(body);
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
            evolutionInstanceLifecycleService.releaseInstanceOnUnlink(usuario.getId());
            whatsAppUserMappingService.unlinkWhatsAppNumber(usuario.getId());

            evolutionPairingService.invalidatePairingCredCache(usuario.getId());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "success");
            out.put("message", "Número removido da app e sessão Evolution encerrada. Pode vincular de novo com QR.");
            out.put("evolutionWaConnected", false);
            out.put("numeroCadastrado", false);
            return ResponseEntity.ok(out);
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
