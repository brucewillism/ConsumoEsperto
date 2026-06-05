package com.consumoesperto.controller;

import com.consumoesperto.dto.ModoViagemJarvisDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.CronosJarvisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jarvis/modo-viagem")
@RequiredArgsConstructor
public class ModoViagemJarvisController {

    private final CronosJarvisService cronosJarvisService;

    @GetMapping("/pendentes")
    public ResponseEntity<List<ModoViagemJarvisDTO>> pendentes(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(cronosJarvisService.listarPendentesApp(user.getId()));
    }

    @PostMapping("/aceitar")
    public ResponseEntity<Void> aceitar(@AuthenticationPrincipal UserPrincipal user) {
        cronosJarvisService.aceitarModoViagemTopoDaFila(user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recusar")
    public ResponseEntity<Void> recusar(@AuthenticationPrincipal UserPrincipal user) {
        cronosJarvisService.descartarTopoModoViagem(user.getId());
        return ResponseEntity.noContent().build();
    }
}
