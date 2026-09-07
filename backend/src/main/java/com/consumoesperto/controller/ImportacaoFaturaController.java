package com.consumoesperto.controller;

import com.consumoesperto.dto.ConfirmarImportacaoFaturaRequest;
import com.consumoesperto.dto.EscolhaRecursoImportacaoRequest;
import com.consumoesperto.dto.ImportacaoConfirmacaoDTO;
import com.consumoesperto.dto.ImportacaoFaturaDTO;
import com.consumoesperto.model.FinancialImportFileType;
import com.consumoesperto.model.ImportacaoFaturaCartao;
import com.consumoesperto.repository.ImportacaoFaturaCartaoRepository;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.FaturaPdfImportService;
import com.consumoesperto.service.WhatsAppCommandService;
import com.consumoesperto.service.importacao.FinancialCsvImportService;
import com.consumoesperto.service.importacao.FinancialImportDetection;
import com.consumoesperto.service.importacao.FinancialImportFileTypeDetector;
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
    private final FinancialCsvImportService financialCsvImportService;
    private final FinancialImportFileTypeDetector fileTypeDetector;
    private final ImportacaoFaturaCartaoRepository importacaoRepository;
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
        @RequestParam(value = "senhaPdf", required = false) String senhaPdf,
        @RequestParam(value = "tipoRecurso", required = false) String tipoRecurso,
        @RequestParam(value = "contaBancariaId", required = false) Long contaBancariaId,
        @RequestParam(value = "cartaoCreditoId", required = false) Long cartaoCreditoId
    ) throws java.io.IOException {
        MultipartFile file = escolherArquivoMultipart(filePart, fileParam);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Envie um ficheiro PDF ou CSV no campo «file».");
        }
        byte[] bytes = file.getBytes();
        FinancialImportDetection detection = fileTypeDetector.detect(
            file.getOriginalFilename(), file.getContentType(), bytes);
        if (detection.type() == FinancialImportFileType.UNKNOWN) {
            throw new IllegalArgumentException(
                "Arquivo não reconhecido. Envie um PDF de fatura ou um CSV de extrato.");
        }
        if (detection.isPdf()) {
            return ResponseEntity.ok(faturaPdfImportService.processarPdf(user.getId(), bytes, senhaPdf));
        }
        return ResponseEntity.ok(financialCsvImportService.processar(
            user.getId(),
            file.getOriginalFilename(),
            bytes,
            detection,
            tipoRecurso,
            contaBancariaId,
            cartaoCreditoId
        ));
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

    @PostMapping("/{id}/escolha-recurso")
    public ResponseEntity<ImportacaoFaturaDTO> escolhaRecurso(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id,
        @RequestBody EscolhaRecursoImportacaoRequest request
    ) {
        return ResponseEntity.ok(financialCsvImportService.escolherRecurso(user.getId(), id, request));
    }

    @PostMapping("/{id}/itens")
    public ResponseEntity<ImportacaoFaturaDTO> atualizarItens(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id,
        @RequestBody(required = false) ConfirmarImportacaoFaturaRequest request
    ) {
        return ResponseEntity.ok(financialCsvImportService.atualizarItens(user.getId(), id, request));
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<ImportacaoConfirmacaoDTO> confirmar(
        @AuthenticationPrincipal UserPrincipal user,
        @PathVariable Long id,
        @RequestBody(required = false) ConfirmarImportacaoFaturaRequest request
    ) {
        ImportacaoFaturaCartao imp = importacaoRepository.findByIdAndUsuarioId(id, user.getId())
            .orElseThrow(() -> new com.consumoesperto.exception.ResourceNotFoundException("Importação não encontrada"));
        if (FinancialCsvImportService.isCsv(imp)) {
            ImportacaoConfirmacaoDTO resultado = financialCsvImportService.confirmar(user.getId(), id, request);
            whatsAppCommandService.sincronizarFaturaResolvidaNoApp(user.getId());
            return ResponseEntity.ok(resultado);
        }
        FaturaPdfImportService.ResultadoConfirmacaoFatura resultado =
            faturaPdfImportService.confirmarComResumo(user.getId(), id, request, true);
        whatsAppCommandService.sincronizarFaturaResolvidaNoApp(user.getId());
        ImportacaoConfirmacaoDTO dto = new ImportacaoConfirmacaoDTO();
        dto.setCriadas(resultado.criadas());
        dto.setConciliadas(resultado.conciliadas());
        dto.setFuturas(resultado.futuras());
        dto.setRegistrosNaFaturaAtual(resultado.registrosNaFaturaAtual());
        dto.setMensagem(faturaPdfImportService.mensagemResumoImportacao(resultado));
        return ResponseEntity.ok(dto);
    }
}
