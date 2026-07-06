package com.consumoesperto.regressao;

import com.consumoesperto.dto.TransacaoDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.service.CerebroSemanticoService;
import com.consumoesperto.service.ContaBancariaService;
import com.consumoesperto.service.FaturaService;
import com.consumoesperto.service.FinancialProactiveService;
import com.consumoesperto.service.SaldoMovimentacaoService;
import com.consumoesperto.service.SaldoService;
import com.consumoesperto.service.ScoreService;
import com.consumoesperto.service.TransacaoSemanticaIndexService;
import com.consumoesperto.service.TransacaoService;

/** ST-05: CRUD genérico bloqueia PAGAMENTO_FATURA. */
@ExtendWith(MockitoExtension.class)
class PagamentoFaturaCrudBloqueadoRegressionTest {

    @Mock private TransacaoRepository transacaoRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private SaldoService saldoService;
    @Mock private FaturaRepository faturaRepository;
    @Mock private com.consumoesperto.repository.CartaoCreditoRepository cartaoCreditoRepository;
    @Mock private FaturaService faturaService;
    @Mock private FinancialProactiveService financialProactiveService;
    @Mock private ScoreService scoreService;
    @Mock private TransacaoSemanticaIndexService transacaoSemanticaIndexService;
    @Mock private ContaBancariaService contaBancariaService;
    @Mock private SaldoMovimentacaoService saldoMovimentacaoService;
    @Mock private CerebroSemanticoService cerebroSemanticoService;

    @InjectMocks private TransacaoService transacaoService;

    @Test
    void criarPagamentoFaturaViaCrud_lancaExcecao() {
        TransacaoDTO dto = new TransacaoDTO();
        dto.setDescricao("Pagamento");
        dto.setValor(new java.math.BigDecimal("100"));
        dto.setTipoTransacao(TransacaoDTO.TipoTransacao.PAGAMENTO_FATURA);
        dto.setFaturaId(1L);

        assertThrows(IllegalArgumentException.class, () -> transacaoService.criarTransacao(dto, 1L));
        verify(transacaoRepository, never()).save(any());
    }
}
