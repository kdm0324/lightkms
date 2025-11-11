package io.lightkms.core.model;

public class EncryptionResult {
    private final String algorithm; // "AES256_GCM"
    private final String alias;     // key alias
    private final String ciphertext; // Base64(salt||iv||ciphertext)

    public EncryptionResult(String algorithm, String alias, String ciphertext) {
        this.algorithm = algorithm;
        this.alias = alias;
        this.ciphertext = ciphertext;
    }
    public String getAlgorithm() { return algorithm; }
    public String getAlias() { return alias; }
    public String getCiphertext() { return ciphertext; }

    @Override public String toString() {
        return String.format("ENC[%s,%s]:%s", algorithm, alias, ciphertext);
    }
}
