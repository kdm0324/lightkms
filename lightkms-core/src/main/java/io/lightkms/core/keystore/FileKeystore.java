package io.lightkms.core.keystore;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

public class FileKeystore implements Keystore {
    private static final int DEK_LEN = 32; // 256-bit
    private static final int IV_LEN = 12;
    private static final int TAG_LEN = 128;
    private static final int KDF_BITS = 256;
    private static final int KDF_ITER = 120_000;

    private final Path path;
    private final SecretKey kek;
    private final SecureRandom rnd = new SecureRandom();
    private final ObjectMapper om = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    // in-memory
    private Store store;

    public static FileKeystore init(Path path, char[] kekPass) throws Exception {
        FileKeystore ks = new FileKeystore(path, kekPass, true);
        ks.save();
        return ks;
    }
    public static FileKeystore load(Path path, char[] kekPass) throws Exception {
        return new FileKeystore(path, kekPass, false);
    }

    private FileKeystore(Path path, char[] kekPass, boolean initialize) throws Exception {
        this.path = path;
        if (initialize) {
            this.store = new Store();
            this.store.version = "v1";
            this.store.kdf = new Kdf();
            this.store.kdf.algo = "PBKDF2-HS512";
            this.store.kdf.iter = KDF_ITER;
            this.store.kdf.salt_b64 = base64(random(20));
            this.store.entries = new HashMap<>();
        } else {
            byte[] raw = Files.readAllBytes(path);
            this.store = om.readValue(raw, Store.class);
            if (this.store.entries == null) this.store.entries = new HashMap<>();
        }
        byte[] salt = base64decode(this.store.kdf.salt_b64);
        this.kek = KekFactory.derive(kekPass, salt, this.store.kdf.iter, KDF_BITS);
    }

    @Override
    public synchronized byte[] getDek(String alias) throws Exception {
        Entry e = store.entries.get(alias);
        if (e == null) throw new IllegalStateException("alias not found: " + alias);
        int cur = e.current;
        Version v = e.versions.get(cur);
        if (v == null) throw new IllegalStateException("missing version: " + cur);
        byte[] wrapped = base64decode(v.dek_wrapped_b64);
        return unwrap(wrapped);
    }

    @Override
    public synchronized void addAlias(String alias) throws Exception {
        if (store.entries.containsKey(alias)) throw new IllegalStateException("alias exists: " + alias);
        byte[] dek = random(DEK_LEN);
        byte[] wrapped = wrap(dek);
        Entry e = new Entry();
        e.current = 1;
        e.versions = new HashMap<>();
        Version v1 = new Version();
        v1.v = 1; v1.dek_wrapped_b64 = base64(wrapped); v1.created_at = Instant.now().toString();
        e.versions.put(1, v1);
        store.entries.put(alias, e);
    }

    @Override
    public synchronized void rotate(String alias) throws Exception {
        Entry e = store.entries.get(alias);
        if (e == null) throw new IllegalStateException("alias not found: " + alias);
        int next = e.current + 1;
        byte[] dek = random(DEK_LEN);
        byte[] wrapped = wrap(dek);
        Version vn = new Version();
        vn.v = next; vn.dek_wrapped_b64 = base64(wrapped); vn.created_at = Instant.now().toString();
        e.versions.put(next, vn);
        e.current = next;
    }

    @Override
    public synchronized void save() throws Exception {
        Files.createDirectories(path.getParent());
        byte[] json = om.writerWithDefaultPrettyPrinter().writeValueAsBytes(store);
        Files.write(path, json);
    }

    // ========== crypto helpers ==========
    private byte[] wrap(byte[] dek) throws Exception {
        byte[] iv = random(IV_LEN);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, kek, new GCMParameterSpec(TAG_LEN, iv));
        byte[] ct = c.doFinal(dek);
        return ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
    }
    private byte[] unwrap(byte[] wrapped) throws Exception {
        byte[] iv = Arrays.copyOfRange(wrapped, 0, IV_LEN);
        byte[] ct = Arrays.copyOfRange(wrapped, IV_LEN, wrapped.length);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, kek, new GCMParameterSpec(TAG_LEN, iv));
        return c.doFinal(ct);
    }
    private byte[] random(int len) { byte[] b = new byte[len]; rnd.nextBytes(b); return b; }
    private static String base64(byte[] b){ return Base64.getEncoder().encodeToString(b); }
    private static byte[] base64decode(String s){ return Base64.getDecoder().decode(s); }

    // ========== json models ==========
    static class Store { public String version; public Kdf kdf; public Map<String,Entry> entries; }
    static class Kdf { public String algo; public int iter; public String salt_b64; }
    static class Entry { public int current; public Map<Integer,Version> versions; }
    static class Version { public int v; public String dek_wrapped_b64; public String created_at; }
}
