package com.consumoesperto.ingest.notificacao;

import com.consumoesperto.config.IngestNotificacaoProperties;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.ingest.notificacao.parser.NotificacaoBancariaParser;
import com.consumoesperto.ingest.notificacao.parser.ParsedNotificacaoBancaria;
import com.consumoesperto.mobilecapture.service.MerchantNormalizationService;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.IngestFonteRecurso;
import com.consumoesperto.model.IngestPreferencia;
import com.consumoesperto.model.NotificacaoBancariaRecebida;
import com.consumoesperto.model.OrigemTransacao;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.NotificacaoBancariaRecebidaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.service.TransacaoService;
import com.consumoesperto.service.importacao.FinancialImportDeduplicationService;
import com.consumoesperto.util.AppTimeZone;
import com.consumoesperto.util.LogSanitizer;
import com.consumoesperto.util.MoedaUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestNotificacaoProcessor {

    private final NotificacaoBancariaRecebidaRepository recebidaRepository;
    private final IngestNotificacaoProperties properties;
    private final IngestPreferenciaService preferenciaService;
    private final NotificacaoBancariaParser parser;
    private final IngestFonteRecursoService fonteRecursoService;
    private final ContaBancariaRepository contaBancariaRepository;
    private final TransacaoRepository transacaoRepository;
    private final TransacaoService transacaoService;
    private final IngestNotificacaoCategorizador categorizador;
    private final MerchantNormalizationService merchantNormalizationService;
    private final FinancialImportDeduplicationService financialImportDeduplicationService;
    private final IngestNotificacaoAvisoService avisoService;

    @Async("cerebroExecutor")
    public void processarAsync(Long id) {
        processar(id);
    }

    @Transactional
    public void processar(Long id) {
        NotificacaoBancariaRecebida row = recebidaRepository.findById(id).orElse(null);
        if (row == null) {
            return;
        }
        if (!NotificacaoBancariaRecebida.STATUS_RECEBIDA.equals(row.getStatus())
            && !NotificacaoBancariaRecebida.STATUS_PROCESSANDO.equals(row.getStatus())) {
            return;
        }
        row.setStatus(NotificacaoBancariaRecebida.STATUS_PROCESSANDO);
        recebidaRepository.save(row);

        IngestPreferencia pref = preferenciaService.obterOuCriar(row.getUsuarioId());
        if (!pref.isAtivo()) {
            finalizar(row, NotificacaoBancariaRecebida.STATUS_IGNORADA, "captura_desativada", null, null);
            return;
        }

        if (duplicataNotificacao(row)) {
            finalizar(row, NotificacaoBancariaRecebida.STATUS_DUPLICADA, "notificacao_repetida", null, null);
            return;
        }

        ParsedNotificacaoBancaria parsed = parser.parse(row.getApp(), row.getTitulo(), row.getTexto());
        if ("IGNORAR".equals(parsed.destino())) {
            finalizar(row, NotificacaoBancariaRecebida.STATUS_IGNORADA, parsed.motivo(), null, null);
            return;
        }
        if ("NAO_RECONHECIDA".equals(parsed.destino()) || !parsed.confiante()) {
            log.info("ingest_notificacao_nao_reconhecida id={} app={} motivo={}",
                row.getId(), row.getApp(), LogSanitizer.sanitize(parsed.motivo()));
            finalizar(row, NotificacaoBancariaRecebida.STATUS_NAO_RECONHECIDA, parsed.motivo(), null, null);
            return;
        }

        RecursoAlvo alvo = resolverRecurso(row.getUsuarioId(), row.getApp(), parsed.canal(), pref);
        if ("ESTORNO".equals(parsed.destino())) {
            Optional<Transacao> alvoEstorno = encontrarParaEstorno(
                row.getUsuarioId(), parsed.valor(), parsed.estabelecimentoNormalizado(),
                row.getRecebidoEm(), alvo);
            if (alvoEstorno.isEmpty()) {
                finalizar(row, NotificacaoBancariaRecebida.STATUS_NAO_RECONHECIDA, "estorno_sem_par", null, null);
                return;
            }
            transacaoService.deletarTransacao(alvoEstorno.get().getId(), row.getUsuarioId());
            finalizar(row, NotificacaoBancariaRecebida.STATUS_ESTORNADA, null, null, alvoEstorno.get().getId());
            return;
        }

        Optional<Transacao> manual = encontrarLancamentoManual(
            row.getUsuarioId(), parsed.valor(), row.getRecebidoEm(), alvo, parsed.estabelecimentoNormalizado());
        if (manual.isPresent()) {
            Transacao existente = enriquecer(manual.get(), parsed, row);
            finalizar(row, NotificacaoBancariaRecebida.STATUS_DUPLICADA, "enriquecida_manual",
                existente.getId(), existente.getId());
            return;
        }

        String fingerprint = financialImportDeduplicationService.buildCanonicalFingerprint(
            row.getUsuarioId(),
            alvo.contaId(),
            alvo.cartaoId(),
            row.getRecebidoEm().toLocalDate(),
            parsed.valor(),
            parsed.estabelecimentoNormalizado(),
            null,
            null,
            parsed.tipoTransacao()
        );
        Optional<Transacao> byFp = transacaoRepository
            .findFirstByUsuarioIdAndIngestionFingerprint(row.getUsuarioId(), fingerprint);
        if (byFp.isPresent()) {
            Transacao existente = enriquecer(byFp.get(), parsed, row);
            finalizar(row, NotificacaoBancariaRecebida.STATUS_DUPLICADA, "fingerprint",
                existente.getId(), existente.getId());
            return;
        }

        try {
            TransacaoDTO dto = montarDto(row, parsed, alvo);
            TransacaoDTO criada = transacaoService.criarTransacao(dto, row.getUsuarioId(), false, true, true);
            transacaoService.atualizarMetadadosIngestao(
                criada.getId(),
                OrigemTransacao.NOTIFICACAO_BANCARIA,
                row.getIdExterno(),
                row.getApp(),
                parsed.estabelecimento(),
                parsed.estabelecimentoNormalizado(),
                fingerprint,
                java.math.BigDecimal.ONE,
                null
            );
            finalizar(row, NotificacaoBancariaRecebida.STATUS_LANCADA, null, criada.getId(), null);
            avisoService.agendarOuEnviar(row.getUsuarioId(), row.getId(), criada.getId(), parsed, alvo, pref);
        } catch (Exception e) {
            log.warn("ingest_notificacao_erro id={} msg={}", row.getId(), LogSanitizer.sanitize(e.getMessage()));
            finalizar(row, NotificacaoBancariaRecebida.STATUS_ERRO, truncarErro(e.getMessage()), null, null);
        }
    }

    private boolean duplicataNotificacao(NotificacaoBancariaRecebida row) {
        LocalDateTime janela = AppTimeZone.agora().minusMinutes(properties.getDedupWindowMinutes());
        return recebidaRepository
            .findByUsuarioIdAndHashDedupAndCriadoEmAfter(row.getUsuarioId(), row.getHashDedup(), janela)
            .stream()
            .anyMatch(o -> !o.getId().equals(row.getId())
                && (NotificacaoBancariaRecebida.STATUS_LANCADA.equals(o.getStatus())
                || NotificacaoBancariaRecebida.STATUS_DUPLICADA.equals(o.getStatus())
                || NotificacaoBancariaRecebida.STATUS_ESTORNADA.equals(o.getStatus())
                || NotificacaoBancariaRecebida.STATUS_PROCESSANDO.equals(o.getStatus())
                || NotificacaoBancariaRecebida.STATUS_RECEBIDA.equals(o.getStatus())));
    }

    private RecursoAlvo resolverRecurso(Long usuarioId, String app, String canal, IngestPreferencia pref) {
        Optional<IngestFonteRecurso> map = fonteRecursoService.resolver(usuarioId, app, canal);
        Long contaId = map.map(IngestFonteRecurso::getContaBancariaId).orElse(null);
        Long cartaoId = map.map(IngestFonteRecurso::getCartaoCreditoId).orElse(null);
        boolean usouPadrao = false;
        if (cartaoId == null && !NotificacaoBancariaParser.CANAL_CREDITO.equals(canal) && contaId == null) {
            contaId = pref.getContaPadraoId();
            if (contaId == null) {
                contaId = contaBancariaRepository.findFirstByUsuarioIdAndPadraoTrueAndAtivaTrue(usuarioId)
                    .map(ContaBancaria::getId)
                    .orElseGet(() -> contaBancariaRepository
                        .findFirstByUsuarioIdAndAtivaTrueOrderByIdAsc(usuarioId)
                        .map(ContaBancaria::getId)
                        .orElse(null));
            }
            usouPadrao = contaId != null && map.isEmpty();
        }
        if (NotificacaoBancariaParser.CANAL_CREDITO.equals(canal) && cartaoId == null && contaId == null) {
            contaId = pref.getContaPadraoId();
            if (contaId == null) {
                contaId = contaBancariaRepository.findFirstByUsuarioIdAndPadraoTrueAndAtivaTrue(usuarioId)
                    .map(ContaBancaria::getId)
                    .orElseGet(() -> contaBancariaRepository
                        .findFirstByUsuarioIdAndAtivaTrueOrderByIdAsc(usuarioId)
                        .map(ContaBancaria::getId)
                        .orElse(null));
            }
            usouPadrao = contaId != null;
        }
        return new RecursoAlvo(contaId, cartaoId, usouPadrao);
    }

    private Optional<Transacao> encontrarLancamentoManual(
        Long usuarioId, BigDecimal valor, LocalDateTime quando, RecursoAlvo alvo, String merchantNorm
    ) {
        LocalDateTime ini = quando.minusHours(properties.getMatchManualHours());
        LocalDateTime fim = quando.plusHours(properties.getMatchManualHours());
        return transacaoRepository.findByUsuarioIdAndPeriodoEfetivoOrderByDataDesc(usuarioId, ini, fim).stream()
            .filter(t -> !t.isExcluido())
            .filter(t -> FinancialImportDeduplicationService.valoresCompativeis(t.getValor(), valor))
            .filter(t -> contaOuCartaoCompativel(t, alvo))
            .filter(t -> origemManualOuWhatsapp(t))
            .filter(t -> descricaoCompativelOpcional(t, merchantNorm))
            .findFirst();
    }

    private Optional<Transacao> encontrarParaEstorno(
        Long usuarioId, BigDecimal valor, String merchantNorm, LocalDateTime quando, RecursoAlvo alvo
    ) {
        LocalDateTime ini = quando.minusHours(48);
        LocalDateTime fim = quando.plusHours(2);
        return transacaoRepository.findByUsuarioIdAndPeriodoEfetivoOrderByDataDesc(usuarioId, ini, fim).stream()
            .filter(t -> !t.isExcluido())
            .filter(t -> t.getTipoTransacao() == Transacao.TipoTransacao.DESPESA)
            .filter(t -> FinancialImportDeduplicationService.valoresCompativeis(t.getValor(), valor))
            .filter(t -> contaOuCartaoCompativel(t, alvo) || alvo.contaId() == null && alvo.cartaoId() == null)
            .filter(t -> descricaoCompativelOpcional(t, merchantNorm))
            .findFirst();
    }

    private static boolean origemManualOuWhatsapp(Transacao t) {
        OrigemTransacao o = t.getOrigemTransacao();
        return o == null || o == OrigemTransacao.MANUAL || o == OrigemTransacao.WHATSAPP
            || o == OrigemTransacao.PIX;
    }

    private static boolean contaOuCartaoCompativel(Transacao t, RecursoAlvo alvo) {
        if (alvo.cartaoId() != null && t.getFatura() != null && t.getFatura().getCartaoCredito() != null) {
            return alvo.cartaoId().equals(t.getFatura().getCartaoCredito().getId());
        }
        if (alvo.contaId() != null && t.getContaBancaria() != null) {
            return alvo.contaId().equals(t.getContaBancaria().getId());
        }
        return true;
    }

    private boolean descricaoCompativelOpcional(Transacao t, String merchantNorm) {
        if (merchantNorm == null || merchantNorm.isBlank()) {
            return true;
        }
        String existing = firstNonBlank(t.getMerchantNormalized(), t.getDescricao());
        String normExisting = merchantNormalizationService.normalize(existing);
        return FinancialImportDeduplicationService.descricaoCompativel(
            merchantNorm, normExisting == null ? "" : normExisting);
    }

    private Transacao enriquecer(Transacao existente, ParsedNotificacaoBancaria parsed, NotificacaoBancariaRecebida row) {
        if (existente.getMerchantRaw() == null || existente.getMerchantRaw().isBlank()) {
            existente.setMerchantRaw(parsed.estabelecimento());
        }
        if (existente.getMerchantNormalized() == null || existente.getMerchantNormalized().isBlank()) {
            existente.setMerchantNormalized(parsed.estabelecimentoNormalizado());
        }
        if (parsed.estabelecimento() != null && (existente.getDescricao() == null
            || existente.getDescricao().length() < parsed.estabelecimento().length())) {
            existente.setDescricao(parsed.estabelecimento());
        }
        if (existente.getOrigemTransacao() == null
            || existente.getOrigemTransacao() == OrigemTransacao.MANUAL
            || existente.getOrigemTransacao() == OrigemTransacao.WHATSAPP) {
            existente.setOrigemTransacao(OrigemTransacao.NOTIFICACAO_BANCARIA);
        }
        transacaoRepository.save(existente);
        return existente;
    }

    private TransacaoDTO montarDto(NotificacaoBancariaRecebida row, ParsedNotificacaoBancaria parsed, RecursoAlvo alvo) {
        TransacaoDTO dto = new TransacaoDTO();
        dto.setDescricao(parsed.estabelecimento());
        dto.setValor(MoedaUtil.nz(parsed.valor()));
        dto.setTipoTransacao("RECEITA".equals(parsed.tipoTransacao())
            ? TransacaoDTO.TipoTransacao.RECEITA
            : TransacaoDTO.TipoTransacao.DESPESA);
        dto.setDataTransacao(row.getRecebidoEm());
        dto.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
        dto.setSugerirCategoriaAutomatica(false);
        categorizador.sugerir(row.getUsuarioId(), parsed.estabelecimento()).ifPresent(dto::setCategoriaId);
        if (alvo.cartaoId() != null) {
            dto.setCartaoCreditoId(alvo.cartaoId());
        } else if (alvo.contaId() != null) {
            dto.setContaBancariaId(alvo.contaId());
        }
        return dto;
    }

    private void finalizar(
        NotificacaoBancariaRecebida row, String status, String erro, Long transacaoId, Long enriquecidaId
    ) {
        row.setStatus(status);
        row.setErro(truncarErro(erro));
        if (transacaoId != null) {
            row.setTransacaoId(transacaoId);
        }
        if (enriquecidaId != null) {
            row.setTransacaoEnriquecidaId(enriquecidaId);
        }
        row.setProcessadoEm(AppTimeZone.agora());
        recebidaRepository.save(row);
    }

    private static String truncarErro(String erro) {
        if (erro == null) {
            return null;
        }
        return erro.length() > 500 ? erro.substring(0, 500) : erro;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    public record RecursoAlvo(Long contaId, Long cartaoId, boolean usouContaPadrao) {
        public boolean temDestino() {
            return contaId != null || cartaoId != null;
        }
    }
}
