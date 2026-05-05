package com.consumoesperto.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@Slf4j
@Deprecated
public class TwilioWhatsAppService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${whatsapp.twilio.account-sid:}")
    private String accountSid;

    @Value("${whatsapp.twilio.auth-token:}")
    private String authToken;

    @Value("${whatsapp.twilio.from-number:}")
    private String fromNumber;

    public void sendMessage(String toNumber, String message) {
        ensureConfigured();

        String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";
        HttpHeaders headers = buildBasicHeaders(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", fromNumber);
        form.add("To", toNumber);
        form.add("Body", message);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
        restTemplate.exchange(url, HttpMethod.POST, request, String.class);
    }

    public byte[] downloadMedia(String mediaUrl) {
        ensureConfigured();
        HttpHeaders headers = buildBasicHeaders(MediaType.ALL);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<byte[]> response = restTemplate.exchange(mediaUrl, HttpMethod.GET, request, byte[].class);
        return response.getBody();
    }

    private HttpHeaders buildBasicHeaders(MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        String token = Base64.getEncoder()
            .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + token);
        return headers;
    }

    private void ensureConfigured() {
        if (accountSid == null || accountSid.isBlank() || authToken == null || authToken.isBlank()) {
            throw new RuntimeException("Credenciais Twilio não configuradas");
        }
        if (fromNumber == null || fromNumber.isBlank()) {
            throw new RuntimeException("WHATSAPP_TWILIO_FROM_NUMBER não configurado");
        }
    }
}
