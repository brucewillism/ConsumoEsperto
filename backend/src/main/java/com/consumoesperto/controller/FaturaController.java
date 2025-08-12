package com.consumoesperto.controller;

import com.consumoesperto.dto.FaturaDTO;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.FaturaService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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
 * Controller responsável por gerenciar operações relacionadas a faturas de cartão de crédito
 * 
 * Este controller expõe endpoints para CRUD completo de faturas, incluindo
 * criação, busca, atualização e exclusão. Também fornece métodos para
 * consultas específicas por status, período e cartão de crédito.
 * 
 * Funcionalidades principais:
 * - CRUD completo de faturas
 * - Consultas por status, período e cartão
 * - Cálculo de totais por status
 * - Busca de faturas vencidas
 * - Validação de segurança por usuário
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/faturas") // Base path para endpoints de faturas
@RequiredArgsConstructor // Lombok: gera construtor com campos final
@Api(tags = "Faturas") // Documentação Swagger
@CrossOrigin(origins = "*") // Permite CORS de qualquer origem
public class FaturaController {

    // Serviço responsável pela lógica de negócio das faturas
    private final FaturaService faturaService;

    /**
     * Cria uma nova fatura de cartão de crédito
     * 
     * Endpoint para registrar uma nova fatura no sistema.
     * A fatura é criada com base nos dados fornecidos.
     * 
     * @param faturaDTO Dados da fatura a ser criada
     * @param currentUser Usuário autenticado (injetado automaticamente)
     * @return Fatura criada com ID e dados completos
     */
    @PostMapping
    @ApiOperation("Criar nova fatura")
    public ResponseEntity<FaturaDTO> criarFatura(
            @Valid @RequestBody FaturaDTO faturaDTO,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Cria a fatura através do serviço
        FaturaDTO faturaCriada = faturaService.criarFatura(faturaDTO);
        return ResponseEntity.ok(faturaCriada);
    }

    /**
     * Busca uma fatura específica por ID
     * 
     * Retorna os detalhes de uma fatura, mas apenas se ela pertencer
     * ao usuário autenticado (segurança).
     * 
     * @param id ID da fatura a ser buscada
     * @param currentUser Usuário autenticado
     * @return Dados da fatura encontrada
     */
    @GetMapping("/{id}")
    @ApiOperation("Buscar fatura por ID")
    public ResponseEntity<FaturaDTO> buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca a fatura verificando se pertence ao usuário
        FaturaDTO fatura = faturaService.buscarPorId(id, currentUser.getId());
        return ResponseEntity.ok(fatura);
    }

    /**
     * Lista todas as faturas do usuário autenticado
     * 
     * Retorna uma lista completa de todas as faturas
     * do usuário logado, ordenadas por data de vencimento.
     * 
     * @param currentUser Usuário autenticado
     * @return Lista de todas as faturas do usuário
     */
    @GetMapping
    @ApiOperation("Listar todas as faturas do usuário")
    public ResponseEntity<List<FaturaDTO>> buscarPorUsuario(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca todas as faturas do usuário logado
        List<FaturaDTO> faturas = faturaService.buscarPorUsuarioId(currentUser.getId());
        return ResponseEntity.ok(faturas);
    }

    /**
     * Lista faturas de um cartão de crédito específico
     * 
     * Filtra faturas por cartão de crédito, útil para
     * acompanhar gastos de um cartão específico.
     * 
     * @param cartaoId ID do cartão de crédito para filtrar
     * @param currentUser Usuário autenticado
     * @return Lista de faturas do cartão especificado
     */
    @GetMapping("/cartao/{cartaoId}")
    @ApiOperation("Listar faturas por cartão de crédito")
    public ResponseEntity<List<FaturaDTO>> buscarPorCartaoCredito(
            @PathVariable Long cartaoId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca faturas do cartão especificado
        List<FaturaDTO> faturas = faturaService.buscarPorCartaoCreditoId(cartaoId, currentUser.getId());
        return ResponseEntity.ok(faturas);
    }

    /**
     * Atualiza uma fatura existente
     * 
     * Permite modificar dados de uma fatura, mas apenas se ela pertencer
     * ao usuário autenticado. Validações são aplicadas antes da atualização.
     * 
     * @param id ID da fatura a ser atualizada
     * @param faturaDTO Novos dados da fatura
     * @param currentUser Usuário autenticado
     * @return Fatura atualizada
     */
    @PutMapping("/{id}")
    @ApiOperation("Atualizar fatura")
    public ResponseEntity<FaturaDTO> atualizarFatura(
            @PathVariable Long id,
            @Valid @RequestBody FaturaDTO faturaDTO,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Atualiza a fatura verificando propriedade do usuário
        FaturaDTO faturaAtualizada = faturaService.atualizarFatura(id, faturaDTO, currentUser.getId());
        return ResponseEntity.ok(faturaAtualizada);
    }

    /**
     * Remove uma fatura do sistema
     * 
     * Exclui permanentemente uma fatura, mas apenas se ela pertencer
     * ao usuário autenticado. ATENÇÃO: Esta operação é irreversível.
     * 
     * @param id ID da fatura a ser excluída
     * @param currentUser Usuário autenticado
     * @return Resposta vazia indicando sucesso
     */
    @DeleteMapping("/{id}")
    @ApiOperation("Deletar fatura")
    public ResponseEntity<Void> deletarFatura(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Remove a fatura verificando propriedade do usuário
        faturaService.deletarFatura(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Filtra faturas por status específico
     * 
     * Útil para buscar faturas pagas, pendentes, vencidas, etc.
     * 
     * @param status Status da fatura para filtrar
     * @param currentUser Usuário autenticado
     * @return Lista de faturas com o status especificado
     */
    @GetMapping("/status/{status}")
    @ApiOperation("Buscar faturas por status")
    public ResponseEntity<List<FaturaDTO>> buscarPorStatus(
            @PathVariable Fatura.StatusFatura status,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca faturas do status especificado
        List<FaturaDTO> faturas = faturaService.buscarPorStatus(currentUser.getId(), status);
        return ResponseEntity.ok(faturas);
    }

    /**
     * Busca faturas dentro de um período específico
     * 
     * Filtra faturas por intervalo de datas, útil para relatórios
     * mensais, trimestrais ou personalizados.
     * 
     * @param dataInicio Data/hora de início do período
     * @param dataFim Data/hora de fim do período
     * @param currentUser Usuário autenticado
     * @return Lista de faturas no período especificado
     */
    @GetMapping("/periodo")
    @ApiOperation("Buscar faturas por período")
    public ResponseEntity<List<FaturaDTO>> buscarPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataFim,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca faturas no período especificado
        List<FaturaDTO> faturas = faturaService.buscarPorPeriodo(currentUser.getId(), dataInicio, dataFim);
        return ResponseEntity.ok(faturas);
    }

    /**
     * Obtém o valor total das faturas por status
     * 
     * Calcula a soma dos valores de todas as faturas
     * com um status específico (ex: total de faturas pendentes).
     * 
     * @param status Status das faturas para calcular o total
     * @param currentUser Usuário autenticado
     * @return Valor total das faturas com o status especificado
     */
    @GetMapping("/total/{status}")
    @ApiOperation("Obter total de faturas por status")
    public ResponseEntity<BigDecimal> getTotalPorStatus(
            @PathVariable Fatura.StatusFatura status,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Calcula o total das faturas por status
        BigDecimal total = faturaService.getTotalFaturasPorStatus(currentUser.getId(), status);
        return ResponseEntity.ok(total);
    }

    /**
     * Busca todas as faturas vencidas do usuário
     * 
     * Retorna faturas que já passaram da data de vencimento,
     * útil para alertas e controle de inadimplência.
     * 
     * @param currentUser Usuário autenticado
     * @return Lista de faturas vencidas
     */
    @GetMapping("/vencidas")
    @ApiOperation("Buscar faturas vencidas")
    public ResponseEntity<List<FaturaDTO>> buscarFaturasVencidas(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca faturas vencidas do usuário
        List<FaturaDTO> faturas = faturaService.buscarFaturasVencidas(currentUser.getId());
        return ResponseEntity.ok(faturas);
    }
}
