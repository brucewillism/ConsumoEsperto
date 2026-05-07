package com.consumoesperto.service;

import com.consumoesperto.model.MetaFinanceira;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.MetaFinanceiraRepository;
import com.consumoesperto.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComportamentoService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private final TransacaoRepository transacaoRepository;
    private final MetaFinanceiraRepository metaFinanceiraRepository;
    private final WhatsAppNotificationService whatsAppNotificationService;

    @Transactional(readOnly = true)
    public void avaliarAposDespesa(Transacao transacao) {
        if (transacao == null || transacao.getUsuario() == null || transacao.getTipoTransacao() != Transacao.TipoTransacao.DESPESA) {
            return;
        }
        String texto = normalizar((transacao.getDescricao() != null ? transacao.getDescricao() : "") + " "
            + (transacao.getCategoria() != null ? transacao.getCategoria().getNome() : ""));
        if (!texto.matches(".*(delivery|ifood|rappi|uber eats|pizza|hamburg|lanche).*")) {
            return;
        }
        Long usuarioId = transacao.getUsuario().getId();
        LocalDate inicio = LocalDate.now().with(DayOfWeek.MONDAY);
        List<Transacao> semana = transacaoRepository.findByUsuarioIdAndTipoAndPeriodo(
            usuarioId,
            Transacao.TipoTransacao.DESPESA,
            inicio.atStartOfDay(),
            LocalDate.now().atTime(23, 59, 59)
        );
        List<Transacao> deliveries = semana.stream()
            .filter(t -> normalizar((t.getDescricao() != null ? t.getDescricao() : "") + " "
                + (t.getCategoria() != null ? t.getCategoria().getNome() : "")).matches(".*(delivery|ifood|rappi|uber eats|pizza|hamburg|lanche).*"))
            .toList();
        if (deliveries.size() < 3) {
            return;
        }
        BigDecimal total = deliveries.stream().map(Transacao::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        MetaFinanceira meta = metaFinanceiraRepository.findByUsuarioIdOrderByPrioridadeDescDataCriacaoDesc(usuarioId)
            .stream()
            .max(Comparator.comparing(MetaFinanceira::getPrioridade, Comparator.nullsFirst(Integer::compareTo)))
            .orElse(null);
        String metaNome = meta != null ? meta.getDescricao() : "Reserva de Emergência";
        BigDecimal pct = meta != null && meta.getValorPoupadoMensal() != null && meta.getValorPoupadoMensal().compareTo(BigDecimal.ZERO) > 0
            ? total.multiply(BigDecimal.valueOf(100)).divide(meta.getValorPoupadoMensal(), 0, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(15);
        String msg = "Notamos um padrão de gastos em Delivery acima da sua média. "
            + "Foram " + deliveries.size() + " pedidos nesta semana (" + BRL.format(total) + "), "
            + "comprometendo cerca de " + pct + "% da meta *" + metaNome + "*. "
            + "Vamos tentar um jantar em casa hoje?";
        whatsAppNotificationService.enviarParaUsuario(usuarioId, msg);
    }

    private static String normalizar(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }
}
