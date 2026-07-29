package com.consumoesperto.service;

import com.consumoesperto.dto.CategoriaDTO;
import com.consumoesperto.dto.ExportacaoTransacaoFiltro;
import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.exception.ResourceNotFoundException;
import com.consumoesperto.model.AgendamentoPagamento;
import com.consumoesperto.model.AssinaturaRecorrente;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.ContaBancaria;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.model.MetaFinanceira;
import com.consumoesperto.model.Orcamento;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.AgendamentoPagamentoRepository;
import com.consumoesperto.repository.AssinaturaRecorrenteRepository;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.MetaFinanceiraRepository;
import com.consumoesperto.repository.OrcamentoRepository;
import com.consumoesperto.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.persistence.EntityManager;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Isolamento horizontal (IDOR): usuário B não acessa recursos do usuário A.
 */
@ExtendWith(MockitoExtension.class)
class HorizontalAccessControlTest {

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;
    private static final Long ALIEN_ID = 999L;

    @Mock private ContaBancariaRepository contaBancariaRepository;
    @Mock private CartaoCreditoRepository cartaoCreditoRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private MetaFinanceiraRepository metaFinanceiraRepository;
    @Mock private OrcamentoRepository orcamentoRepository;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private FaturaRepository faturaRepository;
    @Mock private AssinaturaRecorrenteRepository assinaturaRecorrenteRepository;
    @Mock private AgendamentoPagamentoRepository agendamentoPagamentoRepository;
    @Mock private EntityManager entityManager;
    @Mock private UsuarioService usuarioService;
    @Mock private TextMatcherService textMatcherService;

    @InjectMocks private ContaBancariaService contaBancariaService;
    @InjectMocks private CartaoCreditoService cartaoCreditoService;
    @InjectMocks private CategoriaService categoriaService;
    @InjectMocks private MetaFinanceiraService metaFinanceiraService;
    @InjectMocks private OrcamentoService orcamentoService;
    @InjectMocks private TransacaoExportacaoQueryService transacaoExportacaoQueryService;
    @InjectMocks private TransacaoService transacaoService;

    private Usuario usuarioA;

    @BeforeEach
    void setUp() {
        usuarioA = new Usuario();
        usuarioA.setId(USER_A);
    }

    @Test
    void conta_leitura_usuarioB_naoAcessaContaDeA() {
        when(contaBancariaRepository.findByIdAndUsuarioId(ALIEN_ID, USER_B)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> contaBancariaService.buscarEntidade(ALIEN_ID, USER_B));
    }

    @Test
    void cartao_leitura_usuarioB_naoAcessaCartaoDeA() {
        when(cartaoCreditoRepository.findByIdAndUsuarioId(ALIEN_ID, USER_B)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> cartaoCreditoService.buscarPorId(ALIEN_ID, USER_B));
    }

    @Test
    void categoria_edicao_usuarioB_naoAlteraCategoriaDeA() {
        Categoria cat = new Categoria();
        cat.setId(ALIEN_ID);
        cat.setUsuario(usuarioA);
        when(categoriaRepository.findById(ALIEN_ID)).thenReturn(Optional.of(cat));
        CategoriaDTO dto = new CategoriaDTO();
        dto.setNome("Hack");
        assertThrows(ResourceNotFoundException.class, () -> categoriaService.atualizar(USER_B, ALIEN_ID, dto));
    }

    @Test
    void meta_leitura_usuarioB_naoVeMetaDeA() {
        when(metaFinanceiraRepository.findByIdAndUsuarioId(ALIEN_ID, USER_B)).thenReturn(Optional.empty());
        assertTrue(metaFinanceiraService.buscar(ALIEN_ID, USER_B).isEmpty());
    }

    @Test
    void orcamento_exclusao_usuarioB_naoExcluiOrcamentoDeA() {
        when(orcamentoRepository.findByIdAndUsuarioId(ALIEN_ID, USER_B)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> orcamentoService.excluir(USER_B, ALIEN_ID));
    }

    @Test
    void transacao_leitura_usuarioB_naoAcessaTransacaoDeA() {
        Transacao t = new Transacao();
        t.setId(ALIEN_ID);
        t.setUsuario(usuarioA);
        when(transacaoRepository.findById(ALIEN_ID)).thenReturn(Optional.of(t));
        assertThrows(RuntimeException.class, () -> transacaoService.buscarPorId(ALIEN_ID, USER_B));
    }

    @Test
    void fatura_leitura_usuarioB_naoAcessaFaturaDeA() {
        when(faturaRepository.findByIdAndCartaoCreditoUsuarioId(ALIEN_ID, USER_B)).thenReturn(Optional.empty());
        assertTrue(faturaRepository.findByIdAndCartaoCreditoUsuarioId(ALIEN_ID, USER_B).isEmpty());
    }

    @Test
    void assinatura_leitura_usuarioB_naoAcessaAssinaturaDeA() {
        when(assinaturaRecorrenteRepository.findByIdAndUsuarioId(ALIEN_ID, USER_B)).thenReturn(Optional.empty());
        assertTrue(assinaturaRecorrenteRepository.findByIdAndUsuarioId(ALIEN_ID, USER_B).isEmpty());
    }

    @Test
    void agendamento_leitura_usuarioB_naoVeAgendamentoDeA() {
        when(agendamentoPagamentoRepository.findByIdAndUsuarioId(ALIEN_ID, USER_B)).thenReturn(Optional.empty());
        assertTrue(agendamentoPagamentoRepository.findByIdAndUsuarioId(ALIEN_ID, USER_B).isEmpty());
    }

    @Test
    void exportacaoCsv_filtroContaAlheia_rejeita() {
        ExportacaoTransacaoFiltro f = new ExportacaoTransacaoFiltro();
        f.setContaId(ALIEN_ID);
        when(contaBancariaRepository.findByIdAndUsuarioId(ALIEN_ID, USER_B)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> transacaoExportacaoQueryService.buscarParaExportacao(USER_B, f));
    }

    @Test
    void exportacaoCsv_filtroCartaoAlheio_rejeita() {
        ExportacaoTransacaoFiltro f = new ExportacaoTransacaoFiltro();
        f.setCartaoId(ALIEN_ID);
        when(cartaoCreditoRepository.findByIdAndUsuarioId(ALIEN_ID, USER_B)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> transacaoExportacaoQueryService.buscarParaExportacao(USER_B, f));
    }

    @Test
    void exportacaoCsv_filtroCategoriaAlheia_rejeita() {
        ExportacaoTransacaoFiltro f = new ExportacaoTransacaoFiltro();
        f.setCategoriaId(ALIEN_ID);
        when(categoriaRepository.findByIdAndUsuarioId(ALIEN_ID, USER_B)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> transacaoExportacaoQueryService.buscarParaExportacao(USER_B, f));
    }

    @Test
    void exportacaoCsv_idInvalido_naoGeraExportacaoGlobal() {
        ExportacaoTransacaoFiltro f = new ExportacaoTransacaoFiltro();
        f.setContaId(-1L);
        when(contaBancariaRepository.findByIdAndUsuarioId(-1L, USER_B)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> transacaoExportacaoQueryService.buscarParaExportacao(USER_B, f));
    }
}
