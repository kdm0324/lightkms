package io.lightkms.cli;

import io.lightkms.core.KeyManager;
import io.lightkms.core.LightKmsService;
import io.lightkms.core.model.EncryptionResult;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Command(
    name = "lightkms",
    mixinStandardHelpOptions = true,
    version = "lightkms 0.1.0",
    subcommands = { LightKmsCli.Encrypt.class, LightKmsCli.Decrypt.class, LightKmsCli.Generate.class, LightKmsCli.Rotate.class }
)
public class LightKmsCli implements Runnable {
    public void run() { CommandLine.usage(this, System.out); }

    public static void main(String[] args) {
        int code = new CommandLine(new LightKmsCli()).execute(args);
        System.exit(code);
    }

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

    @Command(name = "encrypt", description = "Encrypt plaintext with alias/password")
    static class Encrypt implements Runnable {
        @Option(names = "--alias", defaultValue = "DEFAULT", description = "Key alias (default: ${DEFAULT-VALUE})")
        String alias;

        @Option(names = "--password", description = "Password for alias (fallback to $LIGHTKMS_PASSWORD or prompt)")
        String password;

        @Parameters(index = "0", description = "Plaintext")
        String plaintext;

        public void run() {
            try {
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

    @Command(name = "decrypt", description = "Decrypt ENC[...] value with alias/password")
    static class Decrypt implements Runnable {
        @Option(names = "--alias", defaultValue = "DEFAULT", description = "Key alias (default: ${DEFAULT-VALUE})")
        String alias;

        @Option(names = "--password", description = "Password for alias (fallback to $LIGHTKMS_PASSWORD or prompt)")
        String password;

        @Parameters(index = "0", description = "ENC[...] value")
        String enc;

        public void run() {
            try {
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
            System.out.printf("alias registered (v0-memory): %s (len=%d)%n", alias, pw == null ? 0 : pw.length());
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
            System.out.printf("alias rotated (v0-memory): %s (len=%d)%n", alias, pw == null ? 0 : pw.length());
        }
    }
}
