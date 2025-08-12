package com.consumoesperto.service;

import com.consumoesperto.dto.CompraParceladaDTO;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.CompraParcelada;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.CompraParceladaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço responsável por gerenciar operações relacionadas a compras parceladas
 * 
 * Este serviço implementa a lógica de negócio para criação, busca, atualização
 * e exclusão de compras parceladas. Também fornece métodos para consultas específicas
 * por status, período e cartão de crédito.
 * 
 * Funcionalidades principais:
 * - CRUD completo de compras parceladas
 * - Consultas por status, período e cartão de crédito
 * - Cálculo de totais por status
 * - Busca de parcelas ativas e vencendo
 * - Validação de propriedade de cartões de crédito
 * - Controle de acesso por usuário
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor // Lombok: gera construtor com campos final automaticamente
@Transactional // Todas as operações são transacionais para garantir consistência
public class CompraParceladaService {

    // Repositório para operações de persistência de compras parceladas
    private final CompraParceladaRepository compraParceladaRepository;
    
    // Repositório para validação e busca de cartões de crédito
    private final CartaoCreditoRepository cartaoCreditoRepository;
    
    // Repositório para validação e busca de categorias
    private final CategoriaRepository categoriaRepository;

    /**
     * Cria uma nova compra parcelada no sistema
     * 
     * Este método implementa o fluxo completo de criação de compra parcelada:
     * 1. Valida se o cartão de crédito existe
     * 2. Valida e associa a categoria (se fornecida)
     * 3. Converte o DTO para entidade
     * 4. Associa a compra ao cartão de crédito e categoria
     * 5. Persiste a compra no banco de dados
     * 
     * @param compraParceladaDTO DTO com os dados da compra a ser criada
     * @return CompraParceladaDTO com os dados da compra criada
     * @throws RuntimeException se o cartão de crédito ou categoria não for encontrado
     */
    public CompraParceladaDTO criarCompraParcelada(CompraParceladaDTO compraParceladaDTO) {
        // Valida se o cartão de crédito existe antes de criar a compra
        CartaoCredito cartaoCredito = cartaoCreditoRepository.findById(compraParceladaDTO.getCartaoCreditoId())
                .orElseThrow(() -> new RuntimeException("Cartão de crédito não encontrado"));

        // Validação e associação da categoria (opcional)
        Categoria categoria = null;
        if (compraParceladaDTO.getCategoriaId() != null) {
            categoria = categoriaRepository.findById(compraParceladaDTO.getCategoriaId())
                    .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
        }

        // Converte o DTO para entidade e associa ao cartão de crédito e categoria
        CompraParcelada compraParcelada = converterParaEntidade(compraParceladaDTO);
        compraParcelada.setCartaoCredito(cartaoCredito);
        compraParcelada.setCategoria(categoria);
        
        // Persiste a compra no banco de dados
        CompraParcelada compraSalva = compraParceladaRepository.save(compraParcelada);
        return converterParaDTO(compraSalva);
    }

    /**
     * Busca uma compra parcelada específica pelo seu ID
     * 
     * Método para recuperar compras específicas por identificador.
     * Inclui validação de acesso por usuário através do cartão de crédito.
     * 
     * @param id ID único da compra parcelada a ser buscada
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @return CompraParceladaDTO com os dados da compra encontrada
     * @throws RuntimeException se a compra não for encontrada ou não pertencer ao usuário
     */
    public CompraParceladaDTO buscarPorId(Long id, Long usuarioId) {
        // Busca a compra pelo ID e valida se pertence ao usuário através do cartão
        CompraParcelada compraParcelada = compraParceladaRepository.findByIdAndCartaoCreditoUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RuntimeException("Compra parcelada não encontrada"));
        return converterParaDTO(compraParcelada);
    }

    /**
     * Lista todas as compras parceladas de um usuário específico
     * 
     * Método usado para exibir o histórico de compras parceladas do usuário
     * no dashboard e outras telas do sistema.
     * 
     * @param usuarioId ID do usuário cujas compras devem ser listadas
     * @return Lista de CompraParceladaDTO com todas as compras do usuário
     */
    public List<CompraParceladaDTO> buscarPorUsuarioId(Long usuarioId) {
        // Busca todas as compras associadas aos cartões de crédito do usuário
        List<CompraParcelada> compras = compraParceladaRepository.findByCartaoCreditoUsuarioId(usuarioId);
        return compras.stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lista todas as compras parceladas de um cartão de crédito específico
     * 
     * Método usado para exibir o histórico de compras de um cartão específico.
     * Inclui validação de propriedade do cartão de crédito.
     * 
     * @param cartaoCreditoId ID do cartão de crédito cujas compras devem ser listadas
     * @param usuarioId ID do usuário solicitante (para validação de propriedade)
     * @return Lista de CompraParceladaDTO com todas as compras do cartão especificado
     * @throws RuntimeException se o cartão de crédito não for encontrado ou não pertencer ao usuário
     */
    public List<CompraParceladaDTO> buscarPorCartaoCreditoId(Long cartaoCreditoId, Long usuarioId) {
        // Verificar se o cartão pertence ao usuário antes de buscar as compras
        cartaoCreditoRepository.findByIdAndUsuarioId(cartaoCreditoId, usuarioId)
                .orElseThrow(() -> new RuntimeException("Cartão de crédito não encontrado"));

        // Busca todas as compras do cartão de crédito especificado
        List<CompraParcelada> compras = compraParceladaRepository.findByCartaoCreditoId(cartaoCreditoId);
        return compras.stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * Atualiza os dados de uma compra parcelada existente
     * 
     * Este método permite modificar informações da compra como:
     * - Descrição
     * - Valor total e valor da parcela
     * - Número de parcelas e parcela atual
     * - Datas (compra, primeira parcela, última parcela)
     * - Status da compra
     * - Categoria associada
     * 
     * Inclui validação de acesso por usuário.
     * 
     * @param id ID da compra a ser atualizada
     * @param compraParceladaDTO DTO com os novos dados da compra
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @return CompraParceladaDTO com os dados atualizados
     * @throws RuntimeException se a compra não for encontrada ou não pertencer ao usuário
     */
    public CompraParceladaDTO atualizarCompraParcelada(Long id, CompraParceladaDTO compraParceladaDTO, Long usuarioId) {
        // Verifica se a compra existe e pertence ao usuário antes de tentar atualizar
        CompraParcelada compraExistente = compraParceladaRepository.findByIdAndCartaoCreditoUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RuntimeException("Compra parcelada não encontrada"));

        // Atualiza todos os campos da compra com os novos valores
        compraExistente.setDescricao(compraParceladaDTO.getDescricao());
        compraExistente.setValorTotal(compraParceladaDTO.getValorTotal());
        compraExistente.setValorParcela(compraParceladaDTO.getValorParcela());
        compraExistente.setNumeroParcelas(compraParceladaDTO.getNumeroParcelas());
        compraExistente.setParcelaAtual(compraParceladaDTO.getParcelaAtual());
        compraExistente.setDataCompra(compraParceladaDTO.getDataCompra());
        compraExistente.setDataPrimeiraParcela(compraParceladaDTO.getDataPrimeiraParcela());
        compraExistente.setDataUltimaParcela(compraParceladaDTO.getDataUltimaParcela());
        compraExistente.setStatusCompra(compraParceladaDTO.getStatusCompra());

        // Atualiza a categoria se uma nova foi fornecida
        if (compraParceladaDTO.getCategoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(compraParceladaDTO.getCategoriaId())
                    .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
            compraExistente.setCategoria(categoria);
        }

        // Persiste as alterações no banco de dados
        CompraParcelada compraAtualizada = compraParceladaRepository.save(compraExistente);
        return converterParaDTO(compraAtualizada);
    }

    /**
     * Remove uma compra parcelada do sistema permanentemente
     * 
     * ATENÇÃO: Esta operação é irreversível e remove todos os dados
     * da compra, incluindo histórico de parcelas.
     * 
     * Inclui validação de acesso por usuário.
     * 
     * @param id ID da compra a ser excluída
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @throws RuntimeException se a compra não for encontrada ou não pertencer ao usuário
     */
    public void deletarCompraParcelada(Long id, Long usuarioId) {
        // Verifica se a compra existe e pertence ao usuário antes de tentar excluir
        CompraParcelada compraParcelada = compraParceladaRepository.findByIdAndCartaoCreditoUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RuntimeException("Compra parcelada não encontrada"));
        
        // Remove a compra do banco de dados
        compraParceladaRepository.delete(compraParcelada);
    }

    /**
     * Busca compras parceladas de um usuário por status específico
     * 
     * Método usado para filtrar compras por status (ATIVA, FINALIZADA, CANCELADA).
     * Útil para relatórios e dashboards organizados por situação.
     * 
     * @param usuarioId ID do usuário cujas compras devem ser filtradas
     * @param status Status da compra para filtrar (enum StatusCompra)
     * @return Lista de CompraParceladaDTO com compras do status especificado
     */
    public List<CompraParceladaDTO> buscarPorStatus(Long usuarioId, CompraParcelada.StatusCompra status) {
        // Busca compras do usuário com o status especificado
        List<CompraParcelada> compras = compraParceladaRepository.findByUsuarioIdAndStatus(usuarioId, status);
        return compras.stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca compras parceladas de um usuário em um período específico
     * 
     * Método usado para relatórios mensais, trimestrais e anuais.
     * Filtra por data de compra das compras parceladas.
     * 
     * @param usuarioId ID do usuário cujas compras devem ser filtradas
     * @param dataInicio Data de início do período (inclusive)
     * @param dataFim Data de fim do período (exclusive)
     * @return Lista de CompraParceladaDTO com compras no período especificado
     */
    public List<CompraParceladaDTO> buscarPorPeriodo(Long usuarioId, LocalDateTime dataInicio, LocalDateTime dataFim) {
        // Busca compras do usuário com data de compra no período especificado
        List<CompraParcelada> compras = compraParceladaRepository.findByUsuarioIdAndDataCompraBetween(usuarioId, dataInicio, dataFim);
        return compras.stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * Calcula o valor total das compras parceladas de um usuário por status
     * 
     * Método usado para cálculos financeiros e relatórios.
     * Retorna BigDecimal para precisão em cálculos monetários.
     * 
     * @param usuarioId ID do usuário para o qual calcular o total
     * @param status Status da compra para filtrar o cálculo
     * @return BigDecimal com o valor total das compras do status especificado
     */
    public BigDecimal getTotalComprasPorStatus(Long usuarioId, CompraParcelada.StatusCompra status) {
        // Busca o total das compras do usuário com o status especificado
        Double total = compraParceladaRepository.getTotalCompraByUsuarioIdAndStatus(usuarioId, status);
        
        // Converte para BigDecimal ou retorna zero se não houver compras
        return total != null ? BigDecimal.valueOf(total) : BigDecimal.ZERO;
    }

    /**
     * Calcula o total de compras parceladas por status para um usuário
     * 
     * @param usuarioId ID do usuário
     * @param status Status das compras a serem consideradas
     * @return Total das compras no status especificado
     */
    public BigDecimal getTotalPorStatus(Long usuarioId, CompraParcelada.StatusCompra status) {
        return getTotalComprasPorStatus(usuarioId, status);
    }

    /**
     * Busca compras parceladas ativas de um usuário
     * 
     * Método usado para exibir compras em andamento no dashboard.
     * Filtra compras com status ATIVA.
     * 
     * @param usuarioId ID do usuário cujas compras ativas devem ser listadas
     * @return Lista de CompraParceladaDTO com compras ativas do usuário
     */
    public List<CompraParceladaDTO> buscarParcelasAtivas(Long usuarioId) {
        // Busca compras ativas do usuário (status ATIVA)
        List<CompraParcelada> compras = compraParceladaRepository.findActiveInstallmentsByUsuarioId(usuarioId);
        return compras.stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca parcelas que estão vencendo em breve
     * 
     * @param usuarioId ID do usuário
     * @return Lista de compras parceladas vencendo em breve
     */
    public List<CompraParceladaDTO> buscarParcelasVencendo(Long usuarioId) {
        LocalDateTime dataLimite = LocalDateTime.now().plusDays(7); // Próximos 7 dias
        List<CompraParcelada> parcelas = compraParceladaRepository.findUpcomingInstallmentsByUsuarioId(usuarioId, dataLimite);
        return parcelas.stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * Converte um CompraParceladaDTO para entidade CompraParcelada
     * 
     * Este método é responsável por:
     * - Mapear dados do DTO para a entidade
     * - Preparar a entidade para persistência
     * 
     * @param dto CompraParceladaDTO a ser convertido
     * @return Entidade CompraParcelada com os dados do DTO
     */
    private CompraParcelada converterParaEntidade(CompraParceladaDTO dto) {
        CompraParcelada compraParcelada = new CompraParcelada();
        compraParcelada.setId(dto.getId());
        compraParcelada.setDescricao(dto.getDescricao());
        compraParcelada.setValorTotal(dto.getValorTotal());
        compraParcelada.setValorParcela(dto.getValorParcela());
        compraParcelada.setNumeroParcelas(dto.getNumeroParcelas());
        compraParcelada.setParcelaAtual(dto.getParcelaAtual());
        compraParcelada.setDataCompra(dto.getDataCompra());
        compraParcelada.setDataPrimeiraParcela(dto.getDataPrimeiraParcela());
        compraParcelada.setDataUltimaParcela(dto.getDataUltimaParcela());
        compraParcelada.setStatusCompra(dto.getStatusCompra());
        return compraParcelada;
    }

    /**
     * Converte uma entidade CompraParcelada para CompraParceladaDTO
     * 
     * Este método é responsável por:
     * - Mapear dados da entidade para o DTO
     * - Incluir informações do cartão de crédito e categoria associados
     * - Garantir que dados sensíveis não sejam expostos
     * 
     * @param compraParcelada Entidade CompraParcelada a ser convertida
     * @return CompraParceladaDTO com todos os dados necessários para exibição
     */
    private CompraParceladaDTO converterParaDTO(CompraParcelada compraParcelada) {
        CompraParceladaDTO dto = new CompraParceladaDTO();
        dto.setId(compraParcelada.getId());
        dto.setDescricao(compraParcelada.getDescricao());
        dto.setValorTotal(compraParcelada.getValorTotal());
        dto.setValorParcela(compraParcelada.getValorParcela());
        dto.setNumeroParcelas(compraParcelada.getNumeroParcelas());
        dto.setParcelaAtual(compraParcelada.getParcelaAtual());
        dto.setDataCompra(compraParcelada.getDataCompra());
        dto.setDataPrimeiraParcela(compraParcelada.getDataPrimeiraParcela());
        dto.setDataUltimaParcela(compraParcelada.getDataUltimaParcela());
        dto.setStatusCompra(compraParcelada.getStatusCompra());
        
        // Inclui informações do cartão de crédito associado
        dto.setCartaoCreditoId(compraParcelada.getCartaoCredito().getId());
        
        // Inclui informações da categoria associada (se existir)
        if (compraParcelada.getCategoria() != null) {
            dto.setCategoriaId(compraParcelada.getCategoria().getId());
        }
        
        // Inclui informações de auditoria
        dto.setDataCriacao(compraParcelada.getDataCriacao());
        dto.setDataAtualizacao(compraParcelada.getDataAtualizacao());
        return dto;
    }
}
