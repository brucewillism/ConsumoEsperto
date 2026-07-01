package com.consumoesperto.service;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.ContextoFinanceiro;
import com.consumoesperto.model.PropostaEmprestimoConsignado;
import com.consumoesperto.model.ResultadoRegistroEmprestimo;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Registro atômico de empréstimo consignado: crédito na conta + parcelas PREVISTO + impacto no comprometimento.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmprestimoService {

    private static final int SCALE = 2;
    private static final BigDecimal LIMITE_VALOR_ATIPICO = new BigDecimal("100000");

    private final TransacaoService transacaoService;
    private final TransacaoRepository transacaoRepository;
    private final ContaBancariaService contaBancariaService;
    private final ContaBancariaRepository contaBancariaRepository;
    private final SaldoMovimentacaoService saldoMovimentacaoService;
    private final JarvisContextoFinanceiroService jarvisContextoFinanceiroService;
    private final FinancialAdviceCalculator financialAdviceCalculator;
    private final MarketDataService marketDataService;

    /** Pré-visualização sem persistir — usada para confirmação no WhatsApp. */
    @Transactional(readOnly = true)
    public ResultadoRegistroEmprestimo calcularRegistro(Long usuarioId, PropostaEmprestimoConsignado proposta) {
        return montarResultado(usuarioId, proposta, null, false);
    }

    @Transactional
    public ResultadoRegistroEmprestimo registrar(Long usuarioId, PropostaEmprestimoConsignado proposta) {
        ResultadoRegistroEmprestimo preview = montarResultado(usuarioId, proposta, null, false);
        if (preview.isPrecisaConfirmacao()) {
            return preview;
        }
        return persistir(usuarioId, proposta, preview);
    }

    @Transactional
    public ResultadoRegistroEmprestimo cancelarEmprestimo(Long usuarioId, String emprestimoId) {
        if (emprestimoId == null || emprestimoId.isBlank()) {
            throw new IllegalArgumentException("Identificador do empréstimo é obrigatório.");
        }
        List<Transacao> transacoes = transacaoRepository.findByUsuarioIdAndEmprestimoIdOrderByDataTransacaoAsc(
            usuarioId, emprestimoId.trim());
        if (transacoes.isEmpty()) {
            throw new IllegalArgumentException("Não encontrei empréstimo com esse identificador.");
        }
        int canceladas = 0;
        BigDecimal creditoEstornado = BigDecimal.ZERO;
        for (Transacao t : transacoes) {
            if (t.isExcluido()) {
                continue;
            }
            if (t.getTipoTransacao() == Transacao.TipoTransacao.RECEITA
                && t.getStatusConferencia() == Transacao.StatusConferencia.CONFIRMADA) {
                saldoMovimentacaoService.aplicarExclusao(t);
                creditoEstornado = nz(t.getValor());
            } else if (t.getTipoTransacao() == Transacao.TipoTransacao.DESPESA
                && t.getStatusConferencia() == Transacao.StatusConferencia.CONFIRMADA) {
                saldoMovimentacaoService.aplicarExclusao(t);
            }
            t.setExcluido(true);
            transacaoRepository.save(t);
            canceladas++;
        }
        log.info("[EMPRESTIMO] Cancelado emprestimoId={} userId={} transacoes={} creditoEstornado={}",
            emprestimoId, usuarioId, canceladas, creditoEstornado);
        return ResultadoRegistroEmprestimo.builder()
            .emprestimoId(emprestimoId)
            .registrado(false)
            .transacoesCriadas(canceladas)
            .valorTomado(creditoEstornado)
            .build();
    }

    @Transactional(readOnly = true)
    public String resolverUltimoEmprestimoId(Long usuarioId) {
        List<String> ids = transacaoRepository.findEmprestimoIdsByUsuario(usuarioId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private ResultadoRegistroEmprestimo persistir(
        Long usuarioId,
        PropostaEmprestimoConsignado proposta,
        ResultadoRegistroEmprestimo preview
    ) {
        String emprestimoId = UUID.randomUUID().toString();
        CalculoEmprestimo calc = calcularParcelaETaxa(proposta);
        ContaBancaria conta = resolverContaObrigatoria(usuarioId, proposta);

        LocalDate hoje = LocalDate.now();
        LocalDate primeira = hoje.plusMonths(1);
        LocalDate ultima = hoje.plusMonths(proposta.getQuantidadeParcelas());

        TransacaoDTO credito = new TransacaoDTO();
        credito.setDescricao("Empréstimo consignado recebido");
        credito.setValor(proposta.getValorTomado());
        credito.setTipoTransacao(TransacaoDTO.TipoTransacao.RECEITA);
        credito.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
        credito.setDataTransacao(hoje.atTime(12, 0));
        credito.setContaBancariaId(conta.getId());
        credito.setEmprestimoId(emprestimoId);
        transacaoService.criarTransacao(credito, usuarioId, false);

        int nParcelas = proposta.getQuantidadeParcelas();
        List<Transacao> parcelas = new ArrayList<>(nParcelas);
        Usuario usuarioRef = new Usuario();
        usuarioRef.setId(usuarioId);
        for (int i = 1; i <= nParcelas; i++) {
            Transacao parc = new Transacao();
            parc.setDescricao("Parcela consignado (" + i + "/" + nParcelas + ")");
            parc.setValor(calc.parcela());
            parc.setTipoTransacao(Transacao.TipoTransacao.DESPESA);
            parc.setStatusConferencia(Transacao.StatusConferencia.PREVISTO);
            parc.setDataTransacao(hoje.plusMonths(i).atTime(12, 0));
            parc.setContaBancaria(conta);
            parc.setUsuario(usuarioRef);
            parc.setEmprestimoId(emprestimoId);
            parc.setParcelaAtual(i);
            parc.setTotalParcelas(nParcelas);
            parc.setExcluido(false);
            parc.setRecorrente(false);
            parcelas.add(parc);
        }
        transacaoRepository.saveAll(parcelas);
        int criadas = 1 + parcelas.size();

        ContaBancaria contaAtualizada = contaBancariaRepository.findById(conta.getId()).orElse(conta);
        ContextoFinanceiro ctxDepois = jarvisContextoFinanceiroService.montarSnapshot(usuarioId);

        ResultadoRegistroEmprestimo out = preview.toBuilder()
            .emprestimoId(emprestimoId)
            .registrado(true)
            .transacoesCriadas(criadas)
            .contaId(conta.getId())
            .contaNome(conta.getNome())
            .novoSaldoConta(nz(contaAtualizada.getSaldoAtual()))
            .rendaLivreDepois(ctxDepois.rendaLivre())
            .pctRendaComprometidaDepois(ctxDepois.percentualRendaComprometida())
            .precisaConfirmacao(false)
            .build();
        log.info("[EMPRESTIMO] Registrado emprestimoId={} userId={} valor={} parcelas={}x{}",
            emprestimoId, usuarioId, proposta.getValorTomado(), proposta.getQuantidadeParcelas(), calc.parcela());
        return out;
    }

    private ResultadoRegistroEmprestimo montarResultado(
        Long usuarioId,
        PropostaEmprestimoConsignado proposta,
        String emprestimoIdOverride,
        boolean registrado
    ) {
        if (proposta == null || !proposta.temMinimoParaCalcular()) {
            throw new IllegalArgumentException(
                "Informe o valor tomado e a quantidade de parcelas (ex.: consignado de 10 mil em 24x).");
        }

        ContextoFinanceiro ctxAntes = jarvisContextoFinanceiroService.montarSnapshot(usuarioId);
        CalculoEmprestimo calc = calcularParcelaETaxa(proposta);
        ResolucaoConta contaRes = resolverConta(usuarioId, proposta);

        LocalDate hoje = LocalDate.now();
        LocalDate primeira = hoje.plusMonths(1);
        LocalDate ultima = hoje.plusMonths(proposta.getQuantidadeParcelas());

        BigDecimal rendaLivreDepois = simularRendaLivreDepois(ctxAntes, calc.parcela());
        BigDecimal pctDepois = simularPctDepois(ctxAntes, calc.parcela());

        boolean parcelaEstimada = calc.parcelaEstimada();
        boolean valorAtipico = proposta.getValorTomado().compareTo(LIMITE_VALOR_ATIPICO) > 0;
        boolean precisaConfirmacao = contaRes.ambiguo() || parcelaEstimada || valorAtipico;

        String motivo = null;
        String msgConfirm = null;
        if (precisaConfirmacao) {
            List<String> motivos = new ArrayList<>();
            if (contaRes.ambiguo()) {
                motivos.add("conta ambígua");
            }
            if (parcelaEstimada) {
                motivos.add("parcela estimada pela taxa de mercado");
            }
            if (valorAtipico) {
                motivos.add("valor elevado");
            }
            motivo = String.join(", ", motivos);
            msgConfirm = montarMensagemConfirmacao(proposta, calc, contaRes, pctDepois);
        }

        return ResultadoRegistroEmprestimo.builder()
            .emprestimoId(emprestimoIdOverride)
            .valorTomado(proposta.getValorTomado())
            .valorParcela(calc.parcela())
            .quantidadeParcelas(proposta.getQuantidadeParcelas())
            .parcelaEstimada(parcelaEstimada)
            .taxaJurosMensalPct(calc.taxaMensalPct())
            .taxaJurosAnualPct(calc.taxaAnualPct())
            .totalAPagar(calc.totalAPagar())
            .jurosTotais(calc.jurosTotais())
            .contaId(contaRes.conta() != null ? contaRes.conta().getId() : null)
            .contaNome(contaRes.conta() != null ? contaRes.conta().getNome() : null)
            .rendaLivreAntes(ctxAntes.rendaLivre())
            .rendaLivreDepois(rendaLivreDepois)
            .pctRendaComprometidaAntes(ctxAntes.percentualRendaComprometida())
            .pctRendaComprometidaDepois(pctDepois)
            .dataPrimeiraParcela(primeira)
            .dataUltimaParcela(ultima)
            .precisaConfirmacao(precisaConfirmacao)
            .motivoConfirmacao(motivo)
            .contasAmbiguas(contaRes.nomesAmbiguos())
            .mensagemConfirmacao(msgConfirm)
            .registrado(registrado)
            .build();
    }

    private String montarMensagemConfirmacao(
        PropostaEmprestimoConsignado proposta,
        CalculoEmprestimo calc,
        ResolucaoConta contaRes,
        BigDecimal pctDepois
    ) {
        NumberFormat brl = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        String contaTxt = contaRes.conta() != null
            ? contaRes.conta().getNome()
            : (contaRes.nomesAmbiguos() != null && !contaRes.nomesAmbiguos().isEmpty()
                ? String.join(" ou ", contaRes.nomesAmbiguos())
                : "conta padrão");
        StringBuilder sb = new StringBuilder();
        sb.append("Confirmando, chefe: *").append(brl.format(proposta.getValorTomado()))
            .append("* caindo no *").append(contaTxt).append("*, em *")
            .append(proposta.getQuantidadeParcelas()).append("x de ")
            .append(brl.format(calc.parcela())).append("*");
        if (calc.parcelaEstimada()) {
            sb.append(" _(parcela estimada pela taxa média de mercado)_");
        }
        if (pctDepois != null) {
            sb.append(".\nIsso compromete *").append(pctDepois).append("%* da sua renda pelos próximos ")
                .append(proposta.getQuantidadeParcelas()).append(" meses.");
        }
        sb.append(" Posso registrar? Responde *sim*.");
        return sb.toString();
    }

    private BigDecimal simularRendaLivreDepois(ContextoFinanceiro ctx, BigDecimal novaParcela) {
        if (ctx.getRendaLiquidaMensal() == null || ctx.getRendaLiquidaMensal().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal compromisso = ctx.comprometimentoMensal().add(novaParcela);
        return ctx.getRendaLiquidaMensal().subtract(compromisso).max(BigDecimal.ZERO)
            .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal simularPctDepois(ContextoFinanceiro ctx, BigDecimal novaParcela) {
        if (ctx.getRendaLiquidaMensal() == null || ctx.getRendaLiquidaMensal().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal compromisso = ctx.comprometimentoMensal().add(novaParcela);
        return compromisso.divide(ctx.getRendaLiquidaMensal(), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(1, RoundingMode.HALF_UP);
    }

    private CalculoEmprestimo calcularParcelaETaxa(PropostaEmprestimoConsignado proposta) {
        BigDecimal valorTomado = proposta.getValorTomado().setScale(SCALE, RoundingMode.HALF_UP);
        int n = proposta.getQuantidadeParcelas();
        double taxaMensal;
        BigDecimal parcela;
        boolean estimada;

        if (proposta.getValorParcela() != null && proposta.getValorParcela().compareTo(BigDecimal.ZERO) > 0) {
            parcela = proposta.getValorParcela().setScale(SCALE, RoundingMode.HALF_UP);
            taxaMensal = financialAdviceCalculator.resolverTaxaMensal(
                valorTomado.doubleValue(), parcela.doubleValue(), n);
            estimada = false;
        } else {
            BigDecimal taxaAa = marketDataService.getTaxaMediaConsignadoResiliente();
            taxaMensal = financialAdviceCalculator.taxaAnualParaMensal(taxaAa);
            parcela = financialAdviceCalculator.calcularParcelaPrice(valorTomado, taxaMensal, n);
            estimada = true;
        }

        double taxaAnual = Math.pow(1.0 + taxaMensal, 12.0) - 1.0;
        BigDecimal totalAPagar = parcela.multiply(BigDecimal.valueOf(n)).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal juros = totalAPagar.subtract(valorTomado).max(BigDecimal.ZERO).setScale(SCALE, RoundingMode.HALF_UP);

        return new CalculoEmprestimo(
            parcela,
            BigDecimal.valueOf(taxaMensal * 100.0).setScale(2, RoundingMode.HALF_UP),
            BigDecimal.valueOf(taxaAnual * 100.0).setScale(2, RoundingMode.HALF_UP),
            totalAPagar,
            juros,
            estimada
        );
    }

    private ContaBancaria resolverContaObrigatoria(Long usuarioId, PropostaEmprestimoConsignado proposta) {
        ResolucaoConta res = resolverConta(usuarioId, proposta);
        if (res.ambiguo()) {
            throw new IllegalArgumentException(
                "Há mais de uma conta parecida. Informe qual conta recebeu o empréstimo.");
        }
        if (res.conta() == null) {
            throw new IllegalArgumentException(
                "Cadastre uma conta bancária ou informe em qual conta o empréstimo caiu.");
        }
        return res.conta();
    }

    private ResolucaoConta resolverConta(Long usuarioId, PropostaEmprestimoConsignado proposta) {
        if (proposta.getContaBancariaId() != null) {
            ContaBancaria c = contaBancariaService.buscarEntidade(proposta.getContaBancariaId(), usuarioId);
            return new ResolucaoConta(c, false, List.of());
        }
        if (proposta.getNomeConta() != null && !proposta.getNomeConta().isBlank()) {
            List<ContaBancaria> candidatos = contaBancariaService.encontrarAtivasPorApelidoNormalizado(
                usuarioId, proposta.getNomeConta());
            if (candidatos.isEmpty()) {
                throw new IllegalArgumentException(
                    "Não encontrei conta ativa parecida com \"" + proposta.getNomeConta() + "\".");
            }
            if (candidatos.size() > 1) {
                return new ResolucaoConta(null, true,
                    candidatos.stream().map(ContaBancaria::getNome).collect(Collectors.toList()));
            }
            return new ResolucaoConta(candidatos.get(0), false, List.of());
        }
        ContaBancaria padrao = contaBancariaService.resolverContaParaTransacao(usuarioId, null);
        if (padrao == null) {
            List<ContaBancaria> ativas = contaBancariaRepository
                .findByUsuarioIdAndAtivaTrueOrderByPadraoDescNomeAsc(usuarioId);
            if (ativas.size() > 1) {
                return new ResolucaoConta(null, true,
                    ativas.stream().map(ContaBancaria::getNome).collect(Collectors.toList()));
            }
            if (ativas.size() == 1) {
                return new ResolucaoConta(ativas.get(0), false, List.of());
            }
            throw new IllegalArgumentException("Cadastre uma conta bancária antes de registrar o empréstimo.");
        }
        return new ResolucaoConta(padrao, false, List.of());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(SCALE, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private record CalculoEmprestimo(
        BigDecimal parcela,
        BigDecimal taxaMensalPct,
        BigDecimal taxaAnualPct,
        BigDecimal totalAPagar,
        BigDecimal jurosTotais,
        boolean parcelaEstimada
    ) {}

    private record ResolucaoConta(ContaBancaria conta, boolean ambiguo, List<String> nomesAmbiguos) {}
}
