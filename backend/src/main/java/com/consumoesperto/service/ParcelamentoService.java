package com.consumoesperto.service;

import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Transacao;
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
import java.util.UUID;

/**
 * Parcelamento sem/com juros, vínculo a faturas (inclui PREVISTA) e metadados para relatório.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParcelamentoService {

    private final FaturaService faturaService;
    private final TransacaoService transacaoService;

    /**
     * Divide o total em N parcelas iguais em centavos; o resto vai na última parcela.
     */
    public static List<BigDecimal> calcularValoresSemJuros(BigDecimal valorTotal, int nParcelas) {
        if (valorTotal == null || nParcelas < 2) {
            throw new IllegalArgumentException("Parcelamento exige valor e pelo menos 2 parcelas");
        }
        long totalCent = valorTotal.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        long base = totalCent / nParcelas;
        long rem = totalCent % nParcelas;
        List<BigDecimal> out = new ArrayList<>(nParcelas);
        for (int i = 0; i < nParcelas; i++) {
            long c = base + (i == nParcelas - 1 ? rem : 0);
            out.add(BigDecimal.valueOf(c).movePointLeft(2));
        }
        return out;
    }

    @Transactional
    public List<TransacaoDTO> criarParcelamentoSemJuros(
        Long usuarioId,
        CartaoCredito cartao,
        String descricaoBase,
        BigDecimal valorTotal,
        int nParcelas,
        TransacaoDTO.StatusConferencia statusConferencia
    ) {
        List<BigDecimal> valores = calcularValoresSemJuros(valorTotal, nParcelas);
        return criarParcelasInterno(usuarioId, cartao, descricaoBase, valores, valorTotal, null, statusConferencia);
    }

    /**
     * Cada parcela = {@code valorParcela}; total financiado = N × valorParcela.
     *
     * @param valorRealPreçoAVista preço à vista do bem (opcional); se nulo, assume {@code valorParcela.multiply(N)}.
     */
    @Transactional
    public List<TransacaoDTO> criarParcelamentoComJuros(
        Long usuarioId,
        CartaoCredito cartao,
        String descricaoBase,
        int nParcelas,
        BigDecimal valorParcela,
        BigDecimal valorRealPreçoAVista,
        TransacaoDTO.StatusConferencia statusConferencia
    ) {
        if (valorParcela == null || valorParcela.compareTo(BigDecimal.ZERO) <= 0 || nParcelas < 2) {
            throw new IllegalArgumentException("Parcelamento com juros exige valor da parcela e N ≥ 2");
        }
        List<BigDecimal> valores = new ArrayList<>();
        for (int i = 0; i < nParcelas; i++) {
            valores.add(valorParcela.setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal totalFin = valorParcela.multiply(BigDecimal.valueOf(nParcelas)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorReal = valorRealPreçoAVista != null && valorRealPreçoAVista.compareTo(BigDecimal.ZERO) > 0
            ? valorRealPreçoAVista.setScale(2, RoundingMode.HALF_UP)
            : totalFin;
        BigDecimal valorComJuros = totalFin.subtract(valorReal).max(BigDecimal.ZERO);
        return criarParcelasInterno(usuarioId, cartao, descricaoBase, valores, valorReal, valorComJuros, statusConferencia);
    }

    private List<TransacaoDTO> criarParcelasInterno(
        Long usuarioId,
        CartaoCredito cartao,
        String descricaoBase,
        List<BigDecimal> valoresParcela,
        BigDecimal valorRealMeta,
        BigDecimal valorComJurosTotal,
        TransacaoDTO.StatusConferencia statusConferencia
    ) {
        String grupo = UUID.randomUUID().toString();
        int n = valoresParcela.size();
        Fatura faturaRef = faturaService.resolverFaturaAbertaParaCartao(usuarioId, cartao);
        LocalDate vencPrimeira = faturaRef.getDataVencimento() != null
            ? faturaRef.getDataVencimento().toLocalDate()
            : LocalDate.now();
        int dia = clampDia(cartao.getDiaVencimento());

        List<TransacaoDTO> criadas = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LocalDate vencParcela = vencimentoParcelaI(vencPrimeira, i, dia);
            Fatura f = faturaService.obterOuCriarFaturaParaVencimentoAlvo(usuarioId, cartao, vencParcela);
            TransacaoDTO dto = new TransacaoDTO();
            dto.setDescricao(montarDescricaoParcela(descricaoBase, i + 1, n));
            dto.setValor(valoresParcela.get(i));
            dto.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
            dto.setDataTransacao(LocalDateTime.now());
            dto.setFaturaId(f.getId());
            dto.setStatusConferencia(statusConferencia != null ? statusConferencia : TransacaoDTO.StatusConferencia.CONFIRMADA);
            dto.setGrupoParcelaId(grupo);
            dto.setParcelaAtual(i + 1);
            dto.setTotalParcelas(n);
            if (i == 0) {
                dto.setValorReal(valorRealMeta);
                dto.setValorComJuros(valorComJurosTotal);
            }
            criadas.add(transacaoService.criarTransacao(dto, usuarioId));
        }
        log.info("[PARCELAMENTO] grupo={} parcelas={} cartaoId={} userId={}", grupo, n, cartao.getId(), usuarioId);
        return criadas;
    }

    private static String montarDescricaoParcela(String base, int atual, int total) {
        String b = base != null ? base.trim() : "Compra";
        return b + " (" + atual + "/" + total + ")";
    }

    private static LocalDate vencimentoParcelaI(LocalDate vencimentoPrimeiraParcela, int indiceZeroBased, int diaPreferido) {
        YearMonth ym = YearMonth.from(vencimentoPrimeiraParcela).plusMonths(indiceZeroBased);
        int d = Math.min(diaPreferido, ym.lengthOfMonth());
        return ym.atDay(d);
    }

    private static int clampDia(Integer dia) {
        if (dia == null) {
            return 10;
        }
        return Math.max(1, Math.min(31, dia));
    }
}
