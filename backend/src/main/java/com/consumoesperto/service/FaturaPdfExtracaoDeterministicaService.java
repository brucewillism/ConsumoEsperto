package com.consumoesperto.service;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.dto.ai.structured.LancamentoFaturaStructuredDTO;
import com.consumoesperto.dto.ai.structured.OcrFaturaStructuredDTO;
import com.consumoesperto.service.fatura.layout.BancoBrasilFaturaTextoExtrator;
import com.consumoesperto.service.fatura.layout.BancoFaturaLayout;
import com.consumoesperto.service.fatura.layout.BancoNordesteFaturaTextoExtrator;
import com.consumoesperto.service.fatura.layout.BradescoFaturaTextoExtrator;
import com.consumoesperto.service.fatura.layout.C6BankFaturaTextoExtrator;
import com.consumoesperto.service.fatura.layout.CaixaFaturaTextoExtrator;
import com.consumoesperto.service.fatura.layout.FaturaPdfLayoutStrategy;
import com.consumoesperto.service.fatura.layout.FaturaPdfLayoutSupport;
import com.consumoesperto.service.fatura.layout.GenericoFaturaTextoExtrator;
import com.consumoesperto.service.fatura.layout.InterFaturaTextoExtrator;
import com.consumoesperto.service.fatura.layout.ItauFaturaTextoExtrator;
import com.consumoesperto.service.fatura.layout.MercadoPagoFaturaTextoExtrator;
import com.consumoesperto.service.fatura.layout.NubankFaturaTextoExtrator;
import com.consumoesperto.service.fatura.layout.SantanderFaturaTextoExtrator;
import com.consumoesperto.service.fatura.layout.XpFaturaTextoExtrator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extração de fatura PDF sem IA — todos os layouts de banco suportados pelo detector.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FaturaPdfExtracaoDeterministicaService {

    private static final Pattern VENCE_EM = Pattern.compile(
        "(?i)vence\\s+em\\s*(\\d{2})/(\\d{2})/(\\d{4})"
    );
    private static final Pattern VENCIMENTO_MES_ABREV = Pattern.compile(
        "(?i)vencimento[^\\d]{0,40}(\\d{2})\\s+(JAN|FEV|MAR|ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ)\\s+(\\d{4})"
    );

    private final ObjectMapper objectMapper;

    public static boolean suporta(BancoFaturaLayout layout) {
        return layout != null;
    }

    public JsonNode extrair(String textoPdf, FaturaPdfLayoutStrategy layout) {
        if (textoPdf == null || textoPdf.isBlank()) {
            throw new IllegalArgumentException("O PDF não contém texto legível.");
        }
        if (layout == null) {
            throw new IllegalArgumentException("Layout de fatura não identificado.");
        }
        String textoNorm = FaturaPdfLayoutSupport.norm(textoPdf);
        Optional<LocalDate> vencimento = extrairDataVencimento(textoPdf);
        int anoReferencia = vencimento.map(LocalDate::getYear).orElseGet(() -> Year.now().getValue());
        Optional<LocalDate> fechamento = extrairDataFechamento(textoPdf, layout.layout());

        List<ImportacaoFaturaItemDTO> itensBrutos = extrairLancamentos(textoPdf, layout.layout(), anoReferencia);
        if (itensBrutos.isEmpty()) {
            throw new IllegalArgumentException(
                "Não consegui ler lançamentos desta fatura "
                    + layout.layout().getNomeExibicao()
                    + " pelo texto do PDF. Confira se enviou a fatura completa do cartão."
            );
        }

        Optional<BigDecimal> total = extrairTotal(textoPdf, layout.layout());
        String bancoCartao = layout.sugerirBancoCartao(textoNorm, layout.layout().getNomeExibicao());

        List<LancamentoFaturaStructuredDTO> lancamentos = itensBrutos.stream()
            .filter(i -> i.getValor() != null && i.getValor().compareTo(BigDecimal.ZERO) > 0)
            .filter(i -> i.getDescricao() != null && !i.getDescricao().isBlank())
            .map(this::toStructured)
            .collect(Collectors.toList());
        if (lancamentos.isEmpty()) {
            throw new IllegalArgumentException(
                "Nenhum lançamento válido foi encontrado na fatura "
                    + layout.layout().getNomeExibicao() + "."
            );
        }

        OcrFaturaStructuredDTO dto = OcrFaturaStructuredDTO.builder()
            .tipoDocumento("FATURA_CARTAO")
            .bancoCartao(bancoCartao)
            .dataVencimento(vencimento.orElse(null))
            .dataFechamento(fechamento.orElse(null))
            .valorTotal(total.orElse(null))
            .lancamentos(lancamentos)
            .build();

        ObjectNode node = objectMapper.valueToTree(dto);
        if (layout.layout() == BancoFaturaLayout.ITAU) {
            ItauFaturaTextoExtrator.extrairPagamentoMinimo(textoPdf)
                .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
                .ifPresent(v -> node.put("pagamentoMinimo", v));
        }
        log.info(
            "Fatura {} lida sem IA: {} lançamento(s), total={}, vencimento={}",
            layout.layout().getNomeExibicao(),
            lancamentos.size(),
            total.orElse(null),
            vencimento.orElse(null)
        );
        return node;
    }

    private LancamentoFaturaStructuredDTO toStructured(ImportacaoFaturaItemDTO item) {
        return LancamentoFaturaStructuredDTO.builder()
            .data(item.getData())
            .descricao(item.getDescricao())
            .valor(item.getValor())
            .parcelaAtual(item.getParcelaAtual())
            .totalParcelas(item.getTotalParcelas())
            .build();
    }

    private static List<ImportacaoFaturaItemDTO> extrairLancamentos(
        String textoPdf,
        BancoFaturaLayout layout,
        int anoReferencia
    ) {
        return switch (layout) {
            case ITAU -> ItauFaturaTextoExtrator.extrairLancamentos(textoPdf, anoReferencia);
            case NUBANK -> NubankFaturaTextoExtrator.extrairLancamentos(textoPdf, anoReferencia);
            case INTER -> InterFaturaTextoExtrator.extrairLancamentos(textoPdf, anoReferencia);
            case MERCADO_PAGO -> MercadoPagoFaturaTextoExtrator.extrairLancamentos(textoPdf, anoReferencia);
            case CAIXA -> CaixaFaturaTextoExtrator.extrairLancamentos(textoPdf, anoReferencia);
            case BANCO_BRASIL -> BancoBrasilFaturaTextoExtrator.extrairLancamentos(textoPdf, anoReferencia);
            case BRADESCO -> BradescoFaturaTextoExtrator.extrairLancamentos(textoPdf, anoReferencia);
            case SANTANDER -> SantanderFaturaTextoExtrator.extrairLancamentos(textoPdf, anoReferencia);
            case C6_BANK -> C6BankFaturaTextoExtrator.extrairLancamentos(textoPdf, anoReferencia);
            case XP -> XpFaturaTextoExtrator.extrairLancamentos(textoPdf, anoReferencia);
            case BANCO_NORDESTE -> BancoNordesteFaturaTextoExtrator.extrairLancamentos(textoPdf, anoReferencia);
            case MASTERCARD, GENERICO -> GenericoFaturaTextoExtrator.extrairLancamentos(textoPdf, anoReferencia);
        };
    }

    private static Optional<BigDecimal> extrairTotal(String textoPdf, BancoFaturaLayout layout) {
        return switch (layout) {
            case ITAU -> ItauFaturaTextoExtrator.extrairTotalFatura(textoPdf);
            case NUBANK -> NubankFaturaTextoExtrator.extrairTotalCompras(textoPdf);
            case INTER -> InterFaturaTextoExtrator.extrairTotalFatura(textoPdf);
            case MERCADO_PAGO -> MercadoPagoFaturaTextoExtrator.extrairTotalFatura(textoPdf);
            case CAIXA -> CaixaFaturaTextoExtrator.extrairTotalFatura(textoPdf);
            case BANCO_BRASIL -> BancoBrasilFaturaTextoExtrator.extrairTotalFatura(textoPdf);
            case BRADESCO -> BradescoFaturaTextoExtrator.extrairTotalFatura(textoPdf);
            case SANTANDER -> SantanderFaturaTextoExtrator.extrairTotalFatura(textoPdf);
            case C6_BANK -> C6BankFaturaTextoExtrator.extrairTotalFatura(textoPdf);
            case XP -> XpFaturaTextoExtrator.extrairTotalFatura(textoPdf);
            case BANCO_NORDESTE -> BancoNordesteFaturaTextoExtrator.extrairTotalFatura(textoPdf);
            case MASTERCARD, GENERICO -> GenericoFaturaTextoExtrator.extrairTotalFatura(textoPdf);
        };
    }

    private static Optional<LocalDate> extrairDataFechamento(String textoPdf, BancoFaturaLayout layout) {
        return FaturaPdfLayoutSupport.extrairDataCorteDoTexto(textoPdf, layout);
    }

    private static Optional<LocalDate> extrairDataVencimento(String textoPdf) {
        Optional<LocalDate> padrao = FaturaPdfLayoutSupport.extrairDataVencimentoDoTexto(textoPdf);
        if (padrao.isPresent()) {
            return padrao;
        }
        Matcher venceEm = VENCE_EM.matcher(textoPdf);
        if (venceEm.find()) {
            return parseDataCompleta(venceEm.group(1), venceEm.group(2), venceEm.group(3));
        }
        Matcher mesAbrev = VENCIMENTO_MES_ABREV.matcher(textoPdf);
        if (mesAbrev.find()) {
            return parseDataMesAbreviado(mesAbrev.group(1), mesAbrev.group(2), mesAbrev.group(3));
        }
        return Optional.empty();
    }

    private static Optional<LocalDate> parseDataCompleta(String dia, String mes, String ano) {
        try {
            return Optional.of(LocalDate.of(
                Integer.parseInt(ano),
                Integer.parseInt(mes),
                Integer.parseInt(dia)
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDate> parseDataMesAbreviado(String dia, String mesTxt, String ano) {
        Month mes = parseMesAbreviado(mesTxt);
        if (mes == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.of(Integer.parseInt(ano), mes, Integer.parseInt(dia)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Month parseMesAbreviado(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "JAN" -> Month.JANUARY;
            case "FEV" -> Month.FEBRUARY;
            case "MAR" -> Month.MARCH;
            case "ABR" -> Month.APRIL;
            case "MAI" -> Month.MAY;
            case "JUN" -> Month.JUNE;
            case "JUL" -> Month.JULY;
            case "AGO" -> Month.AUGUST;
            case "SET" -> Month.SEPTEMBER;
            case "OUT" -> Month.OCTOBER;
            case "NOV" -> Month.NOVEMBER;
            case "DEZ" -> Month.DECEMBER;
            default -> null;
        };
    }
}
