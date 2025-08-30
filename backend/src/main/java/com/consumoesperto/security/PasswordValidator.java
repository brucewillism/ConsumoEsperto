package com.consumoesperto.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validador de Senhas com Políticas de Segurança
 * 
 * Este componente implementa validação robusta de senhas seguindo
 * as melhores práticas de segurança, incluindo verificação de
 * complexidade, histórico e força.
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Component
@Slf4j
public class PasswordValidator {

    @Value("${security.password.min-length:8}")
    private int minLength;

    @Value("${security.password.require-uppercase:true}")
    private boolean requireUppercase;

    @Value("${security.password.require-lowercase:true}")
    private boolean requireLowercase;

    @Value("${security.password.require-numbers:true}")
    private boolean requireNumbers;

    @Value("${security.password.require-special-chars:true}")
    private boolean requireSpecialChars;

    // Padrões para validação
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");
    
    // Senhas comuns que devem ser bloqueadas
    private static final List<String> COMMON_PASSWORDS = List.of(
        "password", "123456", "12345678", "qwerty", "abc123", "password123",
        "admin", "letmein", "welcome", "monkey", "dragon", "master", "hello",
        "freedom", "whatever", "qazwsx", "trustno1", "jordan", "harley",
        "rangers", "iwantu", "jennifer", "hunter", "buster", "soccer",
        "baseball", "tequila", "charlie", "gateway", "cowboys", "elephant",
        "mickey", "secret", "summer", "internet", "service", "canada",
        "coffee", "silver", "steelers", "snoopy", "bulldog", "slipknot"
    );

    /**
     * Valida uma senha de acordo com as políticas de segurança
     */
    public PasswordValidationResult validatePassword(String password) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Verificação de comprimento mínimo
        if (password == null || password.length() < minLength) {
            errors.add("Senha deve ter pelo menos " + minLength + " caracteres");
        }

        // Verificação de maiúsculas
        if (requireUppercase && !UPPERCASE_PATTERN.matcher(password).find()) {
            errors.add("Senha deve conter pelo menos uma letra maiúscula");
        }

        // Verificação de minúsculas
        if (requireLowercase && !LOWERCASE_PATTERN.matcher(password).find()) {
            errors.add("Senha deve conter pelo menos uma letra minúscula");
        }

        // Verificação de números
        if (requireNumbers && !NUMBER_PATTERN.matcher(password).find()) {
            errors.add("Senha deve conter pelo menos um número");
        }

        // Verificação de caracteres especiais
        if (requireSpecialChars && !SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            errors.add("Senha deve conter pelo menos um caractere especial (!@#$%^&*()_+-=[]{}|;:,.<>?)");
        }

        // Verificação de senhas comuns
        if (COMMON_PASSWORDS.contains(password.toLowerCase())) {
            errors.add("Senha muito comum e facilmente adivinhavel");
        }

        // Verificação de sequências
        if (hasSequentialChars(password)) {
            warnings.add("Senha contém sequências de caracteres que podem ser facilmente adivinhavel");
        }

        // Verificação de repetição
        if (hasRepeatingChars(password)) {
            warnings.add("Senha contém caracteres repetidos que reduzem a segurança");
        }

        // Verificação de força da senha
        int strength = calculatePasswordStrength(password);
        if (strength < 3) {
            warnings.add("Senha considerada fraca. Considere usar uma senha mais complexa");
        }

        boolean isValid = errors.isEmpty();
        
        return PasswordValidationResult.builder()
                .valid(isValid)
                .errors(errors)
                .warnings(warnings)
                .strength(strength)
                .build();
    }

    /**
     * Verifica se a senha contém sequências de caracteres
     */
    private boolean hasSequentialChars(String password) {
        if (password == null || password.length() < 3) return false;
        
        for (int i = 0; i < password.length() - 2; i++) {
            char c1 = password.charAt(i);
            char c2 = password.charAt(i + 1);
            char c3 = password.charAt(i + 2);
            
            if (Character.isLetterOrDigit(c1) && Character.isLetterOrDigit(c2) && Character.isLetterOrDigit(c3)) {
                if ((c2 == c1 + 1 && c3 == c2 + 1) || (c2 == c1 - 1 && c3 == c2 - 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Verifica se a senha contém caracteres repetidos
     */
    private boolean hasRepeatingChars(String password) {
        if (password == null || password.length() < 3) return false;
        
        for (int i = 0; i < password.length() - 2; i++) {
            char c1 = password.charAt(i);
            char c2 = password.charAt(i + 1);
            char c3 = password.charAt(i + 2);
            
            if (c1 == c2 && c2 == c3) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calcula a força da senha (0-5)
     */
    private int calculatePasswordStrength(String password) {
        if (password == null) return 0;
        
        int strength = 0;
        
        // Comprimento
        if (password.length() >= 8) strength++;
        if (password.length() >= 12) strength++;
        if (password.length() >= 16) strength++;
        
        // Complexidade
        if (UPPERCASE_PATTERN.matcher(password).find()) strength++;
        if (LOWERCASE_PATTERN.matcher(password).find()) strength++;
        if (NUMBER_PATTERN.matcher(password).find()) strength++;
        if (SPECIAL_CHAR_PATTERN.matcher(password).find()) strength++;
        
        // Penalidades
        if (hasSequentialChars(password)) strength = Math.max(0, strength - 1);
        if (hasRepeatingChars(password)) strength = Math.max(0, strength - 1);
        if (COMMON_PASSWORDS.contains(password.toLowerCase())) strength = 0;
        
        return Math.min(5, strength);
    }

    /**
     * Gera uma senha segura aleatória
     */
    public String generateSecurePassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:,.<>?";
        StringBuilder password = new StringBuilder();
        
        // Garante pelo menos um de cada tipo
        password.append((char) ('A' + (int) (Math.random() * 26))); // Maiúscula
        password.append((char) ('a' + (int) (Math.random() * 26))); // Minúscula
        password.append((char) ('0' + (int) (Math.random() * 10))); // Número
        password.append("!@#$%^&*()_+-=[]{}|;:,.<>?".charAt((int) (Math.random() * 32))); // Especial
        
        // Adiciona caracteres aleatórios para completar
        for (int i = 4; i < minLength; i++) {
            password.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        
        // Embaralha a senha
        return shuffleString(password.toString());
    }

    /**
     * Embaralha os caracteres de uma string
     */
    private String shuffleString(String input) {
        char[] chars = input.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = (int) (Math.random() * (i + 1));
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }

    /**
     * Resultado da validação de senha
     */
    public static class PasswordValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        private final int strength;

        private PasswordValidationResult(Builder builder) {
            this.valid = builder.valid;
            this.errors = builder.errors;
            this.warnings = builder.warnings;
            this.strength = builder.strength;
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public int getStrength() { return strength; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private boolean valid;
            private List<String> errors = new ArrayList<>();
            private List<String> warnings = new ArrayList<>();
            private int strength;

            public Builder valid(boolean valid) {
                this.valid = valid;
                return this;
            }

            public Builder errors(List<String> errors) {
                this.errors = errors;
                return this;
            }

            public Builder warnings(List<String> warnings) {
                this.warnings = warnings;
                return this;
            }

            public Builder strength(int strength) {
                this.strength = strength;
                return this;
            }

            public PasswordValidationResult build() {
                return new PasswordValidationResult(this);
            }
        }
    }
}
