package com.consumoesperto.controller;

import com.consumoesperto.dto.ConviteGrupoFamiliarRequest;
import com.consumoesperto.dto.GrupoFamiliarDTO;
import com.consumoesperto.dto.GrupoFamiliarMembroDTO;
import com.consumoesperto.dto.GrupoFamiliarRequest;
import com.consumoesperto.dto.OrcamentoDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.GrupoFamiliarService;
import com.consumoesperto.service.OrcamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/familia")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:4200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class GrupoFamiliarController {

    private final GrupoFamiliarService grupoFamiliarService;
    private final OrcamentoService orcamentoService;

    @GetMapping
    public ResponseEntity<GrupoFamiliarDTO> meuGrupo(@AuthenticationPrincipal UserPrincipal user) {
        return grupoFamiliarService.meuGrupo(user.getId())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<GrupoFamiliarDTO> criar(@AuthenticationPrincipal UserPrincipal user, @RequestBody GrupoFamiliarRequest request) {
        return ResponseEntity.ok(grupoFamiliarService.criar(user.getId(), request));
    }

    @PostMapping("/convites")
    public ResponseEntity<GrupoFamiliarDTO> convidar(@AuthenticationPrincipal UserPrincipal user, @RequestBody ConviteGrupoFamiliarRequest request) {
        return ResponseEntity.ok(grupoFamiliarService.convidar(user.getId(), request));
    }

    @GetMapping("/convites")
    public ResponseEntity<List<GrupoFamiliarMembroDTO>> convites(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(grupoFamiliarService.convitesPendentes(user.getId()));
    }

    @PostMapping("/convites/{membroId}/responder")
    public ResponseEntity<GrupoFamiliarDTO> responder(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long membroId,
        @RequestBody Map<String, Boolean> body
    ) {
        return ResponseEntity.ok(grupoFamiliarService.responderConvite(user.getId(), membroId, Boolean.TRUE.equals(body.get("aceitar"))));
    }

    @GetMapping("/orcamentos-compartilhados")
    public ResponseEntity<List<OrcamentoDTO>> orcamentosCompartilhados(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestParam(required = false) Integer mes,
        @RequestParam(required = false) Integer ano
    ) {
        return ResponseEntity.ok(orcamentoService.listarCompartilhados(user.getId(), mes, ano));
    }
}
