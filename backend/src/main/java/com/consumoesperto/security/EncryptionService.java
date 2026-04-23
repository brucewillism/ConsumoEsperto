package com.consumoesperto.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Serviço de Criptografia para Dados Sensíveis
 * 
 * Este serviço implementa criptografia AES-256 para proteger dados sensíveis
 * como tokens de acesso, senhas e informações pessoais dos usuários.
 * 
 * Funcionalidades:
 * - Criptografia AES-256-GCM
 * - Geração segura de chaves
 * - Criptografia/descriptografia de strings
 * - Validação de integridade
 * - Suporte a diferentes tipos de dados
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    @Value("${security.encryption.key:}")
    private String encryptionKey;

    private SecretKey secretKey;

    /**
     * Inicializa a chave de criptografia
     */
    public void initializeKey() {
        try {
            if (encryptionKey != null && !encryptionKey.trim().isEmpty()) {
                // Usa a chave fornecida
                byte[] keyBytes = Base64.getDecoder().decode(encryptionKey);
                this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
                log.info("✅ Chave de criptografia carregada com sucesso");
            } else {
                // Gera uma nova chave (apenas para desenvolvimento)
                log.warn("⚠️ Chave de criptografia não configurada, gerando nova chave");
                generateNewKey();
            }
        } catch (Exception e) {
            log.error("❌ Erro ao inicializar chave de criptografia: {}", e.getMessage());
            throw new RuntimeException("Falha ao inicializar criptografia", e);
        }
    }

    /**
     * Gera uma nova chave de criptografia
     */
    private void generateNewKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_LENGTH);
            this.secretKey = keyGenerator.generateKey();
            
            String base64Key = Base64.getEncoder().encodeToString(secretKey.getEncoded());
            log.warn("🔑 NOVA CHAVE GERADA (APENAS PARA DESENVOLVIMENTO): {}", base64Key);
            log.warn("⚠️ CONFIGURE A CHAVE NO ARQUIVO DE CONFIGURAÇÃO!");
        } catch (Exception e) {
            log.error("❌ Erro ao gerar nova chave: {}", e.getMessage());
            throw new RuntimeException("Falha ao gerar chave de criptografia", e);
        }
    }

    /**
     * Criptografa uma string
     * 
     * @param plainText Texto a ser criptografado
     * @return String criptografada em Base64
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.trim().isEmpty()) {
            return plainText;
        }

        try {
            if (secretKey == null) {
                initializeKey();
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            
            // Gera IV aleatório
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH * 8, iv));
            
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // Combina IV + dados criptografados
            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + encryptedBytes.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedBytes, 0, encryptedWithIv, GCM_IV_LENGTH, encryptedBytes.length);
            
            return Base64.getEncoder().encodeToString(encryptedWithIv);
            
        } catch (Exception e) {
            log.error("❌ Erro ao criptografar dados: {}", e.getMessage());
            throw new RuntimeException("Falha na criptografia", e);
        }
    }

    /**
     * Descriptografa uma string
     * 
     * @param encryptedText Texto criptografado em Base64
     * @return String descriptografada
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.trim().isEmpty()) {
            return encryptedText;
        }

        try {
            if (secretKey == null) {
                initializeKey();
            }

            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedText);
            
            // Extrai IV e dados criptografados
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedBytes = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            
            System.arraycopy(encryptedWithIv, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.length);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH * 8, iv));
            
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("❌ Erro ao descriptografar dados: {}", e.getMessage());
            throw new RuntimeException("Falha na descriptografia", e);
        }
    }

    /**
     * Criptografa dados de cartão de crédito
     * 
     * @param cardNumber Número do cartão
     * @return Número criptografado
     */
    public String encryptCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            return cardNumber;
        }
        
        // Remove espaços e caracteres não numéricos
        String cleanNumber = cardNumber.replaceAll("[^0-9]", "");
        
        if (cleanNumber.length() < 13 || cleanNumber.length() > 19) {
            log.warn("⚠️ Número de cartão inválido: {}", maskCardNumber(cardNumber));
            return cardNumber;
        }
        
        return encrypt(cleanNumber);
    }

    /**
     * Descriptografa dados de cartão de crédito
     * 
     * @param encryptedCardNumber Número criptografado
     * @return Número descriptografado
     */
    public String decryptCardNumber(String encryptedCardNumber) {
        if (encryptedCardNumber == null || encryptedCardNumber.trim().isEmpty()) {
            return encryptedCardNumber;
        }
        
        try {
            return decrypt(encryptedCardNumber);
        } catch (Exception e) {
            log.error("❌ Erro ao descriptografar número do cartão: {}", e.getMessage());
            return encryptedCardNumber;
        }
    }

    /**
     * Criptografa token de acesso
     * 
     * @param accessToken Token de acesso
     * @return Token criptografado
     */
    public String encryptAccessToken(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            return accessToken;
        }
        
        return encrypt(accessToken);
    }

    /**
     * Descriptografa token de acesso
     * 
     * @param encryptedAccessToken Token criptografado
     * @return Token descriptografado
     */
    public String decryptAccessToken(String encryptedAccessToken) {
        if (encryptedAccessToken == null || encryptedAccessToken.trim().isEmpty()) {
            return encryptedAccessToken;
        }
        
        try {
            return decrypt(encryptedAccessToken);
        } catch (Exception e) {
            log.error("❌ Erro ao descriptografar token de acesso: {}", e.getMessage());
            return encryptedAccessToken;
        }
    }

    /**
     * Criptografa dados pessoais sensíveis
     * 
     * @param personalData Dados pessoais
     * @return Dados criptografados
     */
    public String encryptPersonalData(String personalData) {
        if (personalData == null || personalData.trim().isEmpty()) {
            return personalData;
        }
        
        return encrypt(personalData);
    }

    /**
     * Descriptografa dados pessoais sensíveis
     * 
     * @param encryptedPersonalData Dados criptografados
     * @return Dados descriptografados
     */
    public String decryptPersonalData(String encryptedPersonalData) {
        if (encryptedPersonalData == null || encryptedPersonalData.trim().isEmpty()) {
            return encryptedPersonalData;
        }
        
        try {
            return decrypt(encryptedPersonalData);
        } catch (Exception e) {
            log.error("❌ Erro ao descriptografar dados pessoais: {}", e.getMessage());
            return encryptedPersonalData;
        }
    }

    /**
     * Mascara um número de cartão para logs
     * 
     * @param cardNumber Número do cartão
     * @return Número mascarado
     */
    public String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 8) {
            return "****";
        }
        
        String cleanNumber = cardNumber.replaceAll("[^0-9]", "");
        if (cleanNumber.length() < 8) {
            return "****";
        }
        
        return cleanNumber.substring(0, 4) + " **** **** " + cleanNumber.substring(cleanNumber.length() - 4);
    }

    /**
     * Mascara um token para logs
     * 
     * @param token Token
     * @return Token mascarado
     */
    public String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }

    /**
     * Mascara dados pessoais para logs
     * 
     * @param personalData Dados pessoais
     * @return Dados mascarados
     */
    public String maskPersonalData(String personalData) {
        if (personalData == null || personalData.length() < 4) {
            return "***";
        }
        
        if (personalData.length() <= 8) {
            return personalData.substring(0, 2) + "***" + personalData.substring(personalData.length() - 2);
        }
        
        return personalData.substring(0, 3) + "***" + personalData.substring(personalData.length() - 3);
    }

    /**
     * Verifica se a criptografia está configurada
     * 
     * @return true se configurada
     */
    public boolean isEncryptionConfigured() {
        return secretKey != null;
    }

    /**
     * Gera uma nova chave de criptografia (apenas para desenvolvimento)
     * 
     * @return Chave em Base64
     */
    public String generateNewKeyBase64() {
        generateNewKey();
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }
}
