package com.consumoesperto.controller;

import com.consumoesperto.dto.JarvisFeedbackRequest;
import com.consumoesperto.dto.MemoriaSemanticaTimelineItemDTO;
import com.consumoesperto.dto.ProtocoloOtimizacaoResponseDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.AutomacaoTaticaService;
import com.consumoesperto.service.CerebroSemanticoService;
import com.consumoesperto.service.JarvisFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/jarvis")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:4200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class JarvisProtocolController {

    private final AutomacaoTaticaService automacaoTaticaService;
    private final CerebroSemanticoService cerebroSemanticoService;
    private final JarvisFeedbackService jarvisFeedbackService;

    @PostMapping("/feedback")
    public ResponseEntity<Void> feedback(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody JarvisFeedbackRequest body
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        jarvisFeedbackService.registrar(user.getId(), body);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/memoria/timeline")
    public ResponseEntity<List<MemoriaSemanticaTimelineItemDTO>> memoriaTimeline(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestParam(name = "limite", defaultValue = "40") int limite
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(cerebroSemanticoService.listarRecentesParaUsuario(user.getId(), limite));
    }

    @PatchMapping("/otimizar-metas")
    public ResponseEntity<ProtocoloOtimizacaoResponseDTO> otimizarMetas(@AuthenticationPrincipal UserPrincipal user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(automacaoTaticaService.executarProtocoloOtimizacaoMetas(user.getId()));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
