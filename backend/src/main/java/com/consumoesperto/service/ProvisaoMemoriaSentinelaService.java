package com.consumoesperto.service;

import com.consumoesperto.dto.ProvisaoMemoriaDTO;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.util.FinanceTextoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sentinela — provisões derivadas de memória e de histórico sazonal (“gasto fantasma”).
 */
@Service
@RequiredArgsConstructor
public class ProvisaoMemoriaSentinelaService {

    private static final Pattern RE_REAL = Pattern.compile(
        "R\\$\\s*([\\d]{1,3}(?:\\.[\\d]{3})*,\\d{2}|[\\d]+,\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MMMM/yyyy", new Locale("pt", "BR"));

    private final CerebroSemanticoService cerebroSemanticoService;
    private final TransacaoRepository transacaoRepository;

    @Transactional(readOnly = true)
    public List<ProvisaoMemoriaDTO> calcularProvisoesParaMesAtual(Long usuarioId) {
        if (usuarioId == null) {
            return List.of();
        }
        YearMonth atual = YearMonth.now();
        YearMonth passado = atual.minusYears(1);
        List<ProvisaoMemoriaDTO> raw = new ArrayList<>();

        List<String> memorias = cerebroSemanticoService.listarContextosMemoriaNoMesCalendario(
            usuarioId, passado.getMonthValue(), passado.getYear());
        for (String ctx : memorias) {
            if (!pareceGastoExtraOuSazonal(ctx)) {
                continue;
            }
            BigDecimal v = extrairValorReais(ctx);
            if (v == null || v.compareTo(new BigDecimal("80")) < 0) {
                continue;
            }
            raw.add(ProvisaoMemoriaDTO.builder()
                .diaAlvo(diaAlvoHeuristica(passado.getMonthValue()))
                .valor(v.setScale(2, RoundingMode.HALF_UP))
                .rotulo(resumirRotulo(ctx))
                .periodoHistorico(passado.atDay(1).format(MES_ANO))
                .contextoOrigem(ctx.length() > 200 ? ctx.substring(0, 197) + "…" : ctx)
                .build());
        }

        LocalDateTime ini = passado.atDay(1).atStartOfDay();
        LocalDateTime fim = passado.atEndOfMonth().atTime(23, 59, 59);
        List<Transacao> despesas = transacaoRepository.findByUsuarioIdAndTipoTransacaoOrderByDataTransacaoDesc(
            usuarioId, Transacao.TipoTransacao.DESPESA);
        for (Transacao t : despesas) {
            if (t.getStatusConferencia() != Transacao.StatusConferencia.CONFIRMADA) {
                continue;
            }
            if (t.getDataTransacao() == null || t.getDataTransacao().isBefore(ini) || t.getDataTransacao().isAfter(fim)) {
                continue;
            }
            if (Boolean.TRUE.equals(t.isRecorrente())) {
                continue;
            }
            BigDecimal val = t.getValor();
            if (val == null || val.compareTo(new BigDecimal("350")) < 0) {
                continue;
            }
            String desc = t.getDescricao() != null ? t.getDescricao() : "";
            if (!pareceGastoExtraOuSazonal(desc)) {
                continue;
            }
            raw.add(ProvisaoMemoriaDTO.builder()
                .diaAlvo(Math.min(28, t.getDataTransacao().getDayOfMonth()))
                .valor(val.setScale(2, RoundingMode.HALF_UP))
                .rotulo(FinanceTextoUtil.rotuloAmigavel(desc))
                .periodoHistorico(passado.atDay(1).format(MES_ANO))
                .contextoOrigem("Histórico: " + FinanceTextoUtil.rotuloAmigavel(desc))
                .build());
        }

        Map<String, ProvisaoMemoriaDTO> dedup = new LinkedHashMap<>();
        for (ProvisaoMemoriaDTO p : raw) {
            String k = p.getRotulo() + "|" + p.getValor();
            dedup.putIfAbsent(k, p);
        }
        List<ProvisaoMemoriaDTO> out = new ArrayList<>(dedup.values());
        out.sort(Comparator.comparing(ProvisaoMemoriaDTO::getValor).reversed());
        return out.size() > 3 ? out.subList(0, 3) : out;
    }

    private static int diaAlvoHeuristica(int mes) {
        return mes == 1 ? 15 : mes == 12 ? 10 : 12;
    }

    private static boolean pareceGastoExtraOuSazonal(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        String n = texto.toLowerCase(Locale.ROOT);
        return n.contains("ipva")
            || n.contains("iptu")
            || n.contains("dpvat")
            || n.contains("licenc")
            || n.contains("licenci")
            || n.contains("anuid")
            || n.contains("condom")
            || n.contains("manuten")
            || n.contains("gasto extra")
            || n.contains("extraordin")
            || n.contains("seminova")
            || n.contains("funrural")
            || n.contains("darf");
    }

    private static BigDecimal extrairValorReais(String texto) {
        Matcher m = RE_REAL.matcher(texto);
        if (!m.find()) {
            return null;
        }
        String g = m.group(1).replace(".", "").replace(',', '.');
        try {
            return new BigDecimal(g);
        } catch (Exception e) {
            return null;
        }
    }

    private static String resumirRotulo(String ctx) {
        String t = ctx.replace('\n', ' ').trim();
        if (t.length() > 56) {
            return t.substring(0, 53) + "...";
        }
        return t;
    }
}
