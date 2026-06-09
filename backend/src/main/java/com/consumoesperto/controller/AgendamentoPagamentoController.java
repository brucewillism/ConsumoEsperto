package com.consumoesperto.controller;

import com.consumoesperto.dto.AgendamentoPagamentoDTO;
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

    @DeleteMapping("/{id}")
    public ResponseEntity<AgendamentoPagamentoDTO> cancelar(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(agendamentoPagamentoService.cancelar(user.getId(), id));
    }
}
