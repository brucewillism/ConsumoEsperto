package com.consumoesperto.service.jarvis;

import com.consumoesperto.config.JarvisPerformanceProperties;
import com.consumoesperto.service.JarvisContextoFinanceiroService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JarvisContextoFinanceiroCacheServiceTest {

    @Mock
    private JarvisContextoFinanceiroService delegate;

    private JarvisContextoFinanceiroCacheService cacheService;

    @BeforeEach
    void setUp() {
        JarvisPerformanceProperties props = new JarvisPerformanceProperties();
        props.setContextoCacheTtlSeconds(120);
        cacheService = new JarvisContextoFinanceiroCacheService(delegate, props);
        cacheService.initCache();
    }

    @Test
    void cacheEvitaSegundaChamadaAoDelegate() {
        when(delegate.montarBlocoContexto(1L)).thenReturn("ctx-a");
        assertEquals("ctx-a", cacheService.montarBlocoContexto(1L));
        assertEquals("ctx-a", cacheService.montarBlocoContexto(1L));
        verify(delegate, times(1)).montarBlocoContexto(1L);
    }

    @Test
    void invalidacaoForcaRecarga() {
        when(delegate.montarBlocoContexto(2L)).thenReturn("ctx-1", "ctx-2");
        cacheService.montarBlocoContexto(2L);
        cacheService.invalidar(2L);
        assertEquals("ctx-2", cacheService.montarBlocoContexto(2L));
        verify(delegate, times(2)).montarBlocoContexto(2L);
    }
}
