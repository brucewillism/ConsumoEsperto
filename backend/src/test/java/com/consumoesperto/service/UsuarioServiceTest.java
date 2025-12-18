package com.consumoesperto.service;

import com.consumoesperto.dto.UsuarioDTO;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para UsuarioService
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario usuario;
    private UsuarioDTO usuarioDTO;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail("teste@teste.com");
        usuario.setNome("Usuário Teste");
        usuario.setPassword("senha123");

        usuarioDTO = new UsuarioDTO();
        usuarioDTO.setId(1L);
        usuarioDTO.setEmail("teste@teste.com");
        usuarioDTO.setNome("Usuário Teste");
    }

    @Test
    void testBuscarPorId_Encontrado() {
        // Arrange
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        // Act
        UsuarioDTO result = usuarioService.buscarPorId(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("teste@teste.com", result.getEmail());
    }

    @Test
    void testBuscarPorId_NaoEncontrado() {
        // Arrange
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            usuarioService.buscarPorId(1L);
        });
    }

    @Test
    void testBuscarPorEmail_Encontrado() {
        // Arrange
        when(usuarioRepository.findByEmail("teste@teste.com")).thenReturn(Optional.of(usuario));

        // Act
        UsuarioDTO result = usuarioService.buscarPorEmail("teste@teste.com");

        // Assert
        assertNotNull(result);
        assertEquals("teste@teste.com", result.getEmail());
    }

    @Test
    void testBuscarPorEmail_NaoEncontrado() {
        // Arrange
        when(usuarioRepository.findByEmail("naoexiste@teste.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            usuarioService.buscarPorEmail("naoexiste@teste.com");
        });
    }
}

