package io.lightkms.cli;

import io.lightkms.core.KeyManager;
import io.lightkms.core.LightKmsService;
import io.lightkms.core.model.EncryptionResult;
import io.lightkms.core.keystore.FileKeystore;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

@Command(
    name = "lightkms",
    mixinStandardHelpOptions = true,
    version = "lightkms 0.1.0",
    subcommands = {
        LightKmsCli.Encrypt.class,
        LightKmsCli.Decrypt.class,
        LightKmsCli.Generate.class,
        LightKmsCli.Rotate.class,
        LightKmsCli.KeystoreInit.class,
        LightKmsCli.KeyAdd.class,
        LightKmsCli.KeyRotate.class
    }
)
public class LightKmsCli implements Runnable {
    public void run() { CommandLine.usage(this, System.out); }

    public static void main(String[] args) {
        int code = new CommandLine(new LightKmsCli()).execute(args);
        System.exit(code);
    }

    /* ------------------------------
       Common helpers
       ------------------------------ */

    static LightKmsService serviceWithSimpleKey(String alias, String password) {
        KeyManager km = new KeyManager();
        km.put(alias, password);
        return new LightKmsService(km);
    }

    /** 우선순위: env > 옵션 > 프롬프트(콘솔 마스킹, 콘솔 없으면 평문 입력) */
    static String resolvePassword(String optionPassword, String purpose, String alias) {
        String fromEnv = System.getenv("LIGHTKMS_PASSWORD");
        if (fromEnv != null && !fromEnv.isEmpty()) return fromEnv;
        if (optionPassword != null && !optionPassword.isEmpty()) return optionPassword;

        String label = (purpose == null ? "password" : purpose) + " (alias=" + alias + "): ";
        try {
            if (System.console() != null) {
                char[] pw = System.console().readPassword(label);
                return pw == null ? "" : new String(pw);
            } else {
                System.err.print(label); // 마스킹 불가 안내
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                return br.readLine();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read password: " + e.getMessage(), e);
        }
    }

    // ==== Keystore helpers (ENV 기반 자동 전환) ====

    static boolean usingKeystore() {
        return System.getenv("LIGHTKMS_KEK") != null;
    }

    static Path resolveKsPath() {
        String p = System.getenv("LIGHTKMS_KEYSTORE");
        if (p == null || p.isEmpty()) {
            p = System.getProperty("user.home") + "/.lightkms/keystore.json";
        }
        return Path.of(p);
    }

    /** keystore(DEK) 사용 가능하면 DEK 반환, 아니면 null */
    static byte[] loadDekOrNull(String alias) throws Exception {
        if (!usingKeystore()) return null;
        char[] kek = System.getenv("LIGHTKMS_KEK").toCharArray();
        FileKeystore ks = FileKeystore.load(resolveKsPath(), kek); // FileKeystore.load(Path, char[])
        return ks.getDek(alias);
    }

    /* ------------------------------
       Commands
       ------------------------------ */

    @Command(name = "encrypt", description = "Encrypt plaintext with alias/password (auto: keystore if LIGHTKMS_KEK present)")
    static class Encrypt implements Runnable {
        @Option(names = "--alias", defaultValue = "DEFAULT", description = "Key alias (default: ${DEFAULT-VALUE})")
        String alias;

        @Option(names = "--password", description = "Password for alias (fallback to $LIGHTKMS_PASSWORD or prompt)")
        String password;

        @Parameters(index = "0", description = "Plaintext")
        String plaintext;

        public void run() {
            try {
                // ① keystore(DEK) 경로 우선
                byte[] dek = loadDekOrNull(alias);
                if (dek != null) {
                    // DEK 경로는 KeyManager를 사용하지 않으므로 더미로 생성자만 만족
                    LightKmsService kms = new LightKmsService(new KeyManager());
                    String enc = kms.encryptWithDek(alias, plaintext, dek);
                    System.out.println(enc);
                    return;
                }

                // ② fallback: 기존 password 경로
                String pw = resolvePassword(password, "Enter password", alias);
                LightKmsService kms = serviceWithSimpleKey(alias, pw);
                EncryptionResult r = kms.encrypt(alias, plaintext);
                System.out.println(r.toString());
            } catch (Exception e) {
                System.err.println("ERR: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    @Command(name = "decrypt", description = "Decrypt ENC[...] value with alias/password (auto: keystore if LIGHTKMS_KEK present)")
    static class Decrypt implements Runnable {
        @Option(names = "--alias", defaultValue = "DEFAULT", description = "Key alias (default: ${DEFAULT-VALUE})")
        String alias;

        @Option(names = "--password", description = "Password for alias (fallback to $LIGHTKMS_PASSWORD or prompt)")
        String password;

        @Parameters(index = "0", description = "ENC[...] value (zsh는 따옴표로 감싸세요)")
        String enc;

        public void run() {
            try {
                // ① keystore(DEK) 경로 우선
                byte[] dek = loadDekOrNull(alias);
                if (dek != null) {
                    LightKmsService kms = new LightKmsService(new KeyManager());
                    System.out.println(kms.decryptWithDek(enc, dek));
                    return;
                }

                // ② fallback: 기존 password 경로
                String pw = resolvePassword(password, "Enter password", alias);
                LightKmsService kms = serviceWithSimpleKey(alias, pw);
                System.out.println(kms.decrypt(enc));
            } catch (Exception e) {
                System.err.println("ERR: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    @Command(name = "generate", description = "Register alias/password (noop v0)")
    static class Generate implements Runnable {
        @Option(names = "--alias", defaultValue = "DEFAULT", description = "Key alias (default: ${DEFAULT-VALUE})")
        String alias;

        @Option(names = "--password", description = "Password for alias (fallback to $LIGHTKMS_PASSWORD or prompt)")
        String password;

        public void run() {
            String pw = resolvePassword(password, "Enter password", alias);
            System.out.printf("alias registered (v0-memory): %s (len=%d)%n",
                alias, pw == null ? 0 : pw.length());
        }
    }

    @Command(name = "rotate", description = "Rotate alias password (noop v0)")
    static class Rotate implements Runnable {
        @Option(names = "--alias", defaultValue = "DEFAULT", description = "Key alias (default: ${DEFAULT-VALUE})")
        String alias;

        @Option(names = "--password", description = "New password (fallback to $LIGHTKMS_PASSWORD or prompt)")
        String password;

        public void run() {
            String pw = resolvePassword(password, "Enter new password", alias);
            System.out.printf("alias rotated (v0-memory): %s (len=%d)%n",
                alias, pw == null ? 0 : pw.length());
        }
    }

    /* ---------- Keystore management (file backend, v0) ---------- */

    @Command(name = "keystore-init", description = "Initialize a new JSON keystore")
    static class KeystoreInit implements Runnable {
        @Option(names = "--path", description = "Keystore file path (default: $HOME/.lightkms/keystore.json)")
        String path;

        public void run() {
            try {
                Path p = (path == null || path.isEmpty()) ? resolveKsPath() : Path.of(path);
                char[] kek = System.getenv("LIGHTKMS_KEK") != null
                    ? System.getenv("LIGHTKMS_KEK").toCharArray()
                    : resolvePassword("", "Enter KEK (LIGHTKMS_KEK absent)", "KEK").toCharArray();
                FileKeystore ks = FileKeystore.init(p, kek); // FileKeystore.init(Path, char[])
                ks.save();
                System.out.println("initialized keystore: " + p);
            } catch (Exception e) {
                System.err.println("ERR: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    @Command(name = "key-add", description = "Create an alias with v1 DEK")
    static class KeyAdd implements Runnable {
        @Option(names = "--alias", required = true, description = "Key alias")
        String alias;

        @Option(names = "--path", description = "Keystore file path")
        String path;

        public void run() {
            try {
                Path p = (path == null || path.isEmpty()) ? resolveKsPath() : Path.of(path);
                char[] kek = System.getenv("LIGHTKMS_KEK") != null
                    ? System.getenv("LIGHTKMS_KEK").toCharArray()
                    : resolvePassword("", "Enter KEK (LIGHTKMS_KEK absent)", "KEK").toCharArray();
                FileKeystore ks = FileKeystore.load(p, kek);
                ks.addAlias(alias);
                ks.save();
                System.out.println("added alias: " + alias);
            } catch (Exception e) {
                System.err.println("ERR: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    @Command(name = "key-rotate", description = "Rotate alias to next version")
    static class KeyRotate implements Runnable {
        @Option(names = "--alias", required = true, description = "Key alias")
        String alias;

        @Option(names = "--path", description = "Keystore file path")
        String path;

        public void run() {
            try {
                Path p = (path == null || path.isEmpty()) ? resolveKsPath() : Path.of(path);
                char[] kek = System.getenv("LIGHTKMS_KEK") != null
                    ? System.getenv("LIGHTKMS_KEK").toCharArray()
                    : resolvePassword("", "Enter KEK (LIGHTKMS_KEK absent)", "KEK").toCharArray();
                FileKeystore ks = FileKeystore.load(p, kek);
                ks.rotate(alias);
                ks.save();
                System.out.println("rotated alias: " + alias);
            } catch (Exception e) {
                System.err.println("ERR: " + e.getMessage());
                System.exit(1);
            }
        }
    }
}
