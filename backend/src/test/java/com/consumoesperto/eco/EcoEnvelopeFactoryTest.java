package com.consumoesperto.eco;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcoEnvelopeFactoryTest {

    @AfterEach
    void clear() {
        EcoEnvelopeHolder.clear();
    }

    @Test
    void inboundCriaNovoSpanComParentEMantemTrace() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(EcoHeaders.TRACE_ID, "tr_01INBOUNDTRACE00000000000001");
        req.addHeader(EcoHeaders.SPAN_ID, "sp_01INBOUNDPARENT0000000000001");
        req.addHeader(EcoHeaders.DEADLINE, Instant.now().plusSeconds(8).toString());
        req.addHeader(EcoHeaders.MODE, "INTERACTIVE");
        EcoEnvelope env = EcoEnvelopeFactory.fromRequest(
            req, null, EcoMode.BALANCED, EcoSensitivity.FINANCIAL, EcoHeaders.APP_EDITH);
        assertEquals("tr_01INBOUNDTRACE00000000000001", env.getTraceId());
        assertEquals("sp_01INBOUNDPARENT0000000000001", env.getParentSpanId());
        assertTrue(env.getSpanId().startsWith("sp_"));
        assertNotEquals("sp_01INBOUNDPARENT0000000000001", env.getSpanId());
    }

    @Test
    void deadlineNoPassadoEDetectado() {
        EcoEnvelope env = EcoEnvelope.builder()
            .traceId("tr_x")
            .spanId("sp_x")
            .deadline(Instant.now().minusSeconds(1))
            .mode(EcoMode.INTERACTIVE)
            .build();
        assertTrue(env.deadlineExceeded());
        EcoEnvelope child = env.childSpan();
        assertEquals("tr_x", child.getTraceId());
        assertEquals("sp_x", child.getParentSpanId());
        assertNotEquals("sp_x", child.getSpanId());
    }
}
