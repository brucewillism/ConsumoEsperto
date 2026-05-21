package com.consumoesperto.service;

import com.consumoesperto.dto.*;
import com.consumoesperto.model.*;
import com.consumoesperto.repository.ConfiguracaoFiscalRepository;
import com.consumoesperto.repository.RendaConfigRepository;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orquestra configuração fiscal, cálculo CLT e provisionamento de receitas previstas no fluxo de caixa.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanejamentoFiscalService {

    private static final int MES_SEGUNDA_PARCELA_13 = 12;
    private static final int DIA_PAGAMENTO_PADRAO = 5;
    private static final RoundingMode ARREDONDAMENTO = RoundingMode.HALF_UP;

    private final ConfiguracaoFiscalRepository configuracaoFiscalRepository;
    private final CalculadorFiscalService calculadorFiscalService;
    private final TransacaoRepository transacaoRepository;
    private final RendaConfigRepository rendaConfigRepository;

    @Transactional(readOnly = true)
    public ConfiguracaoFiscalDTO obterDto(Long usuarioId) {
        return configuracaoFiscalRepository.findByUsuarioId(usuarioId)
            .map(this::toDto)
            .orElse(ConfiguracaoFiscalDTO.vazio());
    }

    @Transactional
    public ConfiguracaoFiscalDTO salvar(Long usuarioId, ConfiguracaoFiscalRequest request) {
        ConfiguracaoFiscal cfg = configuracaoFiscalRepository.findByUsuarioId(usuarioId)
            .orElseGet(() -> {
                ConfiguracaoFiscal novo = new ConfiguracaoFiscal();
                Usuario u = new Usuario();
                u.setId(usuarioId);
                novo.setUsuario(u);
                return novo;
            });

        if (request != null) {
            cfg.setMesRestituicaoIr(clampMes(request.getMesRestituicaoIr()));
            cfg.setValorRestituicao(request.getValorRestituicao());
            cfg.setTipoRecebimento13(request.getTipoRecebimento13());
            cfg.setMesParcelaUnica(clampMes(request.getMesParcelaUnica()));
            cfg.setMesPrimeiraParcela(clampMes(request.getMesPrimeiraParcela()));
            if (request.getProvisionamentoAtivo() != null) {
                cfg.setProvisionamentoAtivo(request.getProvisionamentoAtivo());
            }
        }

        configuracaoFiscalRepository.save(cfg);
        sincronizarProvisoes(usuarioId);
        return toDto(cfg);
    }

    /** Simula parcelas sem persistir transações. */
    @Transactional(readOnly = true)
    public PlanejamentoFiscalResumoDTO simular(Long usuarioId) {
        ConfiguracaoFiscalDTO cfgDto = obterDto(usuarioId);
        Optional<BaseContrachequeFiscalDTO> baseOpt = calculadorFiscalService.obterBaseContracheque(usuarioId);
        int diaPag = resolverDiaPagamento(usuarioId);
        List<ParcelaReceitaFiscalDTO> parcelas =
            montarParcelas(cfgDto, baseOpt.orElse(null), LocalDate.now().getYear(), diaPag);
        return montarResumo(cfgDto, baseOpt.orElse(null), parcelas, 0, avisoBase(baseOpt));
    }

    /** Recria transações PREVISTO fiscais do ano corrente conforme configuração atual. */
    @Transactional
    public PlanejamentoFiscalResumoDTO sincronizarProvisoes(Long usuarioId) {
        ConfiguracaoFiscal cfg = configuracaoFiscalRepository.findByUsuarioId(usuarioId).orElse(null);
        ConfiguracaoFiscalDTO cfgDto = cfg != null ? toDto(cfg) : ConfiguracaoFiscalDTO.vazio();
        Optional<BaseContrachequeFiscalDTO> baseOpt = calculadorFiscalService.obterBaseContracheque(usuarioId);

        int ano = LocalDate.now().getYear();
        transacaoRepository.softDeleteProvisionamentosFiscaisPrevistos(usuarioId);

        if (cfg == null || !cfg.isProvisionamentoAtivo()) {
            return montarResumo(cfgDto, baseOpt.orElse(null), List.of(), 0, avisoBase(baseOpt));
        }

        List<ParcelaReceitaFiscalDTO> parcelas =
            montarParcelas(cfgDto, baseOpt.orElse(null), ano, resolverDiaPagamento(usuarioId));
        int criadas = 0;
        int diaPagamento = resolverDiaPagamento(usuarioId);

        for (ParcelaReceitaFiscalDTO parcela : parcelas) {
            if (parcela.getValor() == null || parcela.getValor().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Transacao tx = new Transacao();
            Usuario u = new Usuario();
            u.setId(usuarioId);
            tx.setUsuario(u);
            tx.setDescricao(parcela.getRotulo());
            tx.setValor(parcela.getValor());
            tx.setTipoTransacao(Transacao.TipoTransacao.RECEITA);
            tx.setStatusConferencia(Transacao.StatusConferencia.PREVISTO);
            tx.setOrigemFiscal(parcela.getOrigem());
            tx.setRecorrente(false);
            tx.setExcluido(false);
            int dia = Math.min(parcela.getDia(), YearMonth.of(ano, parcela.getMes()).lengthOfMonth());
            tx.setDataTransacao(LocalDateTime.of(ano, parcela.getMes(), dia, 12, 0));
            transacaoRepository.save(tx);
            criadas++;
        }

        log.info("[PlanejamentoFiscal] userId={} provisões sincronizadas: {}", usuarioId, criadas);
        return montarResumo(cfgDto, baseOpt.orElse(null), parcelas, criadas, avisoBase(baseOpt));
    }

    /**
     * Receitas fiscais previstas (PREVISTO) no mês — usado pelo Sentinela e projeção de caixa.
     */
    @Transactional(readOnly = true)
    public BigDecimal somarReceitasPrevistasNoMes(Long usuarioId, YearMonth ym) {
        if (usuarioId == null || ym == null) {
            return BigDecimal.ZERO;
        }
        LocalDateTime ini = ym.atDay(1).atStartOfDay();
        LocalDateTime fim = ym.atEndOfMonth().atTime(23, 59, 59);
        BigDecimal soma = transacaoRepository.sumReceitaFiscalPrevistaPeriodo(usuarioId, ini, fim);
        return soma != null ? soma.setScale(2, ARREDONDAMENTO) : BigDecimal.ZERO;
    }

    /**
     * Parcelas fiscais futuras no mês corrente (para gráfico de projeção — dia &gt; hoje).
     */
    @Transactional(readOnly = true)
    public List<ParcelaReceitaFiscalDTO> listarReceitasProjetadasMesAtual(Long usuarioId) {
        if (usuarioId == null) {
            return List.of();
        }
        int ano = LocalDate.now().getYear();
        ConfiguracaoFiscalDTO cfg = obterDto(usuarioId);
        Optional<BaseContrachequeFiscalDTO> base = calculadorFiscalService.obterBaseContracheque(usuarioId);
        int diaHoje = LocalDate.now().getDayOfMonth();
        int mesAtual = LocalDate.now().getMonthValue();

        return montarParcelas(cfg, base.orElse(null), ano, resolverDiaPagamento(usuarioId)).stream()
            .filter(p -> p.getMes() == mesAtual && p.getDia() > diaHoje)
            .filter(p -> p.getValor() != null && p.getValor().compareTo(BigDecimal.ZERO) > 0)
            .collect(java.util.stream.Collectors.toList());
    }

    private List<ParcelaReceitaFiscalDTO> montarParcelas(
        ConfiguracaoFiscalDTO cfg,
        BaseContrachequeFiscalDTO base,
        int ano,
        int diaPag
    ) {
        List<ParcelaReceitaFiscalDTO> out = new ArrayList<>();

        if (cfg.getMesRestituicaoIr() != null
            && cfg.getValorRestituicao() != null
            && cfg.getValorRestituicao().compareTo(BigDecimal.ZERO) > 0) {
            out.add(ParcelaReceitaFiscalDTO.builder()
                .origem(OrigemProvisionamentoFiscal.RESTITUICAO_IR)
                .rotulo("Restituição IR " + ano + " (previsto)")
                .mes(cfg.getMesRestituicaoIr())
                .dia(diaPag)
                .valor(calculadorFiscalService.calcularRestituicaoIr(cfg.getValorRestituicao()))
                .observacao("Valor estimado configurado pelo utilizador")
                .build());
        }

        if (base == null || cfg.getTipoRecebimento13() == null) {
            return out;
        }

        ResultadoDecimoTerceiroDTO decimo =
            calculadorFiscalService.calcularDecimoTerceiro(base, cfg.getTipoRecebimento13());

        if (cfg.getTipoRecebimento13() == TipoRecebimento13.PARCELA_UNICA) {
            if (cfg.getMesParcelaUnica() != null
                && decimo.getParcelaUnicaLiquida() != null
                && decimo.getParcelaUnicaLiquida().compareTo(BigDecimal.ZERO) > 0) {
                out.add(ParcelaReceitaFiscalDTO.builder()
                    .origem(OrigemProvisionamentoFiscal.DECIMO_TERCEIRO_UNICO)
                    .rotulo("13º salário — parcela única " + ano + " (previsto)")
                    .mes(cfg.getMesParcelaUnica())
                    .dia(diaPag)
                    .valor(decimo.getParcelaUnicaLiquida())
                    .observacao("Líquido estimado com base no contracheque")
                    .build());
            }
            return out;
        }

        if (cfg.getMesPrimeiraParcela() != null
            && decimo.getPrimeiraParcelaBruta() != null
            && decimo.getPrimeiraParcelaBruta().compareTo(BigDecimal.ZERO) > 0) {
            out.add(ParcelaReceitaFiscalDTO.builder()
                .origem(OrigemProvisionamentoFiscal.DECIMO_TERCEIRA_PRIMEIRA)
                .rotulo("13º salário — 1ª parcela (50% bruto) " + ano + " (previsto)")
                .mes(cfg.getMesPrimeiraParcela())
                .dia(diaPag)
                .valor(decimo.getPrimeiraParcelaBruta())
                .observacao("Adiantamento sem descontos de INSS/IRRF")
                .build());
        }

        if (decimo.getSegundaParcelaLiquida() != null
            && decimo.getSegundaParcelaLiquida().compareTo(BigDecimal.ZERO) > 0) {
            out.add(ParcelaReceitaFiscalDTO.builder()
                .origem(OrigemProvisionamentoFiscal.DECIMO_TERCEIRA_SEGUNDA)
                .rotulo("13º salário — 2ª parcela " + ano + " (previsto)")
                .mes(MES_SEGUNDA_PARCELA_13)
                .dia(diaPag)
                .valor(decimo.getSegundaParcelaLiquida())
                .observacao("Saldo líquido após retenções tributárias acumuladas")
                .build());
        }

        return out;
    }

    private PlanejamentoFiscalResumoDTO montarResumo(
        ConfiguracaoFiscalDTO cfg,
        BaseContrachequeFiscalDTO base,
        List<ParcelaReceitaFiscalDTO> parcelas,
        int sincronizadas,
        String aviso
    ) {
        BigDecimal total = parcelas.stream()
            .map(ParcelaReceitaFiscalDTO::getValor)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, ARREDONDAMENTO);

        return PlanejamentoFiscalResumoDTO.builder()
            .configuracao(cfg)
            .baseContracheque(base)
            .parcelas(parcelas)
            .totalProvisionado(total)
            .transacoesSincronizadas(sincronizadas)
            .aviso(aviso)
            .build();
    }

    private String avisoBase(Optional<BaseContrachequeFiscalDTO> baseOpt) {
        if (baseOpt.isEmpty()) {
            return "Cadastre ou confirme um contracheque (ou configure a renda) para estimar o 13º salário.";
        }
        if (baseOpt.get().isEstimado()) {
            return "Estimativa baseada na configuração de renda — importe um contracheque para maior precisão.";
        }
        return null;
    }

    private int resolverDiaPagamento(Long usuarioId) {
        return rendaConfigRepository.findByUsuarioId(usuarioId)
            .map(RendaConfig::getDiaPagamento)
            .filter(d -> d != null && d >= 1 && d <= 28)
            .orElse(DIA_PAGAMENTO_PADRAO);
    }

    private Integer clampMes(Integer mes) {
        if (mes == null) {
            return null;
        }
        if (mes < 1 || mes > 12) {
            return null;
        }
        return mes;
    }

    private ConfiguracaoFiscalDTO toDto(ConfiguracaoFiscal cfg) {
        return ConfiguracaoFiscalDTO.builder()
            .mesRestituicaoIr(cfg.getMesRestituicaoIr())
            .valorRestituicao(cfg.getValorRestituicao())
            .tipoRecebimento13(cfg.getTipoRecebimento13())
            .mesParcelaUnica(cfg.getMesParcelaUnica())
            .mesPrimeiraParcela(cfg.getMesPrimeiraParcela())
            .provisionamentoAtivo(cfg.isProvisionamentoAtivo())
            .build();
    }
}
