package com.consumoesperto.dashboard;

import com.consumoesperto.autonomy.LiveInvoiceProjectionService;
import com.consumoesperto.autonomy.SafeToSpendService;
import com.consumoesperto.dto.UsuarioScoreDTO;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.MetaFinanceiraRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.AgendamentoPagamentoService;
import com.consumoesperto.service.AssinaturaRecorrenteService;
import com.consumoesperto.service.ComposicaoProjecaoMesService;
import com.consumoesperto.service.DespesaFixaService;
import com.consumoesperto.service.OrcamentoService;
import com.consumoesperto.service.PrevisaoFluxoCaixaService;
import com.consumoesperto.service.SaldoService;
import com.consumoesperto.service.ScoreService;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.service.TransacaoService;
import com.consumoesperto.util.AppTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardViewServiceTest {

    @Mock private TransacaoService transacaoService;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private SaldoService saldoService;
    @Mock private ComposicaoProjecaoMesService composicao;
    @Mock private LiveInvoiceProjectionService liveInvoice;
    @Mock private FaturaRepository faturaRepository;
    @Mock private SafeToSpendService safeToSpendService;
    @Mock private DespesaFixaService despesaFixaService;
    @Mock private AssinaturaRecorrenteService assinaturaRecorrenteService;
    @Mock private AgendamentoPagamentoService agendamentoPagamentoService;
    @Mock private OrcamentoService orcamentoService;
    @Mock private ScoreService scoreService;
    @Mock private MetaFinanceiraRepository metaFinanceiraRepository;
    @Mock private PrevisaoFluxoCaixaService previsaoFluxoCaixaService;
    @Mock private UsuarioRepository usuarioRepository;

    private DashboardViewService service;

    @BeforeEach
    void setup() {
        service = new DashboardViewService(
            transacaoService, transacaoRepository, saldoService, composicao, liveInvoice,
            faturaRepository, safeToSpendService, despesaFixaService, assinaturaRecorrenteService,
            agendamentoPagamentoService, orcamentoService, scoreService, metaFinanceiraRepository,
            previsaoFluxoCaixaService, usuarioRepository
        );
        YearMonth ym = AppTimeZone.mesAtual();
        when(transacaoService.resumoFinanceiroMes(eq(1L), eq(ym), eq(true))).thenReturn(Map.of(
            "totalReceitas", new BigDecimal("4000.00"),
            "totalDespesas", new BigDecimal("1200.00")
        ));
        when(saldoService.calcularProjecaoMes(1L)).thenReturn(new SaldoService.ProjecaoMesCaixa(
            ym, new BigDecimal("8000.00"), new BigDecimal("1200.00"), new BigDecimal("2000.00"),
            new BigDecimal("4000.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
            new BigDecimal("1500.00"), new BigDecimal("6500.00"), 10, ym.lengthOfMonth()
        ));
        when(composicao.partesObrigacoesMes(eq(1L), eq(ym), any())).thenReturn(
            new ComposicaoProjecaoMesService.PartesObrigacoesMes(
                new BigDecimal("100.00"),
                new BigDecimal("300.00"),
                new BigDecimal("200.00"),
                BigDecimal.ZERO,
                new BigDecimal("200.00")
            ));
        when(liveInvoice.atuais(1L)).thenReturn(List.of(Map.of(
            "id", 9L,
            "cartaoNome", "Nubank",
            "valorProjetado", new BigDecimal("300.00"),
            "dataVencimento", ym.atDay(20).atStartOfDay()
        )));
        when(safeToSpendService.calcular(1L)).thenReturn(Map.of(
            "safeToSpend", new BigDecimal("900.00"),
            "diasRestantesNoMes", 18
        ));
        when(despesaFixaService.listar(1L)).thenReturn(List.of());
        when(assinaturaRecorrenteService.listar(1L)).thenReturn(List.of());
        when(agendamentoPagamentoService.listar(1L)).thenReturn(List.of());
        when(orcamentoService.listar(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(metaFinanceiraRepository.findByUsuarioIdOrderByPrioridadeDescDataCriacaoDesc(1L)).thenReturn(List.of());
        when(transacaoRepository.findDespesasByUsuarioIdAndPeriodoGroupByCategoria(anyLong(), any(), any()))
            .thenReturn(List.of());
        when(transacaoRepository.findEmprestimoIdsByUsuario(1L)).thenReturn(List.of("emp-1"));
        when(faturaRepository.sumValorFaturasPendentesByUsuarioId(1L)).thenReturn(new BigDecimal("300.00"));
        when(saldoService.patrimonioLiquido(1L)).thenReturn(new BigDecimal("8500.00"));
        when(saldoService.saldoLiquidezImediata(1L)).thenReturn(new BigDecimal("2000.00"));
        when(transacaoRepository.sumPassivoEmprestimoAtivo(1L)).thenReturn(new BigDecimal("1500.00"));
        UsuarioScoreDTO score = new UsuarioScoreDTO();
        score.setScore(720);
        score.setNivel("Ouro");
        when(scoreService.obter(1L)).thenReturn(score);
    }

    @Test
    void semViewParametro_fromDevolveGeneral() {
        assertEquals(DashboardViewMode.GENERAL, DashboardViewMode.from(null));
        assertEquals(DashboardViewMode.GENERAL, DashboardViewMode.from(""));
        assertEquals(DashboardViewMode.MONTHLY, DashboardViewMode.from("monthly"));
    }

    @Test
    void monthly_emprestimoMostraParcelaDoMesSemSaldoDevedor() {
        Transacao parcela = parcela("emp-1", "500.00", false, 3, 12);
        when(transacaoRepository.findParcelasEmprestimoNoMes(eq(1L), any(), any())).thenReturn(List.of(parcela));

        DashboardViewDTO dto = service.montar(1L, DashboardViewMode.MONTHLY);
        assertEquals("MONTHLY", dto.getViewMode());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itens = (List<Map<String, Object>>) dto.getCards().get("itens");
        assertTrue(itens.stream().anyMatch(c -> "Parcela do mês".equals(c.get("titulo"))));
        assertTrue(itens.stream().noneMatch(c -> String.valueOf(c.get("titulo")).contains("Saldo devedor")));
        @SuppressWarnings("unchecked")
        Map<String, Object> emp = (Map<String, Object>) dto.getCompromissos().get("emprestimos");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) emp.get("itens");
        assertEquals(new BigDecimal("500.00"), rows.get(0).get("valor"));
        assertFalse(rows.get(0).containsKey("saldoDevedor"));
        assertFalse(rows.get(0).containsKey("totalContratado"));
    }

    @Test
    void monthly_consignadoEmFolhaApareceSinalizado() {
        Transacao folha = parcela("emp-folha", "400.00", true, 1, 24);
        when(transacaoRepository.findParcelasEmprestimoNoMes(eq(1L), any(), any())).thenReturn(List.of(folha));

        DashboardViewDTO dto = service.montar(1L, DashboardViewMode.MONTHLY);
        @SuppressWarnings("unchecked")
        Map<String, Object> emp = (Map<String, Object>) dto.getCompromissos().get("emprestimos");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) emp.get("itens");
        assertEquals(true, rows.get(0).get("descontoEmFolha"));
        assertTrue(String.valueOf(rows.get(0).get("indicacao")).toLowerCase().contains("folha"));
    }

    @Test
    void general_emprestimoMostraSaldoDevedorETotalContratado() {
        Transacao credito = new Transacao();
        credito.setEmprestimoId("emp-1");
        credito.setTipoTransacao(Transacao.TipoTransacao.RECEITA);
        credito.setStatusConferencia(Transacao.StatusConferencia.CONFIRMADA);
        credito.setValor(new BigDecimal("10000.00"));
        credito.setDescricao("Empréstimo consignado recebido");
        Transacao p1 = parcela("emp-1", "500.00", true, 1, 20);
        Transacao p2 = parcela("emp-1", "500.00", true, 2, 20);
        when(transacaoRepository.findByUsuarioIdAndEmprestimoIdOrderByDataTransacaoAsc(1L, "emp-1"))
            .thenReturn(List.of(credito, p1, p2));

        DashboardViewDTO dto = service.montar(1L, DashboardViewMode.GENERAL);
        assertEquals("GENERAL", dto.getViewMode());
        @SuppressWarnings("unchecked")
        Map<String, Object> emp = (Map<String, Object>) dto.getCompromissos().get("emprestimos");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) emp.get("itens");
        assertEquals(new BigDecimal("1000.00"), rows.get(0).get("saldoDevedor"));
        assertEquals(new BigDecimal("10000.00"), rows.get(0).get("totalContratado"));
        assertEquals(2, rows.get(0).get("parcelasRestantes"));
        assertEquals(new BigDecimal("500.00"), rows.get(0).get("valorParcela"));
        assertEquals("CONSIGNADO", rows.get(0).get("tipo"));
        assertTrue(itensTemTitulo(dto, "Saldo devedor") || itensTemTitulo(dto, "Passivos (saldo devedor)"));
        assertTrue(itensTemTitulo(dto, "Dívida total"));
        assertFalse(itensTemTitulo(dto, "Safe-to-spend do mês"));
        assertTrue(itensTemTitulo(dto, "Folga patrimonial"));
    }

    @Test
    void r1_compraParceladaNoCartaoNaoCaiEmEmprestimo() {
        Transacao cartao = new Transacao();
        cartao.setGrupoParcelaId("grp-card");
        Fatura fatura = new Fatura();
        cartao.setFatura(fatura);
        cartao.setValor(new BigDecimal("150.00"));
        Transacao emprestimo = parcela("emp-1", "200.00", false, 1, 10);

        assertEquals(DashboardObligationTaxonomy.Bucket.CARTAO_FATURA, DashboardObligationTaxonomy.of(cartao));
        assertEquals(DashboardObligationTaxonomy.Bucket.EMPRESTIMO, DashboardObligationTaxonomy.of(emprestimo));

        when(transacaoRepository.findParcelasEmprestimoNoMes(eq(1L), any(), any())).thenReturn(List.of(emprestimo));
        DashboardViewDTO monthly = service.montar(1L, DashboardViewMode.MONTHLY);
        assertEquals(Boolean.TRUE, monthly.getMetricas().get("blocosBatemComTotal"));
        assertEquals(0, new BigDecimal("600.00").compareTo((BigDecimal) monthly.getMetricas().get("comprometidoMes")));
        @SuppressWarnings("unchecked")
        Map<String, Object> emp = (Map<String, Object>) monthly.getCompromissos().get("emprestimos");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) emp.get("itens");
        assertEquals(1, rows.size());
        assertEquals("emp-1", rows.get(0).get("emprestimoId"));
    }

    @Test
    void r4_ativosMenosPassivosIgualLiquido() {
        DashboardViewDTO dto = service.montar(1L, DashboardViewMode.GENERAL);
        assertEquals(Boolean.TRUE, dto.getMetricas().get("identidadePatrimonialOk"));
        BigDecimal ativos = (BigDecimal) dto.getMetricas().get("ativos");
        BigDecimal passivos = (BigDecimal) dto.getMetricas().get("passivos");
        BigDecimal liquido = (BigDecimal) dto.getMetricas().get("patrimonioLiquido");
        assertEquals(0, ativos.subtract(passivos).compareTo(liquido));
        BigDecimal divida = (BigDecimal) dto.getMetricas().get("dividaTotal");
        BigDecimal cartao = (BigDecimal) dto.getMetricas().get("exposicaoCartao");
        assertEquals(0, passivos.add(cartao).compareTo(divida));
        BigDecimal reservas = (BigDecimal) dto.getMetricas().get("reservas");
        BigDecimal investido = (BigDecimal) dto.getMetricas().get("totalInvestido");
        assertEquals(0, ativos.subtract(reservas).max(BigDecimal.ZERO).compareTo(investido));
        assertEquals(ativos, dto.getMetricas().get("totalEmContas"));
        assertTrue(itensTemTitulo(dto, "Ativos (contas + investimentos)"));
        assertTrue(itensTemTitulo(dto, "Total em contas"));
        assertTrue(itensTemTitulo(dto, "Total investido"));
    }

    @Test
    void monthly_faturaDeOutroMesNaoEntraNaVisao() {
        YearMonth ym = AppTimeZone.mesAtual();
        when(liveInvoice.atuais(1L)).thenReturn(List.of(
            Map.of(
                "id", 9L,
                "cartaoNome", "Nubank",
                "valorProjetado", new BigDecimal("300.00"),
                "dataVencimento", ym.atDay(20).atStartOfDay()
            ),
            Map.of(
                "id", 10L,
                "cartaoNome", "Inter",
                "valorProjetado", new BigDecimal("999.00"),
                "dataVencimento", ym.plusMonths(1).atDay(5).atStartOfDay()
            )
        ));
        DashboardViewDTO dto = service.montar(1L, DashboardViewMode.MONTHLY);
        assertEquals(0, new BigDecimal("300.00").compareTo((BigDecimal) dto.getMetricas().get("faturaMesExibida")));
        @SuppressWarnings("unchecked")
        Map<String, Object> fat = (Map<String, Object>) dto.getCompromissos().get("cartaoFatura");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itens = (List<Map<String, Object>>) fat.get("itens");
        assertEquals(1, itens.size());
        assertEquals("Nubank", itens.get(0).get("cartaoNome"));
    }

    @Test
    void monthly_consignadoQueDebitaContaNaoTemIndicacaoFolha() {
        Transacao parcela = parcela("emp-caixa", "350.00", false, 2, 10);
        when(transacaoRepository.findParcelasEmprestimoNoMes(eq(1L), any(), any())).thenReturn(List.of(parcela));
        DashboardViewDTO dto = service.montar(1L, DashboardViewMode.MONTHLY);
        @SuppressWarnings("unchecked")
        Map<String, Object> emp = (Map<String, Object>) dto.getCompromissos().get("emprestimos");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) emp.get("itens");
        assertEquals(false, rows.get(0).get("descontoEmFolha"));
        assertTrue(String.valueOf(rows.get(0).get("indicacao")).toLowerCase().contains("debita"));
    }

    @Test
    void general_emprestimoPessoalTipoPessoal() {
        Transacao credito = new Transacao();
        credito.setEmprestimoId("emp-pes");
        credito.setTipoTransacao(Transacao.TipoTransacao.RECEITA);
        credito.setStatusConferencia(Transacao.StatusConferencia.CONFIRMADA);
        credito.setValor(new BigDecimal("4000.00"));
        credito.setDescricao("Empréstimo pessoal recebido");
        Transacao p1 = parcela("emp-pes", "400.00", false, 1, 10);
        when(transacaoRepository.findEmprestimoIdsByUsuario(1L)).thenReturn(List.of("emp-pes"));
        when(transacaoRepository.findByUsuarioIdAndEmprestimoIdOrderByDataTransacaoAsc(1L, "emp-pes"))
            .thenReturn(List.of(credito, p1));
        DashboardViewDTO dto = service.montar(1L, DashboardViewMode.GENERAL);
        @SuppressWarnings("unchecked")
        Map<String, Object> emp = (Map<String, Object>) dto.getCompromissos().get("emprestimos");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) emp.get("itens");
        assertEquals("PESSOAL", rows.get(0).get("tipo"));
        assertEquals(false, rows.get(0).get("descontoEmFolha"));
        assertEquals(new BigDecimal("400.00"), rows.get(0).get("saldoDevedor"));
        assertEquals(new BigDecimal("4000.00"), rows.get(0).get("totalContratado"));
    }

    @Test
    void preferenciaNaoPersistidaDefaultMonthly() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(new Usuario()));
        Map<String, Object> pref = service.lerPreferencia(1L);
        assertEquals("MONTHLY", pref.get("viewMode"));
        assertEquals(false, pref.get("persistida"));
    }

    @Test
    void persistirPreferenciaGravaNoUsuario() {
        Usuario u = new Usuario();
        u.setId(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(u));
        assertEquals(DashboardViewMode.MONTHLY, service.persistirPreferencia(1L, DashboardViewMode.MONTHLY));
        assertEquals("MONTHLY", u.getUltimaVisaoDashboard());
    }

    @Test
    void insightsNaoMisturamModos() {
        DashboardViewDTO mensal = service.montar(1L, DashboardViewMode.MONTHLY);
        DashboardViewDTO geral = service.montar(1L, DashboardViewMode.GENERAL);
        assertTrue(mensal.getInsights().stream().anyMatch(i -> "PROJECAO_FIM_MES".equals(i.get("codigo"))));
        assertTrue(mensal.getInsights().stream().noneMatch(i -> "EVOLUCAO_PATRIMONIAL".equals(i.get("codigo"))));
        assertTrue(geral.getInsights().stream().anyMatch(i -> "EVOLUCAO_PATRIMONIAL".equals(i.get("codigo"))));
        assertTrue(geral.getInsights().stream().noneMatch(i -> "SAFE_TO_SPEND_BAIXO".equals(i.get("codigo"))
            || "PROJECAO_FIM_MES".equals(i.get("codigo"))));
    }

    private static boolean itensTemTitulo(DashboardViewDTO dto, String titulo) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itens = (List<Map<String, Object>>) dto.getCards().get("itens");
        return itens.stream().anyMatch(c -> titulo.equals(c.get("titulo")));
    }

    private static Transacao parcela(String empId, String valor, boolean folha, int atual, int total) {
        Transacao t = new Transacao();
        t.setEmprestimoId(empId);
        t.setTipoTransacao(Transacao.TipoTransacao.DESPESA);
        t.setStatusConferencia(Transacao.StatusConferencia.PREVISTO);
        t.setValor(new BigDecimal(valor));
        t.setDescontoEmFolha(folha);
        t.setParcelaAtual(atual);
        t.setTotalParcelas(total);
        t.setDescricao(folha ? "Parcela consignado (" + atual + "/" + total + ")" : "Parcela pessoal");
        t.setDataTransacao(LocalDateTime.now());
        t.setExcluido(false);
        return t;
    }
}
