package com.consumoesperto.controller;

import com.consumoesperto.model.MovimentacaoSaldoLog;
import com.consumoesperto.repository.MovimentacaoSaldoLogRepository;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.SaldoReparoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reparo de dados financeiros: relatório read-only sempre disponível;
 * aplicação exige flag do servidor + confirmação + backup (fail-closed).
 */
@RestController
@RequestMapping("/api/reparo-financeiro")
@RequiredArgsConstructor
@Tag(name = "Reparo Financeiro", description = "Diagnóstico e reparo de saldos/faturas corrompidos")
public class ReparoFinanceiroController {

    private final SaldoReparoService saldoReparoService;
    private final MovimentacaoSaldoLogRepository movimentacaoSaldoLogRepository;

    @Data
    public static class AplicarReparoRequest {
        /** true = aplicar; false/omitido = dry-run. */
        private boolean confirmar;
        /** O operador declara que fez backup do banco antes de aplicar. */
        private boolean backupConfirmado;
        /** Caso (b) abertura perdida: valor correto do saldo_inicial, se conhecido. */
        private BigDecimal saldoInicialCorreto;
    }

    @GetMapping("/relatorio")
    @Operation(summary = "Relatório de divergências (read-only): saldos e faturas PAGA sem caixa")
    public ResponseEntity<SaldoReparoService.RelatorioReparo> relatorio(
        @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(saldoReparoService.relatorio(currentUser.getId()));
    }

    @PostMapping("/conta/{contaId}")
    @Operation(summary = "Reparo pontual de uma conta (dry-run por padrão; aplicar exige flag+backup)")
    public ResponseEntity<SaldoReparoService.ReparoContaResultado> repararConta(
        @AuthenticationPrincipal UserPrincipal currentUser,
        @PathVariable Long contaId,
        @RequestBody(required = false) AplicarReparoRequest request) {
        AplicarReparoRequest req = request != null ? request : new AplicarReparoRequest();
        return ResponseEntity.ok(saldoReparoService.repararConta(
            contaId,
            currentUser.getId(),
            req.isConfirmar(),
            req.isBackupConfirmado(),
            req.getSaldoInicialCorreto()
        ));
    }

    @PostMapping("/faturas")
    @Operation(summary = "Reseta valorPago de faturas PAGA para a soma real de PAGAMENTO_FATURA")
    public ResponseEntity<List<SaldoReparoService.ReparoFaturaResultado>> repararFaturas(
        @AuthenticationPrincipal UserPrincipal currentUser,
        @RequestBody(required = false) AplicarReparoRequest request) {
        AplicarReparoRequest req = request != null ? request : new AplicarReparoRequest();
        return ResponseEntity.ok(saldoReparoService.repararFaturasValorPago(
            currentUser.getId(),
            req.isConfirmar(),
            req.isBackupConfirmado()
        ));
    }

    @GetMapping("/conta/{contaId}/movimentacoes")
    @Operation(summary = "Trilha de auditoria da conta: por que o saldo mudou (append-only)")
    public ResponseEntity<List<MovimentacaoSaldoLog>> movimentacoes(
        @AuthenticationPrincipal UserPrincipal currentUser,
        @PathVariable Long contaId,
        @RequestParam(defaultValue = "50") int limite) {
        List<MovimentacaoSaldoLog> linhas = movimentacaoSaldoLogRepository
            .findUltimasPorConta(contaId, PageRequest.of(0, Math.min(Math.max(limite, 1), 500)))
            .stream()
            .filter(m -> currentUser.getId().equals(m.getUsuarioId()))
            .toList();
        return ResponseEntity.ok(linhas);
    }
}
