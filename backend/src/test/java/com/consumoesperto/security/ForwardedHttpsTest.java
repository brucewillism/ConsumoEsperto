package com.consumoesperto.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForwardedHttpsTest {

    @Test
    void httpSemHeaders_naoEHttps() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/ingestion/mobile/transactions");
        req.setSecure(false);
        req.setScheme("http");
        assertFalse(ForwardedHttps.isHttps(req));
    }

    @Test
    void isSecureTrue_passa() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setSecure(true);
        assertTrue(ForwardedHttps.isHttps(req));
    }

    @Test
    void forwardedProtoHttps_passa() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setSecure(false);
        req.setScheme("http");
        req.addHeader("X-Forwarded-Proto", "https");
        assertTrue(ForwardedHttps.isHttps(req));
    }

    @Test
    void forwardedProtoListaHttpsHttp_usaPrimeiroSalto() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setSecure(false);
        req.setScheme("http");
        req.addHeader("X-Forwarded-Proto", "https, http");
        assertTrue(ForwardedHttps.isHttps(req));
    }

    @Test
    void forwardedProtoComEspaco_passa() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setSecure(false);
        req.addHeader("X-Forwarded-Proto", " https ");
        assertTrue(ForwardedHttps.isHttps(req));
    }

    @Test
    void rfc7239Forwarded_passa() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setSecure(false);
        req.addHeader("Forwarded", "for=203.0.113.1;proto=https;by=10.0.0.1");
        assertTrue(ForwardedHttps.isHttps(req));
    }

    @Test
    void httpNaFrenteDaLista_naoEHttps() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setSecure(false);
        req.addHeader("X-Forwarded-Proto", "http, https");
        assertFalse(ForwardedHttps.isHttps(req));
    }
}
