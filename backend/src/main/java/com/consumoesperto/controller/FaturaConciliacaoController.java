package com.consumoesperto.controller;

import com.consumoesperto.dto.PagamentoFaturaRequest;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.FaturaConciliacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/faturas")
@RequiredArgsConstructor
@Tag(name = "Conciliação de Faturas", description = "Pagamento consolidado de fatura via saldo em conta")
@CrossOrigin(origins = {"http://localhost:14200", "https://0d723f1e294f.ngrok-free.app"})
public class FaturaConciliacaoController {

    private final FaturaConciliacaoService faturaConciliacaoService;

    @PostMapping("/pagar")
    @Operation(summary = "Pagar fatura debitando conta bancária (sem duplicar despesas do cartão)")
    public ResponseEntity<TransacaoDTO> pagar(
        @AuthenticationPrincipal UserPrincipal user,
        @Valid @RequestBody PagamentoFaturaRequest request
    ) {
        return ResponseEntity.ok(faturaConciliacaoService.pagarFatura(user.getId(), request));
    }
}
