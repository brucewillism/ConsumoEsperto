package com.consumoesperto.controller;

import com.consumoesperto.dto.WhatsappConexaoStatusDTO;
import com.consumoesperto.dto.WhatsappPairingCodeDTO;
import com.consumoesperto.security.SecurityService;
import com.consumoesperto.service.WhatsappConexaoMonitorService;
import com.consumoesperto.service.WhatsappPairingCodeService;
import com.consumoesperto.util.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/whatsapp/conexao")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class WhatsappConexaoController {

    private final SecurityService securityService;
    private final WhatsappConexaoMonitorService monitorService;
    private final WhatsappPairingCodeService pairingCodeService;

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                "status", "error",
                "message", "Usuario nao autenticado"
            ));
        }
        WhatsappConexaoStatusDTO dto = monitorService.obterStatus(usuarioOpt.get().getId());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/reconectar")
    public ResponseEntity<?> reconectar() {
        Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                "status", "error",
                "message", "Usuario nao autenticado"
            ));
        }
        try {
            WhatsappConexaoStatusDTO dto = monitorService.reconectarAgora(usuarioOpt.get().getId());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "success");
            body.put("conexao", dto);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.warn("Reconectar WhatsApp: {}", LogSanitizer.sanitize(e.getMessage()));
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Não foi possível reconectar agora. Tente o QR ou o código por número."
            ));
        }
    }

    @PostMapping("/pairing-code")
    public ResponseEntity<?> pairingCode(@RequestBody(required = false) Map<String, String> payload) {
        Optional<com.consumoesperto.model.Usuario> usuarioOpt = securityService.getCurrentUser();
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                "status", "error",
                "message", "Usuario nao autenticado"
            ));
        }
        String numero = payload == null ? "" : payload.getOrDefault("numero", "");
        try {
            WhatsappPairingCodeDTO dto = pairingCodeService.gerar(usuarioOpt.get().getId(), numero);
            return ResponseEntity.ok(dto);
        } catch (WhatsappPairingCodeService.PairingCodeIndisponivelException e) {
            return ResponseEntity.status(502).body(Map.of(
                "status", "error",
                "message", e.getMessage() == null
                    ? "A Evolution não devolveu código de pareamento."
                    : e.getMessage()
            ));
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "Número inválido." : e.getMessage();
            String lower = msg.toLowerCase();
            if (lower.contains("invalido") || lower.contains("inválido") || lower.contains("vazio")
                || lower.contains("vinculado")) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", msg));
            }
            log.warn("Pairing code: {}", LogSanitizer.sanitize(msg));
            return ResponseEntity.status(502).body(Map.of(
                "status", "error",
                "message", "Não foi possível obter o código na Evolution. Tente o QR."
            ));
        }
    }
}
