package com.consumoesperto.service;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.YearMonth;
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
     * 3. Associa a transação ao usuário
     * 4. Persiste a transação no banco de dados
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
        
        // Cria um usuário temporário com o ID fornecido para associação
        // Em uma implementação completa, você buscaria o usuário do repositório
        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        transacao.setUsuario(usuario);
        
        // Persiste a transação no banco de dados
        Transacao transacaoSalva = transacaoRepository.save(transacao);
        return converterParaDTO(transacaoSalva);
    }

    /**
     * Busca uma transação específica pelo seu ID
     * 
     * Método para recuperar transações específicas por identificador.
     * Inclui validação de acesso para garantir que apenas o proprietário
     * da transação possa acessá-la.
     * 
     * @param id ID único da transação a ser buscada
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @return TransacaoDTO com os dados da transação encontrada
     * @throws RuntimeException se a transação não for encontrada ou não pertencer ao usuário
     */
    public TransacaoDTO buscarPorId(Long id, Long usuarioId) {
        // Busca a transação pelo ID ou lança exceção se não encontrar
        Transacao transacao = transacaoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transação não encontrada"));
        
        // Verifica se a transação pertence ao usuário solicitante
        if (transacao.getUsuario() == null || !transacao.getUsuario().getId().equals(usuarioId)) {
            throw new RuntimeException("Acesso negado: Transação não pertence ao usuário");
        }
        
        return converterParaDTO(transacao);
    }

    /**
     * Lista todas as transações de um usuário específico
     * 
     * Método usado para exibir o histórico de transações do usuário
     * no dashboard e outras telas do sistema.
     * 
     * @param usuarioId ID do usuário cujas transações devem ser listadas
     * @return Lista de TransacaoDTO com todas as transações do usuário
     */
    public List<TransacaoDTO> buscarPorUsuarioId(Long usuarioId) {
        // Busca todas as transações do usuário ordenadas por data (mais recentes primeiro)
        List<Transacao> transacoes = transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(usuarioId);
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
     * @param id ID da transação a ser atualizada
     * @param transacaoDTO DTO com os novos dados da transação
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @return TransacaoDTO com os dados atualizados
     * @throws RuntimeException se a transação não for encontrada ou não pertencer ao usuário
     */
    public TransacaoDTO atualizarTransacao(Long id, TransacaoDTO transacaoDTO, Long usuarioId) {
        // Verifica se a transação existe antes de tentar atualizar
        Transacao transacao = transacaoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transação não encontrada"));
        
        // Verifica se a transação pertence ao usuário solicitante
        if (transacao.getUsuario() == null || !transacao.getUsuario().getId().equals(usuarioId)) {
            throw new RuntimeException("Acesso negado: Transação não pertence ao usuário");
        }
        
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
     * @param id ID da transação a ser excluída
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @throws RuntimeException se a transação não for encontrada ou não pertencer ao usuário
     */
    public void deletarTransacao(Long id, Long usuarioId) {
        // Verifica se a transação existe antes de tentar excluir
        Transacao transacao = transacaoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transação não encontrada"));
        
        // Verifica se a transação pertence ao usuário solicitante
        if (transacao.getUsuario() == null || !transacao.getUsuario().getId().equals(usuarioId)) {
            throw new RuntimeException("Acesso negado: Transação não pertence ao usuário");
        }
        
        // Remove a transação do banco de dados
        transacaoRepository.delete(transacao);
    }

    /**
     * Busca transações de um usuário em um período específico
     * 
     * Método usado para relatórios mensais, trimestrais e anuais.
     * Útil para análise de gastos e receitas por período.
     * 
     * @param usuarioId ID do usuário cujas transações devem ser filtradas
     * @param dataInicio Data de início do período (inclusive)
     * @param dataFim Data de fim do período (exclusive)
     * @return Lista de TransacaoDTO com transações no período especificado
     */
    public List<TransacaoDTO> buscarPorPeriodo(Long usuarioId, LocalDateTime dataInicio, LocalDateTime dataFim) {
        // Busca transações do usuário no período especificado
        List<Transacao> transacoes = transacaoRepository.findByUsuarioIdAndDataTransacaoBetweenOrderByDataTransacaoDesc(usuarioId, dataInicio, dataFim);
        
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
     * @param usuarioId ID do usuário cujas transações devem ser filtradas
     * @param categoriaId ID da categoria para filtrar as transações
     * @return Lista de TransacaoDTO com transações da categoria especificada
     */
    public List<TransacaoDTO> buscarPorCategoria(Long usuarioId, Long categoriaId) {
        // Busca transações do usuário por categoria específica
        List<Transacao> transacoes = transacaoRepository.findByUsuarioIdAndCategoriaIdOrderByDataTransacaoDesc(usuarioId, categoriaId);
        
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
     * @param usuarioId ID do usuário cujas transações devem ser filtradas
     * @param tipo Tipo de transação (RECEITA ou DESPESA)
     * @return Lista de TransacaoDTO com transações do tipo especificado
     */
    public List<TransacaoDTO> buscarPorTipo(Long usuarioId, Transacao.TipoTransacao tipo) {
        // Busca transações do usuário por tipo específico
        List<Transacao> transacoes = transacaoRepository.findByUsuarioIdAndTipoTransacaoOrderByDataTransacaoDesc(usuarioId, tipo);
        
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
     * @param usuarioId ID do usuário para o qual gerar o resumo
     * @return Map com estatísticas financeiras (totalTransacoes, totalReceitas, totalDespesas)
     */
    public Object obterResumo(Long usuarioId) {
        // Busca todas as transações do usuário
        List<Transacao> transacoesUsuario = transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(usuarioId);
        
        // Cria um mapa com as estatísticas financeiras
        Map<String, Object> resumo = new HashMap<>();
        resumo.put("totalTransacoes", transacoesUsuario.size());
        
        // Calcula o total de receitas usando consulta otimizada
        BigDecimal totalReceitas = transacaoRepository.sumValorByUsuarioIdAndTipoTransacao(usuarioId, Transacao.TipoTransacao.RECEITA);
        resumo.put("totalReceitas", totalReceitas != null ? totalReceitas.doubleValue() : 0.0);
        
        // Calcula o total de despesas usando consulta otimizada
        BigDecimal totalDespesas = transacaoRepository.sumValorByUsuarioIdAndTipoTransacao(usuarioId, Transacao.TipoTransacao.DESPESA);
        resumo.put("totalDespesas", totalDespesas != null ? totalDespesas.doubleValue() : 0.0);
        
        // Calcula o saldo (receitas - despesas)
        double saldo = (totalReceitas != null ? totalReceitas.doubleValue() : 0.0) - 
                      (totalDespesas != null ? totalDespesas.doubleValue() : 0.0);
        resumo.put("saldo", saldo);
        
        return resumo;
    }

    /**
     * Busca transações do mês atual para um usuário
     * 
     * Método usado para exibir apenas transações do mês atual no dashboard,
     * mas mantém histórico anual no banco.
     * 
     * @param usuarioId ID do usuário cujas transações devem ser filtradas
     * @return Lista de TransacaoDTO com transações do mês atual
     */
    public List<TransacaoDTO> buscarDoMesAtual(Long usuarioId) {
        // Obter início e fim do mês atual
        YearMonth mesAtual = YearMonth.now();
        LocalDateTime inicioMes = mesAtual.atDay(1).atStartOfDay();
        LocalDateTime fimMes = mesAtual.atEndOfMonth().atTime(23, 59, 59);
        
        return buscarPorPeriodo(usuarioId, inicioMes, fimMes);
    }

    /**
     * Busca transações de um mês específico para um usuário
     * 
     * @param usuarioId ID do usuário cujas transações devem ser filtradas
     * @param ano Ano das transações
     * @param mes Mês das transações (1-12)
     * @return Lista de TransacaoDTO com transações do mês especificado
     */
    public List<TransacaoDTO> buscarDoMes(Long usuarioId, int ano, int mes) {
        // Obter início e fim do mês especificado
        YearMonth mesEspecifico = YearMonth.of(ano, mes);
        LocalDateTime inicioMes = mesEspecifico.atDay(1).atStartOfDay();
        LocalDateTime fimMes = mesEspecifico.atEndOfMonth().atTime(23, 59, 59);
        
        return buscarPorPeriodo(usuarioId, inicioMes, fimMes);
    }

    /**
     * Calcula resumo financeiro do mês atual
     * 
     * @param usuarioId ID do usuário
     * @return Map com estatísticas do mês atual
     */
    public Map<String, Object> obterResumoDoMesAtual(Long usuarioId) {
        List<TransacaoDTO> transacoesMes = buscarDoMesAtual(usuarioId);
        
        Map<String, Object> resumo = new HashMap<>();
        resumo.put("totalTransacoes", transacoesMes.size());
        
        // Calcular receitas do mês
        double totalReceitas = transacoesMes.stream()
            .filter(t -> t.getTipoTransacao() == TransacaoDTO.TipoTransacao.RECEITA)
            .mapToDouble(t -> t.getValor().doubleValue())
            .sum();
        resumo.put("totalReceitas", totalReceitas);
        
        // Calcular despesas do mês
        double totalDespesas = transacoesMes.stream()
            .filter(t -> t.getTipoTransacao() == TransacaoDTO.TipoTransacao.DESPESA)
            .mapToDouble(t -> t.getValor().doubleValue())
            .sum();
        resumo.put("totalDespesas", totalDespesas);
        
        // Calcular saldo do mês
        double saldo = totalReceitas - totalDespesas;
        resumo.put("saldo", saldo);
        
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
        if (transacao.getTipoTransacao() != null) {
            dto.setTipoTransacao(TransacaoDTO.TipoTransacao.valueOf(transacao.getTipoTransacao().name()));
        } else {
            dto.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA); // Valor padrão
        }
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
