package io.lightkms.core;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeyManager {
    private final Map<String, char[]> aliasToPassword = new ConcurrentHashMap<>();

    public void put(String alias, String password) {
        aliasToPassword.put(alias, password.toCharArray());
    }

    public void putRawBytes(String alias, byte[] raw) {
        aliasToPassword.put(alias, new String(raw, StandardCharsets.UTF_8).toCharArray());
    }

    public char[] get(String alias) {
        char[] pwd = aliasToPassword.get(alias);
        if (pwd == null) throw new IllegalArgumentException("No key for alias: " + alias);
        return pwd;
    }

    public void rotate(String alias, String newPassword) {
        aliasToPassword.put(alias, newPassword.toCharArray());
    }

    public boolean exists(String alias) { return aliasToPassword.containsKey(alias); }
}
