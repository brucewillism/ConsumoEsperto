package com.consumoesperto.service.importacao;

import com.consumoesperto.dto.ImportacaoFaturaDTO;
import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.model.ImportacaoFaturaCartao;
import com.consumoesperto.model.ImportacaoItemStatus;
import com.consumoesperto.model.ImportedRowKind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public final class ImportacaoCsvPreviewSupport {

    private ImportacaoCsvPreviewSupport() {}

    public static void fillSummary(ImportacaoFaturaDTO dto, ImportacaoFaturaCartao imp, List<ImportacaoFaturaItemDTO> itens) {
        if (imp.getTipoArquivo() != null) {
            dto.setTipoArquivo(imp.getTipoArquivo().name());
        }
        dto.setArquivoNome(imp.getArquivoNome());
        dto.setPrecisaEscolhaRecurso(imp.isPrecisaEscolhaRecurso());
        if (imp.getContaBancaria() != null) {
            dto.setContaBancariaId(imp.getContaBancaria().getId());
            dto.setContaBancariaNome(imp.getContaBancaria().getNome());
        }
        if (itens == null || itens.isEmpty()) {
            return;
        }
        dto.setQuantidadeLinhas(itens.size());
        int validas = 0;
        int invalidas = 0;
        int dup = 0;
        int matched = 0;
        int review = 0;
        BigDecimal desp = BigDecimal.ZERO;
        BigDecimal rec = BigDecimal.ZERO;
        LocalDate min = null;
        LocalDate max = null;
        for (ImportacaoFaturaItemDTO i : itens) {
            String st = i.getStatusPreview();
            if (ImportacaoItemStatus.INVALID.name().equals(st)) {
                invalidas++;
            } else {
                validas++;
            }
            if (ImportacaoItemStatus.DUPLICATE.name().equals(st)) {
                dup++;
            }
            if (ImportacaoItemStatus.MATCHED_EXISTING.name().equals(st)) {
                matched++;
            }
            if (ImportacaoItemStatus.NEEDS_REVIEW.name().equals(st)) {
                review++;
            }
            if (i.getData() != null) {
                min = min == null || i.getData().isBefore(min) ? i.getData() : min;
                max = max == null || i.getData().isAfter(max) ? i.getData() : max;
            }
            BigDecimal v = i.getValor() == null ? BigDecimal.ZERO : i.getValor().abs();
            String kind = i.getTipoLinha();
            if (ImportedRowKind.RECEITA.name().equals(kind)) {
                rec = rec.add(v);
            } else if (ImportedRowKind.DESPESA.name().equals(kind)
                || ImportedRowKind.TRANSFERENCIA.name().equals(kind)
                || ImportedRowKind.PAGAMENTO_FATURA.name().equals(kind)) {
                desp = desp.add(v);
            }
        }
        dto.setQuantidadeValidas(validas);
        dto.setQuantidadeInvalidas(invalidas);
        dto.setDuplicadas(dup);
        dto.setMatchedExistentes(matched);
        dto.setNecessitamRevisao(review);
        dto.setTotalDespesas(desp.setScale(2, RoundingMode.HALF_UP));
        dto.setTotalReceitas(rec.setScale(2, RoundingMode.HALF_UP));
        if (min != null) {
            dto.setPeriodoInicio(min.atStartOfDay());
        }
        if (max != null) {
            dto.setPeriodoFim(max.atStartOfDay());
        }
    }
}
