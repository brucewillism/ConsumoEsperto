package com.consumoesperto.controller;

import com.consumoesperto.dto.CompraParceladaDTO;
import com.consumoesperto.model.CompraParcelada;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.CompraParceladaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller responsável por gerenciar compras parceladas do usuário
 * 
 * Este controller expõe endpoints para CRUD completo de compras parceladas,
 * incluindo criação, busca, atualização e exclusão. Também fornece métodos
 * para consultas específicas por status, período, cartão de crédito e
 * análise de parcelas ativas e vencendo.
 * 
 * Funcionalidades principais:
 * - CRUD completo de compras parceladas
 * - Consultas por status, período e cartão de crédito
 * - Busca de parcelas ativas e vencendo
 * - Cálculo de totais por status
 * - Validação de segurança por usuário
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/compras-parceladas") // Base path para endpoints de compras parceladas
@RequiredArgsConstructor // Lombok: gera construtor com campos final
@Tag(name = "Compras Parceladas", description = "Endpoints para gestão de compras parceladas")
<<<<<<< HEAD
@CrossOrigin(origins = {"http://localhost:4200", "https://0d723f1e294f.ngrok-free.app"}) // Permite CORS de qualquer origem
=======
@CrossOrigin(origins = {"http://localhost:4200", "https://22e294954ab2.ngrok-free.app"}) // Permite CORS de qualquer origem
>>>>>>> origin/main
public class CompraParceladaController {

    // Serviço responsável pela lógica de negócio das compras parceladas
    private final CompraParceladaService compraParceladaService;

    /**
     * Cria uma nova compra parcelada para o usuário
     * 
     * Endpoint para registrar uma nova compra parcelada no sistema.
     * A compra é criada com base nos dados fornecidos e pode ser
     * associada a um cartão de crédito específico.
     * 
     * @param compraParceladaDTO Dados da compra parcelada a ser criada
     * @param currentUser Usuário autenticado (injetado automaticamente)
     * @return Compra parcelada criada com ID e dados completos
     */
    @PostMapping
    @Operation(summary = "Criar compra parcelada", description = "Cria uma nova compra parcelada para o usuário")
    public ResponseEntity<CompraParceladaDTO> criarCompraParcelada(
            @Valid @RequestBody CompraParceladaDTO compraParceladaDTO,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Cria a compra parcelada através do serviço
        CompraParceladaDTO compraCriada = compraParceladaService.criarCompraParcelada(compraParceladaDTO);
        return ResponseEntity.ok(compraCriada);
    }

    /**
     * Busca uma compra parcelada específica por ID
     * 
     * Retorna os detalhes de uma compra parcelada, mas apenas se ela
     * pertencer ao usuário autenticado (segurança).
     * 
     * @param id ID da compra parcelada a ser buscada
     * @param currentUser Usuário autenticado
     * @return Dados da compra parcelada encontrada
     */
    @GetMapping("/{id}")
    @Operation(summary = "Buscar compra por ID", description = "Busca uma compra parcelada específica por ID")
    public ResponseEntity<CompraParceladaDTO> buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca a compra verificando se pertence ao usuário
        CompraParceladaDTO compra = compraParceladaService.buscarPorId(id, currentUser.getId());
        return ResponseEntity.ok(compra);
    }

    /**
     * Lista todas as compras parceladas do usuário autenticado
     * 
     * Retorna uma lista completa de todas as compras parceladas
     * do usuário logado, incluindo status e informações das parcelas.
     * 
     * @param currentUser Usuário autenticado
     * @return Lista de todas as compras parceladas do usuário
     */
    @GetMapping
    @Operation(summary = "Listar todas as compras parceladas do usuário", description = "Retorna lista completa de todas as compras parceladas do usuário")
    public ResponseEntity<List<CompraParceladaDTO>> buscarPorUsuario(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca todas as compras parceladas do usuário
        List<CompraParceladaDTO> compras = compraParceladaService.buscarPorUsuarioId(currentUser.getId());
        return ResponseEntity.ok(compras);
    }

    /**
     * Lista compras parceladas de um cartão de crédito específico
     * 
     * Endpoint para filtrar compras parceladas por cartão de crédito,
     * permitindo análise detalhada dos gastos em cada cartão.
     * 
     * @param cartaoId ID do cartão de crédito
     * @param currentUser Usuário autenticado
     * @return Lista de compras parceladas do cartão especificado
     */
    @GetMapping("/cartao/{cartaoId}")
    @Operation(summary = "Listar compras parceladas por cartão de crédito", description = "Retorna compras parceladas de um cartão de crédito específico")
    public ResponseEntity<List<CompraParceladaDTO>> buscarPorCartaoCredito(
            @PathVariable Long cartaoId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca compras parceladas do cartão específico
        List<CompraParceladaDTO> compras = compraParceladaService.buscarPorCartaoCreditoId(cartaoId, currentUser.getId());
        return ResponseEntity.ok(compras);
    }

    /**
     * Atualiza uma compra parcelada existente
     * 
     * Endpoint para modificar dados de uma compra parcelada, mas apenas
     * se ela pertencer ao usuário autenticado (segurança).
     * 
     * @param id ID da compra parcelada a ser atualizada
     * @param compraParceladaDTO Novos dados da compra parcelada
     * @param currentUser Usuário autenticado
     * @return Compra parcelada atualizada com dados modificados
     */
    @PutMapping("/{id}")
    @Operation(summary = "Atualizar compra parcelada", description = "Atualiza dados de uma compra parcelada existente")
    public ResponseEntity<CompraParceladaDTO> atualizarCompraParcelada(
            @PathVariable Long id,
            @Valid @RequestBody CompraParceladaDTO compraParceladaDTO,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Atualiza a compra parcelada verificando propriedade
        CompraParceladaDTO compraAtualizada = compraParceladaService.atualizarCompraParcelada(id, compraParceladaDTO, currentUser.getId());
        return ResponseEntity.ok(compraAtualizada);
    }

    /**
     * Remove uma compra parcelada do sistema
     * 
     * Endpoint para deletar uma compra parcelada, mas apenas se ela
     * pertencer ao usuário autenticado (segurança).
     * 
     * @param id ID da compra parcelada a ser deletada
     * @param currentUser Usuário autenticado
     * @return Resposta sem conteúdo (204 No Content)
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar compra parcelada", description = "Remove permanentemente uma compra parcelada")
    public ResponseEntity<Void> deletarCompraParcelada(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Remove a compra parcelada verificando propriedade
        compraParceladaService.deletarCompraParcelada(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Busca compras parceladas por status específico
     * 
     * Endpoint para filtrar compras parceladas por status (ATIVA, FINALIZADA,
     * CANCELADA), permitindo análise por situação da compra.
     * 
     * @param status Status das compras parceladas a serem buscadas
     * @param currentUser Usuário autenticado
     * @return Lista de compras parceladas com o status especificado
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "Buscar compras parceladas por status", description = "Retorna compras parceladas filtradas por status específico")
    public ResponseEntity<List<CompraParceladaDTO>> buscarPorStatus(
            @PathVariable CompraParcelada.StatusCompra status,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca compras parceladas por status
        List<CompraParceladaDTO> compras = compraParceladaService.buscarPorStatus(currentUser.getId(), status);
        return ResponseEntity.ok(compras);
    }

    /**
     * Busca compras parceladas em um período específico
     * 
     * Endpoint para filtrar compras parceladas por intervalo de datas,
     * permitindo análise temporal dos gastos parcelados.
     * 
     * @param dataInicio Data de início do período (formato ISO)
     * @param dataFim Data de fim do período (formato ISO)
     * @param currentUser Usuário autenticado
     * @return Lista de compras parceladas no período especificado
     */
    @GetMapping("/periodo")
    @Operation(summary = "Buscar compras parceladas por período", description = "Retorna compras parceladas em um período específico")
    public ResponseEntity<List<CompraParceladaDTO>> buscarPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataFim,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca compras parceladas no período especificado
        List<CompraParceladaDTO> compras = compraParceladaService.buscarPorPeriodo(currentUser.getId(), dataInicio, dataFim);
        return ResponseEntity.ok(compras);
    }

    /**
     * Calcula o total de compras parceladas por status
     * 
     * Endpoint para obter o valor total das compras parceladas
     * agrupadas por status, facilitando análise financeira.
     * 
     * @param status Status das compras para cálculo do total
     * @param currentUser Usuário autenticado
     * @return Valor total das compras parceladas com o status especificado
     */
    @GetMapping("/total/{status}")
    @Operation(summary = "Obter total de compras parceladas por status", description = "Retorna valor total das compras parceladas por status")
    public ResponseEntity<BigDecimal> getTotalPorStatus(
            @PathVariable CompraParcelada.StatusCompra status,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Calcula total por status através do serviço
        BigDecimal total = compraParceladaService.getTotalPorStatus(currentUser.getId(), status);
        return ResponseEntity.ok(total);
    }

    /**
     * Busca parcelas ativas (não finalizadas) do usuário
     * 
     * Endpoint para identificar compras parceladas em andamento,
     * facilitando o controle de gastos mensais recorrentes.
     * 
     * @param currentUser Usuário autenticado
     * @return Lista de compras parceladas com parcelas ativas
     */
    @GetMapping("/parcelas-ativas")
    @Operation(summary = "Buscar parcelas ativas", description = "Retorna compras parceladas com parcelas ativas (não finalizadas)")
    public ResponseEntity<List<CompraParceladaDTO>> buscarParcelasAtivas(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca parcelas ativas através do serviço
        List<CompraParceladaDTO> compras = compraParceladaService.buscarParcelasAtivas(currentUser.getId());
        return ResponseEntity.ok(compras);
    }

    /**
     * Busca parcelas vencendo nos próximos 30 dias
     * 
     * Endpoint para identificar parcelas que vencem em breve,
     * facilitando o planejamento financeiro e evitando atrasos.
     * 
     * @param currentUser Usuário autenticado
     * @return Lista de compras parceladas com parcelas vencendo em breve
     */
    @GetMapping("/parcelas-vencendo")
    @Operation(summary = "Buscar parcelas vencendo nos próximos 30 dias", description = "Retorna parcelas que vencem nos próximos 30 dias")
    public ResponseEntity<List<CompraParceladaDTO>> buscarParcelasVencendo(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca parcelas vencendo em breve através do serviço
        List<CompraParceladaDTO> compras = compraParceladaService.buscarParcelasVencendo(currentUser.getId());
        return ResponseEntity.ok(compras);
    }
}
