package com.consumoesperto.service;

import com.consumoesperto.dto.FaturaDTO;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.FaturaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço responsável por gerenciar operações relacionadas a faturas de cartão de crédito
 * 
 * Este serviço implementa a lógica de negócio para criação, busca, atualização
 * e exclusão de faturas. Também fornece métodos para consultas específicas
 * por status, período e cartão de crédito.
 * 
 * Funcionalidades principais:
 * - CRUD completo de faturas
 * - Consultas por status, período e cartão de crédito
 * - Cálculo de totais por status
 * - Busca de faturas vencidas
 * - Validação de propriedade de cartões de crédito
 * - Controle de acesso por usuário
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor // Lombok: gera construtor com campos final automaticamente
@Transactional // Todas as operações são transacionais para garantir consistência
public class FaturaService {

    // Repositório para operações de persistência de faturas
    private final FaturaRepository faturaRepository;
    
    // Repositório para validação e busca de cartões de crédito
    private final CartaoCreditoRepository cartaoCreditoRepository;

    /**
     * Cria uma nova fatura de cartão de crédito no sistema
     * 
     * Este método implementa o fluxo completo de criação de fatura:
     * 1. Valida se o cartão de crédito existe
     * 2. Converte o DTO para entidade
     * 3. Associa a fatura ao cartão de crédito
     * 4. Persiste a fatura no banco de dados
     * 
     * @param faturaDTO DTO com os dados da fatura a ser criada
     * @return FaturaDTO com os dados da fatura criada
     * @throws RuntimeException se o cartão de crédito não for encontrado
     */
    public FaturaDTO criarFatura(FaturaDTO faturaDTO) {
        // Valida se o cartão de crédito existe antes de criar a fatura
        CartaoCredito cartaoCredito = cartaoCreditoRepository.findById(faturaDTO.getCartaoCreditoId())
                .orElseThrow(() -> new RuntimeException("Cartão de crédito não encontrado"));

        // Converte o DTO para entidade e associa ao cartão de crédito
        Fatura fatura = converterParaEntidade(faturaDTO);
        fatura.setCartaoCredito(cartaoCredito);
        
        // Persiste a fatura no banco de dados
        Fatura faturaSalva = faturaRepository.save(fatura);
        return converterParaDTO(faturaSalva);
    }

    /**
     * Busca uma fatura específica pelo seu ID
     * 
     * Método para recuperar faturas específicas por identificador.
     * Inclui validação de acesso por usuário através do cartão de crédito.
     * 
     * @param id ID único da fatura a ser buscada
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @return FaturaDTO com os dados da fatura encontrada
     * @throws RuntimeException se a fatura não for encontrada ou não pertencer ao usuário
     */
    public FaturaDTO buscarPorId(Long id, Long usuarioId) {
        // Busca a fatura pelo ID e valida se pertence ao usuário através do cartão
        Fatura fatura = faturaRepository.findByIdAndCartaoCreditoUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RuntimeException("Fatura não encontrada"));
        return converterParaDTO(fatura);
    }

    /**
     * Lista todas as faturas de um usuário específico
     * 
     * Método usado para exibir o histórico de faturas do usuário
     * no dashboard e outras telas do sistema.
     * 
     * @param usuarioId ID do usuário cujas faturas devem ser listadas
     * @return Lista de FaturaDTO com todas as faturas do usuário
     */
    public List<FaturaDTO> buscarPorUsuarioId(Long usuarioId) {
        // Busca todas as faturas associadas aos cartões de crédito do usuário
        List<Fatura> faturas = faturaRepository.findByCartaoCreditoUsuarioId(usuarioId);
        return faturas.stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca faturas por usuário (alias para buscarPorUsuarioId)
     * 
     * @param usuarioId ID do usuário
     * @return Lista de faturas do usuário
     */
    public List<FaturaDTO> buscarPorUsuario(Long usuarioId) {
        return buscarPorUsuarioId(usuarioId);
    }

    /**
     * Lista todas as faturas de um cartão de crédito específico
     * 
     * Método usado para exibir o histórico de faturas de um cartão específico.
     * Inclui validação de propriedade do cartão de crédito.
     * 
     * @param cartaoCreditoId ID do cartão de crédito cujas faturas devem ser listadas
     * @param usuarioId ID do usuário solicitante (para validação de propriedade)
     * @return Lista de FaturaDTO com todas as faturas do cartão especificado
     * @throws RuntimeException se o cartão de crédito não for encontrado ou não pertencer ao usuário
     */
    public List<FaturaDTO> buscarPorCartaoCreditoId(Long cartaoCreditoId, Long usuarioId) {
        // Valida se o cartão de crédito pertence ao usuário antes de buscar as faturas
        cartaoCreditoRepository.findByIdAndUsuarioId(cartaoCreditoId, usuarioId)
                .orElseThrow(() -> new RuntimeException("Cartão de crédito não encontrado"));

        // Busca todas as faturas do cartão de crédito especificado
        List<Fatura> faturas = faturaRepository.findByCartaoCreditoId(cartaoCreditoId);
        return faturas.stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * Atualiza os dados de uma fatura existente
     * 
     * Este método permite modificar informações da fatura como:
     * - Valor da fatura
     * - Valor pago
     * - Data de vencimento
     * - Data de fechamento
     * - Data de pagamento
     * - Status da fatura
     * - Número da fatura
     * 
     * Inclui validação de acesso por usuário.
     * 
     * @param id ID da fatura a ser atualizada
     * @param faturaDTO DTO com os novos dados da fatura
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @return FaturaDTO com os dados atualizados
     * @throws RuntimeException se a fatura não for encontrada ou não pertencer ao usuário
     */
    public FaturaDTO atualizarFatura(Long id, FaturaDTO faturaDTO, Long usuarioId) {
        // Verifica se a fatura existe e pertence ao usuário antes de tentar atualizar
        Fatura faturaExistente = faturaRepository.findByIdAndCartaoCreditoUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RuntimeException("Fatura não encontrada"));

        // Atualiza todos os campos da fatura com os novos valores
        faturaExistente.setValorFatura(faturaDTO.getValorFatura());
        faturaExistente.setValorPago(faturaDTO.getValorPago());
        faturaExistente.setDataVencimento(faturaDTO.getDataVencimento());
        faturaExistente.setDataFechamento(faturaDTO.getDataFechamento());
        faturaExistente.setDataPagamento(faturaDTO.getDataPagamento());
        faturaExistente.setStatusFatura(faturaDTO.getStatusFatura());
        faturaExistente.setNumeroFatura(faturaDTO.getNumeroFatura());

        // Persiste as alterações no banco de dados
        Fatura faturaAtualizada = faturaRepository.save(faturaExistente);
        return converterParaDTO(faturaAtualizada);
    }

    /**
     * Remove uma fatura do sistema permanentemente
     * 
     * ATENÇÃO: Esta operação é irreversível e remove todos os dados
     * da fatura, incluindo histórico de pagamentos.
     * 
     * Inclui validação de acesso por usuário.
     * 
     * @param id ID da fatura a ser excluída
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @throws RuntimeException se a fatura não for encontrada ou não pertencer ao usuário
     */
    public void deletarFatura(Long id, Long usuarioId) {
        // Verifica se a fatura existe e pertence ao usuário antes de tentar excluir
        Fatura fatura = faturaRepository.findByIdAndCartaoCreditoUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RuntimeException("Fatura não encontrada"));
        
        // Remove a fatura do banco de dados
        faturaRepository.delete(fatura);
    }

    /**
     * Busca faturas de um usuário por status específico
     * 
     * Método usado para filtrar faturas por status (ABERTA, FECHADA, PAGA, VENCIDA).
     * Útil para relatórios e dashboards organizados por situação.
     * 
     * @param usuarioId ID do usuário cujas faturas devem ser filtradas
     * @param status Status da fatura para filtrar (enum StatusFatura)
     * @return Lista de FaturaDTO com faturas do status especificado
     */
    public List<FaturaDTO> buscarPorStatus(Long usuarioId, Fatura.StatusFatura status) {
        // Busca faturas do usuário com o status especificado
        List<Fatura> faturas = faturaRepository.findByUsuarioIdAndStatus(usuarioId, status);
        return faturas.stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca faturas de um usuário em um período específico
     * 
     * Método usado para relatórios mensais, trimestrais e anuais.
     * Filtra por data de vencimento das faturas.
     * 
     * @param usuarioId ID do usuário cujas faturas devem ser filtradas
     * @param dataInicio Data de início do período (inclusive)
     * @param dataFim Data de fim do período (exclusive)
     * @return Lista de FaturaDTO com faturas no período especificado
     */
    public List<FaturaDTO> buscarPorPeriodo(Long usuarioId, LocalDateTime dataInicio, LocalDateTime dataFim) {
        // Busca faturas do usuário com vencimento no período especificado
        List<Fatura> faturas = faturaRepository.findByUsuarioIdAndDataVencimentoBetween(usuarioId, dataInicio, dataFim);
        return faturas.stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * Calcula o valor total das faturas de um usuário por status
     * 
     * Método usado para cálculos financeiros e relatórios.
     * Retorna BigDecimal para precisão em cálculos monetários.
     * 
     * @param usuarioId ID do usuário para o qual calcular o total
     * @param status Status da fatura para filtrar o cálculo
     * @return BigDecimal com o valor total das faturas do status especificado
     */
    public BigDecimal getTotalFaturasPorStatus(Long usuarioId, Fatura.StatusFatura status) {
        // Busca o total das faturas do usuário com o status especificado
        Double total = faturaRepository.getTotalFaturaByUsuarioIdAndStatus(usuarioId, status);
        
        // Converte para BigDecimal ou retorna zero se não houver faturas
        return total != null ? BigDecimal.valueOf(total) : BigDecimal.ZERO;
    }

    /**
     * Busca faturas vencidas de um usuário
     * 
     * Método usado para alertas e notificações de faturas em atraso.
     * Filtra faturas com data de vencimento anterior à data atual.
     * 
     * @param usuarioId ID do usuário cujas faturas vencidas devem ser listadas
     * @return Lista de FaturaDTO com faturas vencidas do usuário
     */
    public List<FaturaDTO> buscarFaturasVencidas(Long usuarioId) {
        // Busca faturas vencidas do usuário (vencimento anterior à data atual)
        List<Fatura> faturas = faturaRepository.findVencidasByUsuarioId(usuarioId, LocalDateTime.now());
        return faturas.stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * Remove todas as faturas de um banco específico para um usuário
     * 
     * @param usuarioId ID do usuário
     * @param banco Nome do banco
     */
    @Transactional
    public void removerPorBanco(Long usuarioId, String banco) {
        List<Fatura> faturas = faturaRepository.findByCartaoCreditoUsuarioId(usuarioId);
        
        for (Fatura fatura : faturas) {
            if (fatura.getCartaoCredito().getBanco().equals(banco)) {
                faturaRepository.delete(fatura);
            }
        }
    }

    /**
     * Converte um FaturaDTO para entidade Fatura
     * 
     * Este método é responsável por:
     * - Mapear dados do DTO para a entidade
     * - Preparar a entidade para persistência
     * 
     * @param dto FaturaDTO a ser convertido
     * @return Entidade Fatura com os dados do DTO
     */
    private Fatura converterParaEntidade(FaturaDTO dto) {
        Fatura fatura = new Fatura();
        fatura.setId(dto.getId());
        fatura.setValorFatura(dto.getValorFatura());
        fatura.setValorPago(dto.getValorPago());
        fatura.setDataVencimento(dto.getDataVencimento());
        fatura.setDataFechamento(dto.getDataFechamento());
        fatura.setDataPagamento(dto.getDataPagamento());
        fatura.setStatusFatura(dto.getStatusFatura());
        fatura.setNumeroFatura(dto.getNumeroFatura());
        return fatura;
    }

    /**
     * Converte uma entidade Fatura para FaturaDTO
     * 
     * Este método é responsável por:
     * - Mapear dados da entidade para o DTO
     * - Incluir informações do cartão de crédito associado
     * - Garantir que dados sensíveis não sejam expostos
     * 
     * @param fatura Entidade Fatura a ser convertida
     * @return FaturaDTO com todos os dados necessários para exibição
     */
    private FaturaDTO converterParaDTO(Fatura fatura) {
        FaturaDTO dto = new FaturaDTO();
        dto.setId(fatura.getId());
        dto.setValorFatura(fatura.getValorFatura());
        dto.setValorPago(fatura.getValorPago());
        dto.setDataVencimento(fatura.getDataVencimento());
        dto.setDataFechamento(fatura.getDataFechamento());
        dto.setDataPagamento(fatura.getDataPagamento());
        dto.setStatusFatura(fatura.getStatusFatura());
        dto.setNumeroFatura(fatura.getNumeroFatura());
        
        // Inclui informações do cartão de crédito associado
        dto.setCartaoCreditoId(fatura.getCartaoCredito().getId());
        dto.setDataCriacao(fatura.getDataCriacao());
        dto.setDataAtualizacao(fatura.getDataAtualizacao());
        return dto;
    }
}
