package com.atuy.sittotp;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** SHA-256 / 6 digit / 30 second TOTP used by SIT's Microsoft Authenticator setup. */
public final class Totp {
    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private Totp() {
    }

    public static String normalizeSeed(String value) {
        if (value == null) {
            throw new IllegalArgumentException("シードを入力してください");
        }

        String seed = value.trim();
        if (seed.regionMatches(true, 0, "otpauth://", 0, "otpauth://".length())) {
            seed = extractSecretFromOtpAuth(seed);
        }

        String normalized = seed
                .replaceAll("\\s+", "")
                .replace("-", "")
                .replaceAll("=+$", "")
                .toUpperCase(Locale.ROOT);

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("シードを入力してください");
        }

        for (int i = 0; i < normalized.length(); i++) {
            if (BASE32_ALPHABET.indexOf(normalized.charAt(i)) < 0) {
                throw new IllegalArgumentException("シードはBase32形式で入力してください");
            }
        }

        decodeBase32(normalized);
        return normalized;
    }

    private static String extractSecretFromOtpAuth(String value) {
        try {
            URI uri = URI.create(value);
            String query = uri.getRawQuery();
            if (query == null) {
                throw new IllegalArgumentException("otpauth URIにsecretがありません");
            }
            for (String pair : query.split("&")) {
                int separator = pair.indexOf('=');
                String rawName = separator >= 0 ? pair.substring(0, separator) : pair;
                String rawValue = separator >= 0 ? pair.substring(separator + 1) : "";
                String name = decodeUrlComponent(rawName);
                if ("secret".equals(name)) {
                    String secret = decodeUrlComponent(rawValue);
                    if (!secret.trim().isEmpty()) {
                        return secret;
                    }
                }
            }
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("secret")) {
                throw exception;
            }
            throw new IllegalArgumentException("otpauth URIを読み取れません", exception);
        }
        throw new IllegalArgumentException("otpauth URIにsecretがありません");
    }

    private static String decodeUrlComponent(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static byte[] decodeBase32(String normalizedSeed) {
        if (normalizedSeed == null || normalizedSeed.isEmpty()) {
            throw new IllegalArgumentException("シードを入力してください");
        }

        byte[] output = new byte[(normalizedSeed.length() * 5) / 8];
        int outputIndex = 0;
        int buffer = 0;
        int bitsInBuffer = 0;

        for (int i = 0; i < normalizedSeed.length(); i++) {
            int value = BASE32_ALPHABET.indexOf(normalizedSeed.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("シードはBase32形式で入力してください");
            }

            buffer = (buffer << 5) | value;
            bitsInBuffer += 5;

            if (bitsInBuffer >= 8) {
                bitsInBuffer -= 8;
                output[outputIndex++] = (byte) ((buffer >> bitsInBuffer) & 0xFF);
            }
        }

        if (outputIndex == 0) {
            throw new IllegalArgumentException("シードが短すぎます");
        }
        if (bitsInBuffer > 0 && (buffer & ((1 << bitsInBuffer) - 1)) != 0) {
            throw new IllegalArgumentException("Base32シードの末尾ビットが不正です");
        }
        return output;
    }

    public static String generate(String normalizedSeed, long epochSeconds) {
        if (epochSeconds < 0) {
            throw new IllegalArgumentException("時刻が不正です");
        }
        byte[] key = decodeBase32(normalizedSeed);
        long counter = epochSeconds / PERIOD_SECONDS;

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());

            int offset = digest[digest.length - 1] & 0x0F;
            int binary = ((digest[offset] & 0x7F) << 24)
                    | ((digest[offset + 1] & 0xFF) << 16)
                    | ((digest[offset + 2] & 0xFF) << 8)
                    | (digest[offset + 3] & 0xFF);

            int code = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", code);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTPを生成できません", exception);
        }
    }

    public static int remainingSeconds(long epochSeconds) {
        return PERIOD_SECONDS - (int) Math.floorMod(epochSeconds, PERIOD_SECONDS);
    }
}
