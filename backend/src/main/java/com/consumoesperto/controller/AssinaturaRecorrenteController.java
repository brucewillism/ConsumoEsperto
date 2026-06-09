package com.consumoesperto.controller;

import com.consumoesperto.dto.AssinaturaRecorrenteDTO;
import com.consumoesperto.dto.AssinaturaRecorrenteRequest;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.AssinaturaRecorrenteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assinaturas")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class AssinaturaRecorrenteController {

    private final AssinaturaRecorrenteService assinaturaRecorrenteService;

    @GetMapping
    public ResponseEntity<List<AssinaturaRecorrenteDTO>> listar(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(assinaturaRecorrenteService.listar(user.getId()));
    }

    @PostMapping
    public ResponseEntity<AssinaturaRecorrenteDTO> criar(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody AssinaturaRecorrenteRequest request
    ) {
        return ResponseEntity.ok(assinaturaRecorrenteService.criar(user.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssinaturaRecorrenteDTO> atualizar(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id,
        @Valid @RequestBody AssinaturaRecorrenteRequest request
    ) {
        return ResponseEntity.ok(assinaturaRecorrenteService.atualizar(user.getId(), id, request));
    }

    @PatchMapping("/{id}/ativo")
    public ResponseEntity<AssinaturaRecorrenteDTO> alternarAtivo(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id,
        @RequestBody Map<String, Boolean> body
    ) {
        boolean ativo = Boolean.TRUE.equals(body.get("ativo"));
        return ResponseEntity.ok(assinaturaRecorrenteService.alternarAtivo(user.getId(), id, ativo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        assinaturaRecorrenteService.excluir(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
