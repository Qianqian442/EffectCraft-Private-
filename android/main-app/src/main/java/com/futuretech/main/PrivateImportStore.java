package com.futuretech.main;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;

/**
 * Copies files received through SAF, QQ, or Android Shares into an app-private inbox.
 * It only inspects file headers and metadata. It never extracts archives or executes binaries.
 */
public final class PrivateImportStore {
    private static final long MAX_IMPORT_BYTES = 8L * 1024L * 1024L * 1024L;
    private static final long MIN_FREE_BYTES = 16L * 1024L * 1024L;

    private PrivateImportStore() {}

    public static final class ImportedFile {
        public final String id;
        public final String originalName;
        public final String kind;
        public final String sha256;
        public final String mimeType;
        public final long bytes;
        public final File file;

        ImportedFile(String id, String originalName, String kind, String sha256,
                     String mimeType, long bytes, File file) {
            this.id = id;
            this.originalName = originalName;
            this.kind = kind;
            this.sha256 = sha256;
            this.mimeType = mimeType;
            this.bytes = bytes;
            this.file = file;
        }

        public String summary() {
            return originalName + "\n类型识别：" + kind + "\n大小：" + formatBytes(bytes)
                    + "\nSHA-256：" + sha256;
        }
    }

    public static ImportedFile copyFromUri(ContentResolver resolver, Uri uri, File inbox) throws Exception {
        if (resolver == null || uri == null || inbox == null) throw new IllegalArgumentException("导入参数无效");
        if (!inbox.exists() && !inbox.mkdirs()) throw new IllegalStateException("无法创建应用私有导入目录");
        if (!inbox.isDirectory()) throw new IllegalStateException("导入目录不可用");

        String name = queryDisplayName(resolver, uri);
        String mime = resolver.getType(uri);
        String id = UUID.randomUUID().toString();
        String extension = extensionOf(name);
        File partial = new File(inbox, id + ".partial");
        File stored = new File(inbox, id + extension);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0L;

        try (InputStream input = resolver.openInputStream(uri);
             FileOutputStream output = new FileOutputStream(partial)) {
            if (input == null) throw new IllegalStateException("文件提供方没有返回可读取的数据");
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count == 0) continue;
                total += count;
                if (total > MAX_IMPORT_BYTES) throw new IllegalStateException("单个导入文件不能超过 8 GiB");
                if (inbox.getUsableSpace() < count + MIN_FREE_BYTES)
                    throw new IllegalStateException("手机剩余存储空间不足，文件未导入完成");
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
            output.getFD().sync();
        } catch (Exception error) {
            // Never leave a partial file that could be mistaken for a successful import.
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            throw error;
        }

        if (total == 0L) {
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            throw new IllegalStateException("文件为空，无法导入");
        }
        if (!partial.renameTo(stored)) {
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            throw new IllegalStateException("无法保存导入文件");
        }

        String hash = hex(digest.digest());
        String kind = classify(name, stored);
        JSONObject metadata = new JSONObject();
        metadata.put("id", id);
        metadata.put("originalName", name);
        metadata.put("kind", kind);
        metadata.put("sha256", hash);
        metadata.put("mimeType", mime == null ? "" : mime);
        metadata.put("bytes", total);
        metadata.put("storedFile", stored.getName());
        metadata.put("importedAt", System.currentTimeMillis());

        File metadataFile = new File(inbox, id + ".json");
        File metadataTemp = new File(inbox, id + ".json.partial");
        try (FileOutputStream output = new FileOutputStream(metadataTemp)) {
            output.write(metadata.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.getFD().sync();
        } catch (Exception error) {
            //noinspection ResultOfMethodCallIgnored
            stored.delete();
            //noinspection ResultOfMethodCallIgnored
            metadataTemp.delete();
            throw error;
        }
        if (!metadataTemp.renameTo(metadataFile)) {
            //noinspection ResultOfMethodCallIgnored
            stored.delete();
            //noinspection ResultOfMethodCallIgnored
            metadataTemp.delete();
            throw new IllegalStateException("无法保存导入记录");
        }

        return new ImportedFile(id, name, kind, hash, mime == null ? "" : mime, total, stored);
    }

    public static ArrayList<String> history(File inbox) {
        ArrayList<String> entries = new ArrayList<>();
        File[] files = inbox == null ? null : inbox.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return entries;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = 0; i < files.length && i < 100; i++) {
            try (java.io.FileInputStream input = new java.io.FileInputStream(files[i])) {
                byte[] data = new byte[(int) Math.min(files[i].length(), 64 * 1024)];
                int count = input.read(data);
                if (count <= 0) continue;
                JSONObject item = new JSONObject(new String(data, 0, count, java.nio.charset.StandardCharsets.UTF_8));
                long bytes = item.optLong("bytes", 0L);
                String hash = item.optString("sha256", "");
                entries.add(item.optString("originalName", "未知文件")
                        + "\n" + item.optString("kind", "未识别")
                        + " · " + formatBytes(bytes)
                        + (hash.isEmpty() ? "" : "\nSHA-256：" + hash));
            } catch (Exception ignored) {
                entries.add("有一条导入记录无法读取");
            }
        }
        return entries;
    }

    private static String classify(String name, File file) throws Exception {
        String lower = name.toLowerCase(Locale.ROOT);
        String ext = extensionOf(name);
        byte[] head = new byte[8];
        int n;
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            n = input.read(head);
        }
        boolean pe = n >= 2 && head[0] == 'M' && head[1] == 'Z';
        boolean elf = n >= 4 && head[0] == 0x7f && head[1] == 'E' && head[2] == 'L' && head[3] == 'F';
        boolean wasm = n >= 4 && head[0] == 0x00 && head[1] == 0x61 && head[2] == 0x73 && head[3] == 0x6d;
        boolean zip = n >= 4 && head[0] == 'P' && head[1] == 'K'
                && (head[2] == 3 || head[2] == 5 || head[2] == 7);
        if (".aep".equals(ext)) return "电脑 AE 工程候选（需工程解析器验证）";
        if (".aepx".equals(ext)) return "电脑 AE XML 工程候选（需工程解析器验证）";
        if (".aex".equals(ext)) return pe ? "Adobe AE Windows 插件候选（PE，尚不能直接运行）"
                : "Adobe AE 插件扩展名（文件格式未验证）";
        if (".exe".equals(ext)) return pe ? "Windows PE 可执行文件（仅识别，未执行）"
                : "EXE 扩展名（不是有效 PE 头或格式未知）";
        if (".dll".equals(ext) || ".sys".equals(ext))
            return pe ? "Windows PE 动态库/驱动候选（仅识别，未加载）" : "Windows 库扩展名（格式未验证）";
        if (".msi".equals(ext)) return "Windows MSI 安装包候选（未安装）";
        if (".wasm".equals(ext) || wasm) return wasm ? "WebAssembly 模块（尚需插件 ABI 验证）" : "WASM 扩展名（魔数未验证）";
        if (elf) return "ELF 原生二进制（需识别 ABI 与系统依赖）";
        if (zip) return ".zip / 项目包候选（仅保存，未解压）";
        if (lower.endsWith(".xml")) return "XML 文件（需检查具体工程格式）";
        return "普通文件 / 未识别格式（已安全保存）";
    }

    private static String queryDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) return name.replaceAll("[\\\\/\\p{Cntrl}]", "_");
                }
            }
        } catch (Exception ignored) {}
        String fallback = uri.getLastPathSegment();
        return fallback == null || fallback.trim().isEmpty() ? "imported-file" : fallback.replaceAll("[\\\\/\\p{Cntrl}]", "_");
    }

    private static String extensionOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return ".bin";
        String ext = name.substring(dot).toLowerCase(Locale.ROOT);
        if (!ext.matches("\\.[a-z0-9]{1,12}")) return ".bin";
        return ext;
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.ROOT, "%.2f GiB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
