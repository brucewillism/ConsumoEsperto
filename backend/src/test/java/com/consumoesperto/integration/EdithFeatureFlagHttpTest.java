package com.consumoesperto.integration;

import com.consumoesperto.model.Usuario;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EdithFeatureFlagHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String token;

    @BeforeEach
    void seed() {
        String sfx = String.valueOf(System.nanoTime());
        Usuario u = new Usuario();
        u.setUsername("edith_" + sfx);
        u.setEmail("edith_" + sfx + "@test.local");
        u.setPassword(passwordEncoder.encode("secret"));
        u.setNome("Edith Test");
        u = usuarioRepository.save(u);
        UserPrincipal principal = UserPrincipal.create(u);
        token = "Bearer " + jwtTokenProvider.generateToken(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @Test
    void statusDisabledByDefault() throws Exception {
        mockMvc.perform(get("/api/edith/status").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.state").value("DISABLED"))
            .andExpect(jsonPath("$.assistant").value("LOCAL"));
    }

    @Test
    void edithDesligada_listarCartoesFunciona() throws Exception {
        mockMvc.perform(get("/api/cartoes-credito").header("Authorization", token))
            .andExpect(status().isOk());
    }

    @Test
    void edithDesligada_crudFinanceiroFunciona() throws Exception {
        mockMvc.perform(get("/api/transacoes").header("Authorization", token))
            .andExpect(status().isOk());
    }

    @Test
    void edithDesligada_botaoListarCartoesNaoDependeDeHub() throws Exception {
        mockMvc.perform(post("/api/ia-chat")
                .header("Authorization", token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"mensagem\":\"Lista os meus cartões\",\"capability\":\"finance.cards.list\",\"screen\":\"dashboard\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("LOCAL"))
            .andExpect(jsonPath("$.assistant").value("LOCAL"));
    }

    @Test
    void runtimeHealthSeparaCoreDeEdith() throws Exception {
        mockMvc.perform(get("/api/runtime-health").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.core").value("AVAILABLE"))
            .andExpect(jsonPath("$.database").value("AVAILABLE"))
            .andExpect(jsonPath("$.edith").value("DISABLED"))
            .andExpect(jsonPath("$.assistant").value("LOCAL"));
    }
}
