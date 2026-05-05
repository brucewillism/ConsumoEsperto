package com.consumoesperto.service;

import com.consumoesperto.dto.FaturaDTO;
import com.consumoesperto.dto.MelhorDiaCompraCalculado;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
@Slf4j
public class FaturaService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final DateTimeFormatter DDMMAAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Repositório para operações de persistência de faturas
    private final FaturaRepository faturaRepository;
    
    // Repositório para validação e busca de cartões de crédito
    private final CartaoCreditoRepository cartaoCreditoRepository;

    private final TransacaoRepository transacaoRepository;

    /**
     * Dias corridos entre o fechamento estimado e o vencimento (ex.: 10 = fechamento 10 dias antes do vencimento).
     * Override: {@code consumoesperto.fatura.dias-entre-fechamento-e-vencimento}.
     */
    @Value("${consumoesperto.fatura.dias-entre-fechamento-e-vencimento:10}")
    private int diasEntreFechamentoEVencimento;

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
     * Localiza ou cria a fatura em aberto/parcial do cartão (ciclo atual). Não altera valores;
     * o total da fatura deve refletir a soma das {@link com.consumoesperto.model.Transacao} vinculadas.
     */
    public Fatura resolverFaturaAbertaParaCartao(Long usuarioId, CartaoCredito cartao) {
        if (cartao == null) {
            throw new RuntimeException("Cartão inválido");
        }
        if (!Objects.equals(cartao.getUsuario().getId(), usuarioId)) {
            throw new RuntimeException("Cartão não pertence ao usuário");
        }
        List<Fatura> abertas = faturaRepository.findByCartaoCreditoIdAndStatusInOrderByDataVencimentoAsc(
            cartao.getId(),
            List.of(Fatura.StatusFatura.ABERTA, Fatura.StatusFatura.PARCIAL)
        );
        Fatura faturaAlvo = abertas.stream()
            .filter(f -> f.getDataVencimento() != null && f.getDataVencimento().isAfter(LocalDateTime.now().minusDays(5)))
            .min(Comparator.comparing(Fatura::getDataVencimento))
            .orElseGet(() -> criarFaturaBase(cartao));
        if (faturaAlvo.getStatusFatura() == null || faturaAlvo.getStatusFatura() == Fatura.StatusFatura.CANCELADA) {
            faturaAlvo.setStatusFatura(Fatura.StatusFatura.ABERTA);
        }
        return faturaRepository.save(faturaAlvo);
    }

    /**
     * Localiza fatura do cartão no mês de vencimento (não paga/cancelada) ou cria {@link Fatura.StatusFatura#PREVISTA}.
     */
    public Fatura obterOuCriarFaturaParaVencimentoAlvo(Long usuarioId, CartaoCredito cartao, LocalDate vencimentoDia) {
        if (cartao == null || cartao.getId() == null) {
            throw new RuntimeException("Cartão inválido");
        }
        if (!Objects.equals(cartao.getUsuario().getId(), usuarioId)) {
            throw new RuntimeException("Cartão não pertence ao usuário");
        }
        YearMonth targetYm = YearMonth.from(vencimentoDia);
        List<Fatura> list = faturaRepository.findByCartaoCreditoIdOrderByDataVencimentoAsc(cartao.getId());
        for (Fatura f : list) {
            if (f.getDataVencimento() == null) {
                continue;
            }
            YearMonth ym = YearMonth.from(f.getDataVencimento());
            if (ym.equals(targetYm)) {
                Fatura.StatusFatura st = f.getStatusFatura();
                if (st != Fatura.StatusFatura.PAGA && st != Fatura.StatusFatura.CANCELADA) {
                    return f;
                }
            }
        }
        LocalDateTime vencLdt = vencimentoDia.atTime(12, 0);
        LocalDateTime fechLdt = vencLdt.minusDays(Math.max(1, diasEntreFechamentoEVencimento));
        Fatura nova = new Fatura();
        nova.setCartaoCredito(cartao);
        nova.setUsuario(cartao.getUsuario());
        nova.setStatusFatura(Fatura.StatusFatura.PREVISTA);
        nova.setDataVencimento(vencLdt);
        nova.setDataFechamento(fechLdt);
        nova.setValorFatura(BigDecimal.ZERO);
        nova.setValorTotal(BigDecimal.ZERO);
        nova.setValorMinimo(BigDecimal.ZERO);
        nova.setValorPago(BigDecimal.ZERO);
        nova.setPaga(false);
        nova.setNumeroFatura("PREV-" + cartao.getId() + "-" + targetYm + "-" + System.nanoTime());
        return faturaRepository.save(nova);
    }

    /**
     * Compatibilidade: antes somava manualmente na fatura. Agora o total vem das transações;
     * {@code valor} é ignorado para evitar dupla contagem com lançamentos vinculados.
     */
    public Fatura registrarDespesaNoCartao(Long usuarioId, CartaoCredito cartao, BigDecimal valor) {
        if (cartao == null) {
            throw new RuntimeException("Dados inválidos para registrar despesa na fatura");
        }
        if (!Objects.equals(cartao.getUsuario().getId(), usuarioId)) {
            throw new RuntimeException("Cartão não pertence ao usuário");
        }
        Fatura f = resolverFaturaAbertaParaCartao(usuarioId, cartao);
        sincronizarValorFaturaComTransacoes(f.getId());
        return faturaRepository.findById(f.getId()).orElse(f);
    }

    /**
     * Atualiza valorFatura/valorTotal da fatura com a soma das despesas confirmadas vinculadas.
     */
    public void sincronizarValorFaturaComTransacoes(Long faturaId) {
        if (faturaId == null) {
            return;
        }
        BigDecimal sum = transacaoRepository.sumDespesaConfirmadaPorFaturaId(faturaId);
        if (sum == null) {
            sum = BigDecimal.ZERO;
        }
        Fatura f = faturaRepository.findById(faturaId).orElse(null);
        if (f == null) {
            return;
        }
        f.setValorFatura(sum);
        f.setValorTotal(sum);
        f.setValorMinimo(sum);
        faturaRepository.save(f);
    }

    private Fatura criarFaturaBase(CartaoCredito cartao) {
        LocalDateTime agora = LocalDateTime.now();
        LocalDate proximoVencimentoBase = LocalDate.now();
        int dia = Math.max(1, Math.min(28, cartao.getDiaVencimento() == null ? 10 : cartao.getDiaVencimento()));
        LocalDate venc = LocalDate.of(proximoVencimentoBase.getYear(), proximoVencimentoBase.getMonth(), dia);
        if (!venc.isAfter(LocalDate.now())) {
            venc = venc.plusMonths(1);
        }

        Fatura nova = new Fatura();
        nova.setCartaoCredito(cartao);
        nova.setUsuario(cartao.getUsuario());
        nova.setStatusFatura(Fatura.StatusFatura.ABERTA);
        nova.setDataFechamento(agora);
        nova.setDataVencimento(venc.atTime(12, 0));
        nova.setValorFatura(BigDecimal.ZERO);
        nova.setValorTotal(BigDecimal.ZERO);
        nova.setValorMinimo(BigDecimal.ZERO);
        nova.setValorPago(BigDecimal.ZERO);
        nova.setNumeroFatura(String.format("%d-%02d-%d", venc.getYear(), venc.getMonthValue(), cartao.getId()));
        return faturaRepository.save(nova);
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
        dto.setNomeCartao(fatura.getCartaoCredito().getNome());
        dto.setBanco(fatura.getCartaoCredito().getBanco());
        dto.setValorTotal(fatura.getValorFatura());
        dto.setValorMinimo(fatura.getValorFatura());
        dto.setStatus(fatura.getStatusFatura() != null ? fatura.getStatusFatura().name() : null);
        dto.setPaga(Fatura.StatusFatura.PAGA.equals(fatura.getStatusFatura()));
        
        // Inclui informações do cartão de crédito associado
        dto.setCartaoCreditoId(fatura.getCartaoCredito().getId());
        dto.setDataCriacao(fatura.getDataCriacao());
        dto.setDataAtualizacao(fatura.getDataAtualizacao());
        return dto;
    }

    /**
     * Estratégia de fechamento: vencimento do ciclo corrente menos N dias (configurável), com avanço de ciclo em meses 28–31 dias.
     * Multitenant: cartão sempre carregado com {@code findByIdAndUsuarioId}.
     */
    public MelhorDiaCompraCalculado calcularMelhorDiaCompra(Long cartaoId, Long userId) {
        CartaoCredito cartao = cartaoCreditoRepository.findByIdAndUsuarioId(cartaoId, userId)
            .orElseThrow(() -> new RuntimeException("Cartão não encontrado"));
        if (!Objects.equals(cartao.getUsuario().getId(), userId)) {
            throw new RuntimeException("Cartão não pertence ao usuário");
        }
        LocalDate hoje = LocalDate.now();
        int diaPreferido = clampDiaVencimento(cartao.getDiaVencimento());
        int delta = Math.max(1, diasEntreFechamentoEVencimento);

        List<Fatura> abertas = faturaRepository.findByCartaoCreditoIdAndStatusInOrderByDataVencimentoAsc(
            cartaoId,
            List.of(Fatura.StatusFatura.ABERTA, Fatura.StatusFatura.PARCIAL)
        );
        LocalDate venc = abertas.stream()
            .map(Fatura::getDataVencimento)
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .map(LocalDateTime::toLocalDate)
            .orElse(null);

        if (venc == null || venc.isBefore(hoje)) {
            venc = proximoVencimentoEmCalendario(hoje, diaPreferido);
        }

        LocalDate fech = venc.minusDays(delta);
        int guard = 0;
        while (fech.isBefore(hoje) && guard++ < 36) {
            venc = avancarUmMesRespeitandoDia(venc, diaPreferido);
            fech = venc.minusDays(delta);
        }

        LocalDate vencPagamentoComprasNoFechamento = avancarUmMesRespeitandoDia(venc, diaPreferido);
        long diasAteFech = ChronoUnit.DAYS.between(hoje, fech);
        long diasAteVenc = ChronoUnit.DAYS.between(hoje, venc);
        boolean hojeFechamento = hoje.equals(fech);

        log.info(
            "[BILLING-STRATEGY-LOG] cartaoId={} userId={} diaVencCartao={} deltaDias={} vencCiclo={} fechEstimada={} diasAteFech={} hojeEhFechamento={}",
            cartaoId, userId, diaPreferido, delta, venc, fech, diasAteFech, hojeFechamento
        );

        return new MelhorDiaCompraCalculado(
            venc,
            fech,
            vencPagamentoComprasNoFechamento,
            diasAteFech,
            diasAteVenc,
            hojeFechamento,
            delta
        );
    }

    /**
     * Resumo Markdown (WhatsApp) do cartão: soma das faturas ABERTA/PARCIAL e limite estimado a partir do limite total.
     * O gasto reflete o que o app acumulou na fatura aberta deste cartão (inclui lançamentos via WhatsApp vinculados à fatura).
     */
    public String montarResumoCartaoWhatsapp(Long usuarioId, CartaoCredito cartao) {
        log.info("[BILLING-LOG] Resumo cartão userId={} cartaoId={} nome={}", usuarioId, cartao.getId(), cartao.getNome());
        if (cartao.getUsuario() == null || !Objects.equals(cartao.getUsuario().getId(), usuarioId)) {
            throw new RuntimeException("Cartão não pertence ao usuário");
        }
        List<Fatura> abertas = faturaRepository.findByCartaoCreditoIdAndStatusInOrderByDataVencimentoAsc(
            cartao.getId(),
            List.of(Fatura.StatusFatura.ABERTA, Fatura.StatusFatura.PARCIAL)
        );
        BigDecimal gastoTrans = transacaoRepository.sumDespesaConfirmadaFaturaAbertaPorCartaoId(cartao.getId());
        if (gastoTrans == null) {
            gastoTrans = BigDecimal.ZERO;
        }
        BigDecimal gastoFatura = BigDecimal.ZERO;
        if (gastoTrans.compareTo(BigDecimal.ZERO) > 0) {
            gastoFatura = gastoTrans;
        } else {
            for (Fatura f : abertas) {
                if (f.getValorFatura() != null) {
                    gastoFatura = gastoFatura.add(f.getValorFatura());
                }
            }
        }
        BigDecimal limiteTotal = cartao.getLimiteCredito() != null ? cartao.getLimiteCredito() : BigDecimal.ZERO;
        BigDecimal disponivel = limiteTotal.subtract(gastoFatura);
        if (disponivel.compareTo(BigDecimal.ZERO) < 0) {
            disponivel = BigDecimal.ZERO;
        }

        MelhorDiaCompraCalculado estrategia = calcularMelhorDiaCompra(cartao.getId(), usuarioId);
        String consultoria = montarBlocoConsultoriaEstrategica(estrategia, cartao.getNome());

        return "💳 *Resumo* *" + cartao.getNome() + "* (" + cartao.getBanco() + ")\n"
            + "- *Gasto atual (fatura aberta):* " + BRL.format(gastoFatura) + "\n"
            + "- *Limite total:* " + BRL.format(limiteTotal) + "\n"
            + "- *Limite disponível (estimado):* " + BRL.format(disponivel) + "\n"
            + "- *Próximo vencimento (ciclo):* " + estrategia.proximoVencimentoCiclo().format(DDMMAAAA) + "\n"
            + "\n*Consultoria de prazo*\n"
            + consultoria + "\n"
            + "\n_O total da fatura aberta é o acumulado deste cartão no ConsumoEsperto; fechamento usa *"
            + estrategia.diasEntreFechamentoEVencimentoUsados()
            + " dias* antes do vencimento (regra estimada — confirme no banco)._";
    }

    private String montarBlocoConsultoriaEstrategica(MelhorDiaCompraCalculado s, String apelidoCartao) {
        LocalDate hoje = LocalDate.now();
        if (s.hojeEhDiaDeFechamento()) {
            long prazoPagamento = ChronoUnit.DAYS.between(hoje, s.vencimentoPagamentoComprasNoDiaFechamento());
            String prazoHuman = prazoPagamento > 0
                ? " — é comum ter *cerca de " + prazoPagamento + " dias* até esse vencimento no calendário (~40 dias de alavancagem vs. comprar no fim do ciclo)."
                : "";
            return "🚀 *HOJE É O MELHOR DIA!* Compras feitas hoje no cartão *" + apelidoCartao
                + "* entram na *próxima fatura*; o próximo vencimento dessa leva tende a ser *"
                + s.vencimentoPagamentoComprasNoDiaFechamento().format(DDMMAAAA) + "*" + prazoHuman;
        }
        if (s.fechamentoEmUmATresDias()) {
            return "⚠️ Sua fatura *fecha em " + s.diasCorridosAteFechamento() + " dias* (*"
                + s.dataFechamentoEstimada().format(DDMMAAAA) + "*). Se puder, *espere até o dia "
                + s.dataFechamentoEstimada().format(DDMMAAAA) + "* para compras grandes e ganhe mais prazo até o vencimento (*"
                + s.proximoVencimentoCiclo().format(DDMMAAAA) + "*).";
        }
        return "📅 Sua fatura atual *fecha dia* *" + s.dataFechamentoEstimada().format(DDMMAAAA)
            + "* (" + s.diasEntreFechamentoEVencimentoUsados() + " dias antes do vencimento de *"
            + s.proximoVencimentoCiclo().format(DDMMAAAA) + "*). Planeje compras maiores perto dessa data para alavancar o caixa sem misturar ciclos.";
    }

    private static int clampDiaVencimento(Integer dia) {
        if (dia == null) {
            return 10;
        }
        return Math.max(1, Math.min(31, dia));
    }

    /**
     * Próxima data de vencimento em calendário (dia do mês respeitando 28–31) a partir de {@code hoje}, inclusive.
     */
    private static LocalDate proximoVencimentoEmCalendario(LocalDate hoje, int diaPreferido) {
        YearMonth ym = YearMonth.from(hoje);
        int d = Math.min(diaPreferido, ym.lengthOfMonth());
        LocalDate candidato = ym.atDay(d);
        if (!candidato.isBefore(hoje)) {
            return candidato;
        }
        ym = ym.plusMonths(1);
        d = Math.min(diaPreferido, ym.lengthOfMonth());
        return ym.atDay(d);
    }

    private static LocalDate avancarUmMesRespeitandoDia(LocalDate vencimentoReferencia, int diaPreferido) {
        YearMonth prox = YearMonth.from(vencimentoReferencia).plusMonths(1);
        int d = Math.min(diaPreferido, prox.lengthOfMonth());
        return prox.atDay(d);
    }
}
