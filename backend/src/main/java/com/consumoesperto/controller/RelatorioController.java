package com.consumoesperto.controller;

import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.RelatorioFinanceiroService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
@Api(tags = "Relatórios Financeiros") // Documentação Swagger
@CrossOrigin(origins = "*") // Permite CORS de qualquer origem
public class RelatorioController {

    // Serviço responsável pela geração de relatórios financeiros
    private final RelatorioFinanceiroService relatorioService;

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
    @ApiOperation("Gerar relatório mensal")
    public ResponseEntity<Map<String, Object>> relatorioMensal(
            @RequestParam int ano,
            @RequestParam int mes,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Gera relatório mensal através do serviço
        Map<String, Object> relatorio = relatorioService.gerarRelatorioMensal(currentUser.getId(), ano, mes);
        return ResponseEntity.ok(relatorio);
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
    @ApiOperation("Gerar relatório anual")
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
    @ApiOperation("Gerar alertas financeiros")
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
    @ApiOperation("Gerar relatório por categoria")
    public ResponseEntity<Map<String, Object>> relatorioPorCategoria(
            @RequestParam int ano,
            @RequestParam int mes,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Gera relatório por categoria através do serviço
        Map<String, Object> relatorio = relatorioService.gerarRelatorioPorCategoria(currentUser.getId(), ano, mes);
        return ResponseEntity.ok(relatorio);
    }
}
