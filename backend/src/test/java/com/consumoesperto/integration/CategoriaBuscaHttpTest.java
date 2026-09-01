package com.consumoesperto.integration;

import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.security.JwtTokenProvider;
import com.consumoesperto.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de {@code GET /api/categorias/buscar?nome=} — endpoint que o frontend
 * consome via {@code CategoriaService.buscarPorNome} (antes inexistente no backend).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoriaBuscaHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String tokenA;
    private String nomeAlimentacao;

    @BeforeEach
    void seed() {
        String sfx = String.valueOf(System.nanoTime());
        Usuario a = saveUser("busca_a_" + sfx, "busca_a_" + sfx + "@test.local");
        Usuario b = saveUser("busca_b_" + sfx, "busca_b_" + sfx + "@test.local");
        tokenA = bearerFor(a);

        nomeAlimentacao = "Alimentação_" + sfx;
        saveCategoria(a, nomeAlimentacao, true);
        saveCategoria(a, "Transporte_" + sfx, true);
        saveCategoria(a, "AlimInativa_" + sfx, false);
        saveCategoria(b, "AlimentacaoDoB_" + sfx, true);
    }

    @Test
    void buscaNomeExato_retornaCategoria() throws Exception {
        mockMvc.perform(get("/api/categorias/buscar").param("nome", nomeAlimentacao)
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].nome").value(nomeAlimentacao));
    }

    @Test
    void buscaParcialCaseInsensitive_retornaCategoria() throws Exception {
        mockMvc.perform(get("/api/categorias/buscar").param("nome", "aLiMenta")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].nome").value(nomeAlimentacao));
    }

    @Test
    void buscaSemAcento_encontraNomeComAcento() throws Exception {
        mockMvc.perform(get("/api/categorias/buscar").param("nome", "alimentacao_")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].nome").value(nomeAlimentacao));
    }

    @Test
    void busca_naoRetornaCategoriasDeOutroUsuario() throws Exception {
        mockMvc.perform(get("/api/categorias/buscar").param("nome", "AlimentacaoDoB")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void busca_naoRetornaCategoriaInativa() throws Exception {
        mockMvc.perform(get("/api/categorias/buscar").param("nome", "AlimInativa")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void buscaVazia_retornaListaVazia() throws Exception {
        mockMvc.perform(get("/api/categorias/buscar").param("nome", "   ")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/categorias/buscar")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void buscaInexistente_retornaListaVazia() throws Exception {
        mockMvc.perform(get("/api/categorias/buscar").param("nome", "nao-existe-xyz")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void busca_semAutenticacao_retorna401() throws Exception {
        mockMvc.perform(get("/api/categorias/buscar").param("nome", "x"))
            .andExpect(status().isUnauthorized());
    }

    private Usuario saveUser(String username, String email) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        u.setNome("Test " + username);
        return usuarioRepository.save(u);
    }

    private void saveCategoria(Usuario dono, String nome, boolean ativa) {
        Categoria c = new Categoria();
        c.setNome(nome);
        c.setUsuario(dono);
        c.setAtivo(ativa);
        categoriaRepository.save(c);
    }

    private String bearerFor(Usuario usuario) {
        UserPrincipal principal = UserPrincipal.create(usuario);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtTokenProvider.generateToken(auth);
    }
}
