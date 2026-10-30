package com.laibin.sugarinventory.dt50;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class LoginCredentialStore {
    private static final String FILE_NAME = "remembered_login";
    private static final String KEY_ALIAS = "laibin_warehouse_login_key_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String USER_NAME = "user_name";
    private static final String PASSWORD = "password_ciphertext";
    private static final String PASSWORD_IV = "password_iv";

    private final SharedPreferences preferences;

    LoginCredentialStore(Context context) {
        preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    boolean save(String userName, String password) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            preferences.edit()
                    .putString(USER_NAME, userName == null ? "" : userName.trim())
                    .putString(PASSWORD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(PASSWORD_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .apply();
            return true;
        } catch (Exception ignored) {
            clear();
            return false;
        }
    }

    Credentials load() {
        String userName = preferences.getString(USER_NAME, "");
        String encryptedPassword = preferences.getString(PASSWORD, "");
        String encodedIv = preferences.getString(PASSWORD_IV, "");
        if (userName == null || userName.trim().isEmpty()
                || encryptedPassword == null || encryptedPassword.isEmpty()
                || encodedIv == null || encodedIv.isEmpty()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            byte[] iv = Base64.decode(encodedIv, Base64.NO_WRAP);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            byte[] decrypted = cipher.doFinal(Base64.decode(encryptedPassword, Base64.NO_WRAP));
            return new Credentials(userName.trim(), new String(decrypted, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            clear();
            return null;
        }
    }

    void clear() {
        preferences.edit().clear().apply();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        java.security.Key existingKey = keyStore.getKey(KEY_ALIAS, null);
        if (existingKey instanceof SecretKey) {
            return (SecretKey) existingKey;
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );
        keyGenerator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return keyGenerator.generateKey();
    }

    static final class Credentials {
        final String userName;
        final String password;

        Credentials(String userName, String password) {
            this.userName = userName;
            this.password = password;
        }
    }
}
