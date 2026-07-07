package com.consumoesperto.service.jarvis;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JarvisTratamentoWhatsappServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private UsuarioService usuarioService;
    @Mock
    private TratamentoUsuarioService tratamentoUsuarioService;

    private JarvisTratamentoWhatsappService service;

    @BeforeEach
    void setUp() {
        service = new JarvisTratamentoWhatsappService(usuarioRepository, usuarioService, tratamentoUsuarioService);
    }

    @Test
    void meChameDeAtualizaVocativo() {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setNome("Ana Silva");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(u));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<String> resp = service.tryAtualizarVocativo(1L, "Jarvis, me chame de capitã");
        assertTrue(resp.isPresent());
        assertTrue(resp.get().contains("capitã"));
        verify(usuarioRepository).save(u);
        org.junit.jupiter.api.Assertions.assertEquals(Usuario.GeneroGramatical.FEMININO, u.getGeneroGramatical());
    }
}
