package com.consumoesperto.controller;

import com.consumoesperto.dto.SugestaoContencaoJarvisDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.ContencaoJarvisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jarvis/sugestoes-contencao")
@RequiredArgsConstructor
public class ContencaoJarvisController {

    private final ContencaoJarvisService contencaoJarvisService;

    @GetMapping("/pendentes")
    public ResponseEntity<List<SugestaoContencaoJarvisDTO>> pendentes(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(contencaoJarvisService.listarPendentes(user.getId()));
    }

    @PostMapping("/{id}/aceitar")
    public ResponseEntity<SugestaoContencaoJarvisDTO> aceitar(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal user
    ) {
        return ResponseEntity.ok(contencaoJarvisService.aceitar(user.getId(), id));
    }

    @PostMapping("/{id}/recusar")
    public ResponseEntity<Void> recusar(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal user
    ) {
        contencaoJarvisService.recusar(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
