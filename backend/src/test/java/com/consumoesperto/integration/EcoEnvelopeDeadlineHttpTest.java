package com.consumoesperto.integration;

import com.consumoesperto.eco.EcoHeaders;
import com.consumoesperto.repository.EdithCallbackNonceRepository;
import com.consumoesperto.model.EdithTaskLink;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.EdithTaskLinkRepository;
import com.consumoesperto.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "consumoesperto.edith.enabled=true",
    "consumoesperto.edith.callback-secret=test-callback-secret"
})
class EcoEnvelopeDeadlineHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EdithTaskLinkRepository taskLinkRepository;
    @Autowired private EdithCallbackNonceRepository nonceRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String contextRef;
    private String requestId;

    @BeforeEach
    void seed() {
        String sfx = String.valueOf(System.nanoTime());
        Usuario u = new Usuario();
        u.setUsername("eco_" + sfx);
        u.setEmail("eco_" + sfx + "@t.local");
        u.setPassword(passwordEncoder.encode("secret"));
        u.setNome("Eco Test");
        u = usuarioRepository.save(u);
        contextRef = "ctx-eco-" + sfx;
        requestId = "req-eco-" + sfx;
        taskLinkRepository.save(new EdithTaskLink(
            u.getId(), contextRef, "conv-1", "msg-1", "task-1", requestId, "client-1", "consumo.chat"
        ));
    }

    @Test
    void deadlineExpiradoNaoTocaNonceNemTool(CapturedOutput output) throws Exception {
        long noncesAntes = nonceRepository.count();
        String body = "{\"request_id\":\"" + requestId + "\",\"tool\":\"finance.accounts.list\",\"version\":\"1\","
            + "\"arguments\":{\"context_ref\":\"" + contextRef + "\"}}";
        mockMvc.perform(post("/api/internal/edith/tools")
                .contentType(MediaType.APPLICATION_JSON)
                .header(EcoHeaders.TRACE_ID, "tr_01TESTDEADLINE000000000001")
                .header(EcoHeaders.SPAN_ID, "sp_01TESTDEADLINE000000000002")
                .header(EcoHeaders.DEADLINE, Instant.now().minusSeconds(5).toString())
                .header(EcoHeaders.MODE, "INTERACTIVE")
                .header(EcoHeaders.SENSITIVITY, "FINANCIAL")
                .content(body.getBytes(StandardCharsets.UTF_8)))
            .andExpect(status().isGatewayTimeout())
            .andExpect(jsonPath("$.error.code").value("DEADLINE_EXCEEDED"))
            .andExpect(jsonPath("$.error.retryable").value(false))
            .andExpect(jsonPath("$.error.trace_id").value("tr_01TESTDEADLINE000000000001"))
            .andExpect(header().string(EcoHeaders.TRACE_ID, "tr_01TESTDEADLINE000000000001"));
        assertEquals(noncesAntes, nonceRepository.count());
        String logs = output.getAll();
        assertTrue(logs.contains("eco_span"));
        assertTrue(logs.contains("tr_01TESTDEADLINE000000000001"));
        assertTrue(logs.contains("outcome=FAILED"));
        assertTrue(logs.contains("t_total_ms="));
        assertTrue(logs.contains("t_queue_ms="));
        assertTrue(logs.lines().anyMatch(line ->
            line.contains("eco_span")
                && line.contains("tr_01TESTDEADLINE000000000001")
                && !line.contains("t_tool_ms=")));
    }
}
