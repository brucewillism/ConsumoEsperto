package com.consumoesperto.integration;

import com.consumoesperto.model.EdithTaskLink;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.EdithTaskLinkRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EdithOwnershipHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EdithTaskLinkRepository taskLinkRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String tokenA;
    private String tokenB;
    private String foreignTaskId;

    @BeforeEach
    void seed() {
        String sfx = String.valueOf(System.nanoTime());
        Usuario a = save("edith_a_" + sfx, "edith_a_" + sfx + "@t.local");
        Usuario b = save("edith_b_" + sfx, "edith_b_" + sfx + "@t.local");
        tokenA = bearer(a);
        tokenB = bearer(b);

        EdithTaskLink link = new EdithTaskLink(
            a.getId(), "ctx-" + sfx, "conv-foreign", "msg-1", "task-foreign-" + sfx,
            "req-1", "client-1", "consumo.chat"
        );
        foreignTaskId = link.getEdithTaskId();
        taskLinkRepository.save(link);
    }

    @Test
    void usuarioBNaoAcessaTaskDeA() throws Exception {
        mockMvc.perform(get("/api/edith/tasks/" + foreignTaskId).header("Authorization", tokenB))
            .andExpect(status().isNotFound());
    }

    private Usuario save(String username, String email) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("secret"));
        u.setNome("Test");
        return usuarioRepository.save(u);
    }

    private String bearer(Usuario u) {
        UserPrincipal p = UserPrincipal.create(u);
        return "Bearer " + jwtTokenProvider.generateToken(
            new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities())
        );
    }
}
