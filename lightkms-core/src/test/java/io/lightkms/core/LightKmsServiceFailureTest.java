package io.lightkms.core;

import io.lightkms.core.model.EncryptionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LightKmsServiceFailureTest {

    @Test
    void wrongPassword_shouldFailOnDecrypt() throws Exception {
        KeyManager km1 = new KeyManager();
        km1.put("DEFAULT", "RightPass");
        LightKmsService kms1 = new LightKmsService(km1);

        EncryptionResult enc = kms1.encrypt("DEFAULT", "hello");

        KeyManager km2 = new KeyManager();
        km2.put("DEFAULT", "WrongPass");
        LightKmsService kms2 = new LightKmsService(km2);

        assertThrows(Exception.class, () -> kms2.decrypt(enc.toString()));
    }

    @Test
    void tamperedCipher_shouldFail() throws Exception {
        KeyManager km = new KeyManager();
        km.put("DEFAULT", "P@ss");
        LightKmsService kms = new LightKmsService(km);

        String enc = kms.encrypt("DEFAULT", "hello").toString();
        // base64 끝 한 글자 바꾸기(간단한 변조)
        String bad = enc.substring(0, enc.length() - 2) + "A=";

        assertThrows(Exception.class, () -> kms.decrypt(bad));
    }

    @Test
    void unknownAlias_shouldFailFast() {
        KeyManager km = new KeyManager(); // alias 미등록
        LightKmsService kms = new LightKmsService(km);

        String token = "ENC[AES256_GCM,NOPE]:Zm9v"; // dummy
        assertThrows(Exception.class, () -> kms.decrypt(token));
    }
}
