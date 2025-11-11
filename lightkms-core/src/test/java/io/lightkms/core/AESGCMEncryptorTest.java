package io.lightkms.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AESGCMEncryptorTest {
    @Test
    void roundtrip() throws Exception {
        String plain = "hello-lightkms";
        char[] pwd = "password-123".toCharArray();
        String enc = AESGCMEncryptor.encrypt(plain, pwd);
        System.out.println("ENC: " + enc);
        String dec = AESGCMEncryptor.decrypt(enc, pwd);
        System.out.println("DEC: " + dec);
        assertEquals(plain, dec);
    }
}
