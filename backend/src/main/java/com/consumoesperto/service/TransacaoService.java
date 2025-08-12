package com.consumoesperto.service;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Serviço responsável por gerenciar operações relacionadas a transações financeiras
 * 
 * Este serviço implementa a lógica de negócio para criação, busca, atualização
 * e exclusão de transações. Também fornece métodos para consultas específicas
 * por período, categoria e tipo de transação.
 * 
 * Funcionalidades principais:
 * - CRUD completo de transações
 * - Consultas por período, categoria e tipo
 * - Resumo financeiro com receitas e despesas
 * - Validação de categorias
 * - Controle de acesso por usuário (preparado para implementação)
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor // Lombok: gera construtor com campos final automaticamente
@Transactional // Todas as operações são transacionais para garantir consistência
public class TransacaoService {

    // Repositório para operações de persistência de transações
    private final TransacaoRepository transacaoRepository;
    
    // Repositório para validação e busca de categorias
    private final CategoriaRepository categoriaRepository;

    /**
     * Cria uma nova transação financeira no sistema
     * 
     * Este método implementa o fluxo completo de criação de transação:
     * 1. Valida e associa a categoria (se fornecida)
     * 2. Define o tipo de transação (receita ou despesa)
     * 3. Persiste a transação no banco de dados
     * 
     * TODO: Implementar validação de usuário quando o sistema de autenticação estiver completo
     * 
     * @param transacaoDTO DTO com os dados da transação a ser criada
     * @param usuarioId ID do usuário que está criando a transação
     * @return TransacaoDTO com os dados da transação criada
     * @throws RuntimeException se a categoria não for encontrada
     */
    public TransacaoDTO criarTransacao(TransacaoDTO transacaoDTO, Long usuarioId) {
        // Cria uma nova instância de transação a partir dos dados do DTO
        Transacao transacao = new Transacao();
        transacao.setDescricao(transacaoDTO.getDescricao());
        transacao.setValor(transacaoDTO.getValor());
        
        // Converte o tipo de transação do DTO para o enum da entidade
        transacao.setTipoTransacao(Transacao.TipoTransacao.valueOf(transacaoDTO.getTipoTransacao().name()));
        transacao.setDataTransacao(transacaoDTO.getDataTransacao());
        
        // Validação e associação da categoria (opcional)
        if (transacaoDTO.getCategoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(transacaoDTO.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
            transacao.setCategoria(categoria);
        }
        
        // TODO: Implementar validação de usuário
        // Buscar usuário e associar à transação
        // transacao.setUsuario(usuarioRepository.findById(usuarioId).orElseThrow());
        
        // Persiste a transação no banco de dados
        Transacao transacaoSalva = transacaoRepository.save(transacao);
        return converterParaDTO(transacaoSalva);
    }

    /**
     * Busca uma transação específica pelo seu ID
     * 
     * Método para recuperar transações específicas por identificador.
     * Inclui validação de acesso (preparado para implementação).
     * 
     * TODO: Implementar verificação de propriedade da transação
     * 
     * @param id ID único da transação a ser buscada
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @return TransacaoDTO com os dados da transação encontrada
     * @throws RuntimeException se a transação não for encontrada
     */
    public TransacaoDTO buscarPorId(Long id, Long usuarioId) {
        // Busca a transação pelo ID ou lança exceção se não encontrar
        Transacao transacao = transacaoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transação não encontrada"));
        
        // TODO: Implementar verificação de propriedade da transação
        // Verificar se a transação pertence ao usuário solicitante
        // if (!transacao.getUsuario().getId().equals(usuarioId)) {
        //     throw new RuntimeException("Acesso negado");
        // }
        
        return converterParaDTO(transacao);
    }

    /**
     * Lista todas as transações de um usuário específico
     * 
     * Método usado para exibir o histórico de transações do usuário
     * no dashboard e outras telas do sistema.
     * 
     * TODO: Implementar filtro por usuário quando o sistema estiver completo
     * 
     * @param usuarioId ID do usuário cujas transações devem ser listadas
     * @return Lista de TransacaoDTO com todas as transações do usuário
     */
    public List<TransacaoDTO> buscarPorUsuarioId(Long usuarioId) {
        // TODO: Implementar busca específica por usuário
        // List<Transacao> transacoes = transacaoRepository.findByUsuarioId(usuarioId);
        
        // Por enquanto, retorna todas as transações (implementação temporária)
        List<Transacao> transacoes = transacaoRepository.findAll();
        return transacoes.stream()
            .map(this::converterParaDTO)
            .collect(Collectors.toList());
    }

    /**
     * Atualiza os dados de uma transação existente
     * 
     * Este método permite modificar informações da transação como:
     * - Descrição
     * - Valor
     * - Tipo de transação
     * - Data da transação
     * - Categoria associada
     * 
     * TODO: Implementar verificação de propriedade da transação
     * 
     * @param id ID da transação a ser atualizada
     * @param transacaoDTO DTO com os novos dados da transação
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @return TransacaoDTO com os dados atualizados
     * @throws RuntimeException se a transação não for encontrada
     */
    public TransacaoDTO atualizarTransacao(Long id, TransacaoDTO transacaoDTO, Long usuarioId) {
        // Verifica se a transação existe antes de tentar atualizar
        Transacao transacao = transacaoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transação não encontrada"));
        
        // TODO: Implementar verificação de propriedade da transação
        // Verificar se a transação pertence ao usuário solicitante
        // if (!transacao.getUsuario().getId().equals(usuarioId)) {
        //     throw new RuntimeException("Acesso negado");
        // }
        
        // Atualiza os campos da transação com os novos valores
        transacao.setDescricao(transacaoDTO.getDescricao());
        transacao.setValor(transacaoDTO.getValor());
        transacao.setTipoTransacao(Transacao.TipoTransacao.valueOf(transacaoDTO.getTipoTransacao().name()));
        transacao.setDataTransacao(transacaoDTO.getDataTransacao());
        
        // Atualiza a categoria se uma nova foi fornecida
        if (transacaoDTO.getCategoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(transacaoDTO.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
            transacao.setCategoria(categoria);
        }
        
        // Persiste as alterações no banco de dados
        Transacao transacaoAtualizada = transacaoRepository.save(transacao);
        return converterParaDTO(transacaoAtualizada);
    }

    /**
     * Remove uma transação do sistema permanentemente
     * 
     * ATENÇÃO: Esta operação é irreversível e remove todos os dados
     * da transação, incluindo histórico financeiro.
     * 
     * TODO: Implementar verificação de propriedade da transação
     * 
     * @param id ID da transação a ser excluída
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @throws RuntimeException se a transação não for encontrada
     */
    public void deletarTransacao(Long id, Long usuarioId) {
        // Verifica se a transação existe antes de tentar excluir
        Transacao transacao = transacaoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transação não encontrada"));
        
        // TODO: Implementar verificação de propriedade da transação
        // Verificar se a transação pertence ao usuário solicitante
        // if (!transacao.getUsuario().getId().equals(usuarioId)) {
        //     throw new RuntimeException("Acesso negado");
        // }
        
        // Remove a transação do banco de dados
        transacaoRepository.delete(transacao);
    }

    /**
     * Busca transações de um usuário em um período específico
     * 
     * Método usado para relatórios mensais, trimestrais e anuais.
     * Útil para análise de gastos e receitas por período.
     * 
     * TODO: Implementar busca específica por usuário quando o sistema estiver completo
     * 
     * @param usuarioId ID do usuário cujas transações devem ser filtradas
     * @param dataInicio Data de início do período (inclusive)
     * @param dataFim Data de fim do período (exclusive)
     * @return Lista de TransacaoDTO com transações no período especificado
     */
    public List<TransacaoDTO> buscarPorPeriodo(Long usuarioId, LocalDateTime dataInicio, LocalDateTime dataFim) {
        // TODO: Implementar busca específica por usuário
        // List<Transacao> transacoes = transacaoRepository.findByUsuarioIdAndDataTransacaoBetween(usuarioId, dataInicio, dataFim);
        
        // Por enquanto, filtra todas as transações no período (implementação temporária)
        List<Transacao> transacoes = transacaoRepository.findAll().stream()
            .filter(t -> t.getDataTransacao().isAfter(dataInicio) && t.getDataTransacao().isBefore(dataFim))
            .collect(Collectors.toList());
        
        return transacoes.stream()
            .map(this::converterParaDTO)
            .collect(Collectors.toList());
    }

    /**
     * Busca transações de um usuário por categoria específica
     * 
     * Método usado para análise de gastos por categoria (alimentação, transporte, etc.)
     * e para relatórios de despesas organizados por tipo.
     * 
     * TODO: Implementar busca específica por usuário quando o sistema estiver completo
     * 
     * @param usuarioId ID do usuário cujas transações devem ser filtradas
     * @param categoriaId ID da categoria para filtrar as transações
     * @return Lista de TransacaoDTO com transações da categoria especificada
     */
    public List<TransacaoDTO> buscarPorCategoria(Long usuarioId, Long categoriaId) {
        // TODO: Implementar busca específica por usuário
        // List<Transacao> transacoes = transacaoRepository.findByUsuarioIdAndCategoriaId(usuarioId, categoriaId);
        
        // Por enquanto, filtra todas as transações da categoria (implementação temporária)
        List<Transacao> transacoes = transacaoRepository.findAll().stream()
            .filter(t -> t.getCategoria() != null && t.getCategoria().getId().equals(categoriaId))
            .collect(Collectors.toList());
        
        return transacoes.stream()
            .map(this::converterParaDTO)
            .collect(Collectors.toList());
    }

    /**
     * Busca transações de um usuário por tipo (receita ou despesa)
     * 
     * Método usado para separar receitas de despesas em relatórios
     * e para cálculos de saldo e fluxo de caixa.
     * 
     * TODO: Implementar busca específica por usuário quando o sistema estiver completo
     * 
     * @param usuarioId ID do usuário cujas transações devem ser filtradas
     * @param tipo Tipo de transação (RECEITA ou DESPESA)
     * @return Lista de TransacaoDTO com transações do tipo especificado
     */
    public List<TransacaoDTO> buscarPorTipo(Long usuarioId, Transacao.TipoTransacao tipo) {
        // TODO: Implementar busca específica por usuário
        // List<Transacao> transacoes = transacaoRepository.findByUsuarioIdAndTipoTransacao(usuarioId, tipo);
        
        // Por enquanto, filtra todas as transações do tipo (implementação temporária)
        List<Transacao> transacoes = transacaoRepository.findAll().stream()
            .filter(t -> t.getTipoTransacao() == tipo)
            .collect(Collectors.toList());
        
        return transacoes.stream()
            .map(this::converterParaDTO)
            .collect(Collectors.toList());
    }

    /**
     * Gera um resumo financeiro das transações de um usuário
     * 
     * Este método calcula estatísticas importantes como:
     * - Total de transações
     * - Total de receitas
     * - Total de despesas
     * 
     * Útil para dashboard e relatórios financeiros.
     * 
     * TODO: Implementar filtro por usuário quando o sistema estiver completo
     * 
     * @param usuarioId ID do usuário para o qual gerar o resumo
     * @return Map com estatísticas financeiras (totalTransacoes, totalReceitas, totalDespesas)
     */
    public Object obterResumo(Long usuarioId) {
        // TODO: Implementar filtro por usuário
        // Por enquanto, calcula resumo de todas as transações (implementação temporária)
        List<Transacao> todasTransacoes = transacaoRepository.findAll();
        
        // Cria um mapa com as estatísticas financeiras
        Map<String, Object> resumo = new HashMap<>();
        resumo.put("totalTransacoes", todasTransacoes.size());
        
        // Calcula o total de receitas (transações positivas)
        resumo.put("totalReceitas", todasTransacoes.stream()
            .filter(t -> t.getTipoTransacao() == Transacao.TipoTransacao.RECEITA)
            .mapToDouble(t -> t.getValor().doubleValue())
            .sum());
        
        // Calcula o total de despesas (transações negativas)
        resumo.put("totalDespesas", todasTransacoes.stream()
            .filter(t -> t.getTipoTransacao() == Transacao.TipoTransacao.DESPESA)
            .mapToDouble(t -> t.getValor().doubleValue())
            .sum());
        
        return resumo;
    }

    /**
     * Converte uma entidade Transacao para TransacaoDTO
     * 
     * Este método é responsável por:
     * - Mapear dados da entidade para o DTO
     * - Incluir informações da categoria associada
     * - Garantir que dados sensíveis não sejam expostos
     * 
     * @param transacao Entidade Transacao a ser convertida
     * @return TransacaoDTO com todos os dados necessários para exibição
     */
    private TransacaoDTO converterParaDTO(Transacao transacao) {
        TransacaoDTO dto = new TransacaoDTO();
        dto.setId(transacao.getId());
        dto.setDescricao(transacao.getDescricao());
        dto.setValor(transacao.getValor());
        
        // Converte o tipo de transação da entidade para o DTO
        dto.setTipoTransacao(TransacaoDTO.TipoTransacao.valueOf(transacao.getTipoTransacao().name()));
        dto.setDataTransacao(transacao.getDataTransacao());
        dto.setDataCriacao(transacao.getDataCriacao());
        
        // Inclui informações da categoria se estiver associada
        if (transacao.getCategoria() != null) {
            dto.setCategoriaId(transacao.getCategoria().getId());
            dto.setCategoriaNome(transacao.getCategoria().getNome());
        }
        
        return dto;
    }
}
