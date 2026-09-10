package com.consumoesperto.integration;

import com.consumoesperto.dto.ImportacaoFaturaItemDTO;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.ingest.notificacao.IngestFonteRecursoService;
import com.consumoesperto.ingest.notificacao.IngestNotificacaoWhatsappHandler;
import com.consumoesperto.ingest.notificacao.IngestTokenService;
import com.consumoesperto.ingest.notificacao.parser.NotificacaoBancariaParser;
import com.consumoesperto.ingest.notificacao.security.IngestTokenFilter;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.MovimentacaoSaldoLog;
import com.consumoesperto.model.NotificacaoBancariaRecebida;
import com.consumoesperto.model.OrigemTransacao;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.MovimentacaoSaldoLogRepository;
import com.consumoesperto.repository.NotificacaoBancariaRecebidaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.TransacaoService;
import com.consumoesperto.service.UsuarioSessaoContextoService;
import com.consumoesperto.service.importacao.FinancialImportDeduplicationService;
import com.consumoesperto.mobilecapture.service.MerchantCategoryRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "consumoesperto.ingest.notificacao.enabled=true",
    "consumoesperto.ingest.notificacao.require-https=false",
    "consumoesperto.ingest.notificacao.async=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IngestNotificacaoLancamentoHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private IngestTokenService tokenService;
    @Autowired private ContaBancariaRepository contaBancariaRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private TransacaoRepository transacaoRepository;
    @Autowired private MovimentacaoSaldoLogRepository ledgerRepository;
    @Autowired private NotificacaoBancariaRecebidaRepository recebidaRepository;
    @Autowired private IngestFonteRecursoService fonteRecursoService;
    @Autowired private TransacaoService transacaoService;
    @Autowired private IngestNotificacaoWhatsappHandler whatsappHandler;
    @Autowired private UsuarioSessaoContextoService sessaoContextoService;
    @Autowired private FinancialImportDeduplicationService financialImportDeduplicationService;
    @Autowired private MerchantCategoryRuleService merchantCategoryRuleService;

    private Long usuarioId;
    private Long contaId;
    private Long categoriaId;
    private String token;

    @BeforeEach
    void seed() {
        String sfx = String.valueOf(System.nanoTime());
        Usuario u = new Usuario();
        u.setUsername("ingl_" + sfx);
        u.setEmail("ingl_" + sfx + "@test.local");
        u.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        u.setNome("Ingest Lanc");
        usuarioId = usuarioRepository.save(u).getId();

        ContaBancaria conta = new ContaBancaria();
        conta.setNome("Nubank");
        conta.setUsuario(u);
        conta.setTipo(ContaBancaria.TipoConta.CORRENTE);
        conta.setSaldoAtual(new BigDecimal("1000.00"));
        conta.setSaldoInicial(new BigDecimal("1000.00"));
        conta.setAtiva(true);
        conta.setPadrao(true);
        contaId = contaBancariaRepository.save(conta).getId();

        Categoria cat = new Categoria();
        cat.setNome("Alimentação");
        cat.setUsuario(u);
        cat.setAtivo(true);
        categoriaId = categoriaRepository.save(cat).getId();

        fonteRecursoService.upsert(usuarioId, "nubank", NotificacaoBancariaParser.CANAL_PIX, contaId, null);
        token = tokenService.gerar(usuarioId);
    }

    @Test
    void pixEnviado_lancaConfirmada_movimentaLedger() throws Exception {
        postNotif("Pix de R$ 20,00 para MARIA SILVA", "evt-pix-1");

        List<NotificacaoBancariaRecebida> logs = recebidaRepository
            .findByUsuarioIdOrderByCriadoEmDesc(usuarioId, PageRequest.of(0, 5));
        assertEquals(1, logs.size());
        assertEquals(NotificacaoBancariaRecebida.STATUS_LANCADA, logs.get(0).getStatus());
        assertNotNull(logs.get(0).getTransacaoId());

        Transacao tx = transacaoRepository.findById(logs.get(0).getTransacaoId()).orElseThrow();
        assertEquals(OrigemTransacao.NOTIFICACAO_BANCARIA, tx.getOrigemTransacao());
        assertEquals(Transacao.StatusConferencia.CONFIRMADA, tx.getStatusConferencia());
        assertEquals(0, new BigDecimal("20.00").compareTo(tx.getValor()));

        Optional<MovimentacaoSaldoLog> linha = ledgerRepository.findFirstByTransacaoIdOrderByIdDesc(tx.getId());
        assertTrue(linha.isPresent());
        assertEquals(MovimentacaoSaldoLog.TipoOperacaoSaldo.CRIACAO, linha.get().getTipoOperacao());
        assertEquals(contaId, linha.get().getContaId());
    }

    @Test
    void notificacaoRepetida_mesmoIdExterno_naoDuplicaTransacao() throws Exception {
        postNotif("Pix de R$ 21,00 para MARIA SILVA", "evt-dup-1");
        postNotif("Pix de R$ 21,00 para MARIA SILVA", "evt-dup-1");
        long txs = transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(usuarioId).stream()
            .filter(t -> !t.isExcluido())
            .count();
        assertEquals(1, txs);
    }

    @Test
    void duplicataComLancamentoManual_enriqueceEmVezDeCriar() throws Exception {
        TransacaoDTO dto = new TransacaoDTO();
        dto.setDescricao("padaria");
        dto.setValor(new BigDecimal("45.90"));
        dto.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
        dto.setContaBancariaId(contaId);
        dto.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
        dto.setSugerirCategoriaAutomatica(false);
        TransacaoDTO manual = transacaoService.criarTransacao(dto, usuarioId, false, true, true);
        transacaoService.atualizarMetadadosIngestao(
            manual.getId(), OrigemTransacao.WHATSAPP, null, null, null, null, null, null, null);

        postNotif("Compra aprovada: R$ 45,90 em PADARIA DO ZE", "evt-manual-1");

        long txs = transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(usuarioId).stream()
            .filter(t -> !t.isExcluido())
            .count();
        assertEquals(1, txs);
        Transacao tx = transacaoRepository.findById(manual.getId()).orElseThrow();
        assertTrue(tx.getDescricao().contains("PADARIA"));
        assertEquals(OrigemTransacao.NOTIFICACAO_BANCARIA, tx.getOrigemTransacao());
    }

    @Test
    void faturaPdfDepois_casaComLancamentoDeNotificacao() throws Exception {
        postNotif("Pix de R$ 33,00 para PADARIA DO ZE", "evt-fat-1");
        Transacao tx = transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(usuarioId).get(0);

        ImportacaoFaturaItemDTO item = new ImportacaoFaturaItemDTO();
        item.setDescricao("PADARIA DO ZE");
        item.setValor(new BigDecimal("33.00"));
        item.setData(LocalDate.from(tx.getDataTransacao()));
        item.setTipoLinha("DESPESA");

        Optional<FinancialImportDeduplicationService.Match> match =
            financialImportDeduplicationService.findExisting(usuarioId, item, contaId, null);
        assertTrue(match.isPresent());
        assertEquals(tx.getId(), match.get().transacao().getId());
        assertEquals(FinancialImportDeduplicationService.Kind.MATCHED_EXISTING, match.get().kind());
    }

    @Test
    void categoriaPorRegraMerchant_eApagarEstornaLedger() throws Exception {
        merchantCategoryRuleService.saveUserRule(usuarioId, "MARIA SILVA", categoriaId);
        postNotif("Pix de R$ 18,00 para MARIA SILVA", "evt-cat-1");
        Transacao tx = transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(usuarioId).get(0);
        assertNotNull(tx.getCategoria());
        assertEquals(categoriaId, tx.getCategoria().getId());

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("transacaoId", tx.getId());
        sessaoContextoService.salvar(
            usuarioId,
            UsuarioSessaoContextoService.CANAL_WHATSAPP,
            UsuarioSessaoContextoService.CHAVE_CAPTURA_NOTIFICACAO,
            ctx,
            60
        );
        Optional<String> resp = whatsappHandler.tryHandle(usuarioId, "apagar");
        assertTrue(resp.isPresent());
        Transacao apagada = transacaoRepository.findById(tx.getId()).orElseThrow();
        assertTrue(apagada.isExcluido());
        Optional<MovimentacaoSaldoLog> excl = ledgerRepository.findFirstByTransacaoIdOrderByIdDesc(tx.getId());
        assertTrue(excl.isPresent());
        assertEquals(MovimentacaoSaldoLog.TipoOperacaoSaldo.EXCLUSAO, excl.get().getTipoOperacao());
    }

    private void postNotif(String texto, String idExterno) throws Exception {
        String body = "{\"origem\":\"ANDROID_MACRODROID\",\"app\":\"nubank\",\"titulo\":\"Nubank\","
            + "\"texto\":\"" + texto + "\",\"idExterno\":\"" + idExterno + "\"}";
        mockMvc.perform(post("/api/ingest/notificacao")
                .header(IngestTokenFilter.TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted());
    }
}
