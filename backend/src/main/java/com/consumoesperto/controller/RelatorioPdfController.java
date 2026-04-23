package com.consumoesperto.controller;

import com.consumoesperto.service.RelatorioPdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.consumoesperto.security.UserPrincipal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.List;

/**
 * Controller para gerenciar relatórios PDF
 * 
 * Este controller permite gerar e baixar relatórios em PDF
 * com dados financeiros do usuário.
 */
@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app", "*"})
public class RelatorioPdfController {

    private final RelatorioPdfService relatorioPdfService;

    /**
     * Gera relatório financeiro completo em PDF
     */
    @GetMapping("/financeiro-completo")
    public ResponseEntity<byte[]> gerarRelatorioFinanceiroCompleto(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim) {
        
        try {
            log.info("📊 Gerando relatório financeiro completo para usuário: {}", currentUser.getId());
            
            // Definir período padrão se não informado
            LocalDate inicio = dataInicio != null ? 
                LocalDate.parse(dataInicio, DateTimeFormatter.ofPattern("yyyy-MM-dd")) :
                LocalDate.now().minusMonths(1);
                
            LocalDate fim = dataFim != null ? 
                LocalDate.parse(dataFim, DateTimeFormatter.ofPattern("yyyy-MM-dd")) :
                LocalDate.now();
            
            // Gerar PDF
            byte[] pdfBytes = relatorioPdfService.gerarRelatorioFinanceiroCompleto(
                currentUser.getId(), inicio, fim);
            
            // Configurar headers para download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                "relatorio-financeiro-completo-" + currentUser.getId() + ".pdf");
            headers.setContentLength(pdfBytes.length);
            
            log.info("✅ Relatório financeiro completo gerado com sucesso - {} bytes", pdfBytes.length);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
                
        } catch (Exception e) {
            log.error("❌ Erro ao gerar relatório financeiro completo: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Gera relatório de transações em PDF
     */
    @GetMapping("/transacoes")
    public ResponseEntity<byte[]> gerarRelatorioTransacoes(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim) {
        
        try {
            log.info("💳 Gerando relatório de transações para usuário: {}", currentUser.getId());
            
            // Definir período padrão se não informado
            LocalDate inicio = dataInicio != null ? 
                LocalDate.parse(dataInicio, DateTimeFormatter.ofPattern("yyyy-MM-dd")) :
                LocalDate.now().minusMonths(1);
                
            LocalDate fim = dataFim != null ? 
                LocalDate.parse(dataFim, DateTimeFormatter.ofPattern("yyyy-MM-dd")) :
                LocalDate.now();
            
            // Gerar PDF
            byte[] pdfBytes = relatorioPdfService.gerarRelatorioTransacoes(
                currentUser.getId(), inicio, fim);
            
            // Configurar headers para download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                "relatorio-transacoes-" + currentUser.getId() + ".pdf");
            headers.setContentLength(pdfBytes.length);
            
            log.info("✅ Relatório de transações gerado com sucesso - {} bytes", pdfBytes.length);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
                
        } catch (Exception e) {
            log.error("❌ Erro ao gerar relatório de transações: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Gera relatório de faturas em PDF
     */
    @GetMapping("/faturas")
    public ResponseEntity<byte[]> gerarRelatorioFaturas(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim) {
        
        try {
            log.info("📄 Gerando relatório de faturas para usuário: {}", currentUser.getId());
            
            // Definir período padrão se não informado
            LocalDate inicio = dataInicio != null ? 
                LocalDate.parse(dataInicio, DateTimeFormatter.ofPattern("yyyy-MM-dd")) :
                LocalDate.now().minusMonths(1);
                
            LocalDate fim = dataFim != null ? 
                LocalDate.parse(dataFim, DateTimeFormatter.ofPattern("yyyy-MM-dd")) :
                LocalDate.now();
            
            // Gerar PDF
            byte[] pdfBytes = relatorioPdfService.gerarRelatorioFaturas(
                currentUser.getId(), inicio, fim);
            
            // Configurar headers para download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                "relatorio-faturas-" + currentUser.getId() + ".pdf");
            headers.setContentLength(pdfBytes.length);
            
            log.info("✅ Relatório de faturas gerado com sucesso - {} bytes", pdfBytes.length);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
                
        } catch (Exception e) {
            log.error("❌ Erro ao gerar relatório de faturas: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Lista relatórios disponíveis
     */
    @GetMapping("/disponiveis")
    public ResponseEntity<Object> listarRelatoriosDisponiveis(@AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            log.info("📋 Listando relatórios disponíveis para usuário: {}", currentUser.getId());
            
            Object relatorios = Map.of(
                "relatorios", List.of(
                    Map.of(
                        "id", "financeiro-completo",
                        "nome", "Relatório Financeiro Completo",
                        "descricao", "Relatório completo com todas as informações financeiras",
                        "endpoint", "/api/relatorios/financeiro-completo",
                        "parametros", List.of("dataInicio", "dataFim")
                    ),
                    Map.of(
                        "id", "transacoes",
                        "nome", "Relatório de Transações",
                        "descricao", "Relatório detalhado de todas as transações",
                        "endpoint", "/api/relatorios/transacoes",
                        "parametros", List.of("dataInicio", "dataFim")
                    ),
                    Map.of(
                        "id", "faturas",
                        "nome", "Relatório de Faturas",
                        "descricao", "Relatório detalhado de todas as faturas",
                        "endpoint", "/api/relatorios/faturas",
                        "parametros", List.of("dataInicio", "dataFim")
                    )
                ),
                "instrucoes", Map.of(
                    "formato_data", "yyyy-MM-dd",
                    "exemplo_data_inicio", "2024-01-01",
                    "exemplo_data_fim", "2024-12-31",
                    "periodo_padrao", "Último mês se não informado"
                )
            );
            
            return ResponseEntity.ok(relatorios);
            
        } catch (Exception e) {
            log.error("❌ Erro ao listar relatórios disponíveis: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
