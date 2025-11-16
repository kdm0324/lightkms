package io.lightkms.core.keystore;

import javax.crypto.SecretKeyFactory;
import javax.crypto.SecretKey;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class KekFactory {
    private KekFactory() {}
    public static SecretKey derive(char[] pass, byte[] salt, int iter, int bits) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pass, salt, iter, bits);
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        SecretKey tmp = f.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }
}
