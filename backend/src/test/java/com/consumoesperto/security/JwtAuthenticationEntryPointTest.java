package com.consumoesperto.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationEntryPointTest {

    private final JwtAuthenticationEntryPoint entryPoint =
        new JwtAuthenticationEntryPoint(new ObjectMapper().findAndRegisterModules());

    @Test
    void f5NaRotaAngularRedirecionaParaRaizComResume() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/login");
        req.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        MockHttpServletResponse res = new MockHttpServletResponse();

        entryPoint.commence(req, res, new InsufficientAuthenticationException("anon"));

        assertEquals(302, res.getStatus());
        assertEquals("/?ce_resume=%2Flogin", res.getHeader("Location"));
        assertFalse(res.getContentAsString().contains("Unauthorized"));
    }

    @Test
    void chamadaApiContinua401Json() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/transacoes");
        req.addHeader("Accept", "application/json");
        MockHttpServletResponse res = new MockHttpServletResponse();

        entryPoint.commence(req, res, new InsufficientAuthenticationException("anon"));

        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("Unauthorized"));
        assertTrue(res.getContentAsString().contains("/api/transacoes"));
    }

    @Test
    void f5EmDashboardTambemNaoDevolveJson() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/dashboard");
        req.addHeader("Sec-Fetch-Dest", "document");
        req.addHeader("Sec-Fetch-Mode", "navigate");
        MockHttpServletResponse res = new MockHttpServletResponse();

        entryPoint.commence(req, res, new InsufficientAuthenticationException("anon"));

        assertEquals(302, res.getStatus());
        assertEquals("/?ce_resume=%2Fdashboard", res.getHeader("Location"));
    }
}
