package com.consumoesperto.controller;

import com.consumoesperto.dto.EvolutionHealthDTO;
import com.consumoesperto.service.EvolutionSessionMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/evolution")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class AdminEvolutionController {

    private final EvolutionSessionMonitorService evolutionSessionMonitorService;

    /**
     * Resumo operacional das sessões Evolution.
     * Autorização: papel ADMIN persistido (JWT) ou header {@code X-Admin-Api-Key} de ops.
     */
    @GetMapping("/health")
    public ResponseEntity<EvolutionHealthDTO> health(
        @RequestParam(defaultValue = "false") boolean detalhe
    ) {
        return ResponseEntity.ok(evolutionSessionMonitorService.obterHealth(detalhe));
    }
}
