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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
    "consumoesperto.edith.enabled=true",
    "consumoesperto.edith.fallback-enabled=true",
    "consumoesperto.edith.base-url=",
    "consumoesperto.edith.api-key="
})
class LegacyCognitiveGatewayHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String token;

    @BeforeEach
    void seed() {
        String sfx = String.valueOf(System.nanoTime());
        Usuario u = new Usuario();
        u.setUsername("legacy_" + sfx);
        u.setEmail("legacy_" + sfx + "@test.local");
        u.setPassword(passwordEncoder.encode("secret"));
        u.setNome("Legacy");
        u = usuarioRepository.save(u);
        UserPrincipal principal = UserPrincipal.create(u);
        token = "Bearer " + jwtTokenProvider.generateToken(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @Test
    void edithIndisponivelComFallbackOChatRespondePeloLegado() throws Exception {
        mockMvc.perform(post("/api/ia-chat")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mensagem\":\"oi\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("LEGACY"))
            .andExpect(jsonPath("$.assistant").value("DEGRADED"))
            .andExpect(jsonPath("$.resposta").isNotEmpty());
    }
}
