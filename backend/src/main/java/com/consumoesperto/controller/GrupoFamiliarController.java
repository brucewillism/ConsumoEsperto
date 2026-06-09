package com.consumoesperto.controller;

import com.consumoesperto.dto.BalancoGrupoDTO;
import com.consumoesperto.dto.ConviteGrupoFamiliarRequest;
import com.consumoesperto.dto.DebitoInternoDTO;
import com.consumoesperto.dto.GrupoFamiliarDTO;
import com.consumoesperto.dto.GrupoFamiliarMembroDTO;
import com.consumoesperto.dto.GrupoFamiliarRequest;
import com.consumoesperto.dto.OrcamentoDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.GrupoFamiliarService;
import com.consumoesperto.service.OrcamentoService;
import com.consumoesperto.service.SplitBillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/familia")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class GrupoFamiliarController {

    private final GrupoFamiliarService grupoFamiliarService;
    private final OrcamentoService orcamentoService;
    private final SplitBillService splitBillService;

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

    /** Balanço do racha-contas: valores a receber e suas pendências. */
    @GetMapping("/balanco")
    public ResponseEntity<BalancoGrupoDTO> balanco(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(splitBillService.balanco(user.getId()));
    }

    /** Marca um débito (onde o usuário é credor) como liquidado. */
    @PostMapping("/debitos/{debitoId}/liquidar")
    public ResponseEntity<DebitoInternoDTO> liquidarDebito(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long debitoId
    ) {
        return ResponseEntity.ok(splitBillService.liquidarDebito(user.getId(), debitoId));
    }
}
