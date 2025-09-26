package com.consumoesperto.controller;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.TransacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Controller responsável por gerenciar operações relacionadas a transações financeiras
 * 
 * Este controller expõe endpoints para CRUD completo de transações, incluindo
 * criação, busca, atualização e exclusão. Todas as operações são validadas
 * para garantir que o usuário só acesse suas próprias transações.
 * 
 * Funcionalidades principais:
 * - CRUD completo de transações
 * - Busca por período, categoria e tipo
 * - Resumo financeiro do usuário
 * - Validação de segurança por usuário
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/transacoes") // Base path para endpoints de transações
@RequiredArgsConstructor // Lombok: gera construtor com campos final
@Tag(name = "Transações", description = "Endpoints para gestão de transações financeiras")
@CrossOrigin(origins = {"http://localhost:4200", "https://22e294954ab2.ngrok-free.app"})
public class TransacaoController {

    // Serviço responsável pela lógica de negócio das transações
    private final TransacaoService transacaoService;

    /**
     * Cria uma nova transação financeira para o usuário autenticado
     * 
     * Endpoint para registrar receitas ou despesas do usuário.
     * A transação é automaticamente associada ao usuário logado.
     * 
     * @param transacaoDTO Dados da transação a ser criada
     * @param currentUser Usuário autenticado (injetado automaticamente)
     * @return Transação criada com ID e dados completos
     */
    @PostMapping
    @Operation(summary = "Criar nova transação", description = "Cria uma nova transação financeira para o usuário autenticado")
    public ResponseEntity<TransacaoDTO> criarTransacao(
            @Valid @RequestBody TransacaoDTO transacaoDTO,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Cria a transação associada ao usuário logado
        TransacaoDTO transacaoCriada = transacaoService.criarTransacao(transacaoDTO, currentUser.getId());
        return ResponseEntity.ok(transacaoCriada);
    }

    /**
     * Busca uma transação específica por ID
     * 
     * Retorna os detalhes de uma transação, mas apenas se ela pertencer
     * ao usuário autenticado (segurança).
     * 
     * @param id ID da transação a ser buscada
     * @param currentUser Usuário autenticado
     * @return Dados da transação encontrada
     */
    @GetMapping("/{id}")
    @Operation(summary = "Buscar transação por ID", description = "Busca uma transação específica por ID")
    public ResponseEntity<TransacaoDTO> buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca a transação verificando se pertence ao usuário
        TransacaoDTO transacao = transacaoService.buscarPorId(id, currentUser.getId());
        return ResponseEntity.ok(transacao);
    }

    /**
     * Lista todas as transações do usuário autenticado
     * 
     * Retorna uma lista completa de todas as transações (receitas e despesas)
     * do usuário logado, ordenadas por data.
     * 
     * @param currentUser Usuário autenticado
     * @return Lista de todas as transações do usuário
     */
    @GetMapping
    @Operation(summary = "Listar todas as transações", description = "Lista todas as transações do usuário autenticado")
    public ResponseEntity<List<TransacaoDTO>> buscarPorUsuario(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca todas as transações do usuário logado
        List<TransacaoDTO> transacoes = transacaoService.buscarPorUsuarioId(currentUser.getId());
        return ResponseEntity.ok(transacoes);
    }

    /**
     * Atualiza uma transação existente
     * 
     * Permite modificar dados de uma transação, mas apenas se ela pertencer
     * ao usuário autenticado. Validações são aplicadas antes da atualização.
     * 
     * @param id ID da transação a ser atualizada
     * @param transacaoDTO Novos dados da transação
     * @param currentUser Usuário autenticado
     * @return Transação atualizada
     */
    @PutMapping("/{id}")
    @Operation(summary = "Atualizar transação", description = "Atualiza uma transação existente")
    public ResponseEntity<TransacaoDTO> atualizarTransacao(
            @PathVariable Long id,
            @Valid @RequestBody TransacaoDTO transacaoDTO,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Atualiza a transação verificando propriedade do usuário
        TransacaoDTO transacaoAtualizada = transacaoService.atualizarTransacao(id, transacaoDTO, currentUser.getId());
        return ResponseEntity.ok(transacaoAtualizada);
    }

    /**
     * Remove uma transação do sistema
     * 
     * Exclui permanentemente uma transação, mas apenas se ela pertencer
     * ao usuário autenticado. ATENÇÃO: Esta operação é irreversível.
     * 
     * @param id ID da transação a ser excluída
     * @param currentUser Usuário autenticado
     * @return Resposta vazia indicando sucesso
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar transação", description = "Remove uma transação do sistema")
    public ResponseEntity<Void> deletarTransacao(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Remove a transação verificando propriedade do usuário
        transacaoService.deletarTransacao(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Busca transações dentro de um período específico
     * 
     * Filtra transações por intervalo de datas, útil para relatórios
     * mensais, trimestrais ou personalizados.
     * 
     * @param dataInicio Data/hora de início do período
     * @param dataFim Data/hora de fim do período
     * @param currentUser Usuário autenticado
     * @return Lista de transações no período especificado
     */
    @GetMapping("/periodo")
    @Operation(summary = "Buscar transações por período", description = "Busca transações dentro de um período específico")
    public ResponseEntity<List<TransacaoDTO>> buscarPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataFim,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca transações no período especificado
        List<TransacaoDTO> transacoes = transacaoService.buscarPorPeriodo(currentUser.getId(), dataInicio, dataFim);
        return ResponseEntity.ok(transacoes);
    }

    /**
     * Filtra transações por categoria específica
     * 
     * Útil para análises de gastos por tipo de despesa
     * (alimentação, transporte, lazer, etc.).
     * 
     * @param categoriaId ID da categoria para filtrar
     * @param currentUser Usuário autenticado
     * @return Lista de transações da categoria especificada
     */
    @GetMapping("/categoria/{categoriaId}")
    @Operation(summary = "Buscar transações por categoria", description = "Filtra transações por categoria específica")
    public ResponseEntity<List<TransacaoDTO>> buscarPorCategoria(
            @PathVariable Long categoriaId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca transações da categoria especificada
        List<TransacaoDTO> transacoes = transacaoService.buscarPorCategoria(currentUser.getId(), categoriaId);
        return ResponseEntity.ok(transacoes);
    }

    /**
     * Filtra transações por tipo (receita ou despesa)
     * 
     * Permite separar entradas e saídas de dinheiro para
     * análises de fluxo de caixa.
     * 
     * @param tipo Tipo da transação (RECEITA ou DESPESA)
     * @param currentUser Usuário autenticado
     * @return Lista de transações do tipo especificado
     */
    @GetMapping("/tipo/{tipo}")
    @Operation(summary = "Buscar transações por tipo (RECEITA/DESPESA)", description = "Filtra transações por tipo específico")
    public ResponseEntity<List<TransacaoDTO>> buscarPorTipo(
            @PathVariable Transacao.TipoTransacao tipo,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca transações do tipo especificado
        List<TransacaoDTO> transacoes = transacaoService.buscarPorTipo(currentUser.getId(), tipo);
        return ResponseEntity.ok(transacoes);
    }

    /**
     * Obtém um resumo financeiro das transações do usuário
     * 
     * Retorna estatísticas como total de receitas, despesas,
     * saldo atual e outras métricas financeiras.
     * 
     * @param currentUser Usuário autenticado
     * @return Objeto com resumo financeiro completo
     */
    @GetMapping("/resumo")
    @Operation(summary = "Obter resumo das transações do usuário", description = "Retorna resumo financeiro completo das transações")
    public ResponseEntity<Object> obterResumo(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Obtém resumo financeiro do usuário
        Object resumo = transacaoService.obterResumo(currentUser.getId());
        return ResponseEntity.ok(resumo);
    }
}
