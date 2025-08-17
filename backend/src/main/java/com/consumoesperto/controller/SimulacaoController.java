package com.consumoesperto.controller;

import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.SimulacaoCompraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Controller responsável por gerenciar simulações financeiras do usuário
 * 
 * Este controller expõe endpoints para simular diferentes cenários financeiros,
 * incluindo compras parceladas, compras à vista e cálculo de economia necessária
 * para atingir metas financeiras. As simulações consideram o perfil financeiro
 * atual do usuário para fornecer resultados realistas.
 * 
 * Funcionalidades principais:
 * - Simulação de compras parceladas com análise de juros
 * - Simulação de compras à vista com desconto
 * - Cálculo de economia necessária para metas
 * - Análise de impacto no orçamento mensal
 * - Recomendações personalizadas baseadas no perfil do usuário
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/simulacoes") // Base path para endpoints de simulações
@RequiredArgsConstructor // Lombok: gera construtor com campos final
@Tag(name = "Simulações", description = "Endpoints para simulações financeiras")
@CrossOrigin(origins = "*") // Permite CORS de qualquer origem
public class SimulacaoController {

    // Serviço responsável pela lógica de simulações financeiras
    private final SimulacaoCompraService simulacaoService;

    /**
     * Simula uma compra parcelada considerando o perfil financeiro do usuário
     * 
     * Endpoint para simular o impacto de uma compra parcelada no orçamento
     * mensal, incluindo cálculo de juros, valor das parcelas e análise
     * de viabilidade baseada na renda atual.
     * 
     * @param valorCompra Valor total da compra (deve ser maior que 0.01)
     * @param numeroParcelas Número de parcelas desejadas (deve ser maior que 0)
     * @param currentUser Usuário autenticado (injetado automaticamente)
     * @return Mapa contendo detalhes da simulação parcelada
     */
    @GetMapping("/compra-parcelada")
    @Operation(summary = "Simular compra parcelada", description = "Simula uma compra parcelada considerando o perfil financeiro")
    public ResponseEntity<Map<String, Object>> simularCompraParcelada(
            @RequestParam @NotNull @DecimalMin("0.01") BigDecimal valorCompra,
            @RequestParam @NotNull @Min(1) int numeroParcelas,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Executa simulação de compra parcelada através do serviço
        Map<String, Object> simulacao = simulacaoService.simularCompra(currentUser.getId(), valorCompra, numeroParcelas);
        return ResponseEntity.ok(simulacao);
    }

    /**
     * Simula uma compra à vista com análise de desconto e impacto financeiro
     * 
     * Endpoint para simular o impacto de uma compra à vista no orçamento,
     * considerando possíveis descontos e a disponibilidade de recursos
     * para pagamento imediato.
     * 
     * @param valorCompra Valor total da compra (deve ser maior que 0.01)
     * @param currentUser Usuário autenticado
     * @return Mapa contendo detalhes da simulação à vista
     */
    @GetMapping("/compra-vista")
    @Operation(summary = "Simular compra à vista", description = "Simula uma compra à vista com análise de desconto")
    public ResponseEntity<Map<String, Object>> simularCompraAVista(
            @RequestParam @NotNull @DecimalMin("0.01") BigDecimal valorCompra,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Executa simulação de compra à vista através do serviço
        Map<String, Object> simulacao = simulacaoService.simularCompraAVista(currentUser.getId(), valorCompra);
        return ResponseEntity.ok(simulacao);
    }

    /**
     * Calcula a economia mensal necessária para atingir uma meta financeira
     * 
     * Endpoint para determinar quanto o usuário precisa economizar mensalmente
     * para atingir um objetivo financeiro em um período específico, considerando
     * a renda atual e gastos mensais.
     * 
     * @param valorCompra Valor da meta financeira (deve ser maior que 0.01)
     * @param mesesDesejados Período em meses para atingir a meta (deve ser maior que 0)
     * @param currentUser Usuário autenticado
     * @return Mapa contendo plano de economia e recomendações
     */
    @GetMapping("/economia-necessaria")
    @Operation(summary = "Calcular economia necessária para meta", description = "Calcula economia mensal necessária para atingir meta financeira")
    public ResponseEntity<Map<String, Object>> calcularEconomiaNecessaria(
            @RequestParam @NotNull @DecimalMin("0.01") BigDecimal valorCompra,
            @RequestParam @NotNull @Min(1) int mesesDesejados,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Calcula economia necessária através do serviço
        Map<String, Object> resultado = simulacaoService.calcularEconomiaNecessaria(currentUser.getId(), valorCompra, mesesDesejados);
        return ResponseEntity.ok(resultado);
    }
}
