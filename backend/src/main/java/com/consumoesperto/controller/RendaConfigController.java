package com.consumoesperto.controller;

import com.consumoesperto.dto.RendaConfigDTO;
import com.consumoesperto.dto.RendaConfigRequest;
import com.consumoesperto.dto.ContrachequeDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.ContrachequeImportService;
import com.consumoesperto.service.RendaConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/renda-config")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:4200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class RendaConfigController {

    private final RendaConfigService rendaConfigService;
    private final ContrachequeImportService contrachequeImportService;

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

    @GetMapping("/contracheques")
    public ResponseEntity<java.util.List<ContrachequeDTO>> historicoContracheques(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(contrachequeImportService.listarHistorico(currentUser.getId()));
    }

    @PostMapping(value = "/contracheques/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContrachequeDTO> uploadContracheque(
        @AuthenticationPrincipal UserPrincipal currentUser,
        @RequestPart("file") MultipartFile file
    ) throws java.io.IOException {
        return ResponseEntity.ok(contrachequeImportService.processarPdf(currentUser.getId(), file.getBytes()));
    }

    @GetMapping("/contracheques/pendentes")
    public ResponseEntity<java.util.List<ContrachequeDTO>> contrachequesPendentes(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(contrachequeImportService.listarPendentes(currentUser.getId()));
    }

    @PostMapping("/contracheques/{id}/confirmar")
    public ResponseEntity<ContrachequeDTO> confirmarContracheque(
        @AuthenticationPrincipal UserPrincipal currentUser,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(contrachequeImportService.confirmar(currentUser.getId(), id));
    }
}
