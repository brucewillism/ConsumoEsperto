package com.consumoesperto.controller;

import com.consumoesperto.dto.EmprestimoDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.EmprestimoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/emprestimos")
@RequiredArgsConstructor
@Tag(name = "Empréstimos", description = "Consignados registrados via J.A.R.V.I.S.")
@CrossOrigin(origins = {"http://localhost:14200", "https://0d723f1e294f.ngrok-free.app"})
public class EmprestimoController {

    private final EmprestimoService emprestimoService;

    @GetMapping
    @Operation(summary = "Listar empréstimos do usuário autenticado")
    public ResponseEntity<List<EmprestimoDTO>> listar(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(emprestimoService.listarPorUsuario(currentUser.getId()));
    }
}
