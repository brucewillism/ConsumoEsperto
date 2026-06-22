package com.consumoesperto.controller;

import com.consumoesperto.dto.SentinelaRunResultDTO;
import com.consumoesperto.model.RiscoFluxo;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.AlertaDispatchService;
import com.consumoesperto.service.AlertaTempestadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sentinela")
@RequiredArgsConstructor
@Tag(name = "Sentinela", description = "Simulação manual de alerta proativo de fluxo")
public class SentinelaAdminController {

    private final AlertaTempestadeService alertaTempestadeService;
    private final AlertaDispatchService alertaDispatchService;
    private final UsuarioRepository usuarioRepository;

    @PostMapping("/run")
    @Operation(summary = "Força simulação Sentinela para o usuário autenticado (ou usuarioId próprio)")
    public ResponseEntity<SentinelaRunResultDTO> runSentinela(
        @AuthenticationPrincipal UserPrincipal currentUser,
        @RequestParam(required = false) Long usuarioId
    ) {
        Long alvo = usuarioId != null ? usuarioId : currentUser.getId();
        if (!alvo.equals(currentUser.getId())) {
            return ResponseEntity.status(403).build();
        }
        Usuario usuario = usuarioRepository.findById(alvo)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        RiscoFluxo risco = alertaTempestadeService.simular(alvo);
        boolean enviado = alertaDispatchService.dispatchAlerta(usuario, risco);
        boolean optIn = usuario.getOptInNotificacoes() == null || Boolean.TRUE.equals(usuario.getOptInNotificacoes());
        return ResponseEntity.ok(SentinelaRunResultDTO.from(alvo, risco, enviado, optIn));
    }
}
