package com.consumoesperto.integration;

import com.consumoesperto.edith.security.EdithCallbackHeaders;
import com.consumoesperto.edith.security.EdithHmacSigner;
import com.consumoesperto.model.EdithTaskLink;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.repository.EdithTaskLinkRepository;
import com.consumoesperto.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
  "consumoesperto.edith.enabled=true",
  "consumoesperto.edith.callback-secret=test-callback-secret"
})
class EdithToolBridgeHttpTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private EdithTaskLinkRepository taskLinkRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private String contextRef;
  private String requestId;

  @BeforeEach
  void seed() {
    String sfx = String.valueOf(System.nanoTime());
    Usuario u = new Usuario();
    u.setUsername("tool_" + sfx);
    u.setEmail("tool_" + sfx + "@t.local");
    u.setPassword(passwordEncoder.encode("secret"));
    u.setNome("Tool Test");
    u = usuarioRepository.save(u);

    contextRef = "ctx-" + sfx;
    requestId = "req-" + sfx;
    taskLinkRepository.save(new EdithTaskLink(
      u.getId(), contextRef, "conv-1", "msg-1", "task-1", requestId, "client-1", "consumo.chat"
    ));
  }

  @Test
  void writeToolDeniedWithToolBridgeDenied() throws Exception {
    String body = "{\"request_id\":\"" + requestId + "\",\"tool\":\"finance.transaction.create\",\"version\":\"1\","
      + "\"arguments\":{\"context_ref\":\"" + contextRef + "\"}}";
    byte[] raw = body.getBytes(StandardCharsets.UTF_8);
    String ts = String.valueOf(Instant.now().getEpochSecond());
    String nonce = "nonce-write-" + System.nanoTime();
    String sig = EdithHmacSigner.sign("test-callback-secret", ts, nonce, requestId, raw);

    mockMvc.perform(post("/api/internal/edith/tools")
        .contentType(MediaType.APPLICATION_JSON)
        .header(EdithCallbackHeaders.TIMESTAMP, ts)
        .header(EdithCallbackHeaders.NONCE, nonce)
        .header(EdithCallbackHeaders.REQUEST_ID, requestId)
        .header(EdithCallbackHeaders.SIGNATURE, sig)
        .content(raw))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value("TOOL_BRIDGE_DENIED"));
  }

  @Test
  void invalidHmacRejected() throws Exception {
    String body = "{\"request_id\":\"" + requestId + "\",\"tool\":\"finance.accounts.list\",\"version\":\"1\","
      + "\"arguments\":{\"context_ref\":\"" + contextRef + "\"}}";
    mockMvc.perform(post("/api/internal/edith/tools")
        .contentType(MediaType.APPLICATION_JSON)
        .header(EdithCallbackHeaders.TIMESTAMP, String.valueOf(Instant.now().getEpochSecond()))
        .header(EdithCallbackHeaders.NONCE, "bad-nonce")
        .header(EdithCallbackHeaders.REQUEST_ID, requestId)
        .header(EdithCallbackHeaders.SIGNATURE, "deadbeef")
        .content(body))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void invalidContextRefRejected() throws Exception {
    String body = "{\"request_id\":\"" + requestId + "\",\"tool\":\"finance.accounts.list\",\"version\":\"1\","
      + "\"arguments\":{\"context_ref\":\"ctx-inexistente\"}}";
    byte[] raw = body.getBytes(StandardCharsets.UTF_8);
    String ts = String.valueOf(Instant.now().getEpochSecond());
    String nonce = "nonce-ctx-" + System.nanoTime();
    String sig = EdithHmacSigner.sign("test-callback-secret", ts, nonce, requestId, raw);

    mockMvc.perform(post("/api/internal/edith/tools")
        .contentType(MediaType.APPLICATION_JSON)
        .header(EdithCallbackHeaders.TIMESTAMP, ts)
        .header(EdithCallbackHeaders.NONCE, nonce)
        .header(EdithCallbackHeaders.REQUEST_ID, requestId)
        .header(EdithCallbackHeaders.SIGNATURE, sig)
        .content(raw))
      .andExpect(status().isBadRequest());
  }
}
