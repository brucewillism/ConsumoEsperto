package com.consumoesperto.controller;

import com.consumoesperto.dto.RendaConfigDTO;
import com.consumoesperto.dto.RendaConfigRequest;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.RendaConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/renda-config")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:4200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class RendaConfigController {

    private final RendaConfigService rendaConfigService;

    @GetMapping
    public ResponseEntity<RendaConfigDTO> obter(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
            rendaConfigService.obterDto(currentUser.getId()).orElse(RendaConfigDTO.vazio())
        );
    }

    @PutMapping
    public ResponseEntity<RendaConfigDTO> salvar(
        @AuthenticationPrincipal UserPrincipal currentUser,
        @RequestBody RendaConfigRequest body
    ) {
        return ResponseEntity.ok(rendaConfigService.salvarDePedidoHttp(currentUser.getId(), body));
    }
}
