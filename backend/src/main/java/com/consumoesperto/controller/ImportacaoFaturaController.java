package com.consumoesperto.controller;

import com.consumoesperto.dto.ConfirmarImportacaoFaturaRequest;
import com.consumoesperto.dto.ImportacaoFaturaDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.FaturaPdfImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/importacoes/faturas")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost:4200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class ImportacaoFaturaController {

    private final FaturaPdfImportService faturaPdfImportService;

    @GetMapping("/pendentes")
    public ResponseEntity<List<ImportacaoFaturaDTO>> pendentes(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(faturaPdfImportService.listarPendentes(user.getId()));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportacaoFaturaDTO> upload(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestPart("file") MultipartFile file
    ) throws java.io.IOException {
        return ResponseEntity.ok(faturaPdfImportService.processarPdf(user.getId(), file.getBytes()));
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<Map<String, Integer>> confirmar(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id,
        @RequestBody(required = false) ConfirmarImportacaoFaturaRequest request
    ) {
        FaturaPdfImportService.ResultadoConfirmacaoFatura resultado =
            faturaPdfImportService.confirmarComResumo(user.getId(), id, request, true);
        return ResponseEntity.ok(Map.of(
            "criadas", resultado.criadas(),
            "conciliadas", resultado.conciliadas(),
            "futuras", resultado.futuras(),
            "registrosNaFaturaAtual", resultado.registrosNaFaturaAtual()
        ));
    }
}
