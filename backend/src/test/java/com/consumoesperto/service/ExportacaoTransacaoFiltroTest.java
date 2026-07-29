package com.consumoesperto.service;

import com.consumoesperto.dto.ExportacaoTransacaoFiltro;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.ContaBancariaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportacaoTransacaoFiltroTest {

    @Mock private EntityManager entityManager;
    @Mock private ContaBancariaRepository contaBancariaRepository;
    @Mock private CartaoCreditoRepository cartaoCreditoRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private CriteriaBuilder cb;
    @Mock private CriteriaQuery<Transacao> cq;
    @Mock private Root<Transacao> root;
    @Mock private TypedQuery<Transacao> typedQuery;
    @Mock private Path<Object> path;
    @Mock private Predicate predicate;

    @InjectMocks private TransacaoExportacaoQueryService service;

    @Test
    void rejeitaContaDeOutroUsuario() {
        ExportacaoTransacaoFiltro f = new ExportacaoTransacaoFiltro();
        f.setContaId(99L);
        when(contaBancariaRepository.findByIdAndUsuarioId(99L, 1L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.buscarParaExportacao(1L, f));
    }

    @Test
    void rejeitaIntervaloInvalido() {
        ExportacaoTransacaoFiltro f = new ExportacaoTransacaoFiltro();
        f.setDataInicio(LocalDate.of(2026, 7, 10));
        f.setDataFim(LocalDate.of(2026, 7, 1));
        assertThrows(IllegalArgumentException.class, () -> service.buscarParaExportacao(1L, f));
    }
}
