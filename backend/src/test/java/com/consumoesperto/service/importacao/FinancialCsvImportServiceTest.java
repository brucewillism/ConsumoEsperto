package com.consumoesperto.service.importacao;

import com.consumoesperto.dto.ConfirmarImportacaoFaturaRequest;
import com.consumoesperto.dto.EscolhaRecursoImportacaoRequest;
import com.consumoesperto.dto.ImportacaoConfirmacaoDTO;
import com.consumoesperto.dto.ImportacaoFaturaDTO;
import com.consumoesperto.dto.TransacaoDTO;
import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.FinancialImportFileType;
import com.consumoesperto.model.OrigemTransacao;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.FaturaService;
import com.consumoesperto.service.TransacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV no fluxo de importação: competência via {@link FaturaService#resolverFaturaParaCompra},
 * total da fatura via soma das transações, sem gerar parcelas futuras do CSV.
 */
@SpringBootTest(properties = "consumoesperto.fatura.dias-entre-fechamento-e-vencimento=10")
@ActiveProfiles("test")
@Transactional
class FinancialCsvImportServiceTest {

    @Autowired private FinancialCsvImportService csvImportService;
    @Autowired private FinancialImportFileTypeDetector detector;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CartaoCreditoRepository cartaoCreditoRepository;
    @Autowired private ContaBancariaRepository contaBancariaRepository;
    @Autowired private FaturaRepository faturaRepository;
    @Autowired private TransacaoRepository transacaoRepository;
    @Autowired private TransacaoService transacaoService;
    @Autowired private FaturaService faturaService;

    private Usuario usuario;
    private Usuario usuarioB;
    private CartaoCredito cartao;
    private ContaBancaria conta;

    @BeforeEach
    void seed() {
        usuario = salvarUsuario("csv_a_");
        usuarioB = salvarUsuario("csv_b_");
        cartao = cartaoComVencimento(usuario, 14);
        conta = conta(usuario, "Conta principal", new BigDecimal("20000.00"));
        conta(usuarioB, "Conta B", new BigDecimal("1000.00"));
        cartaoComVencimento(usuarioB, 10);
    }

    @Test
    void csvConta_geraTransacoesNaContaSemFatura() {
        String csv = """
            Data;Histórico;Valor
            10/08/2031;PIX enviado mercado;50,00
            11/08/2031;Salário;3000,00
            """;
        ImportacaoFaturaDTO preview = processar(csv, "extrato.csv", "CONTA", conta.getId(), null);
        assertEquals(FinancialImportFileType.BANK_STATEMENT_CSV.name(), preview.getTipoArquivo());
        ImportacaoConfirmacaoDTO r = confirmarTodos(preview.getId());
        assertEquals(2, r.getCriadas());
        List<Transacao> txs = transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(usuario.getId());
        assertEquals(2, txs.size());
        assertTrue(txs.stream().allMatch(t -> t.getFatura() == null));
        assertTrue(txs.stream().anyMatch(t -> t.getContaBancaria() != null
            && t.getContaBancaria().getId().equals(conta.getId())));
    }

    @Test
    void csvCartao_compraNoPeriodoEForaENoFechamento_duasFaturas() {
        Fatura faturaA = novaFaturaAberta(cartao, LocalDate.of(2031, 9, 14));
        BigDecimal antes = nz(faturaA.getValorTotal());
        String csv = """
            Data;Estabelecimento;Valor
            10/08/2031;SUPERMERCADO;100,00
            20/08/2031;POSTO;150,00
            03/09/2031;RESTAURANTE;250,00
            04/09/2031;PADARIA;10,00
            05/09/2031;FARMACIA;80,00
            """;
        ImportacaoFaturaDTO preview = processar(csv, "fatura-cartao.csv", "CARTAO", null, cartao.getId());
        assertTrue(preview.getItens().stream().anyMatch(i ->
            i.getDescricao().contains("SUPERMERCADO") && i.getFaturaPrevistaLabel() != null));
        ImportacaoConfirmacaoDTO r = confirmarTodos(preview.getId());
        assertEquals(5, r.getCriadas());

        faturaService.sincronizarValorFaturaComTransacoes(faturaA.getId());
        Fatura a = faturaRepository.findById(faturaA.getId()).orElseThrow();
        BigDecimal depoisA = transacaoRepository.sumDespesaConfirmadaPorFaturaId(a.getId());
        assertEquals(new BigDecimal("510.00"), depoisA);
        assertTrue(depoisA.compareTo(antes) > 0);

        Fatura proxima = faturaService.resolverFaturaParaCompra(
            usuario.getId(), cartao, LocalDateTime.of(2031, 9, 5, 10, 0));
        assertEquals(new BigDecimal("80.00"),
            transacaoRepository.sumDespesaConfirmadaPorFaturaId(proxima.getId()));
        assertTrue(r.getFaturas().size() >= 2);
    }

    @Test
    void faturaJaExistenteEAusente_reutilizaResolverDoDominio() {
        Fatura existente = novaFaturaAberta(cartao, LocalDate.of(2031, 9, 14));
        String csv = "Data;Estabelecimento;Valor\n10/08/2031;LOJA A;40,00\n05/09/2031;LOJA B;15,00\n";
        confirmarTodos(processar(csv, "cartao.csv", "CARTAO", null, cartao.getId()).getId());
        assertEquals(existente.getId(), transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(usuario.getId())
            .stream().filter(t -> t.getDescricao().contains("LOJA A")).findFirst().orElseThrow()
            .getFatura().getId());
        Transacao b = transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(usuario.getId())
            .stream().filter(t -> t.getDescricao().contains("LOJA B")).findFirst().orElseThrow();
        assertTrue(b.getFatura() != null && !b.getFatura().getId().equals(existente.getId()));
    }

    @Test
    void importacaoRepetida_zeroDuplicacao() {
        String csv = "Data;Estabelecimento;Valor\n10/08/2031;POSTO SHELL;89,90\n";
        confirmarTodos(processar(csv, "cartao.csv", "CARTAO", null, cartao.getId()).getId());
        ImportacaoConfirmacaoDTO segunda = confirmarTodos(
            processar(csv, "cartao.csv", "CARTAO", null, cartao.getId()).getId());
        assertEquals(0, segunda.getCriadas());
        assertTrue(segunda.getDuplicadas() + segunda.getMatched() >= 1);
        assertEquals(1, transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(usuario.getId()).size());
    }

    @Test
    void capturaMobileDepoisCsv_umaTransacao() {
        TransacaoDTO dto = new TransacaoDTO();
        dto.setDescricao("POSTO SHELL");
        dto.setValor(new BigDecimal("89.90"));
        dto.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
        dto.setDataTransacao(LocalDate.of(2031, 9, 1).atStartOfDay());
        dto.setCartaoCreditoId(cartao.getId());
        dto.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
        TransacaoDTO criada = transacaoService.criarTransacao(dto, usuario.getId(), false, true, false);
        transacaoService.atualizarMetadadosIngestao(
            criada.getId(), OrigemTransacao.IOS_WALLET, "ios-1", "IOS_WALLET",
            "POSTO SHELL", "POSTO SHELL", "fp-mobile", null, null);

        String csv = "Data;Estabelecimento;Valor\n01/09/2031;POSTO SHELL;89.90\n";
        ImportacaoConfirmacaoDTO r = confirmarTodos(
            processar(csv, "cartao.csv", "CARTAO", null, cartao.getId()).getId());
        assertEquals(0, r.getCriadas());
        assertTrue(r.getMatched() >= 1);
        assertEquals(1, transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(usuario.getId()).size());
    }

    @Test
    void parcelaCsv_naoGeraFuturas() {
        String csv = "Data;Estabelecimento;Valor\n10/08/2031;LOJA PARC 01/10;100,00\n";
        confirmarTodos(processar(csv, "cartao.csv", "CARTAO", null, cartao.getId()).getId());
        List<Transacao> txs = transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(usuario.getId());
        assertEquals(1, txs.size());
        assertEquals(1, txs.get(0).getParcelaAtual());
        assertEquals(10, txs.get(0).getTotalParcelas());
    }

    @Test
    void pagamentoFaturaNoExtratoBancario_naoViraDespesaDeCartao() {
        TransacaoDTO compra = new TransacaoDTO();
        compra.setDescricao("COMPRA X");
        compra.setValor(new BigDecimal("200.00"));
        compra.setTipoTransacao(TransacaoDTO.TipoTransacao.DESPESA);
        compra.setDataTransacao(LocalDate.of(2031, 8, 10).atStartOfDay());
        compra.setCartaoCreditoId(cartao.getId());
        compra.setStatusConferencia(TransacaoDTO.StatusConferencia.CONFIRMADA);
        transacaoService.criarTransacao(compra, usuario.getId(), false, true, true);

        String csv = "Data;Histórico;Valor\n15/08/2031;PAGAMENTO FATURA CARTÃO;200,00\n";
        confirmarTodos(processar(csv, "extrato.csv", "CONTA", conta.getId(), null).getId());
        long despesasCartao = transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(usuario.getId()).stream()
            .filter(t -> t.getTipoTransacao() == Transacao.TipoTransacao.DESPESA && t.getFatura() != null)
            .count();
        long pagamentos = transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(usuario.getId()).stream()
            .filter(t -> t.getTipoTransacao() == Transacao.TipoTransacao.PAGAMENTO_FATURA)
            .count();
        assertEquals(1, despesasCartao);
        assertEquals(1, pagamentos);
    }

    @Test
    void estornoNoCartao_reduzTotalDaFatura() {
        String csv = """
            Data;Estabelecimento;Valor
            10/08/2031;LOJA X;80,00
            12/08/2031;ESTORNO LOJA X;80,00
            """;
        confirmarTodos(processar(csv, "cartao.csv", "CARTAO", null, cartao.getId()).getId());
        List<Transacao> txs = transacaoRepository.findByUsuarioIdOrderByDataTransacaoDesc(usuario.getId());
        assertEquals(2, txs.size());
        Long faturaId = txs.stream().filter(t -> t.getFatura() != null).findFirst().orElseThrow().getFatura().getId();
        faturaService.sincronizarValorFaturaComTransacoes(faturaId);
        assertEquals(0, transacaoRepository.sumDespesaConfirmadaPorFaturaId(faturaId).compareTo(BigDecimal.ZERO));
    }

    @Test
    void usuarioB_naoUsaCartaoAlheio() {
        EscolhaRecursoImportacaoRequest req = new EscolhaRecursoImportacaoRequest();
        req.setTipoRecurso("CARTAO");
        req.setCartaoCreditoId(cartao.getId());
        String csv = "Data;Descrição;Valor\n10/08/2031;LOJA;10,00\n";
        ImportacaoFaturaDTO preview = processarComo(usuarioB.getId(), csv, "x.csv", null, null, null);
        assertThrows(ResourceNotFoundException.class,
            () -> csvImportService.escolherRecurso(usuarioB.getId(), preview.getId(), req));
    }

    private ImportacaoFaturaDTO processar(
        String csv, String name, String tipoRecurso, Long contaId, Long cartaoId
    ) {
        return processarComo(usuario.getId(), csv, name, tipoRecurso, contaId, cartaoId);
    }

    private ImportacaoFaturaDTO processarComo(
        Long userId, String csv, String name, String tipoRecurso, Long contaId, Long cartaoId
    ) {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        FinancialImportDetection det = detector.detect(name, "text/csv", bytes);
        return csvImportService.processar(userId, name, bytes, det, tipoRecurso, contaId, cartaoId);
    }

    private ImportacaoConfirmacaoDTO confirmarTodos(Long importacaoId) {
        ConfirmarImportacaoFaturaRequest req = new ConfirmarImportacaoFaturaRequest();
        req.setIndices(null);
        return csvImportService.confirmar(usuario.getId(), importacaoId, req);
    }

    private Usuario salvarUsuario(String prefix) {
        String sfx = String.valueOf(System.nanoTime());
        Usuario u = new Usuario();
        u.setUsername(prefix + sfx);
        u.setEmail(prefix + sfx + "@test.local");
        u.setPassword("senha-de-teste-valida");
        u.setNome("CSV Test");
        return usuarioRepository.save(u);
    }

    private CartaoCredito cartaoComVencimento(Usuario dono, int dia) {
        CartaoCredito c = new CartaoCredito();
        c.setNome("Cartão " + dia);
        c.setBanco("Banco Teste");
        c.setNumeroCartao(String.valueOf(System.nanoTime() % 10_000));
        c.setLimiteCredito(new BigDecimal("8000"));
        c.setLimiteDisponivel(new BigDecimal("8000"));
        c.setDiaVencimento(dia);
        c.setAtivo(true);
        c.setUsuario(dono);
        return cartaoCreditoRepository.save(c);
    }

    private ContaBancaria conta(Usuario dono, String nome, BigDecimal saldo) {
        ContaBancaria c = new ContaBancaria();
        c.setNome(nome);
        c.setTipo(ContaBancaria.TipoConta.CORRENTE);
        c.setSaldoAtual(saldo);
        c.setSaldoInicial(saldo);
        c.setLimiteChequeEspecial(BigDecimal.ZERO);
        c.setAtiva(true);
        c.setPadrao(true);
        c.setUsuario(dono);
        return contaBancariaRepository.save(c);
    }

    private Fatura novaFaturaAberta(CartaoCredito c, LocalDate vencimento) {
        Fatura f = new Fatura();
        f.setCartaoCredito(c);
        f.setUsuario(c.getUsuario());
        f.setStatusFatura(Fatura.StatusFatura.ABERTA);
        f.setDataVencimento(vencimento.atTime(12, 0));
        f.setDataFechamento(vencimento.minusDays(10).atTime(12, 0));
        f.setValorFatura(BigDecimal.ZERO);
        f.setValorTotal(BigDecimal.ZERO);
        f.setValorPago(BigDecimal.ZERO);
        f.setValorMinimo(BigDecimal.ZERO);
        f.setPaga(false);
        f.setNumeroFatura("CSV-" + System.nanoTime());
        return faturaRepository.save(f);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
