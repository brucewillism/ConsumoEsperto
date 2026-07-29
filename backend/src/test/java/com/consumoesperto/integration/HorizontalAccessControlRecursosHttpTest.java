package com.consumoesperto.integration;

import com.consumoesperto.model.AgendamentoPagamento;
import com.consumoesperto.model.AssinaturaRecorrente;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.CompraParcelada;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.DespesaFixa;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.ImportacaoFaturaCartao;
import com.consumoesperto.model.MetaFinanceira;
import com.consumoesperto.model.Orcamento;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.AgendamentoPagamentoRepository;
import com.consumoesperto.repository.AssinaturaRecorrenteRepository;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.CompraParceladaRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.DespesaFixaRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.ImportacaoFaturaCartaoRepository;
import com.consumoesperto.repository.MetaFinanceiraRepository;
import com.consumoesperto.repository.OrcamentoRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.security.JwtTokenProvider;
import com.consumoesperto.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HorizontalAccessControlRecursosHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ContaBancariaRepository contaRepository;
    @Autowired private CartaoCreditoRepository cartaoRepository;
    @Autowired private TransacaoRepository transacaoRepository;
    @Autowired private OrcamentoRepository orcamentoRepository;
    @Autowired private MetaFinanceiraRepository metaRepository;
    @Autowired private AgendamentoPagamentoRepository agendamentoRepository;
    @Autowired private AssinaturaRecorrenteRepository assinaturaRepository;
    @Autowired private FaturaRepository faturaRepository;
    @Autowired private DespesaFixaRepository despesaFixaRepository;
    @Autowired private CompraParceladaRepository compraParceladaRepository;
    @Autowired private ImportacaoFaturaCartaoRepository importacaoRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String tokenB;
    private Long contaA;
    private Long cartaoA;
    private Long txA;
    private Long orcA;
    private Long metaA;
    private Long agA;
    private Long assA;
    private Long catA;
    private Long fatA;
    private Long despA;
    private Long compraA;
    private Long impA;

    @BeforeEach
    void seed() {
        String s = String.valueOf(System.nanoTime());
        Usuario a = saveUser("ha_a_" + s, "ha_a_" + s + "@test.local");
        Usuario b = saveUser("ha_b_" + s, "ha_b_" + s + "@test.local");
        tokenB = bearerFor(b);

        Categoria cat = new Categoria();
        cat.setNome("Secreta HA");
        cat.setUsuario(a);
        categoriaRepository.save(cat);
        catA = cat.getId();

        ContaBancaria conta = new ContaBancaria();
        conta.setNome("Conta Secreta");
        conta.setUsuario(a);
        conta.setTipo(ContaBancaria.TipoConta.CORRENTE);
        conta.setSaldoAtual(BigDecimal.TEN);
        conta.setAtiva(true);
        contaA = contaRepository.save(conta).getId();

        CartaoCredito cartao = new CartaoCredito();
        cartao.setNome("Cartão Secreta");
        cartao.setBanco("Teste");
        cartao.setNumeroCartao("4111111111111111");
        cartao.setDiaVencimento(10);
        cartao.setUsuario(a);
        cartao.setLimiteCredito(new BigDecimal("1000"));
        cartao.setLimiteDisponivel(new BigDecimal("1000"));
        cartao.setAtivo(true);
        cartaoA = cartaoRepository.save(cartao).getId();

        Transacao tx = new Transacao();
        tx.setUsuario(a);
        tx.setDescricao("Tx Secreta");
        tx.setValor(BigDecimal.ONE);
        tx.setTipoTransacao(Transacao.TipoTransacao.DESPESA);
        tx.setCategoria(cat);
        tx.setContaBancaria(conta);
        tx.setDataTransacao(LocalDateTime.now());
        txA = transacaoRepository.save(tx).getId();

        Orcamento orc = new Orcamento();
        orc.setUsuario(a);
        orc.setCategoria(cat);
        orc.setValorLimite(new BigDecimal("500"));
        orc.setMes(YearMonth.now().getMonthValue());
        orc.setAno(YearMonth.now().getYear());
        orcA = orcamentoRepository.save(orc).getId();

        MetaFinanceira meta = new MetaFinanceira();
        meta.setUsuario(a);
        meta.setDescricao("Meta Secreta");
        meta.setValorTotal(new BigDecimal("10000"));
        meta.setPercentualComprometimento(new BigDecimal("10"));
        meta.setValorPoupadoMensal(new BigDecimal("500"));
        meta.setPrazoMeses(new BigDecimal("12"));
        meta.setValorAcumulado(BigDecimal.ZERO);
        metaA = metaRepository.save(meta).getId();

        AgendamentoPagamento ag = new AgendamentoPagamento();
        ag.setUsuario(a);
        ag.setContaDebito(conta);
        ag.setBeneficiario("Benef HA");
        ag.setValor(new BigDecimal("50"));
        ag.setDataVencimento(LocalDate.now().plusDays(5));
        agA = agendamentoRepository.save(ag).getId();

        AssinaturaRecorrente ass = new AssinaturaRecorrente();
        ass.setUsuario(a);
        ass.setNome("Assinatura Secreta");
        ass.setValor(new BigDecimal("29.90"));
        ass.setDiaVencimento(10);
        ass.setAtivo(true);
        assA = assinaturaRepository.save(ass).getId();

        Fatura fat = new Fatura();
        fat.setNumeroFatura("HA-" + s);
        fat.setValorTotal(new BigDecimal("200"));
        fat.setValorFatura(new BigDecimal("200"));
        fat.setValorMinimo(new BigDecimal("50"));
        fat.setDataVencimento(LocalDateTime.now().plusDays(10));
        fat.setDataFechamento(LocalDateTime.now());
        fat.setStatus(Fatura.StatusFatura.ABERTA);
        fat.setCartaoCredito(cartaoRepository.findById(cartaoA).orElseThrow());
        fatA = faturaRepository.save(fat).getId();

        DespesaFixa df = new DespesaFixa();
        df.setUsuario(a);
        df.setDescricao("Despesa fixa secreta");
        df.setValor(new BigDecimal("99"));
        df.setDiaVencimento(5);
        despA = despesaFixaRepository.save(df).getId();

        CompraParcelada cp = new CompraParcelada();
        cp.setDescricao("Compra secreta");
        cp.setValorTotal(new BigDecimal("300"));
        cp.setValorParcela(new BigDecimal("100"));
        cp.setNumeroParcelas(3);
        cp.setParcelaAtual(1);
        cp.setDataCompra(LocalDateTime.now());
        cp.setDataPrimeiraParcela(LocalDateTime.now());
        cp.setCartaoCredito(cartaoRepository.findById(cartaoA).orElseThrow());
        cp.setCategoria(cat);
        cp.setStatusCompra(CompraParcelada.StatusCompra.ATIVA);
        compraA = compraParceladaRepository.save(cp).getId();

        ImportacaoFaturaCartao imp = new ImportacaoFaturaCartao();
        imp.setUsuario(a);
        imp.setCartaoCredito(cartaoRepository.findById(cartaoA).orElseThrow());
        imp.setBancoCartao("Teste");
        imp.setValorTotal(new BigDecimal("150"));
        imp.setItensJson("[]");
        imp.setStatus(ImportacaoFaturaCartao.Status.PENDENTE);
        imp.setNovosDetectados(0);
        imp.setDataCriacao(LocalDateTime.now());
        impA = importacaoRepository.save(imp).getId();
    }

    @Test
    void conta_leituraAlteracaoExclusao_bloqueada() throws Exception {
        assert4xx(get("/api/contas-bancarias/" + contaA));
        assert4xx(put("/api/contas-bancarias/" + contaA).content("{}").contentType(MediaType.APPLICATION_JSON));
        assert4xx(delete("/api/contas-bancarias/" + contaA));
    }

    @Test
    void cartao_leituraAlteracao_bloqueada() throws Exception {
        assert4xx(get("/api/cartoes-credito/" + cartaoA));
        assert4xx(put("/api/cartoes-credito/" + cartaoA).content("{}").contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void transacao_leituraAlteracaoExclusao_bloqueada() throws Exception {
        assert4xx(get("/api/transacoes/" + txA));
        assert4xx(put("/api/transacoes/" + txA).content("{}").contentType(MediaType.APPLICATION_JSON));
        assert4xx(delete("/api/transacoes/" + txA));
    }

    @Test
    void orcamento_exclusao_bloqueada() throws Exception {
        assert4xx(delete("/api/orcamentos/" + orcA));
    }

    @Test
    void meta_alteracao_bloqueada() throws Exception {
        assert4xx(put("/api/metas/" + metaA).content("{}").contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void agendamento_acoes_bloqueadas() throws Exception {
        assert4xx(get("/api/agendamentos-pagamentos/" + agA));
        assert4xx(put("/api/agendamentos-pagamentos/" + agA).content("{}").contentType(MediaType.APPLICATION_JSON));
        assert4xx(post("/api/agendamentos-pagamentos/" + agA + "/executar"));
        assert4xx(delete("/api/agendamentos-pagamentos/" + agA));
    }

    @Test
    void assinatura_alteracao_bloqueada() throws Exception {
        assert4xx(put("/api/assinaturas/" + assA).content("{}").contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void exportacaoCsv_filtroContaAlheia_bloqueada() throws Exception {
        assert4xx(get("/api/exportacao/csv/transacoes?contaId=" + contaA));
    }

    @Test
    void motorFinanceiro_deB_naoExpoeDadosDeA() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/motor-financeiro").header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk())
            .andReturn();
        assertFalse(res.getResponse().getContentAsString().contains("Secreta HA"));
        assertFalse(res.getResponse().getContentAsString().contains("Tx Secreta"));
    }

    @Test
    void respostaErro_semStackTrace() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/transacoes/" + txA).header("Authorization", "Bearer " + tokenB))
            .andExpect(status().is4xxClientError())
            .andReturn();
        assertFalse(res.getResponse().getContentAsString().contains("at com.consumoesperto"));
    }

    @Test
    void categoria_alteracaoExclusao_bloqueada() throws Exception {
        assert4xx(put("/api/categorias/" + catA).content("{}").contentType(MediaType.APPLICATION_JSON));
        assert4xx(delete("/api/categorias/" + catA));
    }

    @Test
    void fatura_leituraAlteracaoExclusao_bloqueada() throws Exception {
        assert4xx(get("/api/faturas/" + fatA));
        assert4xx(put("/api/faturas/" + fatA).content("{}").contentType(MediaType.APPLICATION_JSON));
        assert4xx(delete("/api/faturas/" + fatA));
    }

    @Test
    void relatorio_mensal_pdf_deB_naoExpoeDadosDeA() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/relatorios/mensal.pdf?ano=" + YearMonth.now().getYear()
                + "&mes=" + YearMonth.now().getMonthValue()).header("Authorization", "Bearer " + tokenB))
            .andExpect(status().is4xxClientError())
            .andReturn();
        assertFalse(res.getResponse().getContentAsString().contains("Tx Secreta"));
    }

    @Test
    void relatorio_mensal_json_isolado() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/relatorios/mensal?ano=" + YearMonth.now().getYear()
                + "&mes=" + YearMonth.now().getMonthValue()).header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk())
            .andReturn();
        assertFalse(res.getResponse().getContentAsString().contains("Tx Secreta"));
    }

    @Test
    void agendamento_historico_naoExpoeA() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/agendamentos-pagamentos/historico").header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk())
            .andReturn();
        assertFalse(res.getResponse().getContentAsString().contains("Benef HA"));
    }

    @Test
    void despesaFixa_alteracaoExclusao_bloqueada() throws Exception {
        assert4xx(put("/api/despesas-fixas/" + despA).content("{}").contentType(MediaType.APPLICATION_JSON));
        assert4xx(delete("/api/despesas-fixas/" + despA));
    }

    @Test
    void compraParcelada_leituraAlteracao_bloqueada() throws Exception {
        assert4xx(get("/api/compras-parceladas/" + compraA));
        assert4xx(put("/api/compras-parceladas/" + compraA).content("{}").contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void importacaoFatura_exclusao_bloqueada() throws Exception {
        assert4xx(delete("/api/importacoes/faturas/" + impA));
    }

    @Test
    void meta_leituraExclusao_bloqueada() throws Exception {
        assert4xx(get("/api/metas/" + metaA));
        assert4xx(delete("/api/metas/" + metaA));
    }

    @Test
    void assinatura_exclusao_bloqueada() throws Exception {
        assert4xx(delete("/api/assinaturas/" + assA));
    }

    @Test
    void exportacaoCsv_filtroCartaoAlheio_bloqueada() throws Exception {
        assert4xx(get("/api/exportacao/csv/transacoes?cartaoId=" + cartaoA));
    }

    private void assert4xx(MockHttpServletRequestBuilder req) throws Exception {
        MvcResult res = mockMvc.perform(req.header("Authorization", "Bearer " + tokenB))
            .andExpect(status().is4xxClientError())
            .andReturn();
        assertFalse(res.getResponse().getContentAsString().contains("RuntimeException"));
    }

    private Usuario saveUser(String username, String email) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        u.setNome("HA " + username);
        return usuarioRepository.save(u);
    }

    private String bearerFor(Usuario usuario) {
        UserPrincipal principal = UserPrincipal.create(usuario);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtTokenProvider.generateToken(auth);
    }
}
