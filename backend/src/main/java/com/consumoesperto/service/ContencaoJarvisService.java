package com.consumoesperto.service;

import com.consumoesperto.dto.OrcamentoRequest;
import com.consumoesperto.dto.SugestaoContencaoJarvisDTO;
import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.ImportacaoFaturaCartao;
import com.consumoesperto.model.SugestaoContencaoJarvis;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.ImportacaoFaturaCartaoRepository;
import com.consumoesperto.repository.SugestaoContencaoJarvisRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Protocolo de consultoria proativa: detecta salto de gasto em hábitos (combustível, mercado, restaurante)
 * vs média dos 3 meses anteriores, sugere teto ({@link Orcamento}) e fila confirmação no WhatsApp.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContencaoJarvisService {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final BigDecimal PCT_LIMITE = new BigDecimal("1.15");
    private static final BigDecimal FATOR_REDUCAO = new BigDecimal("0.90");
    private static final int MAX_SUGESTOES_POR_FATURA = 2;

    private final TransacaoRepository transacaoRepository;
    private final SugestaoContencaoJarvisRepository sugestaoRepository;
    private final ImportacaoFaturaCartaoRepository importacaoFaturaCartaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final OrcamentoService orcamentoService;
    private final ScoreService scoreService;
    private final JarvisFeedbackService jarvisFeedbackService;

    /** Fila por usuário: próximas sugestões a confirmar via *sim* no WhatsApp (após importação de fatura). */
    private final Map<Long, ArrayDeque<Long>> filaConfirmacaoWhatsApp = new ConcurrentHashMap<>();

    public record SugestaoContencaoDraft(
        String chaveAgrupamento,
        String rotuloExibicao,
        SugestaoContencaoJarvis.TipoHabito tipoHabito,
        BigDecimal valorGastoReferencia,
        BigDecimal mediaTresMeses,
        BigDecimal percentualAumento,
        BigDecimal valorTetoSugerido,
        Long categoriaId
    ) {}

    public List<String> montarAuditoriasComMetasNaImportacao(
        Long usuarioId,
        Long cartaoId,
        List<ImportacaoFaturaItemDTO> itensMesAnterior,
        List<ImportacaoFaturaItemDTO> itensAtual,
        List<SugestaoContencaoDraft> draftsOut
    ) {
        Map<String, BigDecimal> somaAnterior = somaPorChaveEstabelecimento(itensMesAnterior);
        Map<String, BigDecimal> somaAtual = somaPorChaveEstabelecimento(itensAtual);
        Map<String, String> rotuloPorChave = rotuloExibicaoPorChave(itensAtual);
        List<CandidatoAlerta> candidatos = new ArrayList<>();
        YearMonth ymRef = YearMonth.now();
        YearMonth ymAlvo = ymRef.plusMonths(1);

        for (Map.Entry<String, BigDecimal> e : somaAtual.entrySet()) {
            String chave = e.getKey();
            BigDecimal totalPdf = e.getValue();
            if (totalPdf == null || totalPdf.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String rotulo = rotuloPorChave.getOrDefault(chave, chave);
            if (!habitoAlvo(rotulo)) {
                continue;
            }
            BigDecimal noMesCalendario = somaDespesasConfirmadasNoMesPorChave(usuarioId, ymRef, chave);
            BigDecimal gastoRef = totalPdf.max(noMesCalendario);
            BigDecimal media3 = mediaGastoUltimosTresMesesFechados(usuarioId, chave, ymRef);
            BigDecimal totalMesImpAnterior = somaAnterior.get(chave);
            boolean porMedia = media3.compareTo(BigDecimal.ZERO) > 0
                && gastoRef.compareTo(media3.multiply(PCT_LIMITE).setScale(2, RoundingMode.HALF_UP)) > 0;
            boolean fallbackImport = !porMedia
                && totalMesImpAnterior != null
                && totalMesImpAnterior.compareTo(BigDecimal.ZERO) > 0
                && gastoRef.compareTo(totalMesImpAnterior.multiply(PCT_LIMITE).setScale(2, RoundingMode.HALF_UP)) > 0;
            if (!porMedia && !fallbackImport) {
                continue;
            }
            BigDecimal basePct = porMedia ? media3 : totalMesImpAnterior;
            BigDecimal pct = basePct.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO
                : gastoRef.subtract(basePct).multiply(BigDecimal.valueOf(100)).divide(basePct, 1, RoundingMode.HALF_UP);
            BigDecimal teto = calcularTetoSugerido(media3.compareTo(BigDecimal.ZERO) > 0 ? media3 : totalMesImpAnterior, gastoRef);
            SugestaoContencaoJarvis.TipoHabito tipo = tipoHabitoDeRotulo(rotulo);
            Long cat = resolverCategoriaPreferida(usuarioId, tipo);
            candidatos.add(new CandidatoAlerta(rotulo, chave, tipo, gastoRef, media3, pct, teto, cat));
        }

        candidatos.sort(Comparator.comparing((CandidatoAlerta c) -> impactoFinanceiro(c)).reversed());
        List<String> linhas = new ArrayList<>();
        int n = 0;
        for (CandidatoAlerta c : candidatos) {
            if (jarvisFeedbackService.isMacroEmPenalidade30d(usuarioId, nomeMacroCategoriaJarvis(c.tipo()))) {
                continue;
            }
            if (n >= MAX_SUGESTOES_POR_FATURA) {
                break;
            }
            n++;
            draftsOut.add(new SugestaoContencaoDraft(
                c.chave(),
                c.rotulo(),
                c.tipo(),
                c.gastoRef(),
                c.media3(),
                c.pct(),
                c.teto(),
                c.categoriaId()
            ));
            String nomeMacro = nomeMacroCategoriaJarvis(c.tipo());
            linhas.add(
                "⛽ Senhor, seus gastos em *" + c.rotulo() + "* aumentaram *" + c.pct().stripTrailingZeros().toPlainString()
                    + "%* este mês. Para estabilizar sua reserva de emergência, sugiro uma meta de teto de *"
                    + BRL.format(c.teto()) + "* para *" + nomeMacro + "* no próximo mês. "
                    + "Deseja que eu *configure este protocolo* agora?"
            );
        }
        return linhas;
    }

    private static BigDecimal impactoFinanceiro(CandidatoAlerta c) {
        BigDecimal ref = c.media3() != null && c.media3().compareTo(BigDecimal.ZERO) > 0 ? c.media3() : BigDecimal.ZERO;
        return c.gastoRef().subtract(ref).max(BigDecimal.ZERO);
    }

    private record CandidatoAlerta(
        String rotulo,
        String chave,
        SugestaoContencaoJarvis.TipoHabito tipo,
        BigDecimal gastoRef,
        BigDecimal media3,
        BigDecimal pct,
        BigDecimal teto,
        Long categoriaId
    ) {}

    /**
     * Teto mais agressivo: menor valor entre voltar à referência (média 3m ou fatura anterior) e reduzir 10% do gasto atual.
     */
    public BigDecimal calcularTetoSugerido(BigDecimal referencia, BigDecimal gastoAtual) {
        if (gastoAtual == null || gastoAtual.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal ref = referencia != null && referencia.compareTo(BigDecimal.ZERO) > 0
            ? referencia
            : gastoAtual;
        BigDecimal menos10 = gastoAtual.multiply(FATOR_REDUCAO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal alvo = ref.min(menos10);
        if (alvo.compareTo(new BigDecimal("0.01")) < 0) {
            alvo = new BigDecimal("0.01");
        }
        return alvo.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public void persistirSugestoesPosImportacao(Long usuarioId, Long importacaoId, List<SugestaoContencaoDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return;
        }
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        ImportacaoFaturaCartao imp = importacaoId == null
            ? null
            : importacaoFaturaCartaoRepository.findByIdAndUsuarioId(importacaoId, usuarioId).orElse(null);

        if (importacaoId != null) {
            List<SugestaoContencaoJarvis> existentes = sugestaoRepository
                .findByUsuarioIdAndImportacaoFaturaCartaoIdAndStatusOrderByValorGastoReferenciaDesc(
                    usuarioId, importacaoId, SugestaoContencaoJarvis.Status.PENDENTE);
            if (!existentes.isEmpty()) {
                sugestaoRepository.deleteAll(existentes);
            }
        }
        YearMonth alvo = YearMonth.now().plusMonths(1);
        for (SugestaoContencaoDraft d : drafts) {
            SugestaoContencaoJarvis e = new SugestaoContencaoJarvis();
            e.setUsuario(usuario);
            e.setImportacaoFaturaCartao(imp);
            e.setChaveAgrupamento(d.chaveAgrupamento());
            e.setRotuloExibicao(d.rotuloExibicao());
            e.setTipoHabito(d.tipoHabito());
            e.setValorGastoReferencia(d.valorGastoReferencia());
            e.setMediaTresMeses(d.mediaTresMeses());
            e.setPercentualAumento(d.percentualAumento());
            e.setValorTetoSugerido(d.valorTetoSugerido());
            e.setMesAlvo(alvo.getMonthValue());
            e.setAnoAlvo(alvo.getYear());
            e.setStatus(SugestaoContencaoJarvis.Status.PENDENTE);
            if (d.categoriaId() != null) {
                categoriaRepository.findById(d.categoriaId()).ifPresent(e::setCategoria);
            }
            sugestaoRepository.save(e);
        }
        if (importacaoId != null) {
            recarregarFilaWhatsApp(usuarioId, importacaoId);
        }
    }

    /** Recria fila apenas com sugestões desta importação (no máx. 2). */
    public void recarregarFilaWhatsApp(Long usuarioId, Long importacaoId) {
        List<SugestaoContencaoJarvis> p = sugestaoRepository
            .findByUsuarioIdAndImportacaoFaturaCartaoIdAndStatusOrderByValorGastoReferenciaDesc(
                usuarioId, importacaoId, SugestaoContencaoJarvis.Status.PENDENTE);
        ArrayDeque<Long> q = new ArrayDeque<>();
        for (int i = 0; i < Math.min(MAX_SUGESTOES_POR_FATURA, p.size()); i++) {
            q.add(p.get(i).getId());
        }
        if (!q.isEmpty()) {
            filaConfirmacaoWhatsApp.put(usuarioId, q);
        }
    }

    public Optional<Long> pollSugestaoIdParaConfirmacaoWhatsApp(Long usuarioId) {
        ArrayDeque<Long> q = filaConfirmacaoWhatsApp.get(usuarioId);
        if (q == null || q.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(q.peek());
    }

    public void descartarTopoFilaWhatsApp(Long usuarioId) {
        ArrayDeque<Long> q = filaConfirmacaoWhatsApp.get(usuarioId);
        if (q != null && !q.isEmpty()) {
            q.poll();
            if (q.isEmpty()) {
                filaConfirmacaoWhatsApp.remove(usuarioId);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<SugestaoContencaoJarvisDTO> listarPendentes(Long usuarioId) {
        return sugestaoRepository.findByUsuarioIdAndStatusOrderByValorGastoReferenciaDesc(usuarioId, SugestaoContencaoJarvis.Status.PENDENTE)
            .stream()
            .filter(s -> !jarvisFeedbackService.isMacroEmPenalidade30d(usuarioId, nomeMacroCategoriaJarvis(s.getTipoHabito())))
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public SugestaoContencaoJarvisDTO aceitar(Long usuarioId, Long sugestaoId) {
        SugestaoContencaoJarvis s = sugestaoRepository.findByIdAndUsuarioId(sugestaoId, usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Sugestão não encontrada"));
        if (s.getStatus() != SugestaoContencaoJarvis.Status.PENDENTE) {
            throw new IllegalStateException("Esta sugestão já foi respondida.");
        }
        Long catId = s.getCategoria() != null ? s.getCategoria().getId()
            : resolverCategoriaPreferida(usuarioId, s.getTipoHabito());
        if (catId == null) {
            throw new IllegalArgumentException(
                "Crie no app uma categoria para "
                    + nomeMacroCategoriaJarvis(s.getTipoHabito())
                    + " (ex.: Combustível, Alimentação ou Restaurante) para ativar o protocolo.");
        }
        OrcamentoRequest req = new OrcamentoRequest();
        req.setCategoriaId(catId);
        req.setValorLimite(s.getValorTetoSugerido());
        req.setMes(s.getMesAlvo());
        req.setAno(s.getAnoAlvo());
        req.setCompartilhado(false);
        orcamentoService.salvar(usuarioId, req);
        s.setStatus(SugestaoContencaoJarvis.Status.ACEITA);
        categoriaRepository.findById(catId).ifPresent(s::setCategoria);
        sugestaoRepository.save(s);
        removerIdDaFilaSeTopo(usuarioId, s.getId());
        scoreService.registrarEvento(usuarioId, ScoreService.EventoScore.ORCAMENTO_NO_VERDE,
            "Protocolo de contenção J.A.R.V.I.S.: teto mensal aplicado em orçamento");
        SugestaoContencaoJarvisDTO dto = toDto(s);
        dto.setMensagemResumo(mensagemProtocoloAtivo(s));
        return dto;
    }

    private void removerIdDaFilaSeTopo(Long usuarioId, Long sugestaoId) {
        ArrayDeque<Long> q = filaConfirmacaoWhatsApp.get(usuarioId);
        if (q != null && sugestaoId.equals(q.peek())) {
            q.poll();
            if (q.isEmpty()) {
                filaConfirmacaoWhatsApp.remove(usuarioId);
            }
        }
    }

    @Transactional
    public void recusar(Long usuarioId, Long sugestaoId) {
        SugestaoContencaoJarvis s = sugestaoRepository.findByIdAndUsuarioId(sugestaoId, usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Sugestão não encontrada"));
        if (s.getStatus() != SugestaoContencaoJarvis.Status.PENDENTE) {
            return;
        }
        s.setStatus(SugestaoContencaoJarvis.Status.RECUSADA);
        sugestaoRepository.save(s);
        removerIdDaFilaSeTopo(usuarioId, s.getId());
    }

    public String mensagemProtocoloAtivo(SugestaoContencaoJarvis s) {
        String macro = nomeMacroCategoriaJarvis(s.getTipoHabito());
        return "Protocolo de contenção ativo, Senhor. Vou monitorar seus lançamentos em *" + macro
            + "* e avisarei se chegarmos a *80%* do limite estabelecido.";
    }

    /** Resumo opcional para GET_INSIGHTS (transações: não depende de importação). */
    @Transactional(readOnly = true)
    public String blocoInsightsHabito3Meses(Long usuarioId) {
        YearMonth ym = YearMonth.now();
        Map<String, BigDecimal> atualPorChave = new LinkedHashMap<>();
        Map<String, String> rotulo = new LinkedHashMap<>();
        LocalDateTime ini = ym.atDay(1).atStartOfDay();
        LocalDateTime fim = ym.plusMonths(1).atDay(1).atStartOfDay();
        for (Transacao t : transacaoRepository.findByUsuarioIdAndDataTransacaoBetween(usuarioId, ini, fim)) {
            if (t.getTipoTransacao() != Transacao.TipoTransacao.DESPESA || !isConfirmada(t)) {
                continue;
            }
            if (!habitoAlvo(t.getDescricao())) {
                continue;
            }
            String ch = chaveEstabelecimento(t.getDescricao());
            if (ch.isBlank()) {
                continue;
            }
            BigDecimal v = t.getValor() != null ? t.getValor() : BigDecimal.ZERO;
            atualPorChave.merge(ch, v, BigDecimal::add);
            rotulo.putIfAbsent(ch, t.getDescricao().trim());
        }
        if (atualPorChave.isEmpty()) {
            return "";
        }
        List<String> linhas = new ArrayList<>();
        List<Map.Entry<String, BigDecimal>> ordenado = new ArrayList<>(atualPorChave.entrySet());
        ordenado.sort(Comparator.comparing((Map.Entry<String, BigDecimal> e) -> e.getValue()).reversed());
        int lim = 0;
        for (Map.Entry<String, BigDecimal> e : ordenado) {
            if (lim >= 2) {
                break;
            }
            String ch = e.getKey();
            BigDecimal gasto = e.getValue();
            BigDecimal media3 = mediaGastoUltimosTresMesesFechados(usuarioId, ch, ym);
            if (media3.compareTo(BigDecimal.ZERO) <= 0
                || gasto.compareTo(media3.multiply(PCT_LIMITE).setScale(2, RoundingMode.HALF_UP)) <= 0) {
                continue;
            }
            lim++;
            BigDecimal pct = gasto.subtract(media3).multiply(BigDecimal.valueOf(100))
                .divide(media3, 1, RoundingMode.HALF_UP);
            BigDecimal teto = calcularTetoSugerido(media3, gasto);
            String r = rotulo.getOrDefault(ch, ch);
            linhas.add("⛽ Senhor, em *" + r + "* o mês vai *" + pct.stripTrailingZeros().toPlainString()
                + "%* acima da sua média dos últimos 3 meses. Sugiro teto *" + BRL.format(teto)
                + "* no próximo mês — *aceite no app* (Sugestões J.A.R.V.I.S.) ou peça pelo WhatsApp.");
        }
        if (linhas.isEmpty()) {
            return "";
        }
        return "\n\n*Consultoria proativa*\n" + linhas.stream().map(l -> "• " + l).collect(Collectors.joining("\n"));
    }

    // --- internos ---

    private SugestaoContencaoJarvisDTO toDto(SugestaoContencaoJarvis s) {
        SugestaoContencaoJarvisDTO d = new SugestaoContencaoJarvisDTO();
        d.setId(s.getId());
        d.setImportacaoFaturaCartaoId(s.getImportacaoFaturaCartao() != null ? s.getImportacaoFaturaCartao().getId() : null);
        d.setCategoriaId(s.getCategoria() != null ? s.getCategoria().getId() : null);
        d.setCategoriaNome(s.getCategoria() != null ? s.getCategoria().getNome() : null);
        d.setChaveAgrupamento(s.getChaveAgrupamento());
        d.setRotuloExibicao(s.getRotuloExibicao());
        d.setTipoHabito(s.getTipoHabito().name());
        d.setValorGastoReferencia(s.getValorGastoReferencia());
        d.setMediaTresMeses(s.getMediaTresMeses());
        d.setPercentualAumento(s.getPercentualAumento());
        d.setValorTetoSugerido(s.getValorTetoSugerido());
        d.setMesAlvo(s.getMesAlvo());
        d.setAnoAlvo(s.getAnoAlvo());
        d.setStatus(s.getStatus().name());
        return d;
    }

    private boolean isConfirmada(Transacao t) {
        return t.getStatusConferencia() == null || t.getStatusConferencia() == Transacao.StatusConferencia.CONFIRMADA;
    }

    private BigDecimal somaDespesasConfirmadasNoMesPorChave(Long usuarioId, YearMonth ym, String chave) {
        LocalDateTime inicio = ym.atDay(1).atStartOfDay();
        LocalDateTime fim = ym.plusMonths(1).atDay(1).atStartOfDay();
        BigDecimal acc = BigDecimal.ZERO;
        for (Transacao t : transacaoRepository.findByUsuarioIdAndDataTransacaoBetween(usuarioId, inicio, fim)) {
            if (t.getTipoTransacao() != Transacao.TipoTransacao.DESPESA || !isConfirmada(t)) {
                continue;
            }
            if (!chaveEstabelecimento(t.getDescricao()).equals(chave)) {
                continue;
            }
            if (t.getValor() != null) {
                acc = acc.add(t.getValor());
            }
        }
        return acc.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal mediaGastoUltimosTresMesesFechados(Long usuarioId, String chave, YearMonth mesAtual) {
        BigDecimal s = BigDecimal.ZERO;
        for (int i = 1; i <= 3; i++) {
            s = s.add(somaDespesasConfirmadasNoMesPorChave(usuarioId, mesAtual.minusMonths(i), chave));
        }
        return s.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
    }

    private Long resolverCategoriaPreferida(Long usuarioId, SugestaoContencaoJarvis.TipoHabito tipo) {
        List<Categoria> cats = categoriaRepository.findByUsuarioIdOrderByNome(usuarioId);
        String[] tokens = switch (tipo) {
            case COMBUSTIVEL -> new String[] {"combust", "gasolina", "transporte", "veículo", "veiculo", "carro"};
            case MERCADO -> new String[] {"mercado", "supermercado", "aliment", "compras"};
            case RESTAURANTE -> new String[] {"restaur", "refei", "lanche", "delivery"};
            default -> new String[] {"aliment", "despesa", "variável", "variavel"};
        };
        for (Categoria c : cats) {
            String n = norm(c.getNome());
            for (String tok : tokens) {
                if (n.contains(norm(tok))) {
                    return c.getId();
                }
            }
        }
        return null;
    }

    private static String nomeMacroCategoriaJarvis(SugestaoContencaoJarvis.TipoHabito tipo) {
        return switch (tipo) {
            case COMBUSTIVEL -> "combustível";
            case MERCADO -> "supermercado / alimentação";
            case RESTAURANTE -> "restaurantes e refeições";
            default -> "esta categoria de consumo";
        };
    }

    private static SugestaoContencaoJarvis.TipoHabito tipoHabitoDeRotulo(String rotulo) {
        String n = norm(rotulo);
        if (n.contains("posto") || n.contains("combust") || n.contains("shell") || n.contains("ipiranga")) {
            return SugestaoContencaoJarvis.TipoHabito.COMBUSTIVEL;
        }
        if (n.contains("restaur") || n.contains("ifood") || n.contains("rappi") || n.contains("lanche")) {
            return SugestaoContencaoJarvis.TipoHabito.RESTAURANTE;
        }
        if (n.contains("mercado") || n.contains("super") || n.contains("atacad") || n.contains("padaria")) {
            return SugestaoContencaoJarvis.TipoHabito.MERCADO;
        }
        return SugestaoContencaoJarvis.TipoHabito.OUTRO;
    }

    private static boolean habitoAlvo(String descricao) {
        if (FinanceInsightProfileClassifier.perfilPorDescricao(descricao) != FinanceInsightProfileClassifier.Perfil.HABITO_CONSUMO) {
            return false;
        }
        String n = norm(descricao);
        return n.contains("posto") || n.contains("combust") || n.contains("gasolina") || n.contains("etanol")
            || n.contains("supermercado") || n.contains("mercado") || n.contains("atacad") || n.contains("padaria")
            || n.contains("restaur") || n.contains("ifood") || n.contains("rappi") || n.contains("lanche")
            || n.contains("food");
    }

    private Map<String, BigDecimal> somaPorChaveEstabelecimento(List<ImportacaoFaturaItemDTO> linhas) {
        Map<String, BigDecimal> acc = new LinkedHashMap<>();
        if (linhas == null) {
            return acc;
        }
        for (ImportacaoFaturaItemDTO it : linhas) {
            if (it == null || it.getValor() == null) {
                continue;
            }
            String k = chaveEstabelecimento(it.getDescricao());
            if (k.isBlank()) {
                continue;
            }
            acc.merge(k, it.getValor(), BigDecimal::add);
        }
        return acc;
    }

    private Map<String, String> rotuloExibicaoPorChave(List<ImportacaoFaturaItemDTO> linhas) {
        Map<String, String> map = new LinkedHashMap<>();
        if (linhas == null) {
            return map;
        }
        for (ImportacaoFaturaItemDTO it : linhas) {
            if (it == null || it.getDescricao() == null || it.getDescricao().isBlank()) {
                continue;
            }
            String k = chaveEstabelecimento(it.getDescricao());
            if (!k.isBlank()) {
                map.putIfAbsent(k, it.getDescricao().trim());
            }
        }
        return map;
    }

    private static String chaveEstabelecimento(String descricao) {
        String n = norm(removerMarcadorParcela(descricao));
        if (n.isBlank()) {
            return "";
        }
        int cut = 48;
        return n.length() <= cut ? n : n.substring(0, cut).trim();
    }

    private static String removerMarcadorParcela(String descricao) {
        if (descricao == null) {
            return "";
        }
        return descricao
            .replaceAll("(?i)(?:parc(?:ela)?\\.?\\s*)?\\d{1,2}\\s*/\\s*\\d{1,2}", " ")
            .replaceAll("(?i)(?:parc(?:ela)?\\.?\\s*)?\\d{1,2}\\s*(?:de|DE)\\s*\\d{1,2}", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String norm(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
