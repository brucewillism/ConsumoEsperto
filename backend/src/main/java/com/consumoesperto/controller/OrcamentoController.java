package com.consumoesperto.controller;

import com.consumoesperto.dto.ForecastFinanceiroDTO;
import com.consumoesperto.dto.OrcamentoDTO;
import com.consumoesperto.dto.OrcamentoRequest;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.ForecastFinanceiroService;
import com.consumoesperto.service.OrcamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/orcamentos")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:4200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class OrcamentoController {

    private final OrcamentoService orcamentoService;
    private final ForecastFinanceiroService forecastFinanceiroService;

    @GetMapping
    public ResponseEntity<List<OrcamentoDTO>> listar(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestParam(required = false) Integer mes,
        @RequestParam(required = false) Integer ano
    ) {
        return ResponseEntity.ok(orcamentoService.listar(user.getId(), mes, ano));
    }

    @PostMapping
    public ResponseEntity<OrcamentoDTO> salvar(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody OrcamentoRequest request
    ) {
        return ResponseEntity.ok(orcamentoService.salvar(user.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        orcamentoService.excluir(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/forecast")
    public ResponseEntity<ForecastFinanceiroDTO> forecast(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(forecastFinanceiroService.calcular(user.getId()));
    }
}
