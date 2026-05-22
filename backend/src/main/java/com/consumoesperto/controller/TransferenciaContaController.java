package com.consumoesperto.controller;

import com.consumoesperto.dto.TransferenciaContaDTO;
import com.consumoesperto.dto.TransferenciaContaRequest;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.TransferenciaContaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/transferencias")
@RequiredArgsConstructor
@Tag(name = "Transferências", description = "TED/PIX interno entre contas — patrimônio total inalterado")
@CrossOrigin(origins = {"http://localhost:14200", "https://0d723f1e294f.ngrok-free.app"})
public class TransferenciaContaController {

    private final TransferenciaContaService transferenciaContaService;

    @PostMapping
    @Operation(summary = "Transferir saldo entre contas bancárias")
    public ResponseEntity<TransferenciaContaDTO> transferir(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody TransferenciaContaRequest request
    ) {
        return ResponseEntity.ok(transferenciaContaService.transferir(user.getId(), request));
    }

    @GetMapping
    @Operation(summary = "Histórico de transferências internas")
    public ResponseEntity<List<TransferenciaContaDTO>> listar(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(transferenciaContaService.listar(user.getId()));
    }
}
