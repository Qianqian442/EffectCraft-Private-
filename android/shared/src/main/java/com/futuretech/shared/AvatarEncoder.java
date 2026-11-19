package com.futuretech.shared;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Compresses a photo-picker image before registration; server revalidates the bytes. */
public final class AvatarEncoder {
    private static final int MAX_SOURCE_BYTES = 12 * 1024 * 1024;
    private static final int MAX_OUTPUT_BYTES = 512 * 1024;
    private AvatarEncoder() {}

    public static String readBase64(ContentResolver resolver, Uri uri) throws Exception {
        if (uri == null) return null;
        byte[] source;
        try (InputStream in = resolver.openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalArgumentException("无法读取所选头像。");
            byte[] buffer = new byte[8192];
            int count, total = 0;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > MAX_SOURCE_BYTES)
                    throw new IllegalArgumentException("原始头像超过 12 MB，请先压缩后选择。");
                out.write(buffer, 0, count);
            }
            source = out.toByteArray();
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(source, 0, source.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
            throw new IllegalArgumentException("所选文件不是有效图片。");
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 1024) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeByteArray(source, 0, source.length, options);
        if (bitmap == null) throw new IllegalArgumentException("头像解码失败。");
        float scale = Math.min(1f, 512f / Math.max(bitmap.getWidth(), bitmap.getHeight()));
        Bitmap resized = Bitmap.createScaledBitmap(bitmap,
                Math.max(1, (int)(bitmap.getWidth() * scale)),
                Math.max(1, (int)(bitmap.getHeight() * scale)), true);
        if (resized != bitmap) bitmap.recycle();
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        int quality = 82;
        do {
            encoded.reset();
            resized.compress(Bitmap.CompressFormat.JPEG, quality, encoded);
            quality -= 8;
        } while (encoded.size() > MAX_OUTPUT_BYTES && quality >= 42);
        resized.recycle();
        if (encoded.size() > MAX_OUTPUT_BYTES)
            throw new IllegalArgumentException("头像压缩失败，请选择尺寸更小的图片。");
        return Base64.encodeToString(encoded.toByteArray(), Base64.NO_WRAP);
    }
}
