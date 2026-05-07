package com.consumoesperto.service;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
@Slf4j
public class TransacaoService {

    // Repositório para operações de persistência de transações
    private final TransacaoRepository transacaoRepository;
    
    // Repositório para validação e busca de categorias
    private final CategoriaRepository categoriaRepository;

    private final SaldoService saldoService;

    private final FaturaRepository faturaRepository;

    private final CartaoCreditoRepository cartaoCreditoRepository;

    private final FaturaService faturaService;

    private final FinancialProactiveService financialProactiveService;

    private final ScoreService scoreService;

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
        return criarTransacao(transacaoDTO, usuarioId, true);
    }

    public TransacaoDTO criarTransacao(TransacaoDTO transacaoDTO, Long usuarioId, boolean executarProativos) {
        // Cria uma nova instância de transação a partir dos dados do DTO
        Transacao transacao = new Transacao();
        transacao.setDescricao(transacaoDTO.getDescricao());
        transacao.setValor(transacaoDTO.getValor());
        
        // Converte o tipo de transação do DTO para o enum da entidade
        transacao.setTipoTransacao(Transacao.TipoTransacao.valueOf(transacaoDTO.getTipoTransacao().name()));
        transacao.setDataTransacao(transacaoDTO.getDataTransacao());
        aplicarConfiguracaoRecorrencia(transacao, transacaoDTO);
        transacao.setExcluido(false);
        transacao.setStatusConferencia(transacaoDTO.getStatusConferencia() != null
            ? Transacao.StatusConferencia.valueOf(transacaoDTO.getStatusConferencia().name())
            : Transacao.StatusConferencia.CONFIRMADA);
        transacao.setCnpj(normalizarCnpjOpcional(transacaoDTO.getCnpj()));
        if (transacaoDTO.getGrupoParcelaId() != null && !transacaoDTO.getGrupoParcelaId().isBlank()) {
            transacao.setGrupoParcelaId(transacaoDTO.getGrupoParcelaId().trim());
        }
        if (transacaoDTO.getParcelaAtual() != null) {
            transacao.setParcelaAtual(transacaoDTO.getParcelaAtual());
        }
        if (transacaoDTO.getTotalParcelas() != null) {
            transacao.setTotalParcelas(transacaoDTO.getTotalParcelas());
        }
        if (transacaoDTO.getValorReal() != null) {
            transacao.setValorReal(transacaoDTO.getValorReal());
        }
        if (transacaoDTO.getValorComJuros() != null) {
            transacao.setValorComJuros(transacaoDTO.getValorComJuros());
        }

        Long categoriaId = transacaoDTO.getCategoriaId();
        if (categoriaId == null && transacao.getTipoTransacao() == Transacao.TipoTransacao.DESPESA) {
            categoriaId = financialProactiveService.sugerirCategoria(usuarioId, transacao.getDescricao())
                .map(Categoria::getId)
                .orElse(null);
        }

        // Validação e associação da categoria (opcional)
        if (categoriaId != null) {
            Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
            transacao.setCategoria(categoria);
        }
        
        // Cria um usuário temporário com o ID fornecido para associação
        // Em uma implementação completa, você buscaria o usuário do repositório
        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        transacao.setUsuario(usuario);

        aplicarVinculoFatura(transacao, transacaoDTO, usuarioId, true);

        // Persiste a transação no banco de dados
        Transacao transacaoSalva = transacaoRepository.save(transacao);
        if (transacaoSalva.getFatura() != null) {
            faturaService.sincronizarValorFaturaComTransacoes(transacaoSalva.getFatura().getId());
        }
        saldoService.notificarAlteracaoSaldo(usuarioId);
        if (executarProativos && transacaoSalva.getTipoTransacao() == Transacao.TipoTransacao.DESPESA) {
            financialProactiveService.aposDespesaSalva(transacaoSalva);
        } else if (executarProativos && transacaoSalva.getTipoTransacao() == Transacao.TipoTransacao.INVESTIMENTO
            && transacaoSalva.getStatusConferencia() == Transacao.StatusConferencia.CONFIRMADA) {
            scoreService.registrarEvento(usuarioId, ScoreService.EventoScore.INVESTIMENTO_REGISTRADO,
                "Investimento registrado: " + transacaoSalva.getDescricao());
        }
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

        Long faturaIdAntes = transacao.getFatura() != null ? transacao.getFatura().getId() : null;

        // Atualiza os campos da transação com os novos valores
        transacao.setDescricao(transacaoDTO.getDescricao());
        transacao.setValor(transacaoDTO.getValor());
        transacao.setTipoTransacao(Transacao.TipoTransacao.valueOf(transacaoDTO.getTipoTransacao().name()));
        transacao.setDataTransacao(transacaoDTO.getDataTransacao());
        aplicarConfiguracaoRecorrencia(transacao, transacaoDTO);
        if (transacaoDTO.getStatusConferencia() != null) {
            transacao.setStatusConferencia(Transacao.StatusConferencia.valueOf(transacaoDTO.getStatusConferencia().name()));
        }
        if (transacaoDTO.getCnpj() != null) {
            transacao.setCnpj(normalizarCnpjOpcional(transacaoDTO.getCnpj()));
        }

        // Atualiza a categoria se uma nova foi fornecida
        if (transacaoDTO.getCategoriaId() != null) {
            Categoria categoria = categoriaRepository.findById(transacaoDTO.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));
            transacao.setCategoria(categoria);
        }

        aplicarVinculoFatura(transacao, transacaoDTO, usuarioId, false);

        // Persiste as alterações no banco de dados
        Transacao transacaoAtualizada = transacaoRepository.save(transacao);
        Long faturaIdDepois = transacaoAtualizada.getFatura() != null ? transacaoAtualizada.getFatura().getId() : null;
        if (faturaIdAntes != null && !Objects.equals(faturaIdAntes, faturaIdDepois)) {
            faturaService.sincronizarValorFaturaComTransacoes(faturaIdAntes);
        }
        if (faturaIdDepois != null) {
            faturaService.sincronizarValorFaturaComTransacoes(faturaIdDepois);
        }
        saldoService.notificarAlteracaoSaldo(usuarioId);
        if (transacaoAtualizada.getTipoTransacao() == Transacao.TipoTransacao.DESPESA) {
            financialProactiveService.aposDespesaSalva(transacaoAtualizada);
        }
        return converterParaDTO(transacaoAtualizada);
    }

    public TransacaoDTO atualizarStatusConferencia(Long id, TransacaoDTO.StatusConferencia status, Long usuarioId) {
        Transacao transacao = transacaoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transação não encontrada"));

        if (transacao.getUsuario() == null || !transacao.getUsuario().getId().equals(usuarioId)) {
            throw new RuntimeException("Acesso negado: Transação não pertence ao usuário");
        }

        transacao.setStatusConferencia(Transacao.StatusConferencia.valueOf(status.name()));
        Transacao atualizada = transacaoRepository.save(transacao);
        if (atualizada.getFatura() != null) {
            faturaService.sincronizarValorFaturaComTransacoes(atualizada.getFatura().getId());
        }
        saldoService.notificarAlteracaoSaldo(usuarioId);
        if (atualizada.getTipoTransacao() == Transacao.TipoTransacao.DESPESA) {
            financialProactiveService.aposDespesaSalva(atualizada);
        }
        return converterParaDTO(atualizada);
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
        
        Long faturaId = transacao.getFatura() != null ? transacao.getFatura().getId() : null;

        // Soft delete para manter histórico e auditoria
        transacao.setExcluido(true);
        transacaoRepository.save(transacao);
        if (faturaId != null) {
            faturaService.sincronizarValorFaturaComTransacoes(faturaId);
        }
        saldoService.notificarAlteracaoSaldo(usuarioId);
    }

    /**
     * Exclusão em grupo de parcelas: {@code UM} só esta; {@code FUTURAS} esta e parcelas seguintes; {@code TUDO} o grupo inteiro.
     */
    public void deletarTransacaoComModoParcelamento(Long id, Long usuarioId, String modo) {
        Transacao transacao = transacaoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transação não encontrada"));
        if (transacao.getUsuario() == null || !transacao.getUsuario().getId().equals(usuarioId)) {
            throw new RuntimeException("Acesso negado: Transação não pertence ao usuário");
        }
        String grupo = transacao.getGrupoParcelaId();
        String m = normalizarModoParcelamentoExclusao(modo);
        if (grupo == null || grupo.isBlank() || "UM".equals(m)) {
            deletarTransacao(id, usuarioId);
            return;
        }
        List<Transacao> todas = transacaoRepository.findByUsuarioIdAndGrupoParcelaIdOrderByParcelaAtualAsc(usuarioId, grupo);
        List<Transacao> alvo;
        if ("TUDO".equals(m)) {
            alvo = todas;
        } else if ("FUTURAS".equals(m)) {
            int pa = transacao.getParcelaAtual() != null ? transacao.getParcelaAtual() : 0;
            alvo = todas.stream()
                .filter(x -> x.getParcelaAtual() != null && x.getParcelaAtual() >= pa)
                .collect(Collectors.toList());
        } else {
            deletarTransacao(id, usuarioId);
            return;
        }
        Set<Long> faturas = new HashSet<>();
        for (Transacao t : alvo) {
            if (t.getFatura() != null) {
                faturas.add(t.getFatura().getId());
            }
            t.setExcluido(true);
            transacaoRepository.save(t);
        }
        for (Long fid : faturas) {
            faturaService.sincronizarValorFaturaComTransacoes(fid);
        }
        saldoService.notificarAlteracaoSaldo(usuarioId);
    }

    /**
     * Aceita sinónimos do app (UNICA, ESTA_E_PROXIMAS, TODAS) e valores legados (UM, FUTURAS, TUDO).
     */
    private static String normalizarModoParcelamentoExclusao(String modo) {
        if (modo == null || modo.isBlank()) {
            return "UM";
        }
        String m = modo.trim().toUpperCase();
        switch (m) {
            case "UNICA":
                return "UM";
            case "ESTA_E_PROXIMAS":
                return "FUTURAS";
            case "TODAS":
                return "TUDO";
            default:
                return m;
        }
    }

    /**
     * Busca transações de um usuário em um período específico
     * 
     * Método usado para relatórios mensais, trimestrais e anuais.
     * Útil para análise de gastos e receitas por período.
     * 
     * @param usuarioId ID do usuário cujas transações devem ser filtradas
     * @param dataInicio Data de início do período (inclusive)
     * @param dataFim Data de fim do período (inclusive)
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
        
        // Receitas e despesas confirmadas (saldo dinâmico ao longo do tempo)
        BigDecimal totalReceitas = transacaoRepository.sumValorConfirmadaByUsuarioIdAndTipoTransacao(
            usuarioId, Transacao.TipoTransacao.RECEITA);
        resumo.put("totalReceitas", totalReceitas != null ? totalReceitas.doubleValue() : 0.0);

        BigDecimal totalDespesas = transacaoRepository.sumValorConfirmadaByUsuarioIdAndTipoTransacao(
            usuarioId, Transacao.TipoTransacao.DESPESA);
        resumo.put("totalDespesas", totalDespesas != null ? totalDespesas.doubleValue() : 0.0);
        
        double saldo = saldoService.saldoContaCorrente(usuarioId).doubleValue();
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
     * Resumo financeiro de um mês civil: totais por soma no repositório (apenas confirmadas),
     * contagem de linhas no período (todas as transações não excluídas, com ou sem categoria/cartão).
     * Usado pelo dashboard, relatório JSON e PDF para manter números alinhados.
     */
    public Map<String, Object> resumoFinanceiroMes(Long usuarioId, YearMonth yearMonth) {
        LocalDateTime inicio = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime fim = yearMonth.atEndOfMonth().atTime(23, 59, 59);
        BigDecimal totalReceitas = transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
            usuarioId, Transacao.TipoTransacao.RECEITA, inicio, fim);
        BigDecimal totalDespesas = transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
            usuarioId, Transacao.TipoTransacao.DESPESA, inicio, fim);
        BigDecimal totalInvestimentos = transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
            usuarioId, Transacao.TipoTransacao.INVESTIMENTO, inicio, fim);
        totalReceitas = totalReceitas != null ? totalReceitas : BigDecimal.ZERO;
        totalDespesas = totalDespesas != null ? totalDespesas : BigDecimal.ZERO;
        totalInvestimentos = totalInvestimentos != null ? totalInvestimentos : BigDecimal.ZERO;
        BigDecimal saldo = totalReceitas.subtract(totalDespesas).subtract(totalInvestimentos);
        long totalLinhas = transacaoRepository.countTransacoesUsuarioNoPeriodo(usuarioId, inicio, fim);
        Map<String, Object> resumo = new HashMap<>();
        resumo.put("totalTransacoes", totalLinhas);
        resumo.put("totalReceitas", totalReceitas.doubleValue());
        resumo.put("totalDespesas", totalDespesas.doubleValue());
        resumo.put("totalInvestimentos", totalInvestimentos.doubleValue());
        resumo.put("saldo", saldo.doubleValue());
        return resumo;
    }

    /**
     * Calcula resumo financeiro do mês atual (mesma base que relatório mensal / PDF).
     */
    public Map<String, Object> obterResumoDoMesAtual(Long usuarioId) {
        return resumoFinanceiroMes(usuarioId, YearMonth.now());
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
        dto.setRecorrente(transacao.isRecorrente());
        if (transacao.getFrequencia() != null) {
            dto.setFrequencia(TransacaoDTO.FrequenciaRecorrencia.valueOf(transacao.getFrequencia().name()));
        }
        dto.setProximaExecucao(transacao.getProximaExecucao());
        if (transacao.getStatusConferencia() != null) {
            dto.setStatusConferencia(TransacaoDTO.StatusConferencia.valueOf(transacao.getStatusConferencia().name()));
        }
        dto.setCnpj(transacao.getCnpj());

        // Inclui informações da categoria se estiver associada
        if (transacao.getCategoria() != null) {
            dto.setCategoriaId(transacao.getCategoria().getId());
            dto.setCategoriaNome(transacao.getCategoria().getNome());
        }
        if (transacao.getFatura() != null) {
            dto.setFaturaId(transacao.getFatura().getId());
            if (transacao.getFatura().getCartaoCredito() != null) {
                dto.setCartaoCreditoId(transacao.getFatura().getCartaoCredito().getId());
            }
        }
        dto.setGrupoParcelaId(transacao.getGrupoParcelaId());
        dto.setParcelaAtual(transacao.getParcelaAtual());
        dto.setTotalParcelas(transacao.getTotalParcelas());
        dto.setValorReal(transacao.getValorReal());
        dto.setValorComJuros(transacao.getValorComJuros());

        return dto;
    }

    /**
     * @param criacaoNova {@code true} na criação: sem cartão/fatura = despesa em caixa. Na edição: sem ids = mantém vínculo atual.
     */
    private void aplicarVinculoFatura(Transacao transacao, TransacaoDTO dto, Long usuarioId, boolean criacaoNova) {
        if (transacao.getTipoTransacao() != Transacao.TipoTransacao.DESPESA) {
            transacao.setFatura(null);
            return;
        }
        if (dto.getFaturaId() != null) {
            Fatura f = faturaRepository.findByIdAndCartaoCreditoUsuarioId(dto.getFaturaId(), usuarioId)
                .orElseThrow(() -> new RuntimeException("Fatura não encontrada"));
            transacao.setFatura(f);
            return;
        }
        if (dto.getCartaoCreditoId() != null) {
            CartaoCredito cartao = cartaoCreditoRepository.findByIdAndUsuarioId(dto.getCartaoCreditoId(), usuarioId)
                .orElseThrow(() -> new RuntimeException("Cartão de crédito não encontrado"));
            Fatura f = faturaService.resolverFaturaAbertaParaCartao(usuarioId, cartao);
            transacao.setFatura(f);
            return;
        }
        if (!criacaoNova) {
            return;
        }
        transacao.setFatura(null);
    }

    private String normalizarCnpjOpcional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        return digits.length() == 14 ? digits : null;
    }

    private void aplicarConfiguracaoRecorrencia(Transacao transacao, TransacaoDTO dto) {
        transacao.setRecorrente(dto.isRecorrente());
        if (!dto.isRecorrente()) {
            transacao.setFrequencia(null);
            transacao.setProximaExecucao(null);
            return;
        }
        if (dto.getFrequencia() == null || dto.getProximaExecucao() == null) {
            throw new RuntimeException("Transação recorrente exige frequência e próxima execução");
        }
        transacao.setFrequencia(Transacao.FrequenciaRecorrencia.valueOf(dto.getFrequencia().name()));
        transacao.setProximaExecucao(dto.getProximaExecucao());
    }

    public List<Transacao> listarDespesasRecorrentes(Long usuarioId) {
        return transacaoRepository.findByUsuarioIdAndRecorrenteIsTrueAndTipoTransacao(usuarioId, Transacao.TipoTransacao.DESPESA);
    }

    /**
     * Atualiza descrição e/ou valor de uma despesa recorrente (dono obrigatório).
     */
    public Transacao aplicarPatchDespesaRecorrente(Long usuarioId, Long transacaoId, String novaDescricao, BigDecimal novoValor) {
        Transacao t = transacaoRepository.findById(transacaoId)
            .orElseThrow(() -> new RuntimeException("Transação não encontrada"));
        if (t.getUsuario() == null || !t.getUsuario().getId().equals(usuarioId)) {
            throw new RuntimeException("Transação não pertence ao usuário");
        }
        if (!t.isRecorrente()) {
            throw new RuntimeException("Esta transação não é recorrente (despesa fixa)");
        }
        if (t.getTipoTransacao() != Transacao.TipoTransacao.DESPESA) {
            throw new RuntimeException("Apenas despesas podem ser editadas como despesa fixa");
        }
        boolean changed = false;
        if (novaDescricao != null && !novaDescricao.isBlank()) {
            t.setDescricao(novaDescricao.trim());
            changed = true;
        }
        if (novoValor != null && novoValor.compareTo(BigDecimal.ZERO) > 0) {
            t.setValor(novoValor);
            changed = true;
        }
        if (!changed) {
            throw new RuntimeException("Nenhuma alteração válida na despesa fixa");
        }
        Transacao salva = transacaoRepository.save(t);
        if (salva.getFatura() != null) {
            faturaService.sincronizarValorFaturaComTransacoes(salva.getFatura().getId());
        }
        saldoService.notificarAlteracaoSaldo(usuarioId);
        log.info("[ENTITY-UPDATE] Despesa fixa id={} usuário={}", transacaoId, usuarioId);
        return salva;
    }
}
