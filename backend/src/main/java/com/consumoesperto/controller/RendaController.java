package com.consumoesperto.controller;

import com.consumoesperto.dto.RecalculoProjecaoSazonalDTO;
import com.consumoesperto.dto.RendaDTO;
import com.consumoesperto.dto.RendaProcessamentoDTO;
import com.consumoesperto.dto.RendaRequestDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.RendaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/rendas")
@RequiredArgsConstructor
@Tag(name = "Rendas", description = "Fontes de renda vinculadas a contas bancárias")
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class RendaController {

    private final RendaService rendaService;

    @GetMapping
    @Operation(summary = "Listar rendas do utilizador")
    public ResponseEntity<List<RendaDTO>> listar(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestParam(defaultValue = "true") boolean apenasAtivas
    ) {
        return ResponseEntity.ok(rendaService.listar(user.getId(), apenasAtivas));
    }

    @PostMapping
    @Operation(summary = "Cadastrar renda vinculada a conta e creditar saldo")
    public ResponseEntity<RendaProcessamentoDTO> salvar(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody RendaRequestDTO request
    ) {
        return ResponseEntity.ok(rendaService.salvar(user.getId(), request));
    }

    @PostMapping("/simular")
    @Operation(summary = "Simular entrada de renda (credita conta e recalcula projeções sazonais)")
    public ResponseEntity<RecalculoProjecaoSazonalDTO> simular(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody RendaRequestDTO request
    ) {
        return ResponseEntity.ok(rendaService.simularEntradaRenda(user.getId(), request));
    }
}
