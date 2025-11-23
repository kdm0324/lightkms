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

        // 정상 암호문 생성: ENC[AES256_GCM,DEFAULT]:Base64(salt||iv||ciphertext+tag)
        String token = kms.encrypt("DEFAULT", "hello").toString();

        // 헤더/본문 분리
        int p = token.indexOf("]:");
        String head = token.substring(0, p + 2);
        String b64  = token.substring(p + 2);

        // Base64 → raw bytes
        byte[] raw = java.util.Base64.getDecoder().decode(b64);

        // password 경로 포맷: salt(20) || iv(12) || ciphertext+tag
        int saltLen = 20;
        int ivLen = 12;

        // ciphertext 영역의 임의 바이트 하나를 뒤집어서 태그 검증이 깨지게 함
        int flipIndex = saltLen + ivLen + 5; // 메시지 길이가 짧아도 안전한 쪽으로 살짝 안쪽
        raw[flipIndex] ^= 0x01;

        // 다시 Base64로 싸서 ENC 포맷으로 복원
        String tampered = head + java.util.Base64.getEncoder().encodeToString(raw);

        // 기대: GCM 태그 검증 실패 → 예외 발생
        assertThrows(Exception.class, () -> kms.decrypt(tampered));
    }

    @Test
    void unknownAlias_shouldFailFast() {
        KeyManager km = new KeyManager(); // alias 미등록
        LightKmsService kms = new LightKmsService(km);

        String token = "ENC[AES256_GCM,NOPE]:Zm9v"; // dummy
        assertThrows(Exception.class, () -> kms.decrypt(token));
    }
}
