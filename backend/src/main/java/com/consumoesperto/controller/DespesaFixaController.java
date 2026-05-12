package com.consumoesperto.controller;

import com.consumoesperto.dto.DespesaFixaDTO;
import com.consumoesperto.dto.DespesaFixaRequest;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.DespesaFixaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/despesas-fixas")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:4200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class DespesaFixaController {

    private final DespesaFixaService despesaFixaService;

    @GetMapping
    public ResponseEntity<List<DespesaFixaDTO>> listar(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(despesaFixaService.listar(user.getId()));
    }

    @PostMapping
    public ResponseEntity<DespesaFixaDTO> criar(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody DespesaFixaRequest request
    ) {
        return ResponseEntity.ok(despesaFixaService.criar(user.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DespesaFixaDTO> atualizar(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id,
        @Valid @RequestBody DespesaFixaRequest request
    ) {
        return ResponseEntity.ok(despesaFixaService.atualizar(user.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        despesaFixaService.excluir(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
