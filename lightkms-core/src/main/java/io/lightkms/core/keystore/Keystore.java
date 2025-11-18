package io.lightkms.core.keystore;

public interface Keystore {
    /** 현재(alias)의 DEK(32바이트) 평문을 반환 */
    byte[] getDek(String alias) throws Exception;

    /** alias 신규 생성(v=1) */
    void addAlias(String alias) throws Exception;

    /** alias 로테이션(버전 +1) */
    void rotate(String alias) throws Exception;

    /** 변경사항 flush (파일 저장 등) */
    void save() throws Exception;
}
