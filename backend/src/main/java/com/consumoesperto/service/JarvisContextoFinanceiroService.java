package com.consumoesperto.service;

import com.consumoesperto.dto.OrcamentoDTO;
import com.consumoesperto.dto.RendaConfigDTO;
import com.consumoesperto.model.ContextoFinanceiro;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.TipoConfiguracaoRenda;
import com.consumoesperto.model.DebitoInterno;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.DebitoInternoRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Monta o bloco "CONTEXTO ATUAL" injetado no system prompt da persona J.A.R.V.I.S.
 * (saldo do mês, orçamentos críticos/estourados, mês de referência).
 *
 * <p>Todas as fontes têm fallback seguro: qualquer falha resulta em valor neutro,
 * nunca num placeholder literal a chegar ao modelo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JarvisContextoFinanceiroService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final Locale PT_BR = new Locale("pt", "BR");

    private final OrcamentoService orcamentoService;
    private final SaldoService saldoService;
    private final UsuarioRepository usuarioRepository;
    private final JarvisProtocolService jarvisProtocolService;
    private final ContaBancariaRepository contaBancariaRepository;
    private final DebitoInternoRepository debitoInternoRepository;
    private final AgendamentoPagamentoService agendamentoPagamentoService;
    private final AssinaturaRecorrenteService assinaturaRecorrenteService;
    private final DespesaFixaService despesaFixaService;
    private final RendaConfigService rendaConfigService;
    private final TransacaoRepository transacaoRepository;

    /**
     * Snapshot estruturado para o J.A.R.V.I.S. Advisor — números reais antes da narração IA.
     * Renda {@code ZERO} é normalizada para {@code null} (dispara fallback de configuração).
     */
    public ContextoFinanceiro montarSnapshot(Long userId) {
        if (userId == null) {
            return ContextoFinanceiro.builder().build();
        }
        try {
            BigDecimal patrimonio = nz(saldoService.patrimonioLiquido(userId));
            BigDecimal liquidez = nz(saldoService.saldoLiquidezImediata(userId));
            BigDecimal renda = rendaConfigService.getRendaMensalEstimada(userId);
            if (renda == null || renda.compareTo(BigDecimal.ZERO) <= 0) {
                renda = null;
            } else {
                renda = renda.setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal fixas = nz(despesaFixaService.somarValorMensal(userId));
            BigDecimal assinaturas = nz(assinaturaRecorrenteService.totalAssinaturasAtivas(userId));
            BigDecimal parcelasEmprestimo = somarCompromissoMensalEmprestimosAtivos(userId);
            BigDecimal gastoMensal = resolverGastoMensalMedio(userId);
            BigDecimal mesesReserva = null;
            if (gastoMensal != null && gastoMensal.compareTo(BigDecimal.ZERO) > 0) {
                mesesReserva = patrimonio.divide(gastoMensal, 1, RoundingMode.HALF_UP);
            }
            return ContextoFinanceiro.builder()
                .patrimonioLiquido(patrimonio)
                .saldoLiquidezImediata(liquidez)
                .rendaLiquidaMensal(renda)
                .despesasFixas(fixas)
                .assinaturas(assinaturas)
                .parcelasEmprestimosAtivos(parcelasEmprestimo)
                .reservaEmergencia(liquidez)
                .gastoMensalMedio(gastoMensal)
                .mesesReservaAtual(mesesReserva)
                .build();
        } catch (Exception e) {
            log.debug("Snapshot financeiro indisponível userId={}: {}", userId, e.getMessage());
            return ContextoFinanceiro.builder().build();
        }
    }

    private BigDecimal resolverGastoMensalMedio(Long userId) {
        YearMonth ym = YearMonth.now();
        LocalDateTime inicio = ym.atDay(1).atStartOfDay();
        LocalDateTime fim = ym.atEndOfMonth().atTime(23, 59, 59);
        BigDecimal despesasMes = transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
            userId, Transacao.TipoTransacao.DESPESA, inicio, fim);
        if (despesasMes != null && despesasMes.compareTo(BigDecimal.ZERO) > 0) {
            return despesasMes.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal soma = BigDecimal.ZERO;
        int mesesComDado = 0;
        for (int i = 1; i <= 3; i++) {
            YearMonth ref = ym.minusMonths(i);
            LocalDateTime ini = ref.atDay(1).atStartOfDay();
            LocalDateTime fimRef = ref.atEndOfMonth().atTime(23, 59, 59);
            BigDecimal d = transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
                userId, Transacao.TipoTransacao.DESPESA, ini, fimRef);
            if (d != null && d.compareTo(BigDecimal.ZERO) > 0) {
                soma = soma.add(d);
                mesesComDado++;
            }
        }
        if (mesesComDado > 0) {
            return soma.divide(BigDecimal.valueOf(mesesComDado), 2, RoundingMode.HALF_UP);
        }
        return null;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Uma parcela por empréstimo ativo — compromisso mensal recorrente até quitar as PREVISTO restantes.
     */
    private BigDecimal somarCompromissoMensalEmprestimosAtivos(Long userId) {
        List<Transacao> parcelas = transacaoRepository.findParcelasEmprestimoPrevistasAtivas(userId);
        if (parcelas.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Map<String, BigDecimal> porEmprestimo = new LinkedHashMap<>();
        for (Transacao t : parcelas) {
            if (t.getEmprestimoId() == null || t.getValor() == null) {
                continue;
            }
            porEmprestimo.putIfAbsent(t.getEmprestimoId(), t.getValor());
        }
        return porEmprestimo.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    /** Bloco textual pronto para anexar ao system prompt. Nunca lança exceção. */
    public String montarBlocoContexto(Long userId) {
        Usuario usuario = userId == null ? null : usuarioRepository.findById(userId).orElse(null);
        String nome = jarvisProtocolService.extrairPrimeiroNome(usuario);
        if (nome == null || nome.isBlank()) {
            nome = "o usuário";
        }
        String tratamento = jarvisProtocolService.tratamentoConversacional(usuario);

        String saldoMes = formatarSaldoSeguro(userId);
        List<String> criticas = new ArrayList<>();
        List<String> estouradas = new ArrayList<>();
        preencherOrcamentos(userId, criticas, estouradas);

        YearMonth ym = YearMonth.now();
        String mesRef = ym.getMonth().getDisplayName(TextStyle.FULL, PT_BR) + "/" + ym.getYear();

        StringBuilder sb = new StringBuilder();
        sb.append("CONTEXTO ATUAL\n");
        sb.append("- Usuário: ").append(nome).append("\n");
        sb.append("- Tratamento: ").append(tratamento).append("\n");
        sb.append("- Saldo disponível estimado no mês: ").append(saldoMes).append("\n");
        String blocoRenda = montarBlocoRenda(userId);
        if (!blocoRenda.isBlank()) {
            sb.append(blocoRenda);
        }
        sb.append("- Categorias com orçamento crítico (>80%): ")
            .append(criticas.isEmpty() ? "(nenhuma)" : String.join(", ", criticas)).append("\n");
        sb.append("- Categorias com orçamento estourado (>100%): ")
            .append(estouradas.isEmpty() ? "(nenhuma)" : String.join(", ", estouradas)).append("\n");
        List<String> linhasContas = montarLinhasContas(userId);
        if (!linhasContas.isEmpty()) {
            sb.append("- Contas e cheque especial:\n");
            for (String linha : linhasContas) {
                sb.append("  • ").append(linha).append("\n");
            }
        }
        String debitos = montarBlocoDebitosInternos(userId);
        if (!debitos.isBlank()) {
            sb.append(debitos);
        }
        String agendamentos = montarBlocoAgendamentos(userId);
        if (!agendamentos.isBlank()) {
            sb.append(agendamentos);
        }
        String assinaturas = montarBlocoAssinaturas(userId);
        if (!assinaturas.isBlank()) {
            sb.append(assinaturas);
        }
        String despesasFixas = montarBlocoDespesasFixas(userId);
        if (!despesasFixas.isBlank()) {
            sb.append(despesasFixas);
        }
        sb.append("- Mês de referência: ").append(mesRef).append("\n");
        return sb.toString();
    }

    /** Resumo dos débitos internos (racha-contas) pendentes do usuário no grupo familiar. */
    private String montarBlocoDebitosInternos(Long userId) {
        if (userId == null) {
            return "";
        }
        try {
            List<DebitoInterno> aReceber = debitoInternoRepository.findAReceber(userId);
            List<DebitoInterno> devidos = debitoInternoRepository.findDevidos(userId);
            if (aReceber.isEmpty() && devidos.isEmpty()) {
                return "";
            }
            BigDecimal totalReceber = aReceber.stream().map(DebitoInterno::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalDevido = devidos.stream().map(DebitoInterno::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            StringBuilder sb = new StringBuilder();
            sb.append("- Racha-contas (grupo familiar): a receber ").append(BRL.format(totalReceber))
                .append(", a pagar ").append(BRL.format(totalDevido)).append("\n");
            return sb.toString();
        } catch (Exception e) {
            log.debug("Contexto J.A.R.V.I.S.: débitos internos indisponíveis userId={}: {}", userId, e.getMessage());
            return "";
        }
    }

    /**
     * Linhas de contexto por conta — destaca uso de cheque especial para o J.A.R.V.I.S.
     * Contas sem cheque especial e com saldo positivo não geram menção (cenário E).
     */
    private List<String> montarLinhasContas(Long userId) {
        List<String> linhas = new ArrayList<>();
        if (userId == null) {
            return linhas;
        }
        try {
            List<ContaBancaria> contas = contaBancariaRepository.findByUsuarioIdAndAtivaTrueOrderByPadraoDescNomeAsc(userId);
            for (ContaBancaria conta : contas) {
                if (conta == null || conta.getSaldoAtual() == null) {
                    continue;
                }
                BigDecimal saldo = conta.getSaldoAtual();
                BigDecimal limite = conta.getLimiteChequeEspecial();
                boolean temCheque = limite.compareTo(BigDecimal.ZERO) > 0;

                if (saldo.compareTo(BigDecimal.ZERO) >= 0) {
                    if (temCheque) {
                        linhas.add(String.format(
                            "Conta %s: Saldo %s (cheque especial disponível: %s — não utilizado)",
                            conta.getNome(), BRL.format(saldo), BRL.format(limite)));
                    }
                } else {
                    BigDecimal utilizado = conta.getChequeEspecialUtilizado();
                    BigDecimal disponivelRestante = conta.getSaldoDisponivel();
                    if (temCheque) {
                        linhas.add(String.format(
                            "Conta %s: Saldo %s (ATENÇÃO: utilizando %s do cheque especial de %s. "
                                + "Limite restante disponível: %s)",
                            conta.getNome(), BRL.format(saldo), BRL.format(utilizado),
                            BRL.format(limite), BRL.format(disponivelRestante)));
                    } else {
                        linhas.add(String.format(
                            "Conta %s: Saldo %s (ATENÇÃO: saldo negativo, sem cheque especial configurado)",
                            conta.getNome(), BRL.format(saldo)));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Contexto J.A.R.V.I.S.: contas indisponíveis userId={}: {}", userId, e.getMessage());
        }
        return linhas;
    }

    private String montarBlocoAssinaturas(Long userId) {
        if (userId == null) {
            return "";
        }
        try {
            var proximas = assinaturaRecorrenteService.listarVencendoEmDias(userId, 3);
            BigDecimal totalAtivas = assinaturaRecorrenteService.totalAssinaturasAtivas(userId);
            if (totalAtivas.compareTo(BigDecimal.ZERO) <= 0 && proximas.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("- Assinaturas ativas (mensal): ").append(BRL.format(totalAtivas));
            if (!proximas.isEmpty()) {
                sb.append(" — vencendo em até 3 dias: ");
                sb.append(proximas.stream()
                    .map(a -> a.getNome() + " (" + BRL.format(a.getValor()) + ")")
                    .collect(java.util.stream.Collectors.joining(", ")));
            }
            sb.append(" (lembretes WhatsApp: 5 e 3 dias antes)\n");
            return sb.toString();
        } catch (Exception e) {
            log.debug("Contexto J.A.R.V.I.S.: assinaturas indisponíveis userId={}: {}", userId, e.getMessage());
            return "";
        }
    }

    private String montarBlocoDespesasFixas(Long userId) {
        if (userId == null) {
            return "";
        }
        try {
            var todas = despesaFixaService.listar(userId);
            if (todas.isEmpty()) {
                return "";
            }
            BigDecimal totalMensal = despesaFixaService.somarValorMensal(userId);
            StringBuilder sb = new StringBuilder();
            sb.append("- Despesas fixas mensais (total): ").append(BRL.format(totalMensal));
            var proximas = despesaFixaService.listarVencendoEmDias(userId, 3);
            if (!proximas.isEmpty()) {
                sb.append(" — vencendo em 3 dias: ");
                sb.append(proximas.stream()
                    .map(d -> d.getDescricao() + " (" + BRL.format(d.getValor()) + ", dia " + d.getDiaVencimento() + ")")
                    .collect(java.util.stream.Collectors.joining(", ")));
            }
            sb.append("\n");
            return sb.toString();
        } catch (Exception e) {
            log.debug("Contexto J.A.R.V.I.S.: despesas fixas indisponíveis userId={}: {}", userId, e.getMessage());
            return "";
        }
    }

    private String montarBlocoAgendamentos(Long userId) {
        if (userId == null) {
            return "";
        }
        try {
            BigDecimal total = agendamentoPagamentoService.totalAgendadoFuturo(userId);
            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                return "";
            }
            return "- Pagamentos agendados (saídas previstas): " + BRL.format(total) + " em boletos/Pix futuros\n";
        } catch (Exception e) {
            log.debug("Contexto J.A.R.V.I.S.: agendamentos indisponíveis userId={}: {}", userId, e.getMessage());
            return "";
        }
    }

    private String montarBlocoRenda(Long userId) {
        if (userId == null) {
            return "";
        }
        try {
            RendaConfigDTO config = rendaConfigService.obterDto(userId).orElse(null);
            if (config == null) {
                return "";
            }
            BigDecimal rendaMensal = rendaConfigService.getRendaMensalEstimada(userId);
            if (rendaMensal == null) {
                rendaMensal = BigDecimal.ZERO;
            }
            TipoConfiguracaoRenda tipo = config.getTipoConfiguracaoRenda() != null
                ? config.getTipoConfiguracaoRenda()
                : TipoConfiguracaoRenda.CONTRACHEQUE;
            Integer dia = config.getDiaPagamento();
            int diasNoMes = YearMonth.now().lengthOfMonth();
            String textoRenda = switch (tipo) {
                case CONTRACHEQUE -> String.format(
                    "- Renda: %s/mês (salário líquido CLT, dia de pagamento: %d)\n",
                    BRL.format(rendaMensal), dia != null ? dia : SalarioAutomaticoService.DIA_PAGAMENTO_PADRAO);
                case RECEBIMENTO_UNICO -> String.format(
                    "- Renda: %s/mês (recebimento único mensal, dia esperado: %d)\n",
                    BRL.format(rendaMensal), dia != null ? dia : SalarioAutomaticoService.DIA_PAGAMENTO_PADRAO);
                case FLUXO_DIARIO -> {
                    int diaAtual = LocalDate.now().getDayOfMonth();
                    int diasRestantes = Math.max(0, diasNoMes - diaAtual);
                    String metaTxt = config.getMetaFaturamentoMensal() != null
                        && config.getMetaFaturamentoMensal().compareTo(BigDecimal.ZERO) > 0
                        ? BRL.format(config.getMetaFaturamentoMensal())
                        : "não definida";
                    BigDecimal rendaRestanteLinear = rendaMensal
                        .multiply(BigDecimal.valueOf(diasRestantes))
                        .divide(BigDecimal.valueOf(diasNoMes), 2, java.math.RoundingMode.HALF_UP);
                    yield String.format(
                        "- Renda: %s/mês estimados (média móvel 30 dias — perfil FLUXO_DIARIO, múltiplos PIX)\n"
                            + "- Meta de faturamento: %s\n"
                            + "- Projeção do mês: diluir linearmente a renda restante (%s nos %d dias restantes); "
                            + "NÃO assumir depósito único em data fixa\n",
                        BRL.format(rendaMensal), metaTxt, BRL.format(rendaRestanteLinear), diasRestantes);
                }
            };
            return textoRenda;
        } catch (Exception e) {
            log.debug("Contexto J.A.R.V.I.S.: renda indisponível userId={}: {}", userId, e.getMessage());
            return "";
        }
    }

    private String formatarSaldoSeguro(Long userId) {
        if (userId == null) {
            return "(indisponível)";
        }
        try {
            BigDecimal saldo = saldoService.saldoConfirmado(userId);
            return saldo != null ? BRL.format(saldo) : "(indisponível)";
        } catch (Exception e) {
            log.debug("Contexto J.A.R.V.I.S.: saldo indisponível userId={}: {}", userId, e.getMessage());
            return "(indisponível)";
        }
    }

    private void preencherOrcamentos(Long userId, List<String> criticas, List<String> estouradas) {
        if (userId == null) {
            return;
        }
        try {
            List<OrcamentoDTO> orcamentos = orcamentoService.listar(userId, null, null);
            for (OrcamentoDTO o : orcamentos) {
                if (o == null || o.getPercentualUso() == null) {
                    continue;
                }
                double pct = o.getPercentualUso().doubleValue();
                String nome = o.getCategoriaNome() != null ? o.getCategoriaNome() : "categoria";
                if (pct >= 100.0) {
                    estouradas.add(nome);
                } else if (pct >= 80.0) {
                    criticas.add(nome);
                }
            }
        } catch (Exception e) {
            log.debug("Contexto J.A.R.V.I.S.: orçamentos indisponíveis userId={}: {}", userId, e.getMessage());
        }
    }
}
