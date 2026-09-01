package com.consumoesperto.integration;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.security.JwtTokenProvider;
import com.consumoesperto.security.UserPrincipal;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Segurança de {@code /api/admin/**} e do Actuator.
 *
 * O teste expõe deliberadamente endpoints sensíveis do Actuator (simulando má
 * configuração) para provar que a cadeia de segurança bloqueia mesmo assim:
 * env/beans/configprops exigem ROLE_ADMIN; health permanece público e mínimo.
 */
@SpringBootTest(properties = {
    "management.endpoints.web.exposure.include=health,info,env,beans,configprops",
    "management.endpoint.health.show-details=never"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ActuatorAdminSecurityHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String tokenComum;
    private String tokenAdmin;
    private Long comumId;

    @BeforeEach
    void seed() {
        String sfx = String.valueOf(System.nanoTime());
        Usuario comum = saveUser("sec_user_" + sfx, "sec_user_" + sfx + "@test.local", "USER");
        Usuario admin = saveUser("sec_admin_" + sfx, "sec_admin_" + sfx + "@test.local", "ADMIN");
        comumId = comum.getId();
        tokenComum = bearerFor(comum);
        tokenAdmin = bearerFor(admin);
    }

    // ------------------------------------------------------------------
    // /api/admin/evolution
    // ------------------------------------------------------------------

    @Test
    void adminEvolution_usuarioComum_retorna403() throws Exception {
        mockMvc.perform(get("/api/admin/evolution/health")
                .header("Authorization", "Bearer " + tokenComum))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminEvolution_semToken_retorna401() throws Exception {
        mockMvc.perform(get("/api/admin/evolution/health"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEvolution_tokenInvalido_retorna401() throws Exception {
        mockMvc.perform(get("/api/admin/evolution/health")
                .header("Authorization", "Bearer token.invalido.aqui"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEvolution_admin_acessaSemVazarSecrets() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/evolution/health")
                .header("Authorization", "Bearer " + tokenAdmin))
            .andExpect(status().isOk())
            .andReturn();
        String body = res.getResponse().getContentAsString().toLowerCase(Locale.ROOT);
        assertFalse(body.contains("apikey"), "resposta não deve conter chave da Evolution");
        assertFalse(body.contains("api-key"), "resposta não deve conter chave da Evolution");
        assertFalse(body.contains("secret"), "resposta não deve conter secrets");
        assertFalse(body.contains("password"), "resposta não deve conter senhas");
    }

    /**
     * Role manipulada no payload: token forjado (assinado com outra chave) contendo o ID
     * de um usuário comum não pode autenticar — a assinatura é inválida.
     */
    @Test
    void adminEvolution_tokenForjadoComOutraChave_retorna401() throws Exception {
        String chaveErrada = "outra_chave_qualquer_que_nao_e_a_do_servidor_0123456789_0123456789";
        String forjado = Jwts.builder()
            .setSubject(String.valueOf(comumId))
            .claim("role", "ADMIN")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(SignatureAlgorithm.HS512, chaveErrada.getBytes(StandardCharsets.UTF_8))
            .compact();
        mockMvc.perform(get("/api/admin/evolution/health")
                .header("Authorization", "Bearer " + forjado))
            .andExpect(status().isUnauthorized());
    }

    /**
     * Mesmo que um token válido carregasse claims extras, as autoridades vêm do papel
     * persistido no banco — usuário comum permanece 403.
     */
    @Test
    void adminEvolution_roleSoNoBanco_usuarioComumContinua403() throws Exception {
        mockMvc.perform(get("/api/admin/evolution/health")
                .header("Authorization", "Bearer " + tokenComum)
                .header("X-Role", "ADMIN"))
            .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Actuator
    // ------------------------------------------------------------------

    @Test
    void actuatorHealth_publico_retornaMinimo() throws Exception {
        MvcResult res = mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andReturn();
        String body = res.getResponse().getContentAsString().toLowerCase(Locale.ROOT);
        assertFalse(body.contains("jdbc:"), "health não deve expor URL do banco");
        assertFalse(body.contains("password"), "health não deve expor credenciais");
        assertFalse(body.contains("datasource"), "health mínimo não deve detalhar componentes");
    }

    @Test
    void actuatorEnv_semAutenticacao_bloqueado() throws Exception {
        mockMvc.perform(get("/actuator/env"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorEnv_usuarioComum_bloqueado() throws Exception {
        mockMvc.perform(get("/actuator/env")
                .header("Authorization", "Bearer " + tokenComum))
            .andExpect(status().isForbidden());
    }

    @Test
    void actuatorBeans_usuarioComum_bloqueado() throws Exception {
        mockMvc.perform(get("/actuator/beans")
                .header("Authorization", "Bearer " + tokenComum))
            .andExpect(status().isForbidden());
    }

    @Test
    void actuatorConfigprops_usuarioComum_bloqueado() throws Exception {
        mockMvc.perform(get("/actuator/configprops")
                .header("Authorization", "Bearer " + tokenComum))
            .andExpect(status().isForbidden());
    }

    @Test
    void actuatorHeapdump_naoExposto_retorna4xx() throws Exception {
        mockMvc.perform(get("/actuator/heapdump"))
            .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Usuario saveUser(String username, String email, String role) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("SenhaTeste123!"));
        u.setNome("Test " + username);
        u.setRole(role);
        return usuarioRepository.save(u);
    }

    private String bearerFor(Usuario usuario) {
        UserPrincipal principal = UserPrincipal.create(usuario);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtTokenProvider.generateToken(auth);
    }
}
