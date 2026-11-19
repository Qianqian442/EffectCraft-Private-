package com.futuretech.shared;

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

/** Stores short-lived tokens encrypted by an Android Keystore AES key. */
public final class TokenVault {
    private static final String PREFS = "future_tech_session";
    private static final String ALIAS = "future_tech_session_aes_v1";
    private TokenVault() {}

    public static void save(Context context, String token) throws Exception { saveSession(context, token, null); }

    public static void saveSession(Context context, String accessToken, String refreshToken) throws Exception {
        SharedPreferences.Editor edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (accessToken != null) putEncrypted(edit, "access", accessToken);
        if (refreshToken != null) putEncrypted(edit, "refresh", refreshToken);
        edit.apply();
    }

    private static void putEncrypted(SharedPreferences.Editor edit, String name, String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        edit.putString(name + "_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
        edit.putString(name + "_data", Base64.encodeToString(encrypted, Base64.NO_WRAP));
    }

    private static String readEncrypted(Context context, String name) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String iv = prefs.getString(name + "_iv", null), data = prefs.getString(name + "_data", null);
        if (iv == null || data == null) return null;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    public static String read(Context context) throws Exception { return readEncrypted(context, "access"); }
    public static String readRefresh(Context context) throws Exception { return readEncrypted(context, "refresh"); }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing = store.getKey(ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false).build());
        return generator.generateKey();
    }
}
