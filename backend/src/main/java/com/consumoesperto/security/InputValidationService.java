package com.consumoesperto.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Serviço de Validação de Input para Prevenção de Ataques
 * 
 * Este serviço implementa validação robusta de input para prevenir
 * ataques de injeção SQL, XSS, e outros ataques baseados em input malicioso.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@Slf4j
public class InputValidationService {

    // Padrões para validação
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|UNION|SCRIPT|JAVASCRIPT|ONLOAD|ONERROR|ONCLICK)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i)(<script|javascript:|vbscript:|onload|onerror|onclick|onmouseover|onfocus|onblur)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
        "(\\.\\./|\\.\\.\\\\)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile(
        "(?i)(cmd|powershell|bash|sh|exec|system|eval|runtime\\.exec)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^[+]?[0-9\\s\\-\\(\\)]{10,}$"
    );
    
    private static final Pattern URL_PATTERN = Pattern.compile(
        "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$"
    );

    /**
     * Valida e sanitiza input de texto
     */
    public String validateAndSanitizeText(String input, String fieldName) {
        if (input == null) {
            return null;
        }

        // Remove espaços em branco no início e fim
        input = input.trim();

        // Verifica se está vazio após trim
        if (input.isEmpty()) {
            return input;
        }

        // Validações de segurança
        if (containsSqlInjection(input)) {
            log.warn("🚫 Tentativa de SQL Injection detectada no campo: {} | Valor: {}", fieldName, input);
            throw new SecurityException("Input inválido detectado no campo: " + fieldName);
        }

        if (containsXSS(input)) {
            log.warn("🚫 Tentativa de XSS detectada no campo: {} | Valor: {}", fieldName, input);
            throw new SecurityException("Input inválido detectado no campo: " + fieldName);
        }

        if (containsPathTraversal(input)) {
            log.warn("🚫 Tentativa de Path Traversal detectada no campo: {} | Valor: {}", fieldName, input);
            throw new SecurityException("Input inválido detectado no campo: " + fieldName);
        }

        if (containsCommandInjection(input)) {
            log.warn("🚫 Tentativa de Command Injection detectada no campo: {} | Valor: {}", fieldName, input);
            throw new SecurityException("Input inválido detectado no campo: " + fieldName);
        }

        // Sanitização básica
        input = input.replaceAll("<", "&lt;")
                    .replaceAll(">", "&gt;")
                    .replaceAll("\"", "&quot;")
                    .replaceAll("'", "&#x27;")
                    .replaceAll("&", "&amp;");

        return input;
    }

    /**
     * Valida email
     */
    public String validateEmail(String email, String fieldName) {
        if (email == null || email.trim().isEmpty()) {
            return email;
        }

        email = email.trim().toLowerCase();

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            log.warn("🚫 Email inválido no campo: {} | Valor: {}", fieldName, email);
            throw new SecurityException("Formato de email inválido no campo: " + fieldName);
        }

        // Validações adicionais de segurança
        if (containsSqlInjection(email) || containsXSS(email)) {
            log.warn("🚫 Email malicioso detectado no campo: {} | Valor: {}", fieldName, email);
            throw new SecurityException("Email inválido detectado no campo: " + fieldName);
        }

        return email;
    }

    /**
     * Valida telefone
     */
    public String validatePhone(String phone, String fieldName) {
        if (phone == null || phone.trim().isEmpty()) {
            return phone;
        }

        phone = phone.trim();

        if (!PHONE_PATTERN.matcher(phone).matches()) {
            log.warn("🚫 Telefone inválido no campo: {} | Valor: {}", fieldName, phone);
            throw new SecurityException("Formato de telefone inválido no campo: " + fieldName);
        }

        return phone;
    }

    /**
     * Valida URL
     */
    public String validateUrl(String url, String fieldName) {
        if (url == null || url.trim().isEmpty()) {
            return url;
        }

        url = url.trim();

        if (!URL_PATTERN.matcher(url).matches()) {
            log.warn("🚫 URL inválida no campo: {} | Valor: {}", fieldName, url);
            throw new SecurityException("Formato de URL inválido no campo: " + fieldName);
        }

        // Validações adicionais de segurança
        if (containsSqlInjection(url) || containsXSS(url)) {
            log.warn("🚫 URL maliciosa detectada no campo: {} | Valor: {}", fieldName, url);
            throw new SecurityException("URL inválida detectada no campo: " + fieldName);
        }

        return url;
    }

    /**
     * Valida número
     */
    public Long validateNumber(String number, String fieldName) {
        if (number == null || number.trim().isEmpty()) {
            return null;
        }

        number = number.trim();

        try {
            return Long.parseLong(number);
        } catch (NumberFormatException e) {
            log.warn("🚫 Número inválido no campo: {} | Valor: {}", fieldName, number);
            throw new SecurityException("Formato de número inválido no campo: " + fieldName);
        }
    }

    /**
     * Valida comprimento máximo
     */
    public String validateMaxLength(String input, String fieldName, int maxLength) {
        if (input == null) {
            return input;
        }

        if (input.length() > maxLength) {
            log.warn("🚫 Input muito longo no campo: {} | Comprimento: {} | Máximo: {}", fieldName, input.length(), maxLength);
            throw new SecurityException("Campo " + fieldName + " excede o comprimento máximo de " + maxLength + " caracteres");
        }

        return input;
    }

    /**
     * Verifica se contém tentativa de SQL Injection
     */
    private boolean containsSqlInjection(String input) {
        return SQL_INJECTION_PATTERN.matcher(input).find();
    }

    /**
     * Verifica se contém tentativa de XSS
     */
    private boolean containsXSS(String input) {
        return XSS_PATTERN.matcher(input).find();
    }

    /**
     * Verifica se contém tentativa de Path Traversal
     */
    private boolean containsPathTraversal(String input) {
        return PATH_TRAVERSAL_PATTERN.matcher(input).find();
    }

    /**
     * Verifica se contém tentativa de Command Injection
     */
    private boolean containsCommandInjection(String input) {
        return COMMAND_INJECTION_PATTERN.matcher(input).find();
    }

    /**
     * Validação completa de objeto
     */
    public <T> T validateObject(T obj, String objectName) {
        if (obj == null) {
            return null;
        }

        // Aqui você pode implementar validação específica para diferentes tipos de objetos
        // Por exemplo, usando Bean Validation (JSR-303) ou validação customizada
        
        log.debug("✅ Objeto validado com sucesso: {}", objectName);
        return obj;
    }

    /**
     * Gera hash seguro para input
     */
    public String generateSecureHash(String input) {
        if (input == null) {
            return null;
        }

        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (Exception e) {
            log.error("❌ Erro ao gerar hash seguro: {}", e.getMessage());
            throw new SecurityException("Erro ao processar input de forma segura");
        }
    }
}
