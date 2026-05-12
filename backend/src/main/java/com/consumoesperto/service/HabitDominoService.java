package com.consumoesperto.service;

import com.consumoesperto.model.MemoriaCategoriaOrigem;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.util.FinanceTextoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Efeito dominó: detecta sequências de despesas em até 24h (ex.: posto → conveniência) e grava memória de hábito.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HabitDominoService {

    private static final int LIMITE_OBSERVACOES = 3;
    private static final long COOLDOWN_MS = 6L * 60 * 60 * 1000;
    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    private final TransacaoRepository transacaoRepository;
    private final CerebroSemanticoService cerebroSemanticoService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final JarvisProtocolService jarvisProtocolService;
    private final UsuarioRepository usuarioRepository;

    private final Map<String, Long> cooldownMemoria = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldownAlertaGatilho = new ConcurrentHashMap<>();

    public void avaliarEfeitoDominioPosDespesa(Transacao t) {
        if (t == null || t.getUsuario() == null || t.getId() == null) {
            return;
        }
        if (!isDespesaConfirmada(t)) {
            return;
        }
        Long userId = t.getUsuario().getId();
        LocalDateTime fim = t.getDataTransacao() != null ? t.getDataTransacao() : LocalDateTime.now();
        LocalDateTime iniHist = fim.minusDays(400);

        List<Transacao> todas = transacaoRepository.findByUsuarioIdAndTipoTransacaoOrderByDataTransacaoDesc(
            userId, Transacao.TipoTransacao.DESPESA);
        List<Transacao> conf = todas.stream()
            .filter(x -> x.getStatusConferencia() == Transacao.StatusConferencia.CONFIRMADA)
            .filter(x -> x.getDataTransacao() != null && !x.getDataTransacao().isBefore(iniHist))
            .sorted(Comparator.comparing(Transacao::getDataTransacao))
            .toList();

        Map<String, Integer> pairCount = new HashMap<>();
        Map<String, BigDecimal> secondSum = new HashMap<>();
        Map<String, Integer> secondN = new HashMap<>();

        for (int i = 0; i < conf.size() - 1; i++) {
            Transacao a = conf.get(i);
            Transacao b = conf.get(i + 1);
            long h = ChronoUnit.HOURS.between(a.getDataTransacao(), b.getDataTransacao());
            if (h < 0 || h > 24) {
                continue;
            }
            String ka = FinanceTextoUtil.chaveAgrupamento(a.getDescricao());
            String kb = FinanceTextoUtil.chaveAgrupamento(b.getDescricao());
            if ("_vazio_".equals(ka) || "_vazio_".equals(kb)) {
                continue;
            }
            String pk = ka + "||" + kb;
            pairCount.merge(pk, 1, Integer::sum);
            BigDecimal vb = b.getValor() != null ? b.getValor() : BigDecimal.ZERO;
            secondSum.merge(pk, vb, BigDecimal::add);
            secondN.merge(pk, 1, Integer::sum);
        }

        String keyT = FinanceTextoUtil.chaveAgrupamento(t.getDescricao());
        if ("_vazio_".equals(keyT)) {
            return;
        }

        Transacao prev = null;
        for (int i = conf.size() - 1; i >= 0; i--) {
            Transacao cand = conf.get(i);
            if (cand.getId().equals(t.getId())) {
                continue;
            }
            if (cand.getDataTransacao() == null || !cand.getDataTransacao().isBefore(t.getDataTransacao())) {
                continue;
            }
            long h = ChronoUnit.HOURS.between(cand.getDataTransacao(), t.getDataTransacao());
            if (h >= 0 && h <= 24) {
                prev = cand;
                break;
            }
        }

        if (prev != null) {
            String ka = FinanceTextoUtil.chaveAgrupamento(prev.getDescricao());
            String pk = ka + "||" + keyT;
            int cnt = pairCount.getOrDefault(pk, 0);
            if (cnt > LIMITE_OBSERVACOES && allowMemoryCooldown(userId, pk)) {
                String rotA = FinanceTextoUtil.rotuloAmigavel(prev.getDescricao());
                String rotB = FinanceTextoUtil.rotuloAmigavel(t.getDescricao());
                String ctx = "Hábito de sequência (efeito dominó): após gastos em «" + rotA
                    + "» costuma ocorrer gasto em «" + rotB + "» em até 24h (observado " + cnt + " vezes no histórico).";
                cerebroSemanticoService.gravarMemoria(userId, ctx, MemoriaCategoriaOrigem.HABITO);
            }
            return;
        }

        List<String> alvos = new ArrayList<>();
        Map<String, BigDecimal> mediaSegundo = new HashMap<>();
        for (Map.Entry<String, Integer> e : pairCount.entrySet()) {
            if (e.getValue() <= LIMITE_OBSERVACOES) {
                continue;
            }
            String[] parts = e.getKey().split("\\|\\|");
            if (parts.length != 2) {
                continue;
            }
            if (!keyT.equals(parts[0])) {
                continue;
            }
            String kb = parts[1];
            int n = secondN.getOrDefault(e.getKey(), 1);
            BigDecimal media = secondSum.getOrDefault(e.getKey(), BigDecimal.ZERO)
                .divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
            alvos.add(kb);
            mediaSegundo.put(kb, media);
        }
        if (alvos.isEmpty()) {
            return;
        }
        alvos.sort(Comparator.comparing(k -> pairCount.getOrDefault(keyT + "||" + k, 0)).reversed());
        String bestY = alvos.get(0);
        BigDecimal valY = mediaSegundo.getOrDefault(bestY, BigDecimal.ZERO);
        String rotY = rotuloSegundaPerna(conf, keyT, bestY);
        String pkTrig = userId + "|" + keyT;
        if (!allowTriggerCooldown(pkTrig)) {
            return;
        }
        String voc = jarvisProtocolService.resolveVocative(userId, usuarioRepository);
        String rotX = FinanceTextoUtil.rotuloAmigavel(t.getDescricao());
        String msg = jarvisProtocolService.alertaGatilhoDominioHabito(voc, rotX, BRL.format(valY), rotY);
        whatsAppNotificationService.enviarParaUsuario(userId, msg);
    }

    private static String rotuloSegundaPerna(List<Transacao> conf, String keyPrimeira, String keySegunda) {
        for (int i = conf.size() - 2; i >= 0; i--) {
            Transacao a = conf.get(i);
            Transacao b = conf.get(i + 1);
            long h = ChronoUnit.HOURS.between(a.getDataTransacao(), b.getDataTransacao());
            if (h < 0 || h > 24) {
                continue;
            }
            if (!keyPrimeira.equals(FinanceTextoUtil.chaveAgrupamento(a.getDescricao()))) {
                continue;
            }
            if (!keySegunda.equals(FinanceTextoUtil.chaveAgrupamento(b.getDescricao()))) {
                continue;
            }
            return FinanceTextoUtil.rotuloAmigavel(b.getDescricao());
        }
        return FinanceTextoUtil.rotuloAmigavel(keySegunda);
    }

    private boolean allowMemoryCooldown(Long userId, String pairKey) {
        String k = userId + "|" + pairKey;
        long now = System.currentTimeMillis();
        Long last = cooldownMemoria.get(k);
        if (last != null && now - last < COOLDOWN_MS) {
            return false;
        }
        cooldownMemoria.put(k, now);
        return true;
    }

    private boolean allowTriggerCooldown(String pkTrig) {
        long now = System.currentTimeMillis();
        Long last = cooldownAlertaGatilho.get(pkTrig);
        if (last != null && now - last < COOLDOWN_MS) {
            return false;
        }
        cooldownAlertaGatilho.put(pkTrig, now);
        return true;
    }

    private static boolean isDespesaConfirmada(Transacao t) {
        return t.getTipoTransacao() == Transacao.TipoTransacao.DESPESA
            && t.getStatusConferencia() == Transacao.StatusConferencia.CONFIRMADA;
    }
}
