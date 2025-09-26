package com.consumoesperto.controller;

import com.consumoesperto.dto.CartaoCreditoDTO;
import com.consumoesperto.security.UserPrincipal;
import com.consumoesperto.service.CartaoCreditoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

/**
 * Controller responsável por gerenciar operações relacionadas a cartões de crédito
 * 
 * Este controller expõe endpoints para CRUD completo de cartões de crédito,
 * incluindo criação, busca, atualização e exclusão. Também fornece métodos
 * para consultar limites de crédito disponíveis.
 * 
 * Funcionalidades principais:
 * - CRUD completo de cartões de crédito
 * - Consulta de limites total e disponível
 * - Validação de segurança por usuário
 * - Gestão de múltiplos cartões por usuário
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/cartoes-credito") // Base path para endpoints de cartões de crédito
@RequiredArgsConstructor // Lombok: gera construtor com campos final
@Tag(name = "Cartões de Crédito", description = "Endpoints para gestão de cartões de crédito")
@CrossOrigin(origins = {"http://localhost:4200", "https://22e294954ab2.ngrok-free.app"}) // Permite CORS de qualquer origem
public class CartaoCreditoController {

    // Serviço responsável pela lógica de negócio dos cartões de crédito
    private final CartaoCreditoService cartaoCreditoService;

    /**
     * Cria um novo cartão de crédito para o usuário autenticado
     * 
     * Endpoint para cadastrar um novo cartão de crédito no sistema.
     * O cartão é automaticamente associado ao usuário logado.
     * 
     * @param cartaoCreditoDTO Dados do cartão de crédito a ser criado
     * @param currentUser Usuário autenticado (injetado automaticamente)
     * @return Cartão de crédito criado com ID e dados completos
     */
    @PostMapping
    @Operation(summary = "Criar novo cartão de crédito", description = "Cria um novo cartão de crédito para o usuário")
    public ResponseEntity<CartaoCreditoDTO> criarCartaoCredito(
            @Valid @RequestBody CartaoCreditoDTO cartaoCreditoDTO,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Associa o cartão ao usuário logado
        cartaoCreditoDTO.setUsuarioId(currentUser.getId());
        
        // Cria o cartão através do serviço
        CartaoCreditoDTO cartaoCriado = cartaoCreditoService.criarCartaoCredito(cartaoCreditoDTO);
        return ResponseEntity.ok(cartaoCriado);
    }

    /**
     * Busca um cartão de crédito específico por ID
     * 
     * Retorna os detalhes de um cartão, mas apenas se ele pertencer
     * ao usuário autenticado (segurança).
     * 
     * @param id ID do cartão de crédito a ser buscado
     * @param currentUser Usuário autenticado
     * @return Dados do cartão de crédito encontrado
     */
    @GetMapping("/{id}")
    @Operation(summary = "Buscar cartão por ID", description = "Busca um cartão de crédito específico por ID")
    public ResponseEntity<CartaoCreditoDTO> buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca o cartão verificando se pertence ao usuário
        CartaoCreditoDTO cartao = cartaoCreditoService.buscarPorId(id, currentUser.getId());
        return ResponseEntity.ok(cartao);
    }

    /**
     * Lista todos os cartões de crédito do usuário autenticado
     * 
     * Retorna uma lista completa de todos os cartões de crédito
     * do usuário logado, incluindo limites e status.
     * 
     * @param currentUser Usuário autenticado
     * @return Lista de todos os cartões de crédito do usuário
     */
    @GetMapping
    @Operation(summary = "Listar todos os cartões de crédito do usuário", description = "Retorna lista completa de todos os cartões de crédito do usuário")
    public ResponseEntity<List<CartaoCreditoDTO>> buscarPorUsuario(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Busca todos os cartões do usuário logado
        List<CartaoCreditoDTO> cartoes = cartaoCreditoService.buscarPorUsuarioId(currentUser.getId());
        return ResponseEntity.ok(cartoes);
    }

    /**
     * Atualiza um cartão de crédito existente
     * 
     * Permite modificar dados de um cartão, mas apenas se ele pertencer
     * ao usuário autenticado. Validações são aplicadas antes da atualização.
     * 
     * @param id ID do cartão de crédito a ser atualizado
     * @param cartaoCreditoDTO Novos dados do cartão
     * @param currentUser Usuário autenticado
     * @return Cartão de crédito atualizado
     */
    @PutMapping("/{id}")
    @Operation(summary = "Atualizar cartão de crédito", description = "Atualiza dados de um cartão de crédito existente")
    public ResponseEntity<CartaoCreditoDTO> atualizarCartaoCredito(
            @PathVariable Long id,
            @Valid @RequestBody CartaoCreditoDTO cartaoCreditoDTO,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Atualiza o cartão verificando propriedade do usuário
        CartaoCreditoDTO cartaoAtualizado = cartaoCreditoService.atualizarCartaoCredito(id, cartaoCreditoDTO, currentUser.getId());
        return ResponseEntity.ok(cartaoAtualizado);
    }

    /**
     * Remove um cartão de crédito do sistema
     * 
     * Exclui permanentemente um cartão, mas apenas se ele pertencer
     * ao usuário autenticado. ATENÇÃO: Esta operação é irreversível.
     * 
     * @param id ID do cartão de crédito a ser excluído
     * @param currentUser Usuário autenticado
     * @return Resposta vazia indicando sucesso
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar cartão de crédito", description = "Remove permanentemente um cartão de crédito")
    public ResponseEntity<Void> deletarCartaoCredito(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        
        // Remove o cartão verificando propriedade do usuário
        cartaoCreditoService.deletarCartaoCredito(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Obtém o limite total de crédito do usuário
     * 
     * Soma os limites de todos os cartões de crédito ativos
     * do usuário logado.
     * 
     * @param currentUser Usuário autenticado
     * @return Valor total do limite de crédito disponível
     */
    @GetMapping("/limite-total")
    @Operation(summary = "Obter limite total de crédito do usuário", description = "Retorna o limite total de crédito de todos os cartões ativos")
    public ResponseEntity<BigDecimal> getLimiteTotal(@AuthenticationPrincipal UserPrincipal currentUser) {
        // Calcula o limite total de todos os cartões do usuário
        BigDecimal limiteTotal = cartaoCreditoService.getTotalLimiteCredito(currentUser.getId());
        return ResponseEntity.ok(limiteTotal);
    }

    /**
     * Obtém o limite disponível de crédito do usuário
     * 
     * Calcula o limite disponível considerando o limite total
     * menos o valor já utilizado nos cartões.
     * 
     * @param currentUser Usuário autenticado
     * @return Valor do limite de crédito ainda disponível para uso
     */
    @GetMapping("/limite-disponivel")
    @Operation(summary = "Obter limite disponível de crédito do usuário", description = "Retorna o limite disponível de crédito (total - utilizado)")
    public ResponseEntity<BigDecimal> getLimiteDisponivel(@AuthenticationPrincipal UserPrincipal currentUser) {
        // Calcula o limite disponível (total - utilizado)
        BigDecimal limiteDisponivel = cartaoCreditoService.getTotalLimiteDisponivel(currentUser.getId());
        return ResponseEntity.ok(limiteDisponivel);
    }
}
