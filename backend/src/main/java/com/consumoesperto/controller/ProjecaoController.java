package com.consumoesperto.controller;

import com.consumoesperto.dto.DashboardProjectionDTO;
import com.consumoesperto.dto.SimulacaoImpactoDTO;
import com.consumoesperto.dto.SimulacaoImpactoRequest;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.DashboardProjectionService;
import com.consumoesperto.service.SaldoService;
import com.consumoesperto.service.SimulacaoImpactoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projecoes")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:4200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class ProjecaoController {

    private final DashboardProjectionService dashboardProjectionService;
    private final SimulacaoImpactoService simulacaoImpactoService;
    private final SaldoService saldoService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardProjectionDTO> dashboard(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(dashboardProjectionService.projetar(user.getId()));
    }

    @GetMapping("/oportunidade-investimento")
    public ResponseEntity<SaldoService.OportunidadeInvestimento> oportunidadeInvestimento(@AuthenticationPrincipal UserPrincipal user) {
        return saldoService.sugerirInvestimentoSaldo(user.getId())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/simulacoes")
    public ResponseEntity<List<SimulacaoImpactoDTO>> listarSimulacoes(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(simulacaoImpactoService.listar(user.getId()));
    }

    @PostMapping("/simulacoes")
    public ResponseEntity<SimulacaoImpactoDTO> criarSimulacao(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody SimulacaoImpactoRequest request
    ) {
        return ResponseEntity.ok(simulacaoImpactoService.criar(user.getId(), request));
    }

    @PatchMapping("/simulacoes/ativas")
    public ResponseEntity<List<SimulacaoImpactoDTO>> definirAtivas(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestBody Map<String, Boolean> body
    ) {
        return ResponseEntity.ok(simulacaoImpactoService.definirAtivas(user.getId(), Boolean.TRUE.equals(body.get("ativa"))));
    }
}
