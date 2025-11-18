package io.lightkms.core;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LightKmsServiceDekTest {

    /** LightKmsService 생성자가 KeyManager를 요구하므로 테스트용 더미 구현 */
    static class DummyKeyManager extends KeyManager {
        @Override public char[] get(String alias) { return "ignored".toCharArray(); }
    }

    @Test
    void roundTripWithDek() throws Exception {
        byte[] dek = new byte[32];
        new SecureRandom().nextBytes(dek);

        // keyManager는 DEK 경로에서 쓰지 않지만 시그니처 맞추기 위해 더미 주입
        LightKmsService kms = new LightKmsService(new DummyKeyManager());

        String enc = kms.encryptWithDek("DEFAULT", "hello", dek);
        System.out.println("ENC: " + enc);
        String dec = kms.decryptWithDek(enc, dek);
        System.out.println("DEC: " + dec);
        assertEquals("hello", dec);
    }
}
