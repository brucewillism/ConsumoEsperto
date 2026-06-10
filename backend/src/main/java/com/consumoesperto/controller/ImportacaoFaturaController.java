package com.consumoesperto.controller;

import com.consumoesperto.dto.ConfirmarImportacaoFaturaRequest;
import com.consumoesperto.dto.ImportacaoFaturaDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.FaturaPdfImportService;
import com.consumoesperto.service.WhatsAppCommandService;
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
@CrossOrigin(originPatterns = {"http://localhost:14200", "https://*.ngrok-free.app", "https://*.ngrok.io"})
public class ImportacaoFaturaController {

    private final FaturaPdfImportService faturaPdfImportService;
    private final WhatsAppCommandService whatsAppCommandService;

    @GetMapping("/pendentes")
    public ResponseEntity<List<ImportacaoFaturaDTO>> pendentes(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(faturaPdfImportService.listarPendentes(user.getId()));
    }

    @DeleteMapping("/pendentes")
    public ResponseEntity<Map<String, Integer>> excluirTodasPendentes(@AuthenticationPrincipal UserPrincipal user) {
        int removidas = faturaPdfImportService.excluirTodasPendentes(user.getId());
        whatsAppCommandService.sincronizarFaturaResolvidaNoApp(user.getId());
        return ResponseEntity.ok(Map.of("removidas", removidas));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluirPendente(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id
    ) {
        faturaPdfImportService.excluirPendente(user.getId(), id);
        whatsAppCommandService.sincronizarFaturaResolvidaNoApp(user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportacaoFaturaDTO> upload(
        @AuthenticationPrincipal UserPrincipal user,
        @RequestPart(value = "file", required = false) MultipartFile filePart,
        @RequestParam(value = "file", required = false) MultipartFile fileParam,
        @RequestParam(value = "senhaPdf", required = false) String senhaPdf
    ) throws java.io.IOException {
        MultipartFile file = escolherArquivoMultipart(filePart, fileParam);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Envie um ficheiro PDF no campo «file».");
        }
        String nome = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (!nome.endsWith(".pdf") && !"application/pdf".equalsIgnoreCase(file.getContentType())) {
            throw new IllegalArgumentException("O ficheiro deve ser um PDF de fatura de cartão.");
        }
        return ResponseEntity.ok(faturaPdfImportService.processarPdf(user.getId(), file.getBytes(), senhaPdf));
    }

    private static MultipartFile escolherArquivoMultipart(MultipartFile part, MultipartFile param) {
        if (part != null && !part.isEmpty()) {
            return part;
        }
        if (param != null && !param.isEmpty()) {
            return param;
        }
        return null;
    }

    @PostMapping("/{id}/escolha-saldo-anterior")
    public ResponseEntity<ImportacaoFaturaDTO> escolhaSaldoAnterior(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id,
        @RequestParam boolean somar
    ) {
        ImportacaoFaturaDTO dto = faturaPdfImportService.aplicarEscolhaSaldoAnteriorBb(user.getId(), id, somar);
        whatsAppCommandService.sincronizarEscolhaSaldoAnteriorFaturaNoApp(user.getId());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<Map<String, Integer>> confirmar(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id,
        @RequestBody(required = false) ConfirmarImportacaoFaturaRequest request
    ) {
        FaturaPdfImportService.ResultadoConfirmacaoFatura resultado =
            faturaPdfImportService.confirmarComResumo(user.getId(), id, request, true);
        whatsAppCommandService.sincronizarFaturaResolvidaNoApp(user.getId());
        return ResponseEntity.ok(Map.of(
            "criadas", resultado.criadas(),
            "conciliadas", resultado.conciliadas(),
            "futuras", resultado.futuras(),
            "registrosNaFaturaAtual", resultado.registrosNaFaturaAtual()
        ));
    }
}
