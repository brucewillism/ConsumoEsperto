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
import org.springframework.web.bind.annotation.PathVariable;
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
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
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

    /** Insights compactos para o card do dashboard (2–3 memórias mais relevantes, sem clique). */
    @GetMapping("/memoria/insights")
    public ResponseEntity<List<MemoriaSemanticaTimelineItemDTO>> memoriaInsights(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestParam(name = "limite", defaultValue = "3") int limite
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(cerebroSemanticoService.listarInsightsRelevantes(user.getId(), limite));
    }

    /** Refutação (6.1): marca a memória como REFUTADA — sai do RAG e do painel. Checa posse. */
    @PatchMapping("/memoria/{id}/refutar")
    public ResponseEntity<Void> refutarMemoria(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable("id") Long id
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean ok = cerebroSemanticoService.refutarMemoria(user.getId(), id);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Memória não encontrada ou já inativa.");
        }
        return ResponseEntity.noContent().build();
    }

    /** SUPERADA recentes (item 4): auditoria da superação por contradição, com opção de restaurar. */
    @GetMapping("/memoria/superadas")
    public ResponseEntity<List<MemoriaSemanticaTimelineItemDTO>> memoriaSuperadas(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestParam(name = "limite", defaultValue = "10") int limite
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(cerebroSemanticoService.listarSuperadasRecentes(user.getId(), limite));
    }

    /** Reverte uma superação errada: volta a ATIVA e não volta a ser superada em loop. */
    @PatchMapping("/memoria/{id}/restaurar")
    public ResponseEntity<Void> restaurarMemoria(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable("id") Long id
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean ok = cerebroSemanticoService.restaurarMemoria(user.getId(), id);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Memória não encontrada ou não está SUPERADA.");
        }
        return ResponseEntity.noContent().build();
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
