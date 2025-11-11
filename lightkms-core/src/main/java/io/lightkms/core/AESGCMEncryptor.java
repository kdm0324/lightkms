package io.lightkms.core;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

final class AESGCMEncryptor {
    private static final int KEY_LENGTH = 256;     // bits
    private static final int ITERATIONS = 120_000; // PBKDF2 rounds
    private static final int IV_LENGTH = 12;       // bytes
    private static final int TAG_LENGTH = 128;     // bits
    private static final int SALT_LENGTH = 20;     // bytes

    static String encrypt(String plaintext, char[] password) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);

        SecretKeySpec key = deriveKey(password, salt);
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] result = ByteBuffer.allocate(salt.length + iv.length + ciphertext.length)
            .put(salt).put(iv).put(ciphertext).array();
        return Base64.getEncoder().encodeToString(result);
    }

    static String decrypt(String encBase64, char[] password) throws Exception {
        byte[] blob = Base64.getDecoder().decode(encBase64);
        ByteBuffer bb = ByteBuffer.wrap(blob);

        byte[] salt = new byte[SALT_LENGTH]; bb.get(salt);
        byte[] iv = new byte[IV_LENGTH];     bb.get(iv);
        byte[] ciphertext = new byte[bb.remaining()]; bb.get(ciphertext);

        SecretKeySpec key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
        byte[] plain = cipher.doFinal(ciphertext);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static SecretKeySpec deriveKey(char[] password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
