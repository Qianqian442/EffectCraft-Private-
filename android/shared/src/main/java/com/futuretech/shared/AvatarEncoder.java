package com.futuretech.shared;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Reads a user-selected avatar under a strict size limit; the server must validate image bytes. */
public final class AvatarEncoder {
    private static final int MAX_BYTES = 512 * 1024;
    private AvatarEncoder() {}

    public static String readBase64(ContentResolver resolver, Uri uri) throws Exception {
        if (uri == null) return null;
        try (InputStream in = resolver.openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalArgumentException("无法读取所选头像。");
            byte[] buffer = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > MAX_BYTES) throw new IllegalArgumentException("头像文件过大，请选择 512 KB 以内的图片。");
                out.write(buffer, 0, n);
            }
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        }
    }
}
