package com.consumoesperto.controller;

import com.consumoesperto.dto.AgendamentoPagamentoDTO;
import com.consumoesperto.dto.AgendamentoPagamentoRequest;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.AgendamentoPagamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agendamentos-pagamentos")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class AgendamentoPagamentoController {

    private final AgendamentoPagamentoService agendamentoPagamentoService;

    @GetMapping
    public ResponseEntity<List<AgendamentoPagamentoDTO>> listar(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(agendamentoPagamentoService.listar(user.getId()));
    }

    @GetMapping("/historico")
    public ResponseEntity<List<AgendamentoPagamentoDTO>> historico(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(agendamentoPagamentoService.historico(user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgendamentoPagamentoDTO> buscar(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(agendamentoPagamentoService.buscar(user.getId(), id));
    }

    @PostMapping
    public ResponseEntity<AgendamentoPagamentoDTO> criar(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestBody AgendamentoPagamentoRequest request
    ) {
        return ResponseEntity.ok(agendamentoPagamentoService.criarManual(user.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgendamentoPagamentoDTO> atualizar(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id,
        @RequestBody AgendamentoPagamentoRequest request
    ) {
        return ResponseEntity.ok(agendamentoPagamentoService.atualizar(user.getId(), id, request));
    }

    @PostMapping("/{id}/pausar")
    public ResponseEntity<AgendamentoPagamentoDTO> pausar(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(agendamentoPagamentoService.pausar(user.getId(), id));
    }

    @PostMapping("/{id}/ativar")
    public ResponseEntity<AgendamentoPagamentoDTO> ativar(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(agendamentoPagamentoService.ativar(user.getId(), id));
    }

    @PostMapping("/{id}/marcar-pago")
    public ResponseEntity<AgendamentoPagamentoDTO> marcarPago(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(agendamentoPagamentoService.marcarComoPago(user.getId(), id));
    }

    @PostMapping("/{id}/executar")
    public ResponseEntity<AgendamentoPagamentoDTO> executar(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(agendamentoPagamentoService.executarManual(user.getId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<AgendamentoPagamentoDTO> cancelar(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(agendamentoPagamentoService.cancelar(user.getId(), id));
    }
}
