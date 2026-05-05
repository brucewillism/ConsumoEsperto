package com.consumoesperto.controller;

import com.consumoesperto.dto.MetaFinanceiraDTO;
import com.consumoesperto.dto.MetaFinanceiraListResponse;
import com.consumoesperto.dto.MetaFinanceiraRequest;
import com.consumoesperto.dto.RendaMediaResponse;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.MetaFinanceiraService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/metas")
@RequiredArgsConstructor
@Tag(name = "Metas financeiras", description = "Metas de compra com % da renda")
@CrossOrigin(origins = {"http://localhost:4200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class MetaFinanceiraController {

    private final MetaFinanceiraService metaFinanceiraService;

    @GetMapping("/renda-media")
    public ResponseEntity<RendaMediaResponse> rendaMedia(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(metaFinanceiraService.getRendaMediaResponse(user.getId()));
    }

    @GetMapping
    public ResponseEntity<MetaFinanceiraListResponse> listar(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(metaFinanceiraService.listarComResumo(user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MetaFinanceiraDTO> buscar(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal user
    ) {
        return metaFinanceiraService.buscar(id, user.getId())
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<MetaFinanceiraDTO> criar(
        @Valid @RequestBody MetaFinanceiraRequest request,
        @AuthenticationPrincipal UserPrincipal user
    ) {
        return ResponseEntity.ok(metaFinanceiraService.criar(request, user.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MetaFinanceiraDTO> atualizar(
        @PathVariable Long id,
        @Valid @RequestBody MetaFinanceiraRequest request,
        @AuthenticationPrincipal UserPrincipal user
    ) {
        return ResponseEntity.ok(metaFinanceiraService.atualizar(id, request, user.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal user
    ) {
        metaFinanceiraService.excluir(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
