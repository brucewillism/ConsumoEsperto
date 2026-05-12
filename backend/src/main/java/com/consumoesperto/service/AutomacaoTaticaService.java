package com.consumoesperto.service;

import com.consumoesperto.dto.OrcamentoDTO;
import com.consumoesperto.dto.PrevisaoFuturoChartDTO;
import com.consumoesperto.dto.ProtocoloOtimizacaoResponseDTO;
import com.consumoesperto.model.AuditLog;
import com.consumoesperto.model.Orcamento;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.AuditLogRepository;
import com.consumoesperto.repository.OrcamentoRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Protocolo de otimização tática: rebalanceamento de tetos em categorias não essenciais
 * quando a sentinela de fluxo ou o escudo de energia indicam risco.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutomacaoTaticaService {

    private final PrevisaoFluxoCaixaService previsaoFluxoCaixaService;
    private final OrcamentoRepository orcamentoRepository;
    private final OrcamentoService orcamentoService;
    private final TransacaoRepository transacaoRepository;
    private final AuditLogRepository auditLogRepository;
    private final JarvisProtocolService jarvisProtocolService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public ProtocoloOtimizacaoResponseDTO executarProtocoloOtimizacaoMetas(Long usuarioId) {
        PrevisaoFuturoChartDTO base = previsaoFluxoCaixaService.buildPrevisaoFuturoChart(usuarioId);
        if (!base.isProtocoloOtimizacaoRecomendado()) {
            throw new IllegalStateException(
                "Protocolo não autorizado: trajetória e escudo dentro dos parâmetros de tolerância.");
        }

        YearMonth ym = YearMonth.now();
        LocalDateTime ini = ym.atDay(1).atStartOfDay();
        LocalDateTime fim = ym.atEndOfMonth().atTime(23, 59, 59);

        List<Orcamento> orcamentosNeo = orcamentoRepository.findByUsuarioIdAndMesAndAno(
                usuarioId, ym.getMonthValue(), ym.getYear())
            .stream()
            .filter(o -> o.getCategoria() != null && categoriaNaoEssencial(o.getCategoria().getNome()))
            .collect(Collectors.toList());

        if (orcamentosNeo.isEmpty()) {
            throw new IllegalStateException(
                "Cadastre orçamentos do mês em categorias não essenciais (ex.: Lazer, Restaurantes, Compras extras).");
        }

        BigDecimal receitaMes = nz(transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
            usuarioId, Transacao.TipoTransacao.RECEITA, ini, fim));
        BigDecimal metaMin = receitaMes.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
        if (metaMin.compareTo(new BigDecimal("50")) < 0) {
            metaMin = new BigDecimal("50");
        }

        BigDecimal fimMesAntes = base.getSaldoProjetadoFimMes() != null
            ? base.getSaldoProjetadoFimMes() : BigDecimal.ZERO;

        BigDecimal shortfall = BigDecimal.ZERO;
        if (fimMesAntes.compareTo(metaMin) < 0) {
            shortfall = metaMin.subtract(fimMesAntes);
        }
        if (base.isProjecaoNegativa() && shortfall.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal burn = previsaoFluxoCaixaService.calcularBurnTotalDiario(usuarioId);
            shortfall = burn.multiply(BigDecimal.valueOf(5)).setScale(2, RoundingMode.HALF_UP);
        }
        if (shortfall.compareTo(BigDecimal.ZERO) <= 0) {
            shortfall = nz(base.getSaldoAtual()).multiply(new BigDecimal("0.02")).setScale(2, RoundingMode.HALF_UP);
            if (shortfall.compareTo(new BigDecimal("30")) < 0) {
                shortfall = new BigDecimal("30");
            }
        }

        int diasProj = Math.max(1, base.getUltimoDiaMes() - base.getDiaHoje());
        BigDecimal totalCutDesejado = shortfall.setScale(2, RoundingMode.HALF_UP);

        List<OrcamentoDTO> dtos = orcamentosNeo.stream().map(orcamentoService::toDto).collect(Collectors.toList());
        BigDecimal totalHeadroom = BigDecimal.ZERO;
        for (OrcamentoDTO d : dtos) {
            BigDecimal lim = nz(d.getValorLimite());
            BigDecimal gasto = nz(d.getValorGasto());
            totalHeadroom = totalHeadroom.add(lim.subtract(gasto).max(BigDecimal.ZERO));
        }

        List<ProtocoloOtimizacaoResponseDTO.AjusteOrcamentoDTO> ajustes = new ArrayList<>();
        BigDecimal somaPesosFator = BigDecimal.ZERO;
        int pesos = 0;

        if (totalHeadroom.compareTo(BigDecimal.ZERO) > 0) {
            for (Orcamento o : orcamentosNeo) {
                OrcamentoDTO d = orcamentoService.toDto(o);
                BigDecimal limAnt = nz(o.getValorLimite());
                BigDecimal gasto = nz(d.getValorGasto());
                BigDecimal head = limAnt.subtract(gasto).max(BigDecimal.ZERO);
                if (head.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal share = totalCutDesejado.multiply(head).divide(totalHeadroom, 2, RoundingMode.HALF_UP);
                BigDecimal novo = limAnt.subtract(share)
                    .max(gasto.add(new BigDecimal("25")))
                    .max(limAnt.multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.HALF_UP));
                if (novo.compareTo(limAnt) < 0) {
                    o.setValorLimite(novo);
                    orcamentoRepository.save(o);
                    ajustes.add(new ProtocoloOtimizacaoResponseDTO.AjusteOrcamentoDTO(
                        o.getId(), d.getCategoriaNome(), limAnt, novo));
                    if (limAnt.compareTo(BigDecimal.ZERO) > 0) {
                        somaPesosFator = somaPesosFator.add(novo.divide(limAnt, 4, RoundingMode.HALF_UP));
                        pesos++;
                    }
                }
            }
        }

        if (ajustes.isEmpty()) {
            BigDecimal fator = new BigDecimal("0.80");
            for (Orcamento o : orcamentosNeo) {
                OrcamentoDTO d = orcamentoService.toDto(o);
                BigDecimal limAnt = nz(o.getValorLimite());
                BigDecimal gasto = nz(d.getValorGasto());
                BigDecimal novo = limAnt.multiply(fator)
                    .max(gasto.add(new BigDecimal("25")))
                    .setScale(2, RoundingMode.HALF_UP);
                if (novo.compareTo(limAnt) < 0) {
                    o.setValorLimite(novo);
                    orcamentoRepository.save(o);
                    ajustes.add(new ProtocoloOtimizacaoResponseDTO.AjusteOrcamentoDTO(
                        o.getId(), d.getCategoriaNome(), limAnt, novo));
                    if (limAnt.compareTo(BigDecimal.ZERO) > 0) {
                        somaPesosFator = somaPesosFator.add(novo.divide(limAnt, 4, RoundingMode.HALF_UP));
                        pesos++;
                    }
                }
            }
        }

        if (ajustes.isEmpty()) {
            throw new IllegalStateException("Não foi possível reduzir tetos — verifique limites e gastos já realizados.");
        }

        BigDecimal totalCortado = ajustes.stream()
            .map(a -> a.getLimiteAnterior().subtract(a.getLimiteNovo()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal economiaDiaria = totalCortado.divide(BigDecimal.valueOf(diasProj), 4, RoundingMode.HALF_UP);
        PrevisaoFuturoChartDTO ajustada = previsaoFluxoCaixaService.buildPrevisaoFuturoChartComEconomiaDiaria(
            usuarioId, economiaDiaria);
        BigDecimal fimDepois = ajustada.getSaldoProjetadoFimMes() != null
            ? ajustada.getSaldoProjetadoFimMes() : BigDecimal.ZERO;
        BigDecimal sobrevida = fimDepois.subtract(fimMesAntes);

        BigDecimal fatorMedio = pesos > 0
            ? BigDecimal.ONE.subtract(somaPesosFator.divide(BigDecimal.valueOf(pesos), 4, RoundingMode.HALF_UP))
            : new BigDecimal("0.20");
        int pctMedio = fatorMedio.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
        pctMedio = Math.min(99, Math.max(5, pctMedio));

        Set<String> nomes = new LinkedHashSet<>();
        for (ProtocoloOtimizacaoResponseDTO.AjusteOrcamentoDTO a : ajustes) {
            nomes.add(a.getCategoriaNome());
        }
        String rotulos = String.join(" / ", nomes);
        if (rotulos.length() > 120) {
            rotulos = rotulos.substring(0, 117) + "...";
        }

        AuditLog audit = new AuditLog();
        audit.setUsuarioId(usuarioId);
        audit.setTipo("PROTOCOLO_OTIMIZACAO_JARVIS");
        audit.setDescricao("Protocolo de Otimização J.A.R.V.I.S. aplicado com sucesso");
        auditLogRepository.save(audit);

        String voc = jarvisProtocolService.resolveVocative(usuarioId, usuarioRepository);
        String msg = jarvisProtocolService.mensagemProtocoloOtimizacaoExecutado(
            voc,
            rotulos.isBlank() ? "categorias não essenciais" : rotulos,
            pctMedio,
            sobrevida.max(BigDecimal.ZERO),
            ajustada.getUltimoDiaMes()
        );

        try {
            whatsAppNotificationService.enviarParaUsuario(usuarioId, msg);
        } catch (Exception e) {
            log.warn("WhatsApp pós-protocolo falhou para usuario {}: {}", usuarioId, e.getMessage());
        }

        ProtocoloOtimizacaoResponseDTO out = new ProtocoloOtimizacaoResponseDTO();
        out.setMensagemJarvis(msg);
        out.setFatorAjusteEmergenciaMedio(fatorMedio.setScale(4, RoundingMode.HALF_UP));
        out.setPercentualMedioReducaoTetos(pctMedio);
        out.setSobrevidaSaldoProjetado(sobrevida.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        out.setNovoSaldoProjetadoFimMes(fimDepois.setScale(2, RoundingMode.HALF_UP));
        out.setAjustes(ajustes);
        out.setPrevisaoAjustada(ajustada);
        return out;
    }

    private static boolean categoriaNaoEssencial(String nome) {
        if (nome == null) {
            return false;
        }
        String n = normalizar(nome);
        return n.contains("lazer")
            || n.contains("restaurant")
            || n.contains("restaurante")
            || n.contains("compraextra")
            || n.contains("comprasextra")
            || n.contains("gastosextras")
            || n.contains("entretenimento")
            || n.contains("delivery")
            || n.contains("ifood")
            || n.contains("bar")
            || n.contains("pub");
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
