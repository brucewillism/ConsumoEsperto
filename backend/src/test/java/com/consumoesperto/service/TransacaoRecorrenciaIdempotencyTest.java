package com.consumoesperto.service;

import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.Transacao;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.util.AppTimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransacaoRecorrenciaIdempotencyTest {

    @Mock private TransacaoRepository transacaoRepository;
    @InjectMocks private TransacaoRecorrenciaService service;

    @Test
    void segundaExecucao_mesmaCompetencia_naoInsereDuplicata() {
        LocalDate hoje = AppTimeZone.hoje();
        Transacao original = new Transacao();
        original.setId(1L);
        original.setRecorrente(true);
        original.setProximaExecucao(hoje);
        original.setFrequencia(Transacao.FrequenciaRecorrencia.MENSAL);
        original.setDescricao("Aluguel");
        original.setValor(new BigDecimal("1500.00"));
        Usuario u = new Usuario();
        u.setId(10L);
        original.setUsuario(u);
        Categoria cat = new Categoria();
        cat.setId(5L);
        original.setCategoria(cat);

        when(transacaoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(original));
        when(transacaoRepository.findByUsuarioIdAndDescricaoAndDataTransacaoAndValor(
            10L, "Aluguel", hoje.atStartOfDay(), new BigDecimal("1500.00")
        )).thenReturn(List.of(new Transacao()));

        service.processarUma(1L, hoje);

        verify(transacaoRepository, times(1)).save(original);
    }
}
