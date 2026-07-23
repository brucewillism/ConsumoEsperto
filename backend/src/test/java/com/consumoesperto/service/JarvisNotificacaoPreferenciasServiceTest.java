package com.consumoesperto.service;

import com.consumoesperto.dto.JarvisNotificacaoPreferenciasDTO;
import com.consumoesperto.model.JarvisTipoNotificacaoProativa;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.model.UsuarioAiConfig;
import com.consumoesperto.repository.UsuarioAiConfigRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JarvisNotificacaoPreferenciasServiceTest {

    @Mock private UsuarioAiConfigRepository usuarioAiConfigRepository;
    @Mock private UsuarioRepository usuarioRepository;

    private JarvisNotificacaoPreferenciasService service;

    @BeforeEach
    void setUp() {
        service = new JarvisNotificacaoPreferenciasService(
            usuarioAiConfigRepository, usuarioRepository, new ObjectMapper());
    }

    @Test
    void estaAtiva_respeitaPreferenciaDesligada() {
        UsuarioAiConfig cfg = new UsuarioAiConfig();
        JarvisNotificacaoPreferenciasDTO prefs = JarvisNotificacaoPreferenciasDTO.defaults();
        prefs.setDigestMensalSentinela(false);
        cfg.setJarvisNotifPrefsJson("{\"digestMensalSentinela\":false}");
        when(usuarioAiConfigRepository.findByUsuarioId(1L)).thenReturn(Optional.of(cfg));

        assertFalse(service.estaAtiva(1L, JarvisTipoNotificacaoProativa.DIGEST_MENSAL_SENTINELA));
        assertTrue(service.estaAtiva(1L, JarvisTipoNotificacaoProativa.RESUMO_SEMANAL));
    }

    @Test
    void salvar_persisteJsonNaConfig() {
        Usuario u = new Usuario();
        u.setId(2L);
        when(usuarioAiConfigRepository.findByUsuarioId(2L)).thenReturn(Optional.empty());
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(u));
        when(usuarioAiConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JarvisNotificacaoPreferenciasDTO patch = new JarvisNotificacaoPreferenciasDTO();
        patch.setAlertaRiscoReativo(false);
        service.salvar(2L, patch);

        ArgumentCaptor<UsuarioAiConfig> cap = ArgumentCaptor.forClass(UsuarioAiConfig.class);
        verify(usuarioAiConfigRepository).save(cap.capture());
        assertTrue(cap.getValue().getJarvisNotifPrefsJson().contains("\"alertaRiscoReativo\":false"));
    }
}
