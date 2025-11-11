package io.lightkms.core.model;

public class KeyMetadata {
    private final String alias;
    private final int iterations;
    private final int keyLength;

    public KeyMetadata(String alias, int iterations, int keyLength) {
        this.alias = alias;
        this.iterations = iterations;
        this.keyLength = keyLength;
    }
    public String getAlias() { return alias; }
    public int getIterations() { return iterations; }
    public int getKeyLength() { return keyLength; }
}
