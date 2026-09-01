package com.consumoesperto.edith.client;

import com.consumoesperto.config.EdithProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Duration;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contrato HTTP E.D.I.T.H. SDK 0.4.1 — paths, auth e idempotency.
 */
class EdithHttpClientContractTest {

  private WireMockServer wireMock;
  private EdithHttpClient client;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    wireMock = new WireMockServer(0);
    wireMock.start();
    WireMock.configureFor("localhost", wireMock.port());

    EdithProperties props = new EdithProperties();
    props.setEnabled(true);
    props.setBaseUrl("http://localhost:" + wireMock.port());
    props.setApiKey("test-api-key");
    props.setRequestTimeoutMs(5_000L);

    client = new EdithHttpClient(props, new RestTemplateBuilder(), objectMapper);
  }

  @AfterEach
  void tearDown() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  @Test
  void createConversationUsesOfficialPath() throws Exception {
    wireMock.stubFor(post(urlEqualTo("/api/v1/integrations/conversations"))
      .withHeader("Authorization", equalTo("Bearer test-api-key"))
      .withHeader("X-API-Key", equalTo("test-api-key"))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"conversationId\":\"conv-1\",\"status\":\"OPEN\"}")));

    EdithApiModels.ConversationResponse resp = client.createConversation("ConsumoEsperto");
    assertEquals("conv-1", resp.getConversationId());
  }

  @Test
  void sendMessageUsesIdempotencyKeyAndOfficialPath() throws Exception {
    wireMock.stubFor(post(urlEqualTo("/api/v1/integrations/conversations/conv-1/messages"))
      .withHeader("Idempotency-Key", equalTo("client-req-1"))
      .willReturn(aResponse()
        .withStatus(202)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"conversationId\":\"conv-1\",\"messageId\":\"msg-1\",\"taskId\":\"task-1\",\"status\":\"QUEUED\"}")));

    EdithApiModels.MessageSendRequest req = new EdithApiModels.MessageSendRequest();
    req.setMessage("Olá");
    req.setSourceAction("consumo.chat");
    req.setClientRequestId("client-req-1");
    req.setContext(Map.of("context_ref", "ctx-abc"));

    EdithApiModels.MessageSubmission sub = client.sendMessage("conv-1", req);
    assertEquals("task-1", sub.getTaskId());
  }

  @Test
  void getTaskAndHealthUseOfficialPaths() {
    wireMock.stubFor(get(urlEqualTo("/api/v1/integrations/tasks/task-9"))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"taskId\":\"task-9\",\"status\":\"COMPLETED\",\"result\":\"ok\"}")));

    wireMock.stubFor(get(urlEqualTo("/api/v1/integrations/health"))
      .willReturn(aResponse().withStatus(200).withBody("{\"status\":\"UP\"}")));

    EdithApiModels.TaskResponse task = client.getTask("task-9");
    assertEquals("COMPLETED", task.getStatus());
    assertTrue(client.healthProbe().getStatusCode().is2xxSuccessful());
  }
}
