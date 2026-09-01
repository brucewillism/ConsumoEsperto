package com.consumoesperto.edith.security;

/**
 * Headers HMAC do Tool Bridge — espelham {@code io.edith.sdk.tools.EdithToolAuthenticator}.
 */
public final class EdithCallbackHeaders {

    public static final String TIMESTAMP = "X-Edith-Timestamp";
    public static final String NONCE = "X-Edith-Nonce";
    public static final String REQUEST_ID = "X-Edith-Request-Id";
    public static final String SIGNATURE = "X-Edith-Signature";

    private EdithCallbackHeaders() {
    }
}
