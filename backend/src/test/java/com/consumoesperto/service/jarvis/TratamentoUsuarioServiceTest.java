package com.consumoesperto.service.jarvis;

import com.consumoesperto.config.JarvisPerformanceProperties;
import com.consumoesperto.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TratamentoUsuarioServiceTest {

    private TratamentoUsuarioService service;

    @BeforeEach
    void setUp() {
        service = new TratamentoUsuarioService();
    }

    @Test
    void vocativoUsaCampoConfigurado() {
        Usuario u = new Usuario();
        u.setVocativo("chefa");
        assertEquals("chefa", service.vocativo(u));
    }

    @Test
    void flexionarFeminino() {
        Usuario u = new Usuario();
        u.setGeneroGramatical(Usuario.GeneroGramatical.FEMININO);
        assertEquals("bem-vinda", service.flexionar("bem-vindo", "bem-vinda", "bem-vindo(a)", u));
    }

    @Test
    void flexionarNeutro() {
        Usuario u = new Usuario();
        u.setGeneroGramatical(Usuario.GeneroGramatical.NEUTRO);
        assertEquals("bem-vindo(a)", service.flexionar("bem-vindo", "bem-vinda", "bem-vindo(a)", u));
    }

    @Test
    void precisaColetarQuandoNaoConfigurado() {
        Usuario u = new Usuario();
        u.setJarvisConfigurado(false);
        u.setTratamentoConfigurado(false);
        assertTrue(service.precisaColetarTratamento(u));
    }

    @Test
    void naoPrecisaColetarQuandoTratamentoConfigurado() {
        Usuario u = new Usuario();
        u.setTratamentoConfigurado(true);
        assertFalse(service.precisaColetarTratamento(u));
    }

    @Test
    void saudacaoPeriodoIncluiVocativo() {
        Usuario u = new Usuario();
        u.setVocativo("capitã");
        String s = service.saudacaoPeriodo(u);
        assertTrue(s.contains("capitã"));
    }
}
