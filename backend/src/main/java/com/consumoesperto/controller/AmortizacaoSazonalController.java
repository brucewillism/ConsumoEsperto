package com.consumoesperto.controller;

import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.AmortizacaoSazonalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/amortizacao-sazonal")
@RequiredArgsConstructor
@Tag(name = "Amortização Sazonal", description = "Debt Snowball com 13º/IR")
@CrossOrigin(origins = {"http://localhost:14200", "https://0d723f1e294f.ngrok-free.app"})
public class AmortizacaoSazonalController {

    private final AmortizacaoSazonalService amortizacaoSazonalService;

    @GetMapping("/simulacao")
    @Operation(summary = "Simular antecipação de parcelamentos com receitas fiscais")
    public ResponseEntity<List<AmortizacaoSazonalService.SimulacaoAntecipacao>> simular(
        @AuthenticationPrincipal UserPrincipal user
    ) {
        return ResponseEntity.ok(amortizacaoSazonalService.simularOportunidades(user.getId()));
    }
}
