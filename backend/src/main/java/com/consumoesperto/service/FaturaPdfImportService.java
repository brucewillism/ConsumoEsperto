package com.consumoesperto.service;

import com.consumoesperto.dto.ConfirmarImportacaoFaturaRequest;
import com.consumoesperto.dto.ImportacaoFaturaDTO;
import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.exception.DivergenciaFaturaException;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.ImportacaoFaturaCartao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.ImportacaoFaturaCartaoRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.exception.AiUnavailableException;
import com.consumoesperto.util.AiErroHumanizer;
import com.consumoesperto.util.BancoBrasilCatalog;
import com.consumoesperto.util.SaldoAnteriorFaturaBbSupport;
import com.consumoesperto.util.SaldoAnteriorFaturaBbSupport.SaldoAnteriorBbMeta;
import com.consumoesperto.util.SaldoAnteriorFaturaBbSupport.SaldoAnteriorBbPendente;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaturaPdfImportService {

    private static final TypeReference<List<ImportacaoFaturaItemDTO>> ITEM_LIST = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    /** Metadados internos da conciliação; não exibir ao usuário em WhatsApp/bullets. */
    static final String META_ANUIDADE_CKSM_PREFIX = "__ANUIDADE_CKSM__";

    public record ResultadoConfirmacaoFatura(int criadas, int conciliadas, int futuras) {
        public int registrosNaFaturaAtual() {
            return criadas + conciliadas;
        }
    }

    private final DocumentoIAContextService documentoIAContextService;
    private final ObjectMapper objectMapper;
    private final ImportacaoFaturaCartaoRepository importacaoRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;
    private final UsuarioRepository usuarioRepository;
    private final FaturaRepository faturaRepository;
    private final TransacaoRepository transacaoRepository;
    private final TransacaoService transacaoService;
    private final FaturaService faturaService;
    private final ScoreService scoreService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final ContencaoJarvisService contencaoJarvisService;
    private final ObjectProvider<FaturaPdfImportService> selfProvider;

    /** Filtra marcadores persistidos apenas para lógica de reconciliação. */
    public static boolean isBulletVisivelAoUsuario(String linhaAuditoria) {
        return linhaAuditoria != null
            && !linhaAuditoria.startsWith(META_ANUIDADE_CKSM_PREFIX)
            && !linhaAuditoria.startsWith(SaldoAnteriorFaturaBbSupport.META_SALDO_ANTERIOR_BB_PREFIX);
    }

    public ImportacaoFaturaDTO processarPdf(Long usuarioId, byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("O PDF está vazio ou não foi recebido.");
        }
        log.info("Processando PDF de fatura userId={} bytes={}", usuarioId, pdfBytes.length);
        try {
            JsonNode extracted = documentoIAContextService.extrairDocumentoPdf(usuarioId, pdfBytes);
            return selfProvider.getObject().processarExtracao(usuarioId, extracted);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Falha ao processar PDF de fatura userId={}: {}", usuarioId, e.getMessage(), e);
            String human = AiErroHumanizer.humanizar(e.getMessage());
            if (human != null) {
                throw new AiUnavailableException(human);
            }
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        }
    }

    @Transactional(timeout = 300)
    public ImportacaoFaturaDTO processarExtracao(Long usuarioId, JsonNode extracted) {
        String tipo = extracted.path("tipoDocumento").asText("");
        if (!"FATURA_CARTAO".equalsIgnoreCase(tipo) && !pareceFaturaCartao(extracted)) {
            throw new IllegalArgumentException("O PDF parece ser um extrato de conta, não uma fatura de cartão.");
        }

        String bancoExtraido = firstNonBlank(extracted.path("bancoCartao").asText(""), extracted.path("cartao").asText(""));
        CartaoCredito cartao = localizarCartao(usuarioId, bancoExtraido)
            .orElseThrow(() -> new IllegalArgumentException(mensagemCartaoNaoEncontrado(usuarioId, bancoExtraido)));
        String banco = BancoBrasilCatalog.nomeExibicao(bancoExtraido);

        List<ImportacaoFaturaItemDTO> itens = parseItens(extracted.path("lancamentos"));
        for (ImportacaoFaturaItemDTO item : itens) {
            boolean novo = buscarExistentes(usuarioId, item).isEmpty();
            item.setNovo(novo);
            item.setSelecionado(novo);
        }

        ImportacaoFaturaCartao imp = new ImportacaoFaturaCartao();
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        imp.setUsuario(usuario);
        imp.setCartaoCredito(cartao);
        imp.setBancoCartao(banco);
        imp.setDataVencimento(parseDateTime(extracted.path("dataVencimento").asText("")));
        imp.setDataFechamento(parseDateTime(extracted.path("dataFechamento").asText("")));
        BigDecimal valorTotal = readMoney(extracted.path("valorTotal"));
        imp.setValorTotal(valorTotal);
        imp.setPagamentoMinimo(readMoney(extracted.path("pagamentoMinimo")));
        imp.setItensJson(writeJson(itens));
        List<ImportacaoFaturaItemDTO> prevItens = List.of();
        List<ImportacaoFaturaCartao> anteriores = importacaoRepository
            .findByUsuarioIdAndCartaoCreditoIdOrderByDataVencimentoDesc(usuarioId, cartao.getId());
        if (!anteriores.isEmpty()) {
            prevItens = readItens(anteriores.get(0).getItensJson());
        }
        List<String> auditorias = new ArrayList<>(auditarGastosFantasmas(usuarioId, cartao.getId(), itens));
        List<ContencaoJarvisService.SugestaoContencaoDraft> contencaoDrafts = new ArrayList<>();
        auditorias.addAll(contencaoJarvisService.montarAuditoriasComMetasNaImportacao(
            usuarioId, cartao.getId(), prevItens, itens, contencaoDrafts));
        aplicarValidacaoChecksumFatura(valorTotal, itens, extracted, auditorias);
        Optional<BigDecimal> ultimaFatura = ultimoValorFaturaConfirmadaCartao(cartao.getId());
        BigDecimal somaItens = somaValoresItens(itens);
        SaldoAnteriorFaturaBbSupport.detectar(extracted, banco, valorTotal, somaItens, itens, ultimaFatura)
            .ifPresent(p -> SaldoAnteriorFaturaBbSupport.registrarPendenciaNasAuditorias(auditorias, p));
        imp.setAuditoriaJson(writeJson(auditorias));
        imp.setNovosDetectados((int) itens.stream().filter(ImportacaoFaturaItemDTO::isNovo).count());
        ImportacaoFaturaCartao salvo = importacaoRepository.save(imp);
        contencaoJarvisService.persistirSugestoesPosImportacao(usuarioId, salvo.getId(), contencaoDrafts);
        return toDto(salvo);
    }

    public boolean pareceFaturaCartao(JsonNode extracted) {
        if (extracted == null || extracted.isMissingNode() || extracted.isNull()) {
            return false;
        }
        String tipo = extracted.path("tipoDocumento").asText("");
        if ("FATURA_CARTAO".equalsIgnoreCase(tipo)) {
            return true;
        }
        if (!"EXTRATO_CONTA".equalsIgnoreCase(tipo)) {
            return false;
        }
        boolean temCartao = !firstNonBlank(extracted.path("bancoCartao").asText(""), extracted.path("cartao").asText("")).isBlank();
        boolean temVencimento = parseDate(extracted.path("dataVencimento").asText("")) != null;
        boolean temTotal = readMoney(extracted.path("valorTotal")).compareTo(BigDecimal.ZERO) > 0;
        boolean temMinimoOuFechamento = readMoney(extracted.path("pagamentoMinimo")).compareTo(BigDecimal.ZERO) > 0
            || parseDate(extracted.path("dataFechamento").asText("")) != null;
        boolean temLancamentos = extracted.path("lancamentos").isArray() && extracted.path("lancamentos").size() > 0;
        return temCartao && temVencimento && temTotal && temLancamentos && temMinimoOuFechamento;
    }

    @Transactional(readOnly = true)
    public List<ImportacaoFaturaDTO> listarPendentes(Long usuarioId) {
        return importacaoRepository
            .findByUsuarioIdAndStatusOrderByDataCriacaoDesc(Long.valueOf(usuarioId), ImportacaoFaturaCartao.Status.PENDENTE)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /** Remove uma importação pendente (e sugestões de protocolo vinculadas). */
    @Transactional
    public void excluirPendente(Long usuarioId, Long importacaoId) {
        ImportacaoFaturaCartao imp = importacaoRepository.findByIdAndUsuarioId(importacaoId, usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Importação não encontrada"));
        if (imp.getStatus() != ImportacaoFaturaCartao.Status.PENDENTE) {
            throw new IllegalArgumentException("Só é possível apagar importações pendentes.");
        }
        contencaoJarvisService.removerSugestoesDaImportacao(usuarioId, importacaoId);
        importacaoRepository.delete(imp);
        log.info("Importação pendente removida importacaoId={} userId={}", importacaoId, usuarioId);
    }

    /** Remove todas as importações pendentes do utilizador. */
    @Transactional
    public int excluirTodasPendentes(Long usuarioId) {
        List<ImportacaoFaturaCartao> pendentes = importacaoRepository
            .findByUsuarioIdAndStatusOrderByDataCriacaoDesc(usuarioId, ImportacaoFaturaCartao.Status.PENDENTE);
        for (ImportacaoFaturaCartao imp : pendentes) {
            contencaoJarvisService.removerSugestoesDaImportacao(usuarioId, imp.getId());
            importacaoRepository.delete(imp);
        }
        if (!pendentes.isEmpty()) {
            log.info("Removidas {} importação(ões) pendente(s) userId={}", pendentes.size(), usuarioId);
        }
        return pendentes.size();
    }

    @Transactional
    public int confirmar(Long usuarioId, Long importacaoId, ConfirmarImportacaoFaturaRequest request) {
        return confirmarComResumo(usuarioId, importacaoId, request, true).criadas();
    }

    @Transactional
    public ResultadoConfirmacaoFatura confirmarComResumo(
        Long usuarioId,
        Long importacaoId,
        ConfirmarImportacaoFaturaRequest request,
        boolean enviarNotificacaoFinal
    ) {
        ImportacaoFaturaCartao imp = importacaoRepository.findByIdAndUsuarioId(importacaoId, usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Importação não encontrada"));
        if (imp.getStatus() != ImportacaoFaturaCartao.Status.PENDENTE) {
            return new ResultadoConfirmacaoFatura(0, 0, 0);
        }
        if (SaldoAnteriorFaturaBbSupport.pendenteNaoResolvido(readAuditorias(imp.getAuditoriaJson()))) {
            throw new IllegalArgumentException(
                "Antes de confirmar, escolha se o total deve *somar* o saldo anterior ao saldo desta fatura "
                    + "ou importar *apenas* o saldo atual (responda *sim* ou *não* no WhatsApp ou use os botões em Importações pendentes).");
        }
        List<ImportacaoFaturaItemDTO> itens = readItens(imp.getItensJson());
        log.info("Confirmando importação fatura importacaoId={} userId={} itens={} novos={}",
            importacaoId, usuarioId, itens.size(), imp.getNovosDetectados());
        Set<Integer> indices = request != null && request.getIndices() != null && !request.getIndices().isEmpty()
            ? new HashSet<>(request.getIndices())
            : null;
        List<ImportacaoFaturaItemDTO> itensSelecionados = itensSelecionados(itens, indices);
        boolean ignorarDivergencia = request != null && request.isIgnorarDivergencia();
        Optional<String> divergencia = validarSomaParaConfirmacao(imp.getValorTotal(), itensSelecionados, readAuditorias(imp.getAuditoriaJson()));
        if (divergencia.isPresent() && !ignorarDivergencia) {
            throw new DivergenciaFaturaException(divergencia.get()
                + " Você pode confirmar mesmo assim e completar os lançamentos faltantes depois, ou reimportar o PDF.");
        }
        Fatura fatura = resolverFatura(imp);
        Set<Long> faturasParaSincronizar = new HashSet<>();
        faturasParaSincronizar.add(fatura.getId());
        int criadas = 0;
        int conciliadas = 0;
        int futuras = 0;
        for (int i = 0; i < itens.size(); i++) {
            ImportacaoFaturaItemDTO item = itens.get(i);
            boolean selecionado = indices == null ? item.isSelecionado() : indices.contains(i);
            List<com.consumoesperto.model.Transacao> existentes = buscarExistentes(usuarioId, item);
            if (!existentes.isEmpty()) {
                conciliadas += conciliarExistentesComFatura(existentes, fatura, item);
                futuras += criarParcelasFuturasSeNecessario(usuarioId, imp, fatura, item, faturasParaSincronizar);
                continue;
            }
            if (!selecionado || !item.isNovo()) {
                continue;
            }
            TransacaoDTO dto = new TransacaoDTO();
            dto.setDescricao(item.getDescricao());
            dto.setValor(item.getValor());
            dto.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
            dto.setDataTransacao(item.getData() != null ? item.getData().atStartOfDay() : imp.getDataFechamento());
            dto.setFaturaId(fatura.getId());
            dto.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
            if (item.getParcelaAtual() != null && item.getTotalParcelas() != null && item.getTotalParcelas() > 1) {
                dto.setParcelaAtual(item.getParcelaAtual());
                dto.setTotalParcelas(item.getTotalParcelas());
                dto.setGrupoParcelaId(parcelGroupId(imp, item));
            }
            transacaoService.criarTransacao(dto, usuarioId, false, false);
            futuras += criarParcelasFuturasSeNecessario(usuarioId, imp, fatura, item, faturasParaSincronizar);
            criadas++;
        }
        imp.setFatura(fatura);
        imp.setStatus(ImportacaoFaturaCartao.Status.CONFIRMADA);
        imp.setDataConfirmacao(LocalDateTime.now());
        importacaoRepository.save(imp);
        for (Long faturaId : faturasParaSincronizar) {
            faturaService.sincronizarValorFaturaComTransacoes(faturaId);
        }
        descartarPrevistasObsoletasDoMes(imp.getCartaoCredito().getId(), fatura);
        scoreService.registrarEvento(usuarioId, ScoreService.EventoScore.IMPORTACAO_CONSISTENTE, "Fatura PDF importada em dia");
        contencaoJarvisService.ativarFilaWhatsAppAposConfirmacao(usuarioId, importacaoId);
        ResultadoConfirmacaoFatura resultado = new ResultadoConfirmacaoFatura(criadas, conciliadas, futuras);
        if (enviarNotificacaoFinal) {
            String msg = mensagemResumoImportacao(resultado);
            msg += contencaoJarvisService.blocoConviteConfirmacaoWhatsApp(usuarioId).orElse("");
            whatsAppNotificationService.enviarParaUsuario(usuarioId, msg);
        }
        return resultado;
    }

    @Transactional
    public int confirmarTodos(Long usuarioId, Long importacaoId) {
        return confirmar(usuarioId, importacaoId, null);
    }

    @Transactional
    public ResultadoConfirmacaoFatura confirmarTodosComResumo(Long usuarioId, Long importacaoId, boolean enviarNotificacaoFinal) {
        return confirmarComResumo(usuarioId, importacaoId, null, enviarNotificacaoFinal);
    }

    public String mensagemResumoImportacao(ResultadoConfirmacaoFatura resultado) {
        return "Importei sua fatura: " + resultado.registrosNaFaturaAtual()
            + " registro(s) ficaram na fatura atual e " + resultado.futuras()
            + " foram para faturas seguintes.";
    }

    /**
     * Aplica escolha do utilizador sobre saldo anterior (BB): {@code somar=true} inclui anterior+atual no total.
     */
    @Transactional
    public ImportacaoFaturaDTO aplicarEscolhaSaldoAnteriorBb(Long usuarioId, Long importacaoId, boolean somar) {
        ImportacaoFaturaCartao imp = importacaoRepository.findByIdAndUsuarioId(importacaoId, usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Importação não encontrada"));
        if (imp.getStatus() != ImportacaoFaturaCartao.Status.PENDENTE) {
            throw new IllegalStateException("Importação já foi finalizada.");
        }
        List<String> auditoriasCompletas = new ArrayList<>(readAuditorias(imp.getAuditoriaJson()));
        SaldoAnteriorBbMeta meta = SaldoAnteriorFaturaBbSupport.lerMeta(auditoriasCompletas)
            .filter(m -> !m.resolvido())
            .orElseThrow(() -> new IllegalArgumentException(
                "Não há escolha de saldo anterior pendente nesta importação."));

        List<ImportacaoFaturaItemDTO> itens = readItens(imp.getItensJson());
        int linhasSaldoIgnoradas = 0;
        if (!somar) {
            linhasSaldoIgnoradas = SaldoAnteriorFaturaBbSupport.desmarcarLinhasSaldoAnterior(itens);
            imp.setItensJson(writeJson(itens));
            imp.setNovosDetectados((int) itens.stream().filter(ImportacaoFaturaItemDTO::isNovo).count());
        }

        BigDecimal novoTotal = SaldoAnteriorFaturaBbSupport.valorTotalAposEscolha(meta, somar);
        imp.setValorTotal(novoTotal);

        List<String> auditoriasAtualizadas = SaldoAnteriorFaturaBbSupport.marcarMetaResolvida(auditoriasCompletas, somar);
        List<ImportacaoFaturaItemDTO> itensChecksum = somar
            ? itens
            : itens.stream()
                .filter(i -> !SaldoAnteriorFaturaBbSupport.descricaoEhLinhaSaldoFaturaAnterior(i.getDescricao()))
                .collect(Collectors.toList());
        aplicarValidacaoChecksumFatura(novoTotal, itensChecksum, objectMapper.createObjectNode(), auditoriasAtualizadas);
        auditoriasAtualizadas.add(
            SaldoAnteriorFaturaBbSupport.mensagemPosEscolha(somar, novoTotal, linhasSaldoIgnoradas));
        imp.setAuditoriaJson(writeJson(auditoriasAtualizadas));
        return toDto(importacaoRepository.save(imp));
    }

    public ImportacaoFaturaDTO toDto(ImportacaoFaturaCartao imp) {
        ImportacaoFaturaDTO dto = new ImportacaoFaturaDTO();
        dto.setId(imp.getId());
        dto.setCartaoCreditoId(imp.getCartaoCredito() != null ? imp.getCartaoCredito().getId() : null);
        dto.setCartaoCreditoNome(imp.getCartaoCredito() != null ? imp.getCartaoCredito().getNome() : null);
        dto.setBancoCartao(imp.getBancoCartao());
        dto.setDataVencimento(imp.getDataVencimento());
        dto.setDataFechamento(imp.getDataFechamento());
        dto.setValorTotal(imp.getValorTotal());
        dto.setPagamentoMinimo(imp.getPagamentoMinimo());
        dto.setStatus(imp.getStatus() != null ? imp.getStatus().name() : null);
        dto.setNovosDetectados(imp.getNovosDetectados());
        dto.setItens(readItens(imp.getItensJson()));
        List<String> auditoriasCompletas = readAuditorias(imp.getAuditoriaJson());
        dto.setAuditorias(auditoriasCompletas.stream()
            .filter(FaturaPdfImportService::isBulletVisivelAoUsuario)
            .collect(Collectors.toList()));
        SaldoAnteriorFaturaBbSupport.lerMeta(auditoriasCompletas).ifPresent(m -> {
            dto.setSaldoFaturaAnterior(m.saldoAnterior());
            dto.setSaldoFaturaAtual(m.saldoMesAtual());
            dto.setAguardandoEscolhaSaldoAnterior(!m.resolvido());
        });
        dto.setDataCriacao(imp.getDataCriacao());
        return dto;
    }

    private Optional<BigDecimal> ultimoValorFaturaConfirmadaCartao(Long cartaoCreditoId) {
        if (cartaoCreditoId == null) {
            return Optional.empty();
        }
        List<Fatura> faturas = faturaRepository.findByCartaoCreditoIdOrderByDataVencimentoAsc(cartaoCreditoId);
        for (int i = faturas.size() - 1; i >= 0; i--) {
            Fatura f = faturas.get(i);
            if (f.getValorTotal() != null && f.getValorTotal().compareTo(BigDecimal.ZERO) > 0) {
                return Optional.of(f.getValorTotal());
            }
        }
        return Optional.empty();
    }

    private static BigDecimal somaValoresItens(List<ImportacaoFaturaItemDTO> itens) {
        if (itens == null || itens.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return itens.stream()
            .map(i -> i.getValor() != null ? i.getValor() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private String mensagemCartaoNaoEncontrado(Long usuarioId, String banco) {
        List<CartaoCredito> ativos = cartaoCreditoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        if (ativos.isEmpty()) {
            return "Não há cartão de crédito ativo. Cadastre um cartão em *Cartões* antes de importar a fatura.";
        }
        String ref = banco == null || banco.isBlank() ? "(banco não identificado no PDF)" : banco;
        String lista = ativos.stream()
            .map(c -> c.getNome() + " / " + c.getBanco())
            .distinct()
            .limit(6)
            .collect(Collectors.joining("; "));
        return "Não encontrei cartão ativo correspondente a «" + ref + "». "
            + "Seus cartões ativos: " + lista + ". "
            + "Ajuste o nome ou banco do cartão em *Cartões* para coincidir com a fatura.";
    }

    private Optional<CartaoCredito> localizarCartao(Long usuarioId, String banco) {
        List<CartaoCredito> ativos = cartaoCreditoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        if (ativos.isEmpty()) {
            return Optional.empty();
        }
        String token = norm(banco);
        if (token.isBlank()) {
            if (ativos.size() == 1) {
                return Optional.of(ativos.get(0));
            }
            log.warn("[FaturaPDF] bancoCartao vazio na extração; userId={} tem {} cartões ativos — não adivinho qual usar",
                usuarioId, ativos.size());
            return Optional.empty();
        }
        return ativos.stream()
            .filter(c -> cartaoCorrespondeBancoExtraido(c, banco, token))
            .findFirst();
    }

    private static boolean cartaoCorrespondeBancoExtraido(CartaoCredito c, String bancoRef, String tokenNorm) {
        if (BancoBrasilCatalog.bancosCorrespondem(c.getBanco(), bancoRef)
            || BancoBrasilCatalog.bancosCorrespondem(c.getNome(), bancoRef)) {
            return true;
        }
        String nb = norm(c.getBanco());
        String nn = norm(c.getNome());
        return nb.contains(tokenNorm) || tokenNorm.contains(nb)
            || nn.contains(tokenNorm) || tokenNorm.contains(nn);
    }

    private boolean jaExiste(Long usuarioId, ImportacaoFaturaItemDTO item) {
        return !buscarExistentes(usuarioId, item).isEmpty();
    }

    private List<com.consumoesperto.model.Transacao> buscarExistentes(Long usuarioId, ImportacaoFaturaItemDTO item) {
        if (item.getData() == null || item.getValor() == null || item.getDescricao() == null) {
            return List.of();
        }
        LocalDateTime dt = item.getData().atStartOfDay();
        return transacaoRepository.findByUsuarioIdAndDescricaoAndDataTransacaoAndValor(
            usuarioId, item.getDescricao(), dt, item.getValor());
    }

    private int conciliarExistentesComFatura(List<com.consumoesperto.model.Transacao> existentes, Fatura fatura, ImportacaoFaturaItemDTO item) {
        int count = 0;
        for (com.consumoesperto.model.Transacao tx : existentes) {
            if (tx.getFatura() == null || tx.getFatura().getId() == null || !tx.getFatura().getId().equals(fatura.getId())) {
                tx.setFatura(fatura);
                count++;
            }
            if (item.getParcelaAtual() != null && item.getTotalParcelas() != null && item.getTotalParcelas() > 1) {
                tx.setParcelaAtual(item.getParcelaAtual());
                tx.setTotalParcelas(item.getTotalParcelas());
                tx.setGrupoParcelaId(parcelGroupId(fatura, item));
            }
        }
        if (count > 0) {
            transacaoRepository.saveAll(existentes);
        }
        return count;
    }

    private int criarParcelasFuturasSeNecessario(
        Long usuarioId,
        ImportacaoFaturaCartao imp,
        Fatura faturaAtual,
        ImportacaoFaturaItemDTO item,
        Set<Long> faturasParaSincronizar
    ) {
        if (item.getParcelaAtual() == null || item.getTotalParcelas() == null || item.getTotalParcelas() <= 1) {
            return 0;
        }
        if (item.getParcelaAtual() >= item.getTotalParcelas()) {
            return 0;
        }
        String grupo = parcelGroupId(imp, item);
        List<com.consumoesperto.model.Transacao> existentesGrupo =
            transacaoRepository.findByUsuarioIdAndGrupoParcelaIdOrderByParcelaAtualAsc(usuarioId, grupo);
        int criadas = 0;
        for (int parcela = item.getParcelaAtual() + 1; parcela <= item.getTotalParcelas(); parcela++) {
            final int parcelaAlvo = parcela;
            boolean jaExiste = existentesGrupo.stream()
                .anyMatch(t -> t.getParcelaAtual() != null && t.getParcelaAtual() == parcelaAlvo);
            if (jaExiste) {
                continue;
            }
            LocalDate vencimento = vencimentoParcelaFutura(faturaAtual, imp.getCartaoCredito(), parcela - item.getParcelaAtual());
            Fatura faturaFutura = faturaService.obterOuCriarFaturaParaVencimentoAlvo(usuarioId, imp.getCartaoCredito(), vencimento);
            TransacaoDTO dto = new TransacaoDTO();
            dto.setDescricao(removerMarcadorParcela(item.getDescricao()) + " (" + parcela + "/" + item.getTotalParcelas() + ")");
            dto.setValor(item.getValor());
            dto.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
            dto.setDataTransacao((item.getData() != null ? item.getData() : LocalDate.now()).plusMonths(parcela - item.getParcelaAtual()).atStartOfDay());
            dto.setFaturaId(faturaFutura.getId());
            dto.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
            dto.setGrupoParcelaId(grupo);
            dto.setParcelaAtual(parcela);
            dto.setTotalParcelas(item.getTotalParcelas());
            transacaoService.criarTransacao(dto, usuarioId, false, false);
            faturasParaSincronizar.add(faturaFutura.getId());
            criadas++;
        }
        return criadas;
    }

    private static LocalDate vencimentoParcelaFutura(Fatura faturaAtual, CartaoCredito cartao, int mesesDepois) {
        LocalDate base = faturaAtual.getDataVencimento() != null
            ? faturaAtual.getDataVencimento().toLocalDate()
            : LocalDate.now();
        YearMonth ym = YearMonth.from(base).plusMonths(mesesDepois);
        int dia = cartao != null && cartao.getDiaVencimento() != null
            ? Math.max(1, Math.min(31, cartao.getDiaVencimento()))
            : base.getDayOfMonth();
        return ym.atDay(Math.min(dia, ym.lengthOfMonth()));
    }

    private Fatura resolverFatura(ImportacaoFaturaCartao imp) {
        CartaoCredito cartao = imp.getCartaoCredito();
        if (cartao == null || cartao.getId() == null) {
            throw new IllegalArgumentException(
                "Esta importação não está vinculada a um cartão válido. Cadastre o cartão no app e reimporte o PDF.");
        }
        YearMonth ymVencimento = imp.getDataVencimento() != null
            ? YearMonth.from(imp.getDataVencimento())
            : (imp.getDataFechamento() != null ? YearMonth.from(imp.getDataFechamento()) : YearMonth.now());
        String numeroCanonico = ymVencimento + "-" + cartao.getId();

        Optional<Fatura> porNumero = faturaRepository.findByCartaoCreditoIdAndNumeroFatura(cartao.getId(), numeroCanonico);
        if (porNumero.isPresent()) {
            return atualizarFaturaParaImportacao(porNumero.get(), imp, numeroCanonico);
        }

        Optional<Fatura> existenteMes = faturaRepository.findByCartaoCreditoIdOrderByDataVencimentoAsc(cartao.getId()).stream()
            .filter(f -> f.getDataVencimento() != null && YearMonth.from(f.getDataVencimento()).equals(ymVencimento))
            .filter(f -> f.getStatusFatura() != Fatura.StatusFatura.PAGA
                && f.getStatusFatura() != Fatura.StatusFatura.CANCELADA)
            .min(java.util.Comparator.comparingInt(f -> prioridadeFaturaParaImportacao(f.getStatusFatura())));
        if (existenteMes.isPresent()) {
            log.info("Importação PDF reutiliza fatura existente id={} status={} cartaoId={} mes={}",
                existenteMes.get().getId(), existenteMes.get().getStatusFatura(), cartao.getId(), ymVencimento);
            return atualizarFaturaParaImportacao(existenteMes.get(), imp, numeroCanonico);
        }

        Fatura f = new Fatura();
        f.setCartaoCredito(cartao);
        f.setUsuario(imp.getUsuario());
        f.setNumeroFatura(numeroCanonico);
        f.setValorTotal(nz(imp.getValorTotal()));
        f.setValorFatura(nz(imp.getValorTotal()));
        f.setValorMinimo(nz(imp.getPagamentoMinimo()));
        f.setValorPago(BigDecimal.ZERO);
        f.setPaga(false);
        f.setStatusFatura(Fatura.StatusFatura.ABERTA);
        f.setDataVencimento(imp.getDataVencimento() != null ? imp.getDataVencimento() : LocalDateTime.now().plusDays(10));
        f.setDataFechamento(imp.getDataFechamento() != null ? imp.getDataFechamento() : LocalDateTime.now());
        return faturaRepository.save(f);
    }

    private static int prioridadeFaturaParaImportacao(Fatura.StatusFatura status) {
        if (status == Fatura.StatusFatura.PREVISTA) {
            return 0;
        }
        if (status == Fatura.StatusFatura.ABERTA) {
            return 1;
        }
        if (status == Fatura.StatusFatura.PARCIAL) {
            return 2;
        }
        if (status == Fatura.StatusFatura.VENCIDA) {
            return 3;
        }
        return 9;
    }

    private Fatura atualizarFaturaParaImportacao(Fatura f, ImportacaoFaturaCartao imp, String numeroCanonico) {
        f.setNumeroFatura(numeroCanonico);
        f.setStatusFatura(Fatura.StatusFatura.ABERTA);
        f.setPaga(false);
        f.setValorTotal(nz(imp.getValorTotal()));
        f.setValorFatura(nz(imp.getValorTotal()));
        f.setValorMinimo(nz(imp.getPagamentoMinimo()));
        if (imp.getDataVencimento() != null) {
            f.setDataVencimento(imp.getDataVencimento());
        }
        if (imp.getDataFechamento() != null) {
            f.setDataFechamento(imp.getDataFechamento());
        }
        return faturaRepository.save(f);
    }

    /**
     * Após importar o PDF real, remove PREVISTAS duplicadas do mesmo mês/cartão
     * (projeções de parcelas que ficaram obsoletas).
     */
    private void descartarPrevistasObsoletasDoMes(Long cartaoId, Fatura faturaMantida) {
        if (cartaoId == null || faturaMantida == null || faturaMantida.getDataVencimento() == null) {
            return;
        }
        YearMonth ym = YearMonth.from(faturaMantida.getDataVencimento());
        for (Fatura f : faturaRepository.findByCartaoCreditoIdOrderByDataVencimentoAsc(cartaoId)) {
            if (f.getId() == null || f.getId().equals(faturaMantida.getId())) {
                continue;
            }
            if (f.getStatusFatura() != Fatura.StatusFatura.PREVISTA) {
                continue;
            }
            if (f.getDataVencimento() == null || !YearMonth.from(f.getDataVencimento()).equals(ym)) {
                continue;
            }
            List<com.consumoesperto.model.Transacao> txs =
                transacaoRepository.findByFaturaIdOrderByDataTransacaoAscIdAsc(f.getId());
            if (!txs.isEmpty()) {
                transacaoRepository.deleteAll(txs);
            }
            faturaRepository.delete(f);
            log.info("Removida PREVISTA obsoleta id={} após importação PDF (cartaoId={} mes={})",
                f.getId(), cartaoId, ym);
        }
    }

    private List<String> auditarGastosFantasmas(Long usuarioId, Long cartaoId, List<ImportacaoFaturaItemDTO> itens) {
        List<ImportacaoFaturaCartao> anteriores = importacaoRepository.findByUsuarioIdAndCartaoCreditoIdOrderByDataVencimentoDesc(usuarioId, cartaoId);
        if (anteriores.isEmpty()) {
            return List.of();
        }
        List<ImportacaoFaturaItemDTO> prev = readItens(anteriores.get(0).getItensJson());
        List<String> out = new ArrayList<>();
        Map<String, BigDecimal> somaMesAnterior = somaPorChaveEstabelecimento(prev);
        Map<String, BigDecimal> somaMesAtual = somaPorChaveEstabelecimento(itens);
        Map<String, String> rotuloPorChave = rotuloExibicaoPorChave(itens);
        for (Map.Entry<String, BigDecimal> e : somaMesAtual.entrySet()) {
            String chave = e.getKey();
            BigDecimal totalAtual = e.getValue();
            BigDecimal totalAnterior = somaMesAnterior.get(chave);
            if (totalAnterior == null || totalAnterior.compareTo(BigDecimal.ZERO) <= 0
                || totalAtual == null || totalAtual.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (totalAtual.compareTo(totalAnterior.multiply(BigDecimal.valueOf(1.15))) > 0) {
                String rotulo = rotuloPorChave.getOrDefault(chave, chave);
                if (FinanceInsightProfileClassifier.perfilPorDescricao(rotulo)
                    == FinanceInsightProfileClassifier.Perfil.ASSINATURA_SERVICO) {
                    out.add("⚠️ Senhor, detectei uma elevação na assinatura de *" + rotulo + "*. "
                        + "O valor saltou de *R$ " + formatBrl(totalAnterior).trim() + "* para *R$ "
                        + formatBrl(totalAtual).trim() + "*. Deseja *cancelar* este protocolo?");
                }
                // Hábito (combustível/mercado/restaurante): alerta + meta de teto vêm do ContencaoJarvisService (média 3 meses).
            }
        }
        BigDecimal juros = itens.stream()
            .filter(i -> norm(i.getDescricao()).matches(".*(juros|rotativo|multa|encargo).*"))
            .map(i -> i.getValor() != null ? i.getValor() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (juros.compareTo(BigDecimal.ZERO) > 0) {
            out.add("Você gastou R$ " + juros.setScale(2, RoundingMode.HALF_UP)
                + " em juros/encargos nesta fatura. Cuidado com rotativo e atraso.");
        }
        return out;
    }

    private void aplicarValidacaoChecksumFatura(
        BigDecimal valorTotal,
        List<ImportacaoFaturaItemDTO> itens,
        JsonNode extracted,
        List<String> auditorias
    ) {
        if (valorTotal == null || valorTotal.compareTo(BigDecimal.ZERO) <= 0 || itens == null || itens.isEmpty()) {
            return;
        }
        BigDecimal soma = itens.stream()
            .map(i -> i.getValor() != null ? i.getValor() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal diferenca = soma.subtract(valorTotal).abs().setScale(2, RoundingMode.HALF_UP);
        BigDecimal tol = toleranciaChecksum(valorTotal);
        if (diferenca.compareTo(tol) <= 0) {
            return;
        }

        Optional<BigDecimal> anuidadeDeclarada =
            primeiraNaoVazia(extrairSomaAnuidadeDeclarada(extracted), somarLancamentosComDescricaoContendo(itens, "anuidade"));
        if (anuidadeDeclarada.isPresent()
            && moedasQuaseIguais(diferenca, anuidadeDeclarada.get(), new BigDecimal("0.04"))) {
            BigDecimal ano = anuidadeDeclarada.get().setScale(2, RoundingMode.HALF_UP);
            auditorias.add(
                "Senhor, os cálculos batem perfeitamente após incluirmos a taxa de Anuidade de R$ "
                    + formatBrl(ano).trim() + "."
            );
            auditorias.add(META_ANUIDADE_CKSM_PREFIX + ano.stripTrailingZeros().toPlainString());
            return;
        }

        divergenciaChecksumMensagem(valorTotal, itens, soma, diferenca).ifPresent(auditorias::add);
    }

    /** Na confirmação o JSON já não existe; usa {@link #META_ANUIDADE_CKSM_PREFIX} persistido quando existir. */
    private Optional<String> validarSomaParaConfirmacao(
        BigDecimal valorTotal,
        List<ImportacaoFaturaItemDTO> itens,
        List<String> auditoriasPersistidas
    ) {
        return resolverDivergenciaChecksum(valorTotal, itens,
            somarLancamentosComDescricaoContendo(itens, "anuidade"),
            Optional.empty(),
            auditoriasPersistidas);
    }

    private Optional<String> resolverDivergenciaChecksum(
        BigDecimal valorTotal,
        List<ImportacaoFaturaItemDTO> itens,
        Optional<BigDecimal> anuidadeDeJsonOuLancamento,
        Optional<BigDecimal> somaOpcionalJaCalculada,
        List<String> auditoriasPersistidas
    ) {
        if (valorTotal == null || valorTotal.compareTo(BigDecimal.ZERO) <= 0 || itens == null || itens.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal soma = somaOpcionalJaCalculada.orElseGet(() ->
            itens.stream()
                .map(i -> i.getValor() != null ? i.getValor() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP));
        BigDecimal diferenca = soma.subtract(valorTotal).abs().setScale(2, RoundingMode.HALF_UP);
        BigDecimal tol = toleranciaChecksum(valorTotal);

        if (diferenca.compareTo(tol) <= 0) {
            return Optional.empty();
        }

        Optional<BigDecimal> anuidadeRef = primeiraNaoVazia(
            anuidadeDeJsonOuLancamento,
            lerValorAnuidadeMetaAuditorias(auditoriasPersistidas)
        );

        if (anuidadeRef.isPresent()
            && moedasQuaseIguais(diferenca, anuidadeRef.get(), new BigDecimal("0.04"))) {
            return Optional.empty();
        }

        return divergenciaChecksumMensagem(valorTotal, itens, soma, diferenca);
    }

    private static Optional<String> divergenciaChecksumMensagem(
        BigDecimal valorTotal,
        List<ImportacaoFaturaItemDTO> itens,
        BigDecimal soma,
        BigDecimal diferenca
    ) {
        String suspeito = itens.stream()
            .filter(i -> i.getValor() != null && moedasQuaseIguais(i.getValor().abs(), diferenca, new BigDecimal("0.04")))
            .findFirst()
            .map(i -> " Possível lançamento excedente: " + i.getDescricao() + " (R$ " + i.getValor().setScale(2, RoundingMode.HALF_UP) + ").")
            .orElse("");
        return Optional.of("Atenção: a soma dos lançamentos extraídos (" + soma
            + ") não bate com o total da fatura (" + valorTotal
            + "). Diferença: R$ " + diferenca + "." + suspeito + " Revise antes de confirmar.");
    }

    private static Optional<BigDecimal> primeiraNaoVazia(Optional<BigDecimal> a, Optional<BigDecimal> b) {
        if (a.isPresent()) {
            return a;
        }
        return b;
    }

    private static Optional<BigDecimal> somarLancamentosComDescricaoContendo(List<ImportacaoFaturaItemDTO> itens, String token) {
        if (itens == null || token == null || token.isBlank()) {
            return Optional.empty();
        }
        BigDecimal sum = BigDecimal.ZERO;
        String tkn = norm(token);
        for (ImportacaoFaturaItemDTO item : itens) {
            if (item != null && item.getValor() != null && norm(item.getDescricao()).contains(tkn)) {
                sum = sum.add(item.getValor());
            }
        }
        return sum.compareTo(BigDecimal.ZERO) > 0 ? Optional.of(sum.setScale(2, RoundingMode.HALF_UP)) : Optional.empty();
    }

    /** Anuidade somada apenas de taxas marcadas pela IA ({@code tipo}=ANUIDADE). */
    private static Optional<BigDecimal> extrairSomaAnuidadeDeclarada(JsonNode extracted) {
        if (extracted == null || extracted.isMissingNode()) {
            return Optional.empty();
        }
        JsonNode arr = extracted.path("taxasForaDaTabelaPrincipal");
        if (!arr.isArray()) {
            return Optional.empty();
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode n : arr) {
            if (!"ANUIDADE".equalsIgnoreCase(n.path("tipo").asText(""))) {
                continue;
            }
            BigDecimal v = readMoney(n.path("valor"));
            sum = sum.add(v);
        }
        return sum.compareTo(BigDecimal.ZERO) > 0 ? Optional.of(sum.setScale(2, RoundingMode.HALF_UP)) : Optional.empty();
    }

    private static Optional<BigDecimal> lerValorAnuidadeMetaAuditorias(List<String> auditorias) {
        if (auditorias == null) {
            return Optional.empty();
        }
        for (String a : auditorias) {
            if (a != null && a.startsWith(META_ANUIDADE_CKSM_PREFIX)) {
                String raw = a.substring(META_ANUIDADE_CKSM_PREFIX.length()).trim().replace(",", ".");
                try {
                    return Optional.of(new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP));
                } catch (Exception ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private static BigDecimal toleranciaChecksum(BigDecimal valorTotal) {
        return valorTotal.multiply(new BigDecimal("0.02"))
            .setScale(2, RoundingMode.HALF_UP)
            .max(new BigDecimal("5.00"))
            .min(new BigDecimal("120.00"));
    }

    private static boolean moedasQuaseIguais(BigDecimal a, BigDecimal b, BigDecimal eps) {
        if (a == null || b == null) {
            return false;
        }
        return a.subtract(b).abs().compareTo(eps) <= 0;
    }

    private static String formatBrl(BigDecimal v) {
        if (v == null) {
            return " —";
        }
        return new java.text.DecimalFormat("#,##0.00", new java.text.DecimalFormatSymbols(new Locale("pt", "BR"))).format(v);
    }

    private List<ImportacaoFaturaItemDTO> itensSelecionados(List<ImportacaoFaturaItemDTO> itens, Set<Integer> indices) {
        if (itens == null || itens.isEmpty()) {
            return List.of();
        }
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        for (int i = 0; i < itens.size(); i++) {
            ImportacaoFaturaItemDTO item = itens.get(i);
            boolean selecionado = indices == null ? item.isSelecionado() : indices.contains(i);
            if (selecionado && item.isNovo()) {
                out.add(item);
            }
        }
        return out;
    }

    private List<ImportacaoFaturaItemDTO> parseItens(JsonNode arr) {
        List<ImportacaoFaturaItemDTO> candidatos = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return candidatos;
        }
        boolean existeItemComData = false;
        for (JsonNode n : arr) {
            ImportacaoFaturaItemDTO item = new ImportacaoFaturaItemDTO();
            item.setData(parseDate(n.path("data").asText("")));
            item.setDescricao(n.path("descricao").asText("Lançamento da fatura").trim());
            item.setValor(readMoney(n.path("valor")));
            item.setParcelaAtual(readPositiveInt(n.path("parcelaAtual")));
            item.setTotalParcelas(readPositiveInt(n.path("totalParcelas")));
            aplicarParcelamentoDaDescricao(item);
            if (item.getValor() == null || item.getValor().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            // Pagamentos recebidos, estornos e saldo de fatura anterior não são despesas novas:
            // não devem virar lançamento, nem entrar no checksum (senão a soma estoura o total da fatura).
            if (pareceCreditoOuAjusteFatura(item.getDescricao())) {
                continue;
            }
            // Subtotais/cabeçalhos de seção (ex.: Nubank "Total de compras", "Pagamentos e Financiamentos").
            if (pareceCabecalhoOuSubtotal(item.getDescricao())) {
                log.info("Ignorando linha de subtotal/cabeçalho na fatura: '{}' = {}", item.getDescricao(), item.getValor());
                continue;
            }
            // Rodapé, SAC/telefone, tabela CET e simulações de parcelamento (ex.: Nubank "SAC 4020 0182" + 403,21%).
            if (pareceRuidoRodapeOuSimulacao(item.getDescricao())) {
                log.info("Ignorando linha de rodapé/simulação na fatura: '{}' = {}", item.getDescricao(), item.getValor());
                continue;
            }
            if (item.getData() != null) {
                existeItemComData = true;
            }
            candidatos.add(item);
        }
        // Em faturas de cartão todo lançamento real tem data; linhas sem data costumam ser
        // subtotais de portador (ex.: Nubank "Bruce W M Silva R$ 2.425,51"), que inflam a soma.
        // Só descartamos as sem data quando há pelo menos um lançamento datado (evita zerar
        // faturas em que a IA não conseguiu ler nenhuma data).
        List<ImportacaoFaturaItemDTO> out = new ArrayList<>();
        for (ImportacaoFaturaItemDTO item : candidatos) {
            if (existeItemComData && item.getData() == null) {
                log.info("Ignorando lançamento sem data (provável subtotal de portador): '{}' = {}",
                    item.getDescricao(), item.getValor());
                continue;
            }
            out.add(item);
        }
        return out;
    }

    /**
     * Linhas que são subtotais ou cabeçalhos de seção (não são compras): aparecem em faturas
     * Nubank/outras como agregadores que inflam a soma se contados como lançamento.
     */
    private static boolean pareceCabecalhoOuSubtotal(String descricao) {
        String n = norm(descricao);
        if (n.isBlank()) {
            return false;
        }
        return n.contains("pagamentos e financiamentos")
            || n.contains("total de compras")
            || n.contains("total dos lancamentos")
            || n.contains("total da fatura")
            || n.contains("resumo da fatura")
            || n.contains("transacoes de")
            || n.contains("limites disponiveis")
            || n.contains("proximas faturas")
            || n.contains("saldo em aberto")
            || n.equals("total a pagar")
            || n.contains("resumo da fatura")
            || n.contains("consumos de")
            || n.contains("tarifas e encargos")
            || n.contains("total da fatura de")
            || n.contains("lancamentos futuros")
            || n.contains("opcoes de pagamento")
            || n.contains("seus parcelamentos de fatura")
            || n.contains("fatura parcelada")
            || n.contains("compras parceladas")
            || n.contains("detalhes de consumo")
            || n.contains("movimentacoes na fatura")
            || n.contains("valor total de juros");
    }

    /**
     * Rodapé institucional, telefones de atendimento, tabelas de CET/juros e simulações
     * de parcelamento — comuns em faturas Nubank e frequentemente lidos como lançamento pela IA.
     */
    private static boolean pareceRuidoRodapeOuSimulacao(String descricao) {
        String n = norm(descricao);
        if (n.isBlank()) {
            return false;
        }
        if (n.contains("sac ")
            || n.startsWith("sac")
            || n.contains("ouvidoria")
            || n.contains("0800")
            || n.contains("4020 0182")
            || n.contains("capitais e regioes")
            || n.contains("demais localidades")
            || n.contains("encargos e custo")
            || n.contains("custo efetivo total")
            || n.contains("cet ")
            || n.contains("juros rotativo")
            || n.contains("juros de parcelamento")
            || n.contains("juros e mora")
            || n.contains("nu pagamentos")
            || n.contains("cnpj")
            || n.contains("parcelar em")
            || n.contains("alternativas de pagamento")
            || n.contains("pagamento minimo de")
            || n.contains("saldo em aberto da proxima")
            || n.contains("limite adicional")
            || n.contains("saque no credito")
            || n.contains("saque internacional")
            || n.contains("pix no credito")
            || n.contains("pagamentos de boleto no credito")
            || n.contains("fale com a gente")
            || n.contains("canal de libras")
            || n.contains("pague sua fatura pelo app")
            || n.contains("parcele a fatura do seu cartao")) {
            return true;
        }
        // Simulação Mercado Pago / Inter: "1 + [9]x R$ 120,58" ou "Até 1 + 9x"
        if (n.matches(".*\\d\\s*\\+\\s*\\[?\\d+\\]?x.*") || n.contains("ate 1 +")) {
            return true;
        }
        // Descrição curta só com padrão de telefone (ex.: "sac 4020 0182").
        String digitos = n.replaceAll("[^0-9]", "");
        return n.length() <= 48
            && digitos.length() >= 8
            && (n.contains("sac") || n.contains("tel") || n.startsWith("0800") || n.matches(".*\\d{4}\\s+\\d{4}.*"));
    }

    /**
     * Linhas de crédito/ajuste que aparecem na lista de lançamentos mas não são compras:
     * pagamento recebido, estorno, reembolso, devolução e saldo remanescente da fatura anterior.
     */
    private static boolean pareceCreditoOuAjusteFatura(String descricao) {
        String n = norm(descricao);
        if (n.isBlank()) {
            return false;
        }
        return n.startsWith("pagamento")
            || n.contains("pagamento recebido")
            || n.contains("pagamento efetuado")
            || n.contains("pagamento de fatura")
            || n.contains("pagamento em")
            || n.contains("pgto")
            || n.contains("estorno")
            || n.contains("reembolso")
            || n.contains("devolucao")
            || n.contains("saldo restante da fatura anterior")
            || n.contains("saldo da fatura anterior")
            || n.contains("saldo fatura anterior")
            || n.contains("credito por parcelamento")
            || n.contains("pagamento da fatura de")
            || n.contains("pagamentos e creditos devolvidos")
            || n.contains("pagamento recebido");
    }

    private List<ImportacaoFaturaItemDTO> readItens(String json) {
        try {
            return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, ITEM_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> readAuditorias(String json) {
        try {
            return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, STRING_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar importação", e);
        }
    }

    private static LocalDate parseDate(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime parseDateTime(String raw) {
        LocalDate d = parseDate(raw);
        return d != null ? d.atStartOfDay() : null;
    }

    private static BigDecimal readMoney(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        try {
            if (n.isNumber()) {
                return BigDecimal.valueOf(n.asDouble()).setScale(2, RoundingMode.HALF_UP);
            }
            String t = n.asText("").replace("R$", "").trim();
            if (t.contains(",")) {
                t = t.replace(".", "").replace(",", ".");
            }
            return new BigDecimal(t).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private static Integer readPositiveInt(JsonNode n) {
        try {
            if (n == null || n.isMissingNode() || n.isNull()) {
                return null;
            }
            int v = n.isNumber()
                ? n.asInt(0)
                : Integer.parseInt(n.asText("").replaceAll("[^0-9]", ""));
            return v > 0 ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void aplicarParcelamentoDaDescricao(ImportacaoFaturaItemDTO item) {
        if (item == null || item.getDescricao() == null || item.getDescricao().isBlank()) {
            return;
        }
        Integer atual = item.getParcelaAtual();
        Integer total = item.getTotalParcelas();
        if (atual != null && total != null && total > 1) {
            return;
        }
        String descricao = item.getDescricao();
        java.util.regex.Matcher slash = java.util.regex.Pattern
            .compile("(?i)(?:parc(?:ela)?\\.?\\s*)?(\\d{1,2})\\s*/\\s*(\\d{1,2})")
            .matcher(descricao);
        if (slash.find()) {
            preencherParcelas(item, slash.group(1), slash.group(2));
            return;
        }
        java.util.regex.Matcher de = java.util.regex.Pattern
            .compile("(?i)(?:parc(?:ela)?\\.?\\s*)?(\\d{1,2})\\s*(?:de|DE)\\s*(\\d{1,2})")
            .matcher(descricao);
        if (de.find()) {
            preencherParcelas(item, de.group(1), de.group(2));
        }
    }

    private static void preencherParcelas(ImportacaoFaturaItemDTO item, String atualRaw, String totalRaw) {
        try {
            int atual = Integer.parseInt(atualRaw);
            int total = Integer.parseInt(totalRaw);
            if (atual >= 1 && total > 1 && atual <= total) {
                item.setParcelaAtual(atual);
                item.setTotalParcelas(total);
            }
        } catch (Exception ignored) {
            // Mantém os campos vazios se a descrição tiver um padrão ambíguo.
        }
    }

    /** Agrupa linhas da fatura por “estabelecimento” para evitar N×M alertas no WhatsApp. */
    private static String chaveEstabelecimentoFatura(String descricao) {
        String n = norm(removerMarcadorParcela(descricao));
        if (n.isBlank()) {
            return "";
        }
        int cut = 48;
        return n.length() <= cut ? n : n.substring(0, cut).trim();
    }

    private static Map<String, BigDecimal> somaPorChaveEstabelecimento(List<ImportacaoFaturaItemDTO> linhas) {
        Map<String, BigDecimal> acc = new HashMap<>();
        if (linhas == null) {
            return acc;
        }
        for (ImportacaoFaturaItemDTO it : linhas) {
            if (it == null || it.getValor() == null) {
                continue;
            }
            String k = chaveEstabelecimentoFatura(it.getDescricao());
            if (k.isBlank()) {
                continue;
            }
            acc.merge(k, it.getValor(), BigDecimal::add);
        }
        return acc;
    }

    private static Map<String, String> rotuloExibicaoPorChave(List<ImportacaoFaturaItemDTO> linhas) {
        Map<String, String> map = new HashMap<>();
        if (linhas == null) {
            return map;
        }
        for (ImportacaoFaturaItemDTO it : linhas) {
            if (it == null || it.getDescricao() == null || it.getDescricao().isBlank()) {
                continue;
            }
            String k = chaveEstabelecimentoFatura(it.getDescricao());
            if (!k.isBlank()) {
                map.putIfAbsent(k, it.getDescricao().trim());
            }
        }
        return map;
    }

    private static String norm(String raw) {
        if (raw == null) {
            return "";
        }
        return java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null ? b : "");
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static String parcelGroupId(ImportacaoFaturaCartao imp, ImportacaoFaturaItemDTO item) {
        String descricaoBase = removerMarcadorParcela(item.getDescricao());
        String key = imp.getUsuario().getId() + "|" + imp.getCartaoCredito().getId() + "|" + norm(descricaoBase) + "|" + item.getTotalParcelas();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String parcelGroupId(Fatura fatura, ImportacaoFaturaItemDTO item) {
        String descricaoBase = removerMarcadorParcela(item.getDescricao());
        String key = fatura.getUsuario().getId() + "|" + fatura.getCartaoCredito().getId() + "|" + norm(descricaoBase) + "|" + item.getTotalParcelas();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
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
}
