package com.atuy.sittotp;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores the TOTP seed encrypted with a non-exportable Android Keystore key. */
public final class SeedStore {
    private static final String PREFS_NAME = "encrypted_seed";
    private static final String PREF_CIPHERTEXT = "ciphertext";
    private static final String PREF_IV = "iv";
    private static final String KEY_ALIAS = "com.atuy.sittotp.seed_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SharedPreferences preferences;

    public SeedStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean hasSeed() {
        return preferences.contains(PREF_CIPHERTEXT) && preferences.contains(PREF_IV);
    }

    public void save(String normalizedSeed) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] ciphertext = cipher.doFinal(normalizedSeed.getBytes(StandardCharsets.UTF_8));

        boolean committed = preferences.edit()
                .putString(PREF_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putString(PREF_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .commit();
        if (!committed) {
            throw new GeneralSecurityException("シードを保存できませんでした");
        }
    }

    public String read() throws GeneralSecurityException {
        String ciphertextValue = preferences.getString(PREF_CIPHERTEXT, null);
        String ivValue = preferences.getString(PREF_IV, null);
        if (ciphertextValue == null || ivValue == null) {
            return null;
        }

        byte[] ciphertext = Base64.decode(ciphertextValue, Base64.NO_WRAP);
        byte[] iv = Base64.decode(ivValue, Base64.NO_WRAP);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    public void clear() throws GeneralSecurityException {
        preferences.edit().clear().commit();
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        try {
            keyStore.load(null);
        } catch (Exception exception) {
            throw new GeneralSecurityException("Android Keystoreを開けません", exception);
        }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS);
        }
    }

    private SecretKey getOrCreateKey() throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            SecretKey existing = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            if (existing != null) {
                return existing;
            }

            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
            );
            generator.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
            )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            return generator.generateKey();
        } catch (GeneralSecurityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GeneralSecurityException("Android Keystoreを利用できません", exception);
        }
    }
}
