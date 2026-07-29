package com.consumoesperto.integration;

import com.consumoesperto.dto.CategoriaDTO;
import com.consumoesperto.model.Categoria;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.CategoriaRepository;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.security.JwtTokenProvider;
import com.consumoesperto.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de isolamento horizontal (IDOR) — usuário B não acessa recursos do usuário A.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HorizontalAccessControlHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String tokenB;
    private Long categoriaIdA;

    @BeforeEach
    void seed() {
        String suffix = String.valueOf(System.nanoTime());
        Usuario a = saveUser("user_a_" + suffix, "usera_" + suffix + "@test.local");
        Usuario b = saveUser("user_b_" + suffix, "userb_" + suffix + "@test.local");

        Categoria cat = new Categoria();
        cat.setNome("Categoria Secreta A");
        cat.setUsuario(a);
        categoriaIdA = categoriaRepository.save(cat).getId();

        tokenB = bearerFor(b);
    }

    @Test
    void usuarioB_naoAlteraCategoriaDeA() throws Exception {
        CategoriaDTO dto = new CategoriaDTO();
        dto.setNome("Hack");
        mockMvc.perform(put("/api/categorias/" + categoriaIdA)
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void usuarioB_naoExcluiCategoriaDeA() throws Exception {
        mockMvc.perform(delete("/api/categorias/" + categoriaIdA)
                .header("Authorization", "Bearer " + tokenB))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void listagemDeB_naoContemDadosDeA() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/categorias")
                .header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk())
            .andReturn();
        String body = res.getResponse().getContentAsString();
        assertFalse(body.contains("Categoria Secreta A"));
        assertFalse(body.contains(String.valueOf(categoriaIdA)));
    }

    @Test
    void respostaDeErro_naoExpoeStackTrace() throws Exception {
        MvcResult res = mockMvc.perform(put("/api/categorias/" + categoriaIdA)
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().is4xxClientError())
            .andReturn();
        String body = res.getResponse().getContentAsString();
        assertFalse(body.contains("RuntimeException"));
        assertFalse(body.contains("at com.consumoesperto"));
    }

    private Usuario saveUser(String username, String email) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        u.setNome("Test " + username);
        return usuarioRepository.save(u);
    }

    private String bearerFor(Usuario usuario) {
        UserPrincipal principal = UserPrincipal.create(usuario);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtTokenProvider.generateToken(auth);
    }
}
