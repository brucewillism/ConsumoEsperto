package com.consumoesperto.service;

import com.consumoesperto.dto.CartaoCreditoDTO;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Serviço responsável por gerenciar operações relacionadas a cartões de crédito
 * 
 * Este serviço implementa a lógica de negócio para criação, busca, atualização
 * e exclusão de cartões de crédito. Também fornece métodos para cálculos
 * de limites e consultas específicas por usuário.
 * 
 * Funcionalidades principais:
 * - CRUD completo de cartões de crédito
 * - Validação de unicidade de números de cartão por usuário
 * - Cálculo de limites totais de crédito e disponível
 * - Controle de acesso por usuário
 * - Soft delete (desativação em vez de exclusão física)
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor // Lombok: gera construtor com campos final automaticamente
@Transactional // Todas as operações são transacionais para garantir consistência
public class CartaoCreditoService {

    // Repositório para operações de persistência de cartões de crédito
    private final CartaoCreditoRepository cartaoCreditoRepository;
    
    // Repositório para validação e busca de usuários
    private final UsuarioRepository usuarioRepository;

    /**
     * Cria um novo cartão de crédito no sistema
     * 
     * Este método implementa o fluxo completo de criação de cartão:
     * 1. Valida se o usuário existe
     * 2. Verifica se o número do cartão já existe para o usuário
     * 3. Converte o DTO para entidade
     * 4. Associa o cartão ao usuário
     * 5. Persiste o cartão no banco de dados
     * 
     * @param cartaoCreditoDTO DTO com os dados do cartão a ser criado
     * @return CartaoCreditoDTO com os dados do cartão criado
     * @throws RuntimeException se o usuário não for encontrado ou se o cartão já existir
     */
    public CartaoCreditoDTO criarCartaoCredito(CartaoCreditoDTO cartaoCreditoDTO) {
        // Valida se o usuário existe antes de criar o cartão
        Usuario usuario = usuarioRepository.findById(cartaoCreditoDTO.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        // Validação de unicidade: verifica se o número do cartão já existe para o usuário
        if (cartaoCreditoRepository.existsByNumeroCartaoAndUsuarioId(cartaoCreditoDTO.getNumeroCartao(), cartaoCreditoDTO.getUsuarioId())) {
            throw new RuntimeException("Cartão de crédito já existe para este usuário");
        }

        // Converte o DTO para entidade e associa ao usuário
        CartaoCredito cartaoCredito = converterParaEntidade(cartaoCreditoDTO);
        cartaoCredito.setUsuario(usuario);
        
        // Persiste o cartão no banco de dados
        CartaoCredito cartaoSalvo = cartaoCreditoRepository.save(cartaoCredito);
        return converterParaDTO(cartaoSalvo);
    }

    /**
     * Busca um cartão de crédito específico pelo seu ID
     * 
     * Método para recuperar cartões específicos por identificador.
     * Inclui validação de acesso por usuário.
     * 
     * @param id ID único do cartão de crédito a ser buscado
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @return CartaoCreditoDTO com os dados do cartão encontrado
     * @throws RuntimeException se o cartão não for encontrado ou não pertencer ao usuário
     */
    public CartaoCreditoDTO buscarPorId(Long id, Long usuarioId) {
        // Busca o cartão pelo ID e valida se pertence ao usuário
        CartaoCredito cartaoCredito = cartaoCreditoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RuntimeException("Cartão de crédito não encontrado"));
        return converterParaDTO(cartaoCredito);
    }

    /**
     * Lista todos os cartões de crédito ativos de um usuário específico
     * 
     * Método usado para exibir os cartões do usuário no dashboard
     * e outras telas do sistema. Retorna apenas cartões ativos.
     * 
     * @param usuarioId ID do usuário cujos cartões devem ser listados
     * @return Lista de CartaoCreditoDTO com os cartões ativos do usuário
     */
    public List<CartaoCreditoDTO> buscarPorUsuarioId(Long usuarioId) {
        List<CartaoCredito> cartoes = cartaoCreditoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        return cartoes.stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca cartões de crédito por usuário (alias para buscarPorUsuarioId)
     * 
     * @param usuarioId ID do usuário
     * @return Lista de cartões do usuário
     */
    public List<CartaoCreditoDTO> buscarPorUsuario(Long usuarioId) {
        return buscarPorUsuarioId(usuarioId);
    }

    /**
     * Atualiza os dados de um cartão de crédito existente
     * 
     * Este método permite modificar informações do cartão como:
     * - Nome do cartão
     * - Banco emissor
     * - Limite de crédito
     * - Limite disponível
     * - Data de vencimento
     * - Tipo do cartão
     * - Status ativo/inativo
     * 
     * Inclui validação de acesso por usuário.
     * 
     * @param id ID do cartão a ser atualizado
     * @param cartaoCreditoDTO DTO com os novos dados do cartão
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @return CartaoCreditoDTO com os dados atualizados
     * @throws RuntimeException se o cartão não for encontrado ou não pertencer ao usuário
     */
    public CartaoCreditoDTO atualizarCartaoCredito(Long id, CartaoCreditoDTO cartaoCreditoDTO, Long usuarioId) {
        // Verifica se o cartão existe e pertence ao usuário antes de tentar atualizar
        CartaoCredito cartaoExistente = cartaoCreditoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RuntimeException("Cartão de crédito não encontrado"));

        // Atualiza todos os campos do cartão com os novos valores
        cartaoExistente.setNome(cartaoCreditoDTO.getNome());
        cartaoExistente.setBanco(cartaoCreditoDTO.getBanco());
        cartaoExistente.setLimiteCredito(cartaoCreditoDTO.getLimiteCredito());
        cartaoExistente.setLimiteDisponivel(cartaoCreditoDTO.getLimiteDisponivel());
        cartaoExistente.setDataVencimento(cartaoCreditoDTO.getDataVencimento());
        cartaoExistente.setTipoCartao(cartaoCreditoDTO.getTipoCartao());
        cartaoExistente.setAtivo(cartaoCreditoDTO.getAtivo());

        // Persiste as alterações no banco de dados
        CartaoCredito cartaoAtualizado = cartaoCreditoRepository.save(cartaoExistente);
        return converterParaDTO(cartaoAtualizado);
    }

    /**
     * Desativa um cartão de crédito (soft delete)
     * 
     * ATENÇÃO: Esta operação não remove o cartão fisicamente do banco,
     * apenas o marca como inativo. Isso preserva o histórico de transações
     * e faturas associadas ao cartão.
     * 
     * Inclui validação de acesso por usuário.
     * 
     * @param id ID do cartão a ser desativado
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @throws RuntimeException se o cartão não for encontrado ou não pertencer ao usuário
     */
    public void deletarCartaoCredito(Long id, Long usuarioId) {
        // Verifica se o cartão existe e pertence ao usuário antes de tentar desativar
        CartaoCredito cartaoCredito = cartaoCreditoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RuntimeException("Cartão de crédito não encontrado"));
        
        // Soft delete: marca o cartão como inativo em vez de removê-lo fisicamente
        cartaoCredito.setAtivo(false);
        cartaoCreditoRepository.save(cartaoCredito);
    }

    /**
     * Calcula o limite total de crédito de um usuário
     * 
     * Método usado para cálculos financeiros e relatórios.
     * Soma o limite de crédito de todos os cartões ativos do usuário.
     * Retorna BigDecimal para precisão em cálculos monetários.
     * 
     * @param usuarioId ID do usuário para o qual calcular o total
     * @return BigDecimal com o limite total de crédito do usuário
     */
    public BigDecimal getTotalLimiteCredito(Long usuarioId) {
        // Busca o total do limite de crédito de todos os cartões ativos do usuário
        Double total = cartaoCreditoRepository.getTotalCreditLimitByUsuarioId(usuarioId);
        
        // Converte para BigDecimal ou retorna zero se não houver cartões
        return total != null ? BigDecimal.valueOf(total) : BigDecimal.ZERO;
    }

    /**
     * Calcula o limite total disponível de um usuário
     * 
     * Método usado para cálculos financeiros e relatórios.
     * Soma o limite disponível de todos os cartões ativos do usuário.
     * Retorna BigDecimal para precisão em cálculos monetários.
     * 
     * @param usuarioId ID do usuário para o qual calcular o total
     * @return BigDecimal com o limite total disponível do usuário
     */
    public BigDecimal getTotalLimiteDisponivel(Long usuarioId) {
        // Busca o total do limite disponível de todos os cartões ativos do usuário
        Double total = cartaoCreditoRepository.getTotalAvailableLimitByUsuarioId(usuarioId);
        
        // Converte para BigDecimal ou retorna zero se não houver cartões
        return total != null ? BigDecimal.valueOf(total) : BigDecimal.ZERO;
    }

    /**
     * Remove todos os cartões de crédito de um banco específico para um usuário
     * 
     * @param usuarioId ID do usuário
     * @param banco Nome do banco
     */
    @Transactional
    public void removerPorBanco(Long usuarioId, String banco) {
        List<CartaoCredito> cartoes = cartaoCreditoRepository.findByUsuarioId(usuarioId);
        
        for (CartaoCredito cartao : cartoes) {
            if (cartao.getBanco().equals(banco)) {
                cartao.setAtivo(false);
                cartaoCreditoRepository.save(cartao);
            }
        }
    }

    /**
     * Converte um CartaoCreditoDTO para entidade CartaoCredito
     * 
     * Este método é responsável por:
     * - Mapear dados do DTO para a entidade
     * - Preparar a entidade para persistência
     * 
     * @param dto CartaoCreditoDTO a ser convertido
     * @return Entidade CartaoCredito com os dados do DTO
     */
    private CartaoCredito converterParaEntidade(CartaoCreditoDTO dto) {
        CartaoCredito cartaoCredito = new CartaoCredito();
        cartaoCredito.setId(dto.getId());
        cartaoCredito.setNome(dto.getNome());
        cartaoCredito.setBanco(dto.getBanco());
        cartaoCredito.setNumeroCartao(dto.getNumeroCartao());
        cartaoCredito.setLimiteCredito(dto.getLimiteCredito());
        cartaoCredito.setLimiteDisponivel(dto.getLimiteDisponivel());
        cartaoCredito.setDataVencimento(dto.getDataVencimento());
        cartaoCredito.setTipoCartao(dto.getTipoCartao());
        cartaoCredito.setAtivo(dto.getAtivo());
        return cartaoCredito;
    }

    /**
     * Converte uma entidade CartaoCredito para CartaoCreditoDTO
     * 
     * Este método é responsável por:
     * - Mapear dados da entidade para o DTO
     * - Incluir informações do usuário associado
     * - Garantir que dados sensíveis não sejam expostos
     * 
     * @param cartaoCredito Entidade CartaoCredito a ser convertida
     * @return CartaoCreditoDTO com todos os dados necessários para exibição
     */
    private CartaoCreditoDTO converterParaDTO(CartaoCredito cartaoCredito) {
        CartaoCreditoDTO dto = new CartaoCreditoDTO();
        dto.setId(cartaoCredito.getId());
        dto.setNome(cartaoCredito.getNome());
        dto.setBanco(cartaoCredito.getBanco());
        dto.setNumeroCartao(cartaoCredito.getNumeroCartao());
        dto.setLimiteCredito(cartaoCredito.getLimiteCredito());
        dto.setLimiteDisponivel(cartaoCredito.getLimiteDisponivel());
        dto.setDataVencimento(cartaoCredito.getDataVencimento());
        dto.setTipoCartao(cartaoCredito.getTipoCartao());
        dto.setAtivo(cartaoCredito.getAtivo());
        
        // Inclui informações do usuário associado
        dto.setUsuarioId(cartaoCredito.getUsuario().getId());
        dto.setDataCriacao(cartaoCredito.getDataCriacao());
        dto.setDataAtualizacao(cartaoCredito.getDataAtualizacao());
        return dto;
    }
}
