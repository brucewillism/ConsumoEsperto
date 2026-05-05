package com.consumoesperto.controller;

import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.ReportService;
import com.consumoesperto.service.RelatorioFinanceiroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

/**
 * Controller responsável por gerenciar relatórios financeiros do usuário
 * 
 * Este controller expõe endpoints para geração de diversos tipos de relatórios
 * financeiros, incluindo relatórios mensais, anuais, por categoria e alertas.
 * Todos os relatórios são baseados nas transações e dados financeiros do
 * usuário autenticado.
 * 
 * Funcionalidades principais:
 * - Relatórios mensais com resumo financeiro
 * - Relatórios anuais com análise de tendências
 * - Alertas financeiros e notificações
 * - Relatórios por categoria de gastos
 * - Validação de segurança por usuário
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/relatorios") // Base path para endpoints de relatórios
@RequiredArgsConstructor // Lombok: gera construtor com campos final
@Tag(name = "Relatórios", description = "Endpoints para geração de relatórios financeiros")
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"}) // Permite CORS de qualquer origem
public class RelatorioController {

    // Serviço responsável pela geração de relatórios financeiros
    private final RelatorioFinanceiroService relatorioService;
    private final ReportService reportService;

    /**
     * Gera relatório financeiro mensal do usuário
     * 
     * Endpoint para obter um resumo completo das finanças do usuário
     * em um mês específico, incluindo receitas, despesas, saldo e
     * análise por categoria.
     * 
     * @param ano Ano do relatório (ex: 2024)
     * @param mes Mês do relatório (1-12)
     * @param currentUser Usuário autenticado (injetado automaticamente)
     * @return Mapa contendo dados do relatório mensal
     */
    @GetMapping("/mensal")
    @Operation(summary = "Relatório mensal", description = "Gera relatório financeiro mensal do usuário")
    public ResponseEntity<Map<String, Object>> relatorioMensal(
            @RequestParam int ano,
            @RequestParam int mes,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Gera relatório mensal através do serviço
        Map<String, Object> relatorio = relatorioService.gerarRelatorioMensal(currentUser.getId(), ano, mes);
        return ResponseEntity.ok(relatorio);
    }

    /**
     * Descarrega o relatório mensal em PDF (gerado em memória; não é guardado em disco no servidor).
     */
    @GetMapping(value = "/mensal.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Relatório mensal em PDF", description = "Gera PDF com resumo do mês (transações confirmadas e metas)")
    public ResponseEntity<byte[]> relatorioMensalPdf(
            @RequestParam int ano,
            @RequestParam int mes,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return reportService.gerarRelatorioMensal(currentUser.getId(), mes, ano)
            .map(g -> {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentDisposition(
                    ContentDisposition.attachment().filename(g.nomeArquivo()).build()
                );
                return ResponseEntity.ok().headers(headers).body(g.bytes());
            })
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Gera relatório financeiro anual do usuário
     * 
     * Endpoint para obter uma visão anual das finanças, incluindo
     * tendências, sazonalidade e análise de crescimento/declínio
     * financeiro ao longo do ano.
     * 
     * @param ano Ano do relatório (ex: 2024)
     * @param currentUser Usuário autenticado
     * @return Mapa contendo dados do relatório anual
     */
    @GetMapping("/anual")
    @Operation(summary = "Relatório anual", description = "Gera relatório financeiro anual do usuário")
    public ResponseEntity<Map<String, Object>> relatorioAnual(
            @RequestParam int ano,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Gera relatório anual através do serviço
        Map<String, Object> relatorio = relatorioService.gerarRelatorioAnual(currentUser.getId(), ano);
        return ResponseEntity.ok(relatorio);
    }

    /**
     * Gera alertas financeiros para o usuário
     * 
     * Endpoint para obter notificações e alertas importantes sobre
     * a situação financeira, incluindo faturas vencidas, limites
     * de crédito próximos e recomendações de economia.
     * 
     * @param currentUser Usuário autenticado
     * @return Mapa contendo alertas e notificações financeiras
     */
    @GetMapping("/alertas")
    @Operation(summary = "Gerar alertas financeiros", description = "Gera alertas e notificações financeiras importantes")
    public ResponseEntity<Map<String, Object>> alertas(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Gera alertas financeiros através do serviço
        Map<String, Object> alertas = relatorioService.gerarAlertas(currentUser.getId());
        return ResponseEntity.ok(alertas);
    }

    /**
     * Gera relatório financeiro por categoria
     * 
     * Endpoint para analisar gastos e receitas agrupados por categoria,
     * permitindo identificar padrões de consumo e áreas de oportunidade
     * para economia.
     * 
     * @param ano Ano do relatório (ex: 2024)
     * @param mes Mês do relatório (1-12)
     * @param currentUser Usuário autenticado
     * @return Mapa contendo análise financeira por categoria
     */
    @GetMapping("/categoria")
    @Operation(summary = "Gerar relatório por categoria", description = "Gera relatório financeiro agrupado por categoria")
    public ResponseEntity<Map<String, Object>> relatorioPorCategoria(
            @RequestParam int ano,
            @RequestParam int mes,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Gera relatório por categoria através do serviço
        Map<String, Object> relatorio = relatorioService.gerarRelatorioPorCategoria(currentUser.getId(), ano, mes);
        return ResponseEntity.ok(relatorio);
    }

    @GetMapping("/categoria/mes-atual")
    @Operation(summary = "Despesas por categoria do mês atual", description = "Retorna o somatório de despesas agrupado por categoria no mês atual")
    public ResponseEntity<Map<String, Object>> despesasPorCategoriaMesAtual(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Map<String, Object> relatorio = relatorioService.gerarDespesasPorCategoriaMesAtual(currentUser.getId());
        return ResponseEntity.ok(relatorio);
    }

    @GetMapping(value = "/exportar-ir", produces = "text/csv;charset=UTF-8")
    @Operation(summary = "Exportar CSV para IR", description = "Despesas confirmadas do ano-calendário (padrão: ano anterior), agrupadas por categoria e CNPJ")
    public void exportarIr(
            @RequestParam(required = false) Integer ano,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletResponse response) throws IOException {
        int anoIr = ano != null ? ano : LocalDate.now().getYear() - 1;
        String filename = "consumo-esperto-ir-" + anoIr + ".csv";
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        try (PrintWriter writer = response.getWriter()) {
            relatorioService.escreverCsvIr(currentUser.getId(), anoIr, writer);
        }
    }

    @GetMapping(value = "/exportar-ir.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Exportar PDF para IR", description = "Despesas confirmadas do ano-calendário (padrão: ano anterior), por categoria e CNPJ — mesmo critério do CSV")
    public ResponseEntity<byte[]> exportarIrPdf(
            @RequestParam(required = false) Integer ano,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        int anoIr = ano != null ? ano : LocalDate.now().getYear() - 1;
        ReportService.RelatorioPdf pdf = reportService.gerarRelatorioIrPdf(currentUser.getId(), anoIr);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
            ContentDisposition.attachment().filename(pdf.nomeArquivo()).build()
        );
        return ResponseEntity.ok().headers(headers).body(pdf.bytes());
    }
}
