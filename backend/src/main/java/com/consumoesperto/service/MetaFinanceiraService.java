package com.consumoesperto.service;

import com.consumoesperto.dto.MetaFinanceiraDTO;
import com.consumoesperto.dto.MetaFinanceiraListResponse;
import com.consumoesperto.dto.MetaFinanceiraRequest;
import com.consumoesperto.dto.RendaConfigDTO;
import com.consumoesperto.dto.RendaMediaResponse;
import com.consumoesperto.model.MetaFinanceira;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.MetaFinanceiraRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.util.ApelidoNormalizador;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetaFinanceiraService {

    private static final int MONTHS_WINDOW = 3;
    private static final int SCALE_MONEY = 2;
    /** A partir deste percentual (soma de todas as metas), exibe aviso ao usuário. */
    private static final BigDecimal ALERTA_COMPROMETIMENTO_MIN = new BigDecimal("15");

    private final MetaFinanceiraRepository metaFinanceiraRepository;
    private final TransacaoRepository transacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final RendaConfigService rendaConfigService;

    /**
     * Soma das receitas confirmadas nos últimos 3 meses, dividida por 3 (média mensal).
     */
    public Optional<BigDecimal> calcularRendaMensalMediaUltimosTresMeses(Long usuarioId) {
        LocalDateTime fim = LocalDateTime.now();
        LocalDateTime inicio = fim.minusMonths(MONTHS_WINDOW);
        BigDecimal total = transacaoRepository.sumConfirmadaByUsuarioIdAndTipoAndPeriodo(
            usuarioId,
            Transacao.TipoTransacao.RECEITA,
            inicio,
            fim
        );
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        BigDecimal media = total.divide(BigDecimal.valueOf(MONTHS_WINDOW), SCALE_MONEY, RoundingMode.HALF_UP);
        return Optional.of(media);
    }

    public RendaMediaResponse getRendaMediaResponse(Long usuarioId) {
        return calcularRendaMensalMediaUltimosTresMeses(usuarioId)
            .map(r -> new RendaMediaResponse(r, true))
            .orElseGet(() -> new RendaMediaResponse(BigDecimal.ZERO.setScale(SCALE_MONEY), false));
    }

    public BigDecimal calcularValorPoupadoMensal(BigDecimal rendaMensalMedia, BigDecimal percentualComprometimento) {
        return rendaMensalMedia
            .multiply(percentualComprometimento)
            .divide(BigDecimal.valueOf(100), SCALE_MONEY, RoundingMode.HALF_UP);
    }

    public BigDecimal calcularPrazoMeses(BigDecimal valorTotal, BigDecimal valorPoupadoMensal) {
        if (valorPoupadoMensal == null || valorPoupadoMensal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor poupado mensal deve ser maior que zero");
        }
        return valorTotal.divide(valorPoupadoMensal, 2, RoundingMode.HALF_UP);
    }

    /**
     * Soma dos percentuais de comprometimento das metas do usuário.
     *
     * @param excluirMetaId meta a ignorar (ex.: em edição, antes de aplicar o novo percentual)
     */
    public BigDecimal somaPercentualComprometimento(Long usuarioId, Long excluirMetaId) {
        return metaFinanceiraRepository.findByUsuarioIdOrderByPrioridadeDescDataCriacaoDesc(usuarioId).stream()
            .filter(m -> excluirMetaId == null || !m.getId().equals(excluirMetaId))
            .map(MetaFinanceira::getPercentualComprometimento)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Total após incluir uma nova meta ou simulação (metas atuais + percentual proposto).
     */
    public BigDecimal projetarComprometimentoComNovaMeta(Long usuarioId, BigDecimal percentualNovaMeta) {
        BigDecimal base = somaPercentualComprometimento(usuarioId, null);
        if (percentualNovaMeta == null) {
            return base;
        }
        return base.add(percentualNovaMeta);
    }

    public String montarAlertaComprometimento(BigDecimal totalPercentual, boolean textoIncluiNovaMeta) {
        if (totalPercentual == null || totalPercentual.compareTo(ALERTA_COMPROMETIMENTO_MIN) < 0) {
            return null;
        }
        String pct = totalPercentual.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        if (textoIncluiNovaMeta) {
            return String.format("Cuidado, com essa nova meta, você agora compromete %s%% da sua renda total!", pct);
        }
        return String.format("Cuidado: suas metas somam %s%% da sua renda total!", pct);
    }

    public MetaFinanceiraListResponse listarComResumo(Long usuarioId) {
        List<MetaFinanceiraDTO> metas = metaFinanceiraRepository.findByUsuarioIdOrderByPrioridadeDescDataCriacaoDesc(usuarioId).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
        BigDecimal total = somaPercentualComprometimento(usuarioId, null);
        String alerta = montarAlertaComprometimento(total, false);
        return new MetaFinanceiraListResponse(metas, total, alerta);
    }

    public Optional<MetaFinanceiraDTO> buscar(Long id, Long usuarioId) {
        return metaFinanceiraRepository.findByIdAndUsuarioId(id, usuarioId).map(this::toDto);
    }

    public List<MetaFinanceira> encontrarPorDescricaoNormalizada(Long usuarioId, String identificador) {
        List<MetaFinanceira> all = metaFinanceiraRepository.findByUsuarioIdOrderByPrioridadeDescDataCriacaoDesc(usuarioId);
        return ApelidoNormalizador.filtrarPorNomeNormalizado(all, MetaFinanceira::getDescricao, identificador);
    }

    /**
     * Atualização parcial vinda do WhatsApp (campos conhecidos apenas).
     */
    @Transactional
    public MetaFinanceiraDTO aplicarPatchWhatsApp(Long usuarioId, Long metaId, JsonNode updates) {
        MetaFinanceira m = metaFinanceiraRepository.findByIdAndUsuarioId(metaId, usuarioId)
            .orElseThrow(() -> new RuntimeException("Meta não encontrada"));
        boolean recalcPadrao = false;
        boolean prazoManual = false;
        var it = updates.fields();
        while (it.hasNext()) {
            var entry = it.next();
            String key = ApelidoNormalizador.normalizar(entry.getKey()).replace(' ', '_');
            JsonNode v = entry.getValue();
            switch (key) {
                case "nome", "apelido", "descricao" -> {
                    if (v != null && !v.isNull() && v.asText() != null && !v.asText().isBlank()) {
                        m.setDescricao(v.asText().trim());
                    }
                }
                case "valorobjetivo", "valortotal", "valor" -> {
                    BigDecimal nv = readBigDecimalNode(v);
                    if (nv != null && nv.compareTo(BigDecimal.ZERO) > 0) {
                        m.setValorTotal(nv);
                        recalcPadrao = true;
                    }
                }
                case "percentualcomprometimento", "percentual" -> {
                    BigDecimal nv = readBigDecimalNode(v);
                    if (nv != null && nv.compareTo(BigDecimal.ZERO) > 0) {
                        m.setPercentualComprometimento(nv.min(new BigDecimal("100")));
                        recalcPadrao = true;
                    }
                }
                case "prioridade" -> {
                    if (v != null && v.isInt()) {
                        int p = v.asInt();
                        m.setPrioridade(Math.max(1, Math.min(5, p)));
                    }
                }
                case "dataprazo", "data_prazo" -> {
                    if (v != null && !v.isNull() && v.asText() != null && !v.asText().isBlank()) {
                        LocalDate d = LocalDate.parse(v.asText().trim());
                        LocalDate base = LocalDate.now();
                        long meses = ChronoUnit.MONTHS.between(base.withDayOfMonth(1), d.withDayOfMonth(1));
                        if (meses < 1) {
                            meses = 1;
                        }
                        m.setPrazoMeses(BigDecimal.valueOf(meses));
                        if (m.getValorTotal() != null && m.getValorTotal().compareTo(BigDecimal.ZERO) > 0) {
                            m.setValorPoupadoMensal(m.getValorTotal().divide(BigDecimal.valueOf(meses), SCALE_MONEY, RoundingMode.HALF_UP));
                        }
                        prazoManual = true;
                    }
                }
                default -> {
                }
            }
        }
        if (recalcPadrao && !prazoManual) {
            BigDecimal renda = m.getRendaMediaReferencia();
            if (renda == null || renda.compareTo(BigDecimal.ZERO) <= 0) {
                renda = calcularRendaMensalMediaUltimosTresMeses(usuarioId)
                    .orElseThrow(() -> new RuntimeException("Sem renda de referência para recalcular a meta"));
            }
            BigDecimal poupado = calcularValorPoupadoMensal(renda, m.getPercentualComprometimento());
            m.setValorPoupadoMensal(poupado);
            m.setPrazoMeses(calcularPrazoMeses(m.getValorTotal(), poupado));
            m.setRendaMediaReferencia(renda);
        }
        m = metaFinanceiraRepository.save(m);
        log.info("[ENTITY-UPDATE] Meta id={} usuário={} patch WhatsApp", metaId, usuarioId);
        BigDecimal total = somaPercentualComprometimento(usuarioId, null);
        MetaFinanceiraDTO dto = toDto(m);
        dto.setTotalPercentualComprometidoMetas(total);
        dto.setAlertaComprometimento(montarAlertaComprometimento(total, false));
        return dto;
    }

    private static BigDecimal readBigDecimalNode(JsonNode v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return BigDecimal.valueOf(v.asDouble());
        }
        try {
            return new BigDecimal(v.asText().replace(',', '.').trim());
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public MetaFinanceiraDTO criar(MetaFinanceiraRequest request, Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        BigDecimal renda = calcularRendaMensalMediaUltimosTresMeses(usuarioId)
            .orElseThrow(() -> new RuntimeException("Sem receitas nos últimos 3 meses para calcular a meta. Lance receitas ou informe a renda na simulação."));
        return salvarComRenda(request, usuario, renda);
    }

    @Transactional
    public MetaFinanceiraDTO criarComRendaInformada(MetaFinanceiraRequest request, Long usuarioId, BigDecimal rendaInformada) {
        if (rendaInformada == null || rendaInformada.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Renda informada inválida");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        return salvarComRenda(request, usuario, rendaInformada);
    }

    private MetaFinanceiraDTO salvarComRenda(MetaFinanceiraRequest request, Usuario usuario, BigDecimal rendaMedia) {
        BigDecimal poupado = calcularValorPoupadoMensal(rendaMedia, request.getPercentualComprometimento());
        BigDecimal prazo = calcularPrazoMeses(request.getValorTotal(), poupado);
        MetaFinanceira m = new MetaFinanceira();
        m.setUsuario(usuario);
        m.setDescricao(request.getDescricao().trim());
        m.setValorTotal(request.getValorTotal());
        m.setPercentualComprometimento(request.getPercentualComprometimento());
        m.setValorPoupadoMensal(poupado);
        m.setPrazoMeses(prazo);
        m.setRendaMediaReferencia(rendaMedia);
        m.setPrioridade(resolvePrioridade(request));
        m = metaFinanceiraRepository.save(m);
        BigDecimal total = somaPercentualComprometimento(usuario.getId(), null);
        log.info("[META-LOG] Meta financeira criada id={} usuario={} descricao={} prazoMeses={} comprometimentoTotal={}%",
            m.getId(), usuario.getId(), m.getDescricao(), prazo, total);
        MetaFinanceiraDTO dto = toDto(m);
        dto.setTotalPercentualComprometidoMetas(total);
        dto.setAlertaComprometimento(montarAlertaComprometimento(total, true));
        return dto;
    }

    @Transactional
    public MetaFinanceiraDTO atualizar(Long id, MetaFinanceiraRequest request, Long usuarioId) {
        MetaFinanceira m = metaFinanceiraRepository.findByIdAndUsuarioId(id, usuarioId)
            .orElseThrow(() -> new RuntimeException("Meta não encontrada"));
        BigDecimal renda = calcularRendaMensalMediaUltimosTresMeses(usuarioId)
            .orElseThrow(() -> new RuntimeException("Sem receitas nos últimos 3 meses para recalcular a meta."));
        BigDecimal poupado = calcularValorPoupadoMensal(renda, request.getPercentualComprometimento());
        BigDecimal prazo = calcularPrazoMeses(request.getValorTotal(), poupado);
        m.setDescricao(request.getDescricao().trim());
        m.setValorTotal(request.getValorTotal());
        m.setPercentualComprometimento(request.getPercentualComprometimento());
        m.setValorPoupadoMensal(poupado);
        m.setPrazoMeses(prazo);
        m.setRendaMediaReferencia(renda);
        m.setPrioridade(resolvePrioridade(request));
        m = metaFinanceiraRepository.save(m);
        BigDecimal total = somaPercentualComprometimento(usuarioId, null);
        log.info("[META-LOG] Meta financeira atualizada id={} usuario={} comprometimentoTotal={}%", id, usuarioId, total);
        MetaFinanceiraDTO dto = toDto(m);
        dto.setTotalPercentualComprometidoMetas(total);
        dto.setAlertaComprometimento(montarAlertaComprometimento(total, false));
        return dto;
    }

    @Transactional
    public void excluir(Long id, Long usuarioId) {
        MetaFinanceira m = metaFinanceiraRepository.findByIdAndUsuarioId(id, usuarioId)
            .orElseThrow(() -> new RuntimeException("Meta não encontrada"));
        metaFinanceiraRepository.delete(m);
        log.info("[META-LOG] Meta financeira removida id={} usuario={}", id, usuarioId);
    }

    @Transactional
    public MetaFinanceiraDTO criarMetaTemporariaModoViagem(
        Long usuarioId,
        String tituloEvento,
        BigDecimal valorTeto,
        LocalDate dataExpiracao,
        String googleEventId
    ) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        BigDecimal renda = calcularRendaMensalMediaUltimosTresMeses(usuarioId)
            .orElseGet(() -> rendaConfigService.obterDto(usuarioId)
                .map(RendaConfigDTO::getSalarioLiquido)
                .filter(b -> b != null && b.compareTo(BigDecimal.ZERO) > 0)
                .orElse(new BigDecimal("5000")));
        MetaFinanceiraRequest req = new MetaFinanceiraRequest();
        req.setDescricao("[Modo Viagem] " + (tituloEvento == null ? "Evento" : tituloEvento.trim()));
        req.setValorTotal(valorTeto);
        req.setPercentualComprometimento(new BigDecimal("2"));
        req.setPrioridade(4);
        MetaFinanceiraDTO dto = salvarComRenda(req, usuario, renda);
        MetaFinanceira m = metaFinanceiraRepository.findByIdAndUsuarioId(dto.getId(), usuarioId)
            .orElseThrow(() -> new RuntimeException("Meta não persistida"));
        m.setDataExpiracao(dataExpiracao);
        m.setGoogleCalendarEventId(googleEventId);
        m = metaFinanceiraRepository.save(m);
        return toDto(m);
    }

    private int resolvePrioridade(MetaFinanceiraRequest request) {
        Integer p = request.getPrioridade();
        return p != null ? p : 3;
    }

    private MetaFinanceiraDTO toDto(MetaFinanceira m) {
        return new MetaFinanceiraDTO(
            m.getId(),
            m.getDescricao(),
            m.getValorTotal(),
            m.getPercentualComprometimento(),
            m.getValorPoupadoMensal(),
            m.getPrazoMeses(),
            m.getRendaMediaReferencia(),
            m.getDataCriacao(),
            m.getPrioridade() != null ? m.getPrioridade() : 3,
            calcularProgressoPercentual(m),
            null,
            null,
            m.getDataExpiracao(),
            m.getGoogleCalendarEventId()
        );
    }

    private int calcularProgressoPercentual(MetaFinanceira m) {
        if (m.getPrazoMeses() == null || m.getPrazoMeses().compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(m.getDataCriacao().toLocalDate(), java.time.LocalDate.now());
        if (days <= 0) {
            return 0;
        }
        BigDecimal mesesDecorridos = BigDecimal.valueOf(days).divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP);
        BigDecimal raw = mesesDecorridos
            .divide(m.getPrazoMeses(), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        if (raw.compareTo(BigDecimal.valueOf(100)) > 0) {
            raw = BigDecimal.valueOf(100);
        }
        if (raw.compareTo(BigDecimal.ZERO) < 0) {
            raw = BigDecimal.ZERO;
        }
        return raw.setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
