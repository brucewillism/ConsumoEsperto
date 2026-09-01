package com.consumoesperto.integration;

import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.UsuarioRepository;
import com.consumoesperto.security.JwtTokenProvider;
import com.consumoesperto.security.UserPrincipal;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gate de deploy: sobe o profile de PRODUÇÃO ({@code prod}) com secrets fictícios
 * e confirma a política de exposição antes de qualquer release:
 *
 * <pre>
 * /actuator/health      → acessível, corpo mínimo (sem componentes/detalhes)
 * /actuator/env         → bloqueado
 * /actuator/beans       → bloqueado
 * /actuator/configprops → bloqueado
 * /admin (API)          → bloqueado para usuário comum e para anônimo
 * </pre>
 *
 * O banco é substituído por H2 apenas para o boot do contexto (Flyway off);
 * toda a configuração de segurança/Actuator vem do application-prod.properties real.
 */
@SpringBootTest(properties = {
    // Secrets fictícios exigidos pelo profile prod
    "DATABASE_URL=jdbc:h2:mem:prodcfg;DB_CLOSE_DELAY=-1",
    "DATABASE_USERNAME=sa",
    "DATABASE_PASSWORD=test",
    "JWT_SECRET=segredo_de_teste_para_ci_apenas_nao_usar_em_producao_0123456789abcdef",
    "CORS_ALLOWED_PATTERNS=http://localhost:14200",
    // O resource server exige URIs bem formadas; sem NGROK_URL no ambiente o
    // fallback vira "/.well-known/jwks.json" (inválido) e o contexto não sobe.
    "JWT_ISSUER_URI=https://issuer.teste.invalid",
    "JWT_JWK_SET_URI=https://issuer.teste.invalid/.well-known/jwks.json",
    // Substituições mínimas para o contexto subir sem Postgres/arquivos de log
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create",
    "spring.jpa.properties.hibernate.hbm2ddl.auto=create",
    "consumoesperto.schema.autopatch.enabled=false",
    "logging.file.name=",
    "spring.task.scheduling.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Transactional
class ProducaoConfiguracaoSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    // ------------------------------------------------------------------
    // Actuator
    // ------------------------------------------------------------------

    @Test
    void actuatorHealth_publicoComCorpoMinimo() throws Exception {
        MvcResult res = mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andReturn();
        String body = res.getResponse().getContentAsString();
        assertTrue(body.contains("\"status\""), "Health deve reportar status");
        assertFalse(body.contains("components"), "Health de produção não pode listar componentes");
        assertFalse(body.toLowerCase().contains("jdbc"), "Health não pode expor URL de banco");
        assertFalse(body.toLowerCase().contains("postgres"), "Health não pode expor detalhes do banco");
    }

    @Test
    void actuatorEnv_bloqueado() throws Exception {
        assertActuatorBloqueado("/actuator/env");
    }

    @Test
    void actuatorBeans_bloqueado() throws Exception {
        assertActuatorBloqueado("/actuator/beans");
    }

    @Test
    void actuatorConfigprops_bloqueado() throws Exception {
        assertActuatorBloqueado("/actuator/configprops");
    }

    @Test
    void actuatorHeapdumpEThreaddump_bloqueados() throws Exception {
        assertActuatorBloqueado("/actuator/heapdump");
        assertActuatorBloqueado("/actuator/threaddump");
    }

    @Test
    void actuatorMappingsELoggers_bloqueados() throws Exception {
        assertActuatorBloqueado("/actuator/mappings");
        assertActuatorBloqueado("/actuator/loggers");
    }

    // ------------------------------------------------------------------
    // Endpoints administrativos
    // ------------------------------------------------------------------

    @Test
    void adminEvolution_anonimo_401() throws Exception {
        mockMvc.perform(get("/api/admin/evolution/health"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEvolution_usuarioComum_403() throws Exception {
        Usuario comum = saveUser("prodcfg_user_" + System.nanoTime());
        mockMvc.perform(get("/api/admin/evolution/health")
                .header("Authorization", "Bearer " + bearerFor(comum)))
            .andExpect(status().isForbidden());
    }

    @Test
    void swagger_desabilitadoEmProducao() throws Exception {
        int status = mockMvc.perform(get("/v3/api-docs"))
            .andReturn().getResponse().getStatus();
        assertTrue(status == 404 || status == 401 || status == 403,
            "Swagger/api-docs não pode responder 200 em produção (status=" + status + ")");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void assertActuatorBloqueado(String path) throws Exception {
        MvcResult res = mockMvc.perform(get(path)).andReturn();
        int status = res.getResponse().getStatus();
        assertTrue(status == 401 || status == 403 || status == 404,
            path + " deve estar bloqueado em produção (status=" + status + ")");
        String body = res.getResponse().getContentAsString();
        assertFalse(body.contains("propertySources"), path + " vazou variáveis de ambiente");
        // "\"beans\":" só aparece no payload real do actuator; o JSON de erro
        // contém apenas o path da requisição ("/actuator/beans").
        assertFalse(body.contains("\"beans\":"), path + " vazou definição de beans");
    }

    private Usuario saveUser(String username) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setEmail(username + "@test.local");
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
