package io.lightkms.core;

import io.lightkms.core.model.EncryptionResult;

public class LightKmsService {
    private final KeyManager keyManager;

    public LightKmsService(KeyManager keyManager) {
        this.keyManager = keyManager;
    }

    public EncryptionResult encrypt(String alias, String plaintext) throws Exception {
        char[] pwd = keyManager.get(alias);
        String b64 = AESGCMEncryptor.encrypt(plaintext, pwd);
        return new EncryptionResult("AES256_GCM", alias, b64);
    }

    public String decrypt(String encValue) throws Exception {
        if (!encValue.startsWith("ENC[")) return encValue;
        int headerEnd = encValue.indexOf("]:");
        if (headerEnd < 0) throw new IllegalArgumentException("Invalid ENC format");
        String header = encValue.substring(4, headerEnd); // "AES256_GCM,ALIAS"
        String[] parts = header.split(",", 2);
        String algorithm = parts[0];
        String alias = parts[1];

        if (!"AES256_GCM".equals(algorithm))
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);

        String base64 = encValue.substring(headerEnd + 2);
        char[] pwd = keyManager.get(alias);
        return AESGCMEncryptor.decrypt(base64, pwd);
    }
}
