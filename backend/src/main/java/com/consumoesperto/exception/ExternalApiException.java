package com.consumoesperto.exception;

/**
 * Exceção para erros de integração com APIs externas
 */
public class ExternalApiException extends RuntimeException {
    private final String apiName;
    private final int statusCode;
    private final String responseBody;
    
    public ExternalApiException(String message, String apiName, int statusCode, String responseBody) {
        super(message);
        this.apiName = apiName;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
    
    public ExternalApiException(String message, String apiName, int statusCode, String responseBody, Throwable cause) {
        super(message, cause);
        this.apiName = apiName;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
    
    public String getApiName() {
        return apiName;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public String getResponseBody() {
        return responseBody;
    }
}
