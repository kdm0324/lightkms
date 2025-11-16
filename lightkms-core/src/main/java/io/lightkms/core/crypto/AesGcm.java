package io.lightkms.core.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public final class AesGcm {
    private static final int IV_LEN = 12;
    private static final int TAG_LEN = 128;
    private static final byte SCHEME_DEK = 0x02; // format tag

    private AesGcm() {}

    public static String encrypt(byte[] dek, String plaintext) throws Exception {
        byte[] iv = new byte[IV_LEN];
        new SecureRandom().nextBytes(iv);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dek, "AES"), new GCMParameterSpec(TAG_LEN, iv));
        byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] out = ByteBuffer.allocate(1 + iv.length + ct.length)
            .put(SCHEME_DEK).put(iv).put(ct).array();
        return Base64.getEncoder().encodeToString(out);
    }

    public static String decrypt(byte[] dek, String b64) throws Exception {
        byte[] in = Base64.getDecoder().decode(b64);
        if (in.length < 1 + IV_LEN + 1 || in[0] != SCHEME_DEK) {
            throw new IllegalArgumentException("unsupported payload format");
        }
        byte[] iv = new byte[IV_LEN];
        System.arraycopy(in, 1, iv, 0, IV_LEN);
        byte[] ct = new byte[in.length - 1 - IV_LEN];
        System.arraycopy(in, 1 + IV_LEN, ct, 0, ct.length);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dek, "AES"), new GCMParameterSpec(TAG_LEN, iv));
        byte[] pt = c.doFinal(ct);
        return new String(pt, StandardCharsets.UTF_8);
    }
}
