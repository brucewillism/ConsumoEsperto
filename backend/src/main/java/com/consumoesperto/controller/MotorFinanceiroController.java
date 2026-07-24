package com.consumoesperto.controller;

import com.consumoesperto.dto.motor.MotorFinanceiroInteligenteDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.MotorFinanceiroService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/motor-financeiro")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class MotorFinanceiroController {

    private final MotorFinanceiroService motorFinanceiroService;

    /**
     * Visão completa: perfil, forecast probabilístico, score explicável, metas e advisor educacional.
     * Cálculos 100% determinísticos; narrativa IA opcional via {@code narrativa=true}.
     */
    @GetMapping
    public ResponseEntity<MotorFinanceiroInteligenteDTO> obter(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestParam(defaultValue = "false") boolean narrativa,
        @RequestParam(defaultValue = "true") boolean persistirPerfil
    ) {
        MotorFinanceiroInteligenteDTO dto = persistirPerfil
            ? motorFinanceiroService.calcularEPersistirPerfil(user.getId(), narrativa)
            : motorFinanceiroService.calcular(user.getId(), narrativa);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/perfil/historico")
    public ResponseEntity<List<Map<String, Object>>> historicoPerfil(
        @AuthenticationPrincipal UserPrincipal user
    ) {
        return ResponseEntity.ok(motorFinanceiroService.historicoPerfil(user.getId()));
    }
}
