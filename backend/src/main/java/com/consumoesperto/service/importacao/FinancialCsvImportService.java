package com.consumoesperto.service.importacao;

import com.consumoesperto.dto.ConfirmarImportacaoFaturaRequest;
import com.consumoesperto.dto.EscolhaRecursoImportacaoRequest;
import com.consumoesperto.dto.ImportacaoConfirmacaoDTO;
import com.consumoesperto.dto.ImportacaoFaturaDTO;
import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.dto.ImportedFinancialRow;
import com.consumoesperto.dto.PagamentoFaturaRequest;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.mobilecapture.service.MerchantCategoryRuleService;
import com.consumoesperto.mobilecapture.service.MerchantNormalizationService;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.FinancialImportFileType;
import com.consumoesperto.model.ImportacaoFaturaCartao;
import com.consumoesperto.model.ImportacaoItemStatus;
import com.consumoesperto.model.ImportedRowKind;
import com.consumoesperto.model.OrigemTransacao;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.ImportacaoFaturaCartaoRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.ContaBancariaService;
import com.consumoesperto.service.FaturaConciliacaoService;
import com.consumoesperto.service.FaturaService;
import com.consumoesperto.service.SaldoService;
import com.consumoesperto.service.TransacaoService;
import com.consumoesperto.service.importacao.csv.CsvParseResult;
import com.consumoesperto.service.importacao.csv.FinancialCsvParserRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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

/**
 * Importação de extrato CSV no fluxo de {@code /api/importacoes/faturas}.
 * <p>
 * Decisões de domínio (não inventar regra paralela):
 * <ul>
 *   <li>Fatura ausente: {@link FaturaService#resolverFaturaParaCompra} cria {@code PREVISTA}.</li>
 *   <li>Competência: a mesma regra da compra manual de cartão (fechamento derivado do vencimento).</li>
 *   <li>Total da fatura: {@link FaturaService#sincronizarValorFaturaComTransacoes} (soma das despesas).</li>
 *   <li>Pagamento de fatura no extrato da CONTA: {@link FaturaConciliacaoService#pagarFatura}.</li>
 *   <li>Pagamento recebido no extrato do CARTÃO: ignorado como compra (não aumenta a fatura).</li>
 *   <li>Estorno no cartão: despesa negativa na mesma competência (o total é a soma).</li>
 *   <li>Parcela {@code n/N} no CSV: grava só aquela parcela; não gera as futuras.</li>
 *   <li>Confirmação parcial: igual ao PDF — só linhas selecionadas; o lote é atômico.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialCsvImportService {

    private static final TypeReference<List<ImportacaoFaturaItemDTO>> ITEM_LIST = new TypeReference<>() {};

    private final FinancialCsvParserRegistry parserRegistry;
    private final ImportacaoFaturaCartaoRepository importacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;
    private final ContaBancariaService contaBancariaService;
    private final com.consumoesperto.repository.ContaBancariaRepository contaBancariaRepository;
    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;
    private final TransacaoService transacaoService;
    private final FaturaService faturaService;
    private final FaturaConciliacaoService faturaConciliacaoService;
    private final SaldoService saldoService;
    private final FinancialImportDeduplicationService deduplicationService;
    private final MerchantNormalizationService merchantNormalizationService;
    private final MerchantCategoryRuleService categoryRuleService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ImportacaoFaturaDTO processar(
        Long usuarioId,
        String fileName,
        byte[] bytes,
        FinancialImportDetection detection,
        String tipoRecursoHint,
        Long contaBancariaId,
        Long cartaoCreditoId
    ) {
        Instant start = Instant.now();
        CsvParseResult parsed = parserRegistry.parse(bytes, fileName, detection.type());
        if (parsed.rows().isEmpty()) {
            throw new IllegalArgumentException("O CSV não contém lançamentos.");
        }
        if (parsed.valid() == 0) {
            throw new IllegalArgumentException("Nenhuma linha válida no CSV.");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        FinancialImportFileType tipo = resolverTipoEfetivo(detection.type(), tipoRecursoHint);
        ContaBancaria conta = null;
        CartaoCredito cartao = null;
        if (contaBancariaId != null) {
            conta = contaBancariaService.buscarEntidade(contaBancariaId, usuarioId);
            tipo = FinancialImportFileType.BANK_STATEMENT_CSV;
        }
        if (cartaoCreditoId != null) {
            cartao = cartaoDoUsuario(usuarioId, cartaoCreditoId);
            tipo = FinancialImportFileType.CARD_STATEMENT_CSV;
        }
        if (tipo == FinancialImportFileType.CARD_STATEMENT_CSV && cartao == null) {
            cartao = sugerirCartaoUnico(usuarioId, parsed).orElse(null);
        }
        if (tipo == FinancialImportFileType.BANK_STATEMENT_CSV && conta == null) {
            List<ContaBancaria> ativas = contaBancariaRepository
                .findByUsuarioIdAndAtivaTrueOrderByPadraoDescNomeAsc(usuarioId);
            if (ativas.size() == 1) {
                conta = ativas.get(0);
            }
        }

        boolean precisaEscolha = tipo == FinancialImportFileType.NEEDS_REVIEW
            || (tipo == FinancialImportFileType.CARD_STATEMENT_CSV && cartao == null)
            || (tipo == FinancialImportFileType.BANK_STATEMENT_CSV && conta == null
                && contaBancariaId == null);

        List<ImportacaoFaturaItemDTO> itens = toItens(parsed.rows(), tipo);
        enriquecerPreview(usuarioId, itens, tipo, conta, cartao, precisaEscolha);

        ImportacaoFaturaCartao imp = new ImportacaoFaturaCartao();
        imp.setUsuario(usuario);
        imp.setTipoArquivo(tipo);
        imp.setArquivoNome(fileName);
        imp.setPrecisaEscolhaRecurso(precisaEscolha);
        imp.setContaBancaria(conta);
        imp.setCartaoCredito(cartao);
        imp.setBancoCartao(titulo(fileName, tipo, conta, cartao));
        imp.setValorTotal(somaDespesasCard(itens, tipo));
        imp.setPagamentoMinimo(BigDecimal.ZERO);
        imp.setItensJson(writeJson(itens));
        imp.setAuditoriaJson(writeJson(List.of(
            "Extrato CSV: " + parsed.valid() + " válidas, " + parsed.invalid() + " inválidas ("
                + parsed.layout() + ", " + parsed.encoding() + ")."
        )));
        imp.setNovosDetectados((int) itens.stream()
            .filter(i -> ImportacaoItemStatus.NOVO.name().equals(i.getStatusPreview()))
            .count());
        ImportacaoFaturaCartao salvo = importacaoRepository.save(imp);

        log.info("financial_csv_import import_id={} file_type={} user_id={} rows={} parsed={} duplicates={} created={} matched={} review={} failed={} invoice_ids={} duration={}",
            salvo.getId(), tipo, usuarioId, parsed.totalLines(), parsed.valid(),
            countStatus(itens, ImportacaoItemStatus.DUPLICATE), 0,
            countStatus(itens, ImportacaoItemStatus.MATCHED_EXISTING),
            countStatus(itens, ImportacaoItemStatus.NEEDS_REVIEW),
            countStatus(itens, ImportacaoItemStatus.INVALID),
            "", Duration.between(start, Instant.now()).toMillis());
        return toDto(salvo);
    }

    @Transactional
    public ImportacaoFaturaDTO escolherRecurso(Long usuarioId, Long importacaoId, EscolhaRecursoImportacaoRequest request) {
        ImportacaoFaturaCartao imp = importacaoPendente(usuarioId, importacaoId);
        if (!isCsv(imp)) {
            throw new IllegalArgumentException("Esta importação não é um extrato CSV.");
        }
        aplicarRecurso(usuarioId, imp, request.getTipoRecurso(), request.getContaBancariaId(), request.getCartaoCreditoId());
        List<ImportacaoFaturaItemDTO> itens = readItens(imp.getItensJson());
        enriquecerPreview(usuarioId, itens, imp.getTipoArquivo(), imp.getContaBancaria(), imp.getCartaoCredito(), false);
        imp.setPrecisaEscolhaRecurso(false);
        imp.setItensJson(writeJson(itens));
        imp.setNovosDetectados(countStatus(itens, ImportacaoItemStatus.NOVO));
        imp.setValorTotal(somaDespesasCard(itens, imp.getTipoArquivo()));
        return toDto(importacaoRepository.save(imp));
    }

    @Transactional
    public ImportacaoFaturaDTO atualizarItens(
        Long usuarioId,
        Long importacaoId,
        ConfirmarImportacaoFaturaRequest request
    ) {
        ImportacaoFaturaCartao imp = importacaoPendente(usuarioId, importacaoId);
        if (!isCsv(imp)) {
            throw new IllegalArgumentException("Esta importação não é um extrato CSV.");
        }
        List<ImportacaoFaturaItemDTO> itens = readItens(imp.getItensJson());
        aplicarAjustes(usuarioId, itens, request);
        enriquecerPreview(usuarioId, itens, imp.getTipoArquivo(), imp.getContaBancaria(), imp.getCartaoCredito(),
            imp.isPrecisaEscolhaRecurso());
        imp.setItensJson(writeJson(itens));
        imp.setNovosDetectados(countStatus(itens, ImportacaoItemStatus.NOVO));
        return toDto(importacaoRepository.save(imp));
    }

    @Transactional
    public ImportacaoConfirmacaoDTO confirmar(
        Long usuarioId,
        Long importacaoId,
        ConfirmarImportacaoFaturaRequest request
    ) {
        Instant start = Instant.now();
        ImportacaoFaturaCartao imp = importacaoRepository.findByIdAndUsuarioIdForUpdate(importacaoId, usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Importação não encontrada"));
        if (imp.getStatus() != ImportacaoFaturaCartao.Status.PENDENTE) {
            return vazio();
        }
        if (!isCsv(imp)) {
            throw new IllegalArgumentException("Confirmação CSV chamada para importação PDF.");
        }
        if (request != null && (request.getTipoRecurso() != null || request.getContaBancariaId() != null
            || request.getCartaoCreditoId() != null)) {
            aplicarRecurso(usuarioId, imp, request.getTipoRecurso(), request.getContaBancariaId(), request.getCartaoCreditoId());
        }
        if (imp.isPrecisaEscolhaRecurso()
            || imp.getTipoArquivo() == FinancialImportFileType.NEEDS_REVIEW) {
            throw new IllegalArgumentException("Indique se este CSV é de conta bancária ou de cartão antes de confirmar.");
        }
        if (imp.getTipoArquivo() == FinancialImportFileType.CARD_STATEMENT_CSV && imp.getCartaoCredito() == null) {
            throw new IllegalArgumentException("Selecione o cartão deste extrato.");
        }
        if (imp.getTipoArquivo() == FinancialImportFileType.BANK_STATEMENT_CSV && imp.getContaBancaria() == null) {
            throw new IllegalArgumentException("Selecione a conta bancária deste extrato.");
        }

        List<ImportacaoFaturaItemDTO> itens = readItens(imp.getItensJson());
        aplicarAjustes(usuarioId, itens, request);
        enriquecerPreview(usuarioId, itens, imp.getTipoArquivo(), imp.getContaBancaria(), imp.getCartaoCredito(), false);

        Set<Integer> indices = request != null && request.getIndices() != null && !request.getIndices().isEmpty()
            ? new HashSet<>(request.getIndices())
            : null;

        Map<Long, BigDecimal> totaisAntes = new HashMap<>();
        Map<Long, Integer> adicionadasPorFatura = new HashMap<>();
        int criadas = 0;
        int matched = 0;
        int duplicadas = 0;
        int revisao = 0;
        int falhas = 0;
        Set<Long> faturasSync = new HashSet<>();

        for (int i = 0; i < itens.size(); i++) {
            ImportacaoFaturaItemDTO item = itens.get(i);
            boolean selecionado = indices == null ? item.isSelecionado() : indices.contains(i);
            String st = item.getStatusPreview();
            if (ImportacaoItemStatus.INVALID.name().equals(st)
                || ImportacaoItemStatus.IGNORED.name().equals(st)) {
                falhas += ImportacaoItemStatus.INVALID.name().equals(st) ? 1 : 0;
                continue;
            }
            if (ImportacaoItemStatus.NEEDS_REVIEW.name().equals(st)) {
                revisao++;
                continue;
            }
            if (ImportacaoItemStatus.DUPLICATE.name().equals(st)) {
                duplicadas++;
                continue;
            }
            if (ImportacaoItemStatus.MATCHED_EXISTING.name().equals(st)) {
                if (item.getMatchedTransacaoId() != null) {
                    enriquecerExistente(item);
                    matched++;
                }
                continue;
            }
            if (!selecionado || !ImportacaoItemStatus.NOVO.name().equals(st)) {
                continue;
            }
            try {
                        Long faturaId = persistirLinha(usuarioId, imp, item, totaisAntes, adicionadasPorFatura);
                        if (faturaId != null) {
                            faturasSync.add(faturaId);
                        }
                criadas++;
            } catch (DataIntegrityViolationException dup) {
                duplicadas++;
            }
        }

        for (Long faturaId : faturasSync) {
            faturaService.sincronizarValorFaturaComTransacoes(faturaId);
        }
        saldoService.notificarAlteracaoSaldo(usuarioId);

        imp.setItensJson(writeJson(itens));
        imp.setStatus(ImportacaoFaturaCartao.Status.CONFIRMADA);
        imp.setDataConfirmacao(LocalDateTime.now());
        imp.setNovosDetectados(0);
        importacaoRepository.save(imp);

        ImportacaoConfirmacaoDTO out = montarRelatorio(
            criadas, matched, duplicadas, revisao, falhas, itens.size(), faturasSync, totaisAntes, adicionadasPorFatura, usuarioId);
        log.info("financial_csv_import import_id={} file_type={} user_id={} rows={} parsed={} duplicates={} created={} matched={} review={} failed={} invoice_ids={} duration={}",
            importacaoId, imp.getTipoArquivo(), usuarioId, itens.size(), itens.size(), duplicadas, criadas, matched, revisao, falhas,
            faturasSync, Duration.between(start, Instant.now()).toMillis());
        return out;
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
        List<ImportacaoFaturaItemDTO> itens = readItens(imp.getItensJson());
        dto.setItens(itens);
        dto.setAuditorias(readStrings(imp.getAuditoriaJson()));
        dto.setDataCriacao(imp.getDataCriacao());
        ImportacaoCsvPreviewSupport.fillSummary(dto, imp, itens);
        return dto;
    }

    public static boolean isCsv(ImportacaoFaturaCartao imp) {
        FinancialImportFileType t = imp.getTipoArquivo();
        return t == FinancialImportFileType.BANK_STATEMENT_CSV
            || t == FinancialImportFileType.CARD_STATEMENT_CSV
            || t == FinancialImportFileType.NEEDS_REVIEW;
    }

    private Long persistirLinha(
        Long usuarioId,
        ImportacaoFaturaCartao imp,
        ImportacaoFaturaItemDTO item,
        Map<Long, BigDecimal> totaisAntes,
        Map<Long, Integer> adicionadasPorFatura
    ) {
        ImportedRowKind kind = parseKind(item.getTipoLinha());
        boolean card = imp.getTipoArquivo() == FinancialImportFileType.CARD_STATEMENT_CSV;

        if (kind == ImportedRowKind.PAGAMENTO_FATURA) {
            if (card) {
                item.setStatusPreview(ImportacaoItemStatus.IGNORED.name());
                return null;
            }
            return registrarPagamentoConta(usuarioId, imp, item, totaisAntes, adicionadasPorFatura);
        }

        if (kind == ImportedRowKind.ESTORNO && card) {
            return registrarEstornoCartao(usuarioId, imp, item, totaisAntes, adicionadasPorFatura);
        }

        TransacaoDTO dto = new TransacaoDTO();
        dto.setDescricao(descricaoPersistivel(item.getDescricao()));
        dto.setValor(item.getValor() == null ? BigDecimal.ZERO : item.getValor().abs());
        dto.setDataTransacao(item.getData() != null ? item.getData().atStartOfDay() : LocalDateTime.now());
        dto.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
        if (item.getCategoriaId() != null) {
            dto.setCategoriaId(categoriaDoUsuario(usuarioId, item.getCategoriaId()));
        } else {
            sugerirCategoria(usuarioId, item).ifPresent(dto::setCategoriaId);
        }
        if (item.getParcelaAtual() != null && item.getTotalParcelas() != null && item.getTotalParcelas() > 1) {
            dto.setParcelaAtual(item.getParcelaAtual());
            dto.setTotalParcelas(item.getTotalParcelas());
        }

        if (kind == ImportedRowKind.RECEITA
            || (kind == ImportedRowKind.ESTORNO && !card)
            || (kind == ImportedRowKind.TRANSFERENCIA && !card && pareceEntrada(item))) {
            dto.setTipoTransacao(TransacaoDTO.TipoTransacao.RECEITA);
            dto.setContaBancariaId(contaId(imp, item, usuarioId));
        } else if (card && (kind == ImportedRowKind.DESPESA || kind == ImportedRowKind.TRANSFERENCIA)) {
            dto.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
            Long cartaoId = cartaoId(imp, item, usuarioId);
            dto.setCartaoCreditoId(cartaoId);
            FaturaService.PrevisaoCompetenciaFatura prev = faturaService.preverCompetenciaParaCompra(
                usuarioId, cartaoDoUsuario(usuarioId, cartaoId), dto.getDataTransacao());
            if (prev.faturaId() != null) {
                capturarTotalAntes(prev.faturaId(), totaisAntes);
            }
        } else {
            dto.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
            dto.setContaBancariaId(contaId(imp, item, usuarioId));
        }

        TransacaoDTO created = transacaoService.criarTransacao(dto, usuarioId, false, false, false);
        attachMeta(created.getId(), origem(imp), item);
        if (created.getFaturaId() != null) {
            BigDecimal contrib = created.getValor() == null ? BigDecimal.ZERO : created.getValor();
            registrarImpactoFatura(created.getFaturaId(), contrib, totaisAntes, adicionadasPorFatura);
            return created.getFaturaId();
        }
        return null;
    }

    private Long registrarPagamentoConta(
        Long usuarioId,
        ImportacaoFaturaCartao imp,
        ImportacaoFaturaItemDTO item,
        Map<Long, BigDecimal> totaisAntes,
        Map<Long, Integer> adicionadasPorFatura
    ) {
        CartaoCredito cartao = resolverCartaoPagamento(usuarioId, imp, item);
        if (cartao == null) {
            throw new IllegalArgumentException("Não identifiquei o cartão do pagamento de fatura.");
        }
        Fatura fatura = faturaService.resolverFaturaAbertaParaCartao(usuarioId, cartao);
        capturarTotalAntes(fatura.getId(), totaisAntes);
        PagamentoFaturaRequest req = new PagamentoFaturaRequest();
        req.setFaturaId(fatura.getId());
        req.setContaBancariaId(contaId(imp, item, usuarioId));
        req.setValor(item.getValor() == null ? null : item.getValor().abs());
        req.setDataPagamento(item.getData() != null ? item.getData().atStartOfDay() : LocalDateTime.now());
        TransacaoDTO pag = faturaConciliacaoService.pagarFatura(usuarioId, req);
        attachMeta(pag.getId(), OrigemTransacao.CSV_BANK_STATEMENT, item);
        adicionadasPorFatura.merge(fatura.getId(), 0, Integer::sum);
        return fatura.getId();
    }

    private Long registrarEstornoCartao(
        Long usuarioId,
        ImportacaoFaturaCartao imp,
        ImportacaoFaturaItemDTO item,
        Map<Long, BigDecimal> totaisAntes,
        Map<Long, Integer> adicionadasPorFatura
    ) {
        Long cartaoId = cartaoId(imp, item, usuarioId);
        CartaoCredito cartao = cartaoDoUsuario(usuarioId, cartaoId);
        Optional<FinancialImportDeduplicationService.Match> original = deduplicationService.findExisting(
            usuarioId, item, null, cartaoId);
        TransacaoDTO dto = new TransacaoDTO();
        dto.setDescricao(descricaoPersistivel("Estorno " + item.getDescricao()));
        dto.setValor(item.getValor() == null ? BigDecimal.ZERO : item.getValor().abs().negate());
        dto.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
        dto.setDataTransacao(item.getData() != null ? item.getData().atStartOfDay() : LocalDateTime.now());
        dto.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
        dto.setCartaoCreditoId(cartaoId);
        if (original.isPresent() && original.get().transacao().getFatura() != null) {
            dto.setFaturaId(original.get().transacao().getFatura().getId());
            dto.setCartaoCreditoId(null);
        }
        TransacaoDTO created = transacaoService.criarTransacao(dto, usuarioId, false, false, false);
        attachMeta(created.getId(), OrigemTransacao.CSV_CARD_STATEMENT, item);
        if (created.getFaturaId() != null) {
            BigDecimal contrib = created.getValor() == null ? BigDecimal.ZERO : created.getValor();
            registrarImpactoFatura(created.getFaturaId(), contrib, totaisAntes, adicionadasPorFatura);
            return created.getFaturaId();
        }
        Fatura f = faturaService.resolverFaturaParaCompra(usuarioId, cartao, dto.getDataTransacao());
        return f.getId();
    }

    private void enriquecerExistente(ImportacaoFaturaItemDTO item) {
        transacaoService.atualizarMetadadosIngestao(
            item.getMatchedTransacaoId(),
            null,
            item.getExternalId(),
            "CSV",
            item.getMerchant(),
            merchantNormalizationService.normalize(item.getMerchant() != null ? item.getMerchant() : item.getDescricao()),
            item.getFingerprint(),
            null,
            null
        );
        Transacao t = transacaoRepository.findById(item.getMatchedTransacaoId()).orElse(null);
        if (t != null && t.getOrigemTransacao() == null) {
            t.setOrigemTransacao(OrigemTransacao.CSV_CARD_STATEMENT);
            transacaoRepository.save(t);
        }
    }

    private void attachMeta(Long transacaoId, OrigemTransacao origem, ImportacaoFaturaItemDTO item) {
        if (transacaoId == null) {
            return;
        }
        String merchant = item.getMerchant() != null ? item.getMerchant() : item.getDescricao();
        transacaoService.atualizarMetadadosIngestao(
            transacaoId,
            origem,
            item.getExternalId(),
            "CSV",
            merchant,
            merchantNormalizationService.normalize(merchant),
            item.getFingerprint(),
            null,
            null
        );
    }

    private void enriquecerPreview(
        Long usuarioId,
        List<ImportacaoFaturaItemDTO> itens,
        FinancialImportFileType tipo,
        ContaBancaria conta,
        CartaoCredito cartao,
        boolean precisaEscolha
    ) {
        Long contaId = conta != null ? conta.getId() : null;
        Long cartaoId = cartao != null ? cartao.getId() : null;
        boolean card = tipo == FinancialImportFileType.CARD_STATEMENT_CSV;
        for (ImportacaoFaturaItemDTO item : itens) {
            if (item.getInvalidReason() != null && !item.getInvalidReason().isBlank()) {
                item.setStatusPreview(ImportacaoItemStatus.INVALID.name());
                item.setNovo(false);
                item.setSelecionado(false);
                continue;
            }
            ImportedRowKind kind = parseKind(item.getTipoLinha());
            if (card && kind == ImportedRowKind.PAGAMENTO_FATURA) {
                item.setStatusPreview(ImportacaoItemStatus.IGNORED.name());
                item.setNovo(false);
                item.setSelecionado(false);
                item.setFaturaPrevistaLabel("Pagamento (não é compra)");
                continue;
            }
            if (precisaEscolha && tipo == FinancialImportFileType.NEEDS_REVIEW) {
                item.setStatusPreview(ImportacaoItemStatus.NEEDS_REVIEW.name());
                item.setNovo(true);
                item.setSelecionado(false);
                continue;
            }
            Long cta = item.getContaBancariaId() != null ? item.getContaBancariaId() : contaId;
            Long crd = item.getCartaoCreditoId() != null ? item.getCartaoCreditoId() : cartaoId;
            if (card && crd == null) {
                item.setStatusPreview(ImportacaoItemStatus.NEEDS_REVIEW.name());
                item.setNovo(true);
                item.setSelecionado(false);
                continue;
            }
            if (!card && cta == null && kind != ImportedRowKind.DESPESA) {
                // conta será a padrão na efetivação; ok
            }
            item.setFingerprint(deduplicationService.buildCanonicalFingerprint(
                usuarioId, cta, crd, item.getData(), item.getValor(),
                item.getMerchant() != null ? item.getMerchant() : item.getDescricao(),
                item.getParcelaAtual(), item.getTotalParcelas(), item.getTipoLinha()));
            Optional<FinancialImportDeduplicationService.Match> match =
                deduplicationService.findExisting(usuarioId, item, cta, crd);
            if (match.isPresent()) {
                item.setMatchedTransacaoId(match.get().transacao().getId());
                if (match.get().kind() == FinancialImportDeduplicationService.Kind.DUPLICATE) {
                    item.setStatusPreview(ImportacaoItemStatus.DUPLICATE.name());
                    item.setNovo(false);
                    item.setSelecionado(false);
                } else {
                    item.setStatusPreview(ImportacaoItemStatus.MATCHED_EXISTING.name());
                    item.setNovo(false);
                    item.setSelecionado(false);
                }
            } else {
                item.setStatusPreview(ImportacaoItemStatus.NOVO.name());
                item.setNovo(true);
                item.setSelecionado(true);
            }
            if (card && crd != null && (kind == ImportedRowKind.DESPESA || kind == ImportedRowKind.ESTORNO)) {
                try {
                    FaturaService.PrevisaoCompetenciaFatura prev = faturaService.preverCompetenciaParaCompra(
                        usuarioId,
                        cartaoDoUsuario(usuarioId, crd),
                        item.getData() != null ? item.getData().atStartOfDay() : LocalDateTime.now()
                    );
                    item.setFaturaId(prev.faturaId());
                    item.setFaturaPrevistaLabel(prev.rotulo());
                } catch (RuntimeException ex) {
                    item.setStatusPreview(ImportacaoItemStatus.NEEDS_REVIEW.name());
                    item.setSelecionado(false);
                }
            }
        }
    }

    private List<ImportacaoFaturaItemDTO> toItens(List<ImportedFinancialRow> rows, FinancialImportFileType tipo) {
        List<ImportacaoFaturaItemDTO> itens = new ArrayList<>();
        for (ImportedFinancialRow r : rows) {
            ImportacaoFaturaItemDTO i = new ImportacaoFaturaItemDTO();
            i.setSourceLine(r.getSourceLine());
            i.setData(r.getTransactionDate());
            i.setDescricao(r.getDescription());
            i.setMerchant(r.getMerchant());
            i.setValor(r.getAmount());
            i.setCurrency(r.getCurrency() != null ? r.getCurrency() : "BRL");
            i.setTipoLinha(r.getTransactionType() != null ? r.getTransactionType().name() : ImportedRowKind.DESPESA.name());
            i.setAccountHint(r.getAccountHint());
            i.setCardHint(r.getCardHint());
            i.setExternalId(r.getExternalId());
            i.setParcelaAtual(r.getInstallmentNumber());
            i.setTotalParcelas(r.getInstallmentTotal());
            i.setRawReference(r.getRawReference());
            i.setInvalidReason(r.getInvalidReason());
            i.setNovo(r.isValid());
            i.setSelecionado(r.isValid());
            itens.add(i);
        }
        return itens;
    }

    private void aplicarAjustes(Long usuarioId, List<ImportacaoFaturaItemDTO> itens, ConfirmarImportacaoFaturaRequest request) {
        if (request == null || request.getAjustes() == null) {
            return;
        }
        for (ConfirmarImportacaoFaturaRequest.ImportacaoItemAjusteDTO a : request.getAjustes()) {
            if (a.getIndex() == null || a.getIndex() < 0 || a.getIndex() >= itens.size()) {
                continue;
            }
            ImportacaoFaturaItemDTO item = itens.get(a.getIndex());
            if (a.getSelecionado() != null) {
                item.setSelecionado(a.getSelecionado());
                if (Boolean.FALSE.equals(a.getSelecionado())
                    && ImportacaoItemStatus.NOVO.name().equals(item.getStatusPreview())) {
                    item.setStatusPreview(ImportacaoItemStatus.IGNORED.name());
                }
            }
            if (a.getData() != null) {
                item.setData(a.getData());
            }
            if (a.getTipoLinha() != null && !a.getTipoLinha().isBlank()) {
                item.setTipoLinha(parseKind(a.getTipoLinha()).name());
            }
            if (a.getCategoriaId() != null) {
                item.setCategoriaId(categoriaDoUsuario(usuarioId, a.getCategoriaId()));
            }
            if (a.getContaBancariaId() != null) {
                contaBancariaService.buscarEntidade(a.getContaBancariaId(), usuarioId);
                item.setContaBancariaId(a.getContaBancariaId());
            }
            if (a.getCartaoCreditoId() != null) {
                cartaoDoUsuario(usuarioId, a.getCartaoCreditoId());
                item.setCartaoCreditoId(a.getCartaoCreditoId());
            }
            if ("IGNORED".equalsIgnoreCase(a.getStatusPreview())) {
                item.setStatusPreview(ImportacaoItemStatus.IGNORED.name());
                item.setSelecionado(false);
                item.setNovo(false);
            }
        }
    }

    private void aplicarRecurso(
        Long usuarioId,
        ImportacaoFaturaCartao imp,
        String tipoRecurso,
        Long contaBancariaId,
        Long cartaoCreditoId
    ) {
        String tipo = tipoRecurso == null ? "" : tipoRecurso.trim().toUpperCase(Locale.ROOT);
        if ("CONTA".equals(tipo) || contaBancariaId != null) {
            if (contaBancariaId == null) {
                throw new IllegalArgumentException("Selecione a conta bancária.");
            }
            ContaBancaria conta = contaBancariaService.buscarEntidade(contaBancariaId, usuarioId);
            imp.setContaBancaria(conta);
            imp.setCartaoCredito(null);
            imp.setTipoArquivo(FinancialImportFileType.BANK_STATEMENT_CSV);
            imp.setBancoCartao(conta.getNome());
            return;
        }
        if ("CARTAO".equals(tipo) || cartaoCreditoId != null) {
            if (cartaoCreditoId == null) {
                throw new IllegalArgumentException("Selecione o cartão.");
            }
            CartaoCredito cartao = cartaoDoUsuario(usuarioId, cartaoCreditoId);
            imp.setCartaoCredito(cartao);
            imp.setContaBancaria(null);
            imp.setTipoArquivo(FinancialImportFileType.CARD_STATEMENT_CSV);
            imp.setBancoCartao(cartao.getNome());
            return;
        }
        throw new IllegalArgumentException("Indique se o CSV é de conta bancária ou de cartão.");
    }

    private FinancialImportFileType resolverTipoEfetivo(FinancialImportFileType detected, String tipoRecursoHint) {
        if (tipoRecursoHint == null || tipoRecursoHint.isBlank()) {
            return detected;
        }
        String t = tipoRecursoHint.trim().toUpperCase(Locale.ROOT);
        if ("CONTA".equals(t) || "BANK".equals(t)) {
            return FinancialImportFileType.BANK_STATEMENT_CSV;
        }
        if ("CARTAO".equals(t) || "CARD".equals(t)) {
            return FinancialImportFileType.CARD_STATEMENT_CSV;
        }
        return detected;
    }

    private CartaoCredito cartaoDoUsuario(Long usuarioId, Long cartaoId) {
        return cartaoCreditoRepository.findByIdAndUsuarioId(cartaoId, usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Cartão de crédito não encontrado"));
    }

    private Long categoriaDoUsuario(Long usuarioId, Long categoriaId) {
        categoriaRepository.findByIdAndUsuarioId(categoriaId, usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
        return categoriaId;
    }

    private Optional<Long> sugerirCategoria(Long usuarioId, ImportacaoFaturaItemDTO item) {
        String merchant = item.getMerchant() != null ? item.getMerchant() : item.getDescricao();
        return categoryRuleService.match(usuarioId, merchant).map(MerchantCategoryRuleService.CategoryMatch::categoriaId);
    }

    private Optional<CartaoCredito> sugerirCartaoUnico(Long usuarioId, CsvParseResult parsed) {
        String hint = parsed.rows().stream()
            .map(ImportedFinancialRow::getCardHint)
            .filter(h -> h != null && !h.isBlank())
            .findFirst()
            .orElse(null);
        if (hint == null) {
            List<CartaoCredito> ativos = cartaoCreditoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
            return ativos.size() == 1 ? Optional.of(ativos.get(0)) : Optional.empty();
        }
        String digits = hint.replaceAll("\\D", "");
        String last4 = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        List<CartaoCredito> match = cartaoCreditoRepository.findByUsuarioIdAndAtivoTrue(usuarioId).stream()
            .filter(c -> c.getNumeroCartao() != null && c.getNumeroCartao().replaceAll("\\D", "").endsWith(last4))
            .toList();
        return match.size() == 1 ? Optional.of(match.get(0)) : Optional.empty();
    }

    private CartaoCredito resolverCartaoPagamento(Long usuarioId, ImportacaoFaturaCartao imp, ImportacaoFaturaItemDTO item) {
        if (item.getCartaoCreditoId() != null) {
            return cartaoDoUsuario(usuarioId, item.getCartaoCreditoId());
        }
        if (imp.getCartaoCredito() != null) {
            return imp.getCartaoCredito();
        }
        List<CartaoCredito> ativos = cartaoCreditoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        return ativos.size() == 1 ? ativos.get(0) : null;
    }

    private Long contaId(ImportacaoFaturaCartao imp, ImportacaoFaturaItemDTO item, Long usuarioId) {
        Long id = item.getContaBancariaId() != null
            ? item.getContaBancariaId()
            : (imp.getContaBancaria() != null ? imp.getContaBancaria().getId() : null);
        if (id == null) {
            throw new IllegalArgumentException("Selecione a conta bancária.");
        }
        contaBancariaService.buscarEntidade(id, usuarioId);
        return id;
    }

    private Long cartaoId(ImportacaoFaturaCartao imp, ImportacaoFaturaItemDTO item, Long usuarioId) {
        Long id = item.getCartaoCreditoId() != null
            ? item.getCartaoCreditoId()
            : (imp.getCartaoCredito() != null ? imp.getCartaoCredito().getId() : null);
        if (id == null) {
            throw new ResourceNotFoundException("Cartão de crédito não encontrado");
        }
        cartaoDoUsuario(usuarioId, id);
        return id;
    }

    private ImportacaoFaturaCartao importacaoPendente(Long usuarioId, Long importacaoId) {
        ImportacaoFaturaCartao imp = importacaoRepository.findByIdAndUsuarioId(importacaoId, usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Importação não encontrada"));
        if (imp.getStatus() != ImportacaoFaturaCartao.Status.PENDENTE) {
            throw new IllegalArgumentException("Só é possível alterar importações pendentes.");
        }
        return imp;
    }

    private void capturarTotalAntes(Long faturaId, Map<Long, BigDecimal> totaisAntes) {
        totaisAntes.computeIfAbsent(faturaId, id ->
            Optional.ofNullable(transacaoRepository.sumDespesaConfirmadaPorFaturaId(id)).orElse(BigDecimal.ZERO));
    }

    private void registrarImpactoFatura(
        Long faturaId,
        BigDecimal contribuicao,
        Map<Long, BigDecimal> totaisAntes,
        Map<Long, Integer> adicionadasPorFatura
    ) {
        totaisAntes.computeIfAbsent(faturaId, id -> {
            BigDecimal agora = Optional.ofNullable(transacaoRepository.sumDespesaConfirmadaPorFaturaId(id))
                .orElse(BigDecimal.ZERO);
            BigDecimal c = contribuicao == null ? BigDecimal.ZERO : contribuicao;
            return agora.subtract(c);
        });
        adicionadasPorFatura.merge(faturaId, 1, Integer::sum);
    }

    private ImportacaoConfirmacaoDTO montarRelatorio(
        int criadas,
        int matched,
        int duplicadas,
        int revisao,
        int falhas,
        int processadas,
        Set<Long> faturasSync,
        Map<Long, BigDecimal> totaisAntes,
        Map<Long, Integer> adicionadasPorFatura,
        Long usuarioId
    ) {
        ImportacaoConfirmacaoDTO out = new ImportacaoConfirmacaoDTO();
        out.setCriadas(criadas);
        out.setConciliadas(matched);
        out.setMatched(matched);
        out.setDuplicadas(duplicadas);
        out.setRevisao(revisao);
        out.setFalhas(falhas);
        out.setProcessadas(processadas);
        out.setRegistrosNaFaturaAtual(criadas + matched);
        StringBuilder msg = new StringBuilder("Arquivo importado com sucesso\n\n")
            .append(processadas).append(" linhas processadas\n")
            .append(criadas).append(" transações criadas\n")
            .append(matched).append(" conciliadas com lançamentos existentes\n")
            .append(duplicadas).append(" duplicadas ignoradas\n")
            .append(revisao).append(" necessita revisão");
        for (Long faturaId : faturasSync) {
            BigDecimal depois = Optional.ofNullable(transacaoRepository.sumDespesaConfirmadaPorFaturaId(faturaId))
                .orElse(BigDecimal.ZERO);
            BigDecimal antes = totaisAntes.getOrDefault(faturaId, BigDecimal.ZERO);
            ImportacaoConfirmacaoDTO.FaturaImpactoDTO fi = new ImportacaoConfirmacaoDTO.FaturaImpactoDTO();
            fi.setFaturaId(faturaId);
            try {
                var faturaDto = faturaService.buscarPorId(faturaId, usuarioId);
                YearMonth ym = faturaDto.getDataVencimento() != null
                    ? YearMonth.from(faturaDto.getDataVencimento())
                    : null;
                fi.setRotulo(ym != null ? FaturaService.rotuloCompetencia(ym) : "Fatura");
                fi.setPeriodo(fi.getRotulo());
            } catch (RuntimeException ex) {
                fi.setRotulo("Fatura " + faturaId);
            }
            fi.setTransacoesAdicionadas(adicionadasPorFatura.getOrDefault(faturaId, 0));
            fi.setTotalAntes(antes);
            fi.setTotalDepois(depois);
            out.getFaturas().add(fi);
            BigDecimal delta = depois.subtract(antes);
            msg.append("\n\n").append(fi.getRotulo()).append(":\n+ R$ ").append(delta);
        }
        out.setMensagem(msg.toString());
        return out;
    }

    private static ImportacaoConfirmacaoDTO vazio() {
        ImportacaoConfirmacaoDTO d = new ImportacaoConfirmacaoDTO();
        d.setMensagem("Importação já confirmada.");
        return d;
    }

    private OrigemTransacao origem(ImportacaoFaturaCartao imp) {
        return imp.getTipoArquivo() == FinancialImportFileType.BANK_STATEMENT_CSV
            ? OrigemTransacao.CSV_BANK_STATEMENT
            : OrigemTransacao.CSV_CARD_STATEMENT;
    }

    private static ImportedRowKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return ImportedRowKind.DESPESA;
        }
        try {
            return ImportedRowKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ImportedRowKind.DESPESA;
        }
    }

    private static boolean pareceEntrada(ImportacaoFaturaItemDTO item) {
        String d = item.getDescricao() == null ? "" : item.getDescricao().toLowerCase(Locale.ROOT);
        return d.contains("recebido") || d.contains("entrada") || d.contains("crédito") || d.contains("credito");
    }

    private static String descricaoPersistivel(String d) {
        if (d == null || d.isBlank()) {
            return "Lançamento CSV";
        }
        String t = d.trim();
        if (t.length() < 3) {
            t = t + "...";
        }
        return t.length() > 200 ? t.substring(0, 200) : t;
    }

    private static String titulo(String fileName, FinancialImportFileType tipo, ContaBancaria conta, CartaoCredito cartao) {
        if (cartao != null) {
            return cartao.getNome();
        }
        if (conta != null) {
            return conta.getNome();
        }
        if (tipo == FinancialImportFileType.BANK_STATEMENT_CSV) {
            return "Extrato bancário CSV";
        }
        if (tipo == FinancialImportFileType.CARD_STATEMENT_CSV) {
            return "Extrato de cartão CSV";
        }
        return fileName != null && !fileName.isBlank() ? fileName : "Extrato CSV";
    }

    private static BigDecimal somaDespesasCard(List<ImportacaoFaturaItemDTO> itens, FinancialImportFileType tipo) {
        if (tipo != FinancialImportFileType.CARD_STATEMENT_CSV) {
            return BigDecimal.ZERO;
        }
        return itens.stream()
            .filter(i -> ImportedRowKind.DESPESA.name().equals(i.getTipoLinha()))
            .map(i -> i.getValor() == null ? BigDecimal.ZERO : i.getValor().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static int countStatus(List<ImportacaoFaturaItemDTO> itens, ImportacaoItemStatus st) {
        return (int) itens.stream().filter(i -> st.name().equals(i.getStatusPreview())).count();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar importação CSV", e);
        }
    }

    private List<ImportacaoFaturaItemDTO> readItens(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, ITEM_LIST);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler itens da importação", e);
        }
    }

    private List<String> readStrings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
