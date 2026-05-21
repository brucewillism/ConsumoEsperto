package com.consumoesperto.controller;

import com.consumoesperto.dto.ConfiguracaoFiscalDTO;
import com.consumoesperto.dto.ConfiguracaoFiscalRequest;
import com.consumoesperto.dto.PlanejamentoFiscalResumoDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.PlanejamentoFiscalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/planejamento-fiscal")
@RequiredArgsConstructor
@Tag(name = "Planejamento Fiscal", description = "IR, 13º salário e provisões no fluxo de caixa")
public class PlanejamentoFiscalController {

    private final PlanejamentoFiscalService planejamentoFiscalService;

    @GetMapping
    @Operation(summary = "Obter configuração fiscal do utilizador")
    public ResponseEntity<ConfiguracaoFiscalDTO> obter(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(planejamentoFiscalService.obterDto(user.getId()));
    }

    @PutMapping
    @Operation(summary = "Salvar configuração fiscal e sincronizar provisões")
    public ResponseEntity<ConfiguracaoFiscalDTO> salvar(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestBody ConfiguracaoFiscalRequest body
    ) {
        return ResponseEntity.ok(planejamentoFiscalService.salvar(user.getId(), body));
    }

    @GetMapping("/simulacao")
    @Operation(summary = "Simular parcelas fiscais sem persistir transações")
    public ResponseEntity<PlanejamentoFiscalResumoDTO> simular(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(planejamentoFiscalService.simular(user.getId()));
    }

    @PostMapping("/sincronizar")
    @Operation(summary = "Recriar transações PREVISTO fiscais do ano corrente")
    public ResponseEntity<PlanejamentoFiscalResumoDTO> sincronizar(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(planejamentoFiscalService.sincronizarProvisoes(user.getId()));
    }
}
