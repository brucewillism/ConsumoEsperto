package com.consumoesperto.controller;

import com.consumoesperto.dto.ContaBancariaDTO;
import com.consumoesperto.dto.ContaBancariaUpdateDTO;
import com.consumoesperto.dto.DivergenciaSaldoDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.ContaBancariaService;
import com.consumoesperto.service.SaldoIntegridadeService;
import com.consumoesperto.service.SaldoService;
import com.consumoesperto.service.SalarioAutomaticoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/contas-bancarias")
@RequiredArgsConstructor
@Tag(name = "Contas Bancárias", description = "Multicarteira — contas e saldos")
@CrossOrigin(origins = {"http://localhost:14200", "https://0d723f1e294f.ngrok-free.app"})
public class ContaBancariaController {

    private final ContaBancariaService contaBancariaService;
    private final SaldoService saldoService;
    private final SalarioAutomaticoService salarioAutomaticoService;
    private final SaldoIntegridadeService saldoIntegridadeService;

    @GetMapping("/integridade")
    @Operation(summary = "Auditar divergências de saldo (persistido vs movimentos confirmados)")
    public ResponseEntity<List<DivergenciaSaldoDTO>> auditarIntegridade(
        @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(saldoIntegridadeService.auditarUsuario(currentUser.getId()));
    }

    @GetMapping
    @Operation(summary = "Listar contas do usuário")
    public ResponseEntity<List<ContaBancariaDTO>> listar(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "true") boolean apenasAtivas) {
        try {
            salarioAutomaticoService.executarCatchUpSalario(currentUser.getId());
        } catch (Exception ignored) {
            // catch-up best-effort
        }
        try {
            saldoService.repararSaldosPosBugReconciliacao(currentUser.getId());
        } catch (Exception ignored) {
            // reparo best-effort
        }
        return ResponseEntity.ok(contaBancariaService.listarPorUsuario(currentUser.getId(), apenasAtivas));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar conta por id")
    public ResponseEntity<ContaBancariaDTO> buscar(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(contaBancariaService.buscarPorId(id, currentUser.getId()));
    }

    @GetMapping("/patrimonio")
    @Operation(summary = "Patrimônio líquido (multicarteira ou legado unificado)")
    public ResponseEntity<BigDecimal> patrimonio(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(saldoService.patrimonioLiquido(currentUser.getId()));
    }

    @PostMapping
    @Operation(summary = "Cadastrar conta bancária")
    public ResponseEntity<ContaBancariaDTO> criar(
            @Valid @RequestBody ContaBancariaDTO dto,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        dto.setUsuarioId(currentUser.getId());
        return ResponseEntity.ok(contaBancariaService.criar(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar conta bancária")
    public ResponseEntity<ContaBancariaDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody ContaBancariaUpdateDTO dto,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(contaBancariaService.atualizar(id, dto, currentUser.getId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Inativar conta bancária")
    public ResponseEntity<Void> inativar(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        contaBancariaService.inativar(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reconciliar-saldo")
    @Operation(summary = "Reconciliar saldo da conta",
        description = "Realinha saldo_inicial com transações e transferências; mantém saldo_atual se houver divergência.")
    public ResponseEntity<SaldoService.ResultadoReconciliacaoSaldo> reconciliarSaldo(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(saldoService.reconciliarSaldo(id, currentUser.getId()));
    }

    @PostMapping("/sincronizar-saldos")
    @Operation(summary = "Informar saldos do app bancário em lote",
        description = "Ajusta várias contas de uma vez — use após divergência de saldo.")
    public ResponseEntity<java.util.List<SaldoService.ResultadoReconciliacaoSaldo>> sincronizarSaldosLote(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody java.util.List<SaldoService.SincronizarSaldoItem> body) {
        return ResponseEntity.ok(saldoService.sincronizarSaldosReferenciaLote(currentUser.getId(), body));
    }

    @PostMapping("/{id}/sincronizar-saldo")
    @Operation(summary = "Informar saldo do app bancário",
        description = "Ajusta saldo_atual ao valor informado e recalcula saldo_inicial (reparo manual).")
    public ResponseEntity<SaldoService.ResultadoReconciliacaoSaldo> sincronizarSaldo(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody java.util.Map<String, BigDecimal> body) {
        BigDecimal saldo = body != null ? body.get("saldoAtual") : null;
        return ResponseEntity.ok(saldoService.sincronizarSaldoReferenciaExterna(id, currentUser.getId(), saldo));
    }
}
