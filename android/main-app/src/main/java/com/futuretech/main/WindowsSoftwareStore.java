package com.futuretech.main;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** App-private catalogue and safe file staging for Windows software hosted by Winlator. */
public final class WindowsSoftwareStore {
    private static final long MAX_ARCHIVE_EXPANDED_BYTES = 4L * 1024L * 1024L * 1024L;
    private static final int MAX_ARCHIVE_ENTRIES = 10000;
    private static final int MAX_SCAN_RESULTS = 500;

    private WindowsSoftwareStore() {}

    public static File inbox(Context context) {
        return new File(context.getFilesDir(), "software/installers");
    }

    public static File containerRoot(Context context, int containerId) {
        return new File(new File(context.getFilesDir(), "rootfs/home"), "xuser-" + containerId);
    }

    public static File driveC(Context context, int containerId) {
        return new File(containerRoot(context, containerId), ".wine/drive_c");
    }

    public static JSONArray importedPackages(File inbox) {
        JSONArray result = new JSONArray();
        File[] records = inbox == null ? null : inbox.listFiles((dir, name) -> name.endsWith(".json"));
        if (records == null) return result;
        Arrays.sort(records, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = 0; i < records.length && i < 100; i++) {
            try {
                result.put(readJson(records[i]));
            } catch (Exception ignored) {}
        }
        return result;
    }

    public static File resolveImportedFile(File inbox, JSONObject record) throws IOException {
        String storedName = record.optString("storedFile", "");
        if (storedName.isEmpty() || storedName.contains("/") || storedName.contains("\\\\"))
            throw new IOException("导入文件记录无效");
        File base = inbox.getCanonicalFile();
        File file = new File(base, storedName).getCanonicalFile();
        if (!base.equals(file.getParentFile()) || !file.isFile())
            throw new IOException("导入文件不存在或路径无效");
        return file;
    }

    public static File copyIntoContainer(Context context, int containerId, File source, String packageId)
            throws IOException {
        if (source == null || !source.isFile() || source.length() <= 0) throw new IOException("软件包文件无效");
        File root = containerRoot(context, containerId).getCanonicalFile();
        File driveC = driveC(context, containerId).getCanonicalFile();
        if (!driveC.isDirectory()) throw new IOException("Windows 容器尚未初始化完成");
        File targetDir = new File(driveC, "FutureTechnology/Installers");
        if (!targetDir.isDirectory() && !targetDir.mkdirs()) throw new IOException("无法创建 Windows 安装目录");
        targetDir = targetDir.getCanonicalFile();
        if (!targetDir.getPath().startsWith(driveC.getPath() + File.separator))
            throw new IOException("安装目录越界");
        String safeName = source.getName().replaceAll("[^A-Za-z0-9._ -]", "_");
        if (safeName.isEmpty()) safeName = "package.bin";
        File target = new File(targetDir, packageId + "_" + safeName);
        File partial = new File(targetDir, target.getName() + ".partial");
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(partial)) {
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.getFD().sync();
        } catch (IOException error) {
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            throw error;
        }
        if (!partial.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            throw new IOException("无法将安装包复制到 Windows 容器");
        }
        if (!target.getCanonicalPath().startsWith(root.getPath() + File.separator))
            throw new IOException("安装包目标路径越界");
        return target;
    }

    public static ArrayList<File> extractZipIntoContainer(Context context, int containerId, File archive, String packageId)
            throws IOException {
        if (archive == null || !archive.isFile()) throw new IOException("ZIP 文件不存在");
        File driveC = driveC(context, containerId).getCanonicalFile();
        File appsRoot = new File(driveC, "FutureTechnology/Apps");
        if (!appsRoot.isDirectory() && !appsRoot.mkdirs()) throw new IOException("无法创建应用目录");
        File outputDir = new File(appsRoot, packageId).getCanonicalFile();
        if (!outputDir.getPath().startsWith(appsRoot.getCanonicalPath() + File.separator))
            throw new IOException("ZIP 目标路径无效");
        if (!outputDir.isDirectory() && !outputDir.mkdirs()) throw new IOException("无法创建解压目录");

        long expanded = 0L;
        int entryCount = 0;
        try (ZipInputStream input = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            byte[] buffer = new byte[128 * 1024];
            while ((entry = input.getNextEntry()) != null) {
                if (++entryCount > MAX_ARCHIVE_ENTRIES) throw new IOException("ZIP 文件包含过多条目");
                String entryName = entry.getName().replace('\\\\', '/');
                if (entryName.startsWith("/") || entryName.matches("^[A-Za-z]:.*"))
                    throw new IOException("ZIP 中包含绝对路径，已拒绝解压");
                String[] parts = entryName.split("/");
                for (String part : parts) {
                    if ("..".equals(part)) throw new IOException("ZIP 中包含目录穿越路径，已拒绝解压");
                }
                File target = new File(outputDir, entryName).getCanonicalFile();
                if (!target.getPath().startsWith(outputDir.getPath() + File.separator)
                        && !target.equals(outputDir))
                    throw new IOException("ZIP 解压路径越界");
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) throw new IOException("无法创建解压子目录");
                } else {
                    File parent = target.getParentFile();
                    if (parent == null || (!parent.isDirectory() && !parent.mkdirs()))
                        throw new IOException("无法创建解压子目录");
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            expanded += count;
                            if (expanded > MAX_ARCHIVE_EXPANDED_BYTES)
                                throw new IOException("ZIP 解压后超过 4 GiB 安全上限");
                            if (context.getFilesDir().getUsableSpace() < count + 16L * 1024L * 1024L)
                                throw new IOException("手机可用存储不足，已停止解压");
                            output.write(buffer, 0, count);
                        }
                        output.getFD().sync();
                    }
                }
                input.closeEntry();
            }
        }
        ArrayList<File> executables = new ArrayList<>();
        collectExecutables(outputDir, executables, 0, new HashSet<>());
        if (executables.isEmpty()) throw new IOException("压缩包内没有找到可运行的 EXE 文件");
        return executables;
    }

    public static ArrayList<File> scanInstalledExecutables(Context context, int containerId) throws IOException {
        File driveC = driveC(context, containerId).getCanonicalFile();
        ArrayList<File> result = new ArrayList<>();
        HashSet<String> visited = new HashSet<>();
        File[] roots = {
                new File(driveC, "Program Files"),
                new File(driveC, "Program Files (x86)"),
                new File(driveC, "users/xuser/Desktop"),
                new File(driveC, "ProgramData/Microsoft/Windows/Start Menu/Programs")
        };
        for (File root : roots) if (root.isDirectory()) collectExecutables(root, result, 0, visited);
        result.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static void collectExecutables(File directory, ArrayList<File> output, int depth, HashSet<String> visited)
            throws IOException {
        if (depth > 8 || output.size() >= MAX_SCAN_RESULTS) return;
        String canonical = directory.getCanonicalPath();
        if (!visited.add(canonical)) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File child : children) {
            if (output.size() >= MAX_SCAN_RESULTS) return;
            if (child.isDirectory()) {
                String name = child.getName().toLowerCase(Locale.ROOT);
                if (name.equals("windows") || name.equals("microsoft.net") || name.equals("temp")
                        || name.equals("cache") || name.equals("uninstall")) continue;
                collectExecutables(child, output, depth + 1, visited);
            } else if (child.isFile() && child.getName().toLowerCase(Locale.ROOT).endsWith(".exe")) {
                String name = child.getName().toLowerCase(Locale.ROOT);
                if (name.startsWith("unins") || name.startsWith("uninstall") || name.equals("setup.exe")
                        || name.equals("install.exe") || name.startsWith("crashreporter")) continue;
                output.add(child.getCanonicalFile());
            }
        }
    }

    public static JSONObject importVerifiedMarketplaceFile(Context context, File source, String originalName,
                                                            String expectedSha256) throws Exception {
        if (source == null || !source.isFile() || source.length() <= 0)
            throw new IOException("商城下载文件不存在");
        File inbox = inbox(context);
        if (!inbox.isDirectory() && !inbox.mkdirs()) throw new IOException("无法创建私有安装包目录");
        String id = UUID.randomUUID().toString();
        String extension = extensionOf(originalName == null ? source.getName() : originalName);
        File partial = new File(inbox, id + ".partial");
        File stored = new File(inbox, id + extension);
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        long total = 0L;
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(partial)) {
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count == 0) continue;
                total += count;
                if (total > 8L * 1024L * 1024L * 1024L)
                    throw new IOException("商城安装包超过 8 GiB 上限");
                if (inbox.getUsableSpace() < count + 16L * 1024L * 1024L)
                    throw new IOException("手机剩余空间不足");
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
            output.getFD().sync();
        } catch (Exception error) {
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            throw error;
        }
        String actualSha256 = hex(digest.digest());
        if (expectedSha256 == null || !actualSha256.equalsIgnoreCase(expectedSha256)) {
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            throw new SecurityException("商城安装包复制后 SHA-256 不匹配");
        }
        if (!partial.renameTo(stored)) {
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            throw new IOException("无法将商城安装包保存到私有目录");
        }
        JSONObject record = new JSONObject();
        try {
            record.put("id", id);
            record.put("originalName", originalName == null ? source.getName() : originalName);
            record.put("kind", extension.equals(".msi") ? "Windows MSI 安装包（已校验）"
                    : extension.equals(".zip") ? "Windows ZIP 软件包（已校验）"
                    : "Windows EXE 程序包（已校验）");
            record.put("sha256", actualSha256);
            record.put("mimeType", "application/octet-stream");
            record.put("bytes", total);
            record.put("storedFile", stored.getName());
            record.put("importedAt", System.currentTimeMillis());
        } catch (org.json.JSONException error) {
            //noinspection ResultOfMethodCallIgnored
            stored.delete();
            throw new IOException("无法保存商城下载记录", error);
        }
        try {
            writeAtomic(new File(inbox, id + ".json"), record.toString());
        } catch (IOException error) {
            //noinspection ResultOfMethodCallIgnored
            stored.delete();
            throw error;
        }
        return record;
    }

    private static String extensionOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return ".bin";
        String ext = name.substring(dot).toLowerCase(Locale.ROOT);
        return ext.matches("\\\\.[a-z0-9]{1,12}") ? ext : ".bin";
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }

    public static JSONArray installedApps(Context context) {
        File file = installedAppsFile(context);
        if (!file.isFile()) return new JSONArray();
        try {
            return new JSONArray(readText(file));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    public static JSONObject findInstalledApp(Context context, String id) {
        JSONArray apps = installedApps(context);
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app != null && id.equals(app.optString("id"))) return app;
        }
        return null;
    }

    public static JSONObject addInstalledApp(Context context, int containerId, String name, File executable)
            throws IOException {
        File driveC = driveC(context, containerId).getCanonicalFile();
        File appFile = executable.getCanonicalFile();
        if (!appFile.isFile() || !appFile.getPath().startsWith(driveC.getPath() + File.separator))
            throw new IOException("所选程序不在当前 Windows 容器内");
        JSONArray old = installedApps(context);
        JSONArray updated = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null && !appFile.getPath().equals(item.optString("execPath"))) updated.put(item);
        }
        JSONObject record = new JSONObject();
        String id = UUID.randomUUID().toString();
        try {
            record.put("id", id);
            record.put("name", name);
            record.put("containerId", containerId);
            record.put("execPath", appFile.getPath());
            record.put("addedAt", System.currentTimeMillis());
            updated.put(record);
        } catch (org.json.JSONException error) {
            throw new IOException("无法保存软件记录", error);
        }
        writeAtomic(installedAppsFile(context), updated.toString());
        return record;
    }

    private static File installedAppsFile(Context context) {
        File directory = new File(context.getFilesDir(), "software");
        if (!directory.isDirectory()) directory.mkdirs();
        return new File(directory, "installed_apps.json");
    }

    private static JSONObject readJson(File file) throws Exception {
        return new JSONObject(readText(file));
    }

    private static String readText(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int)Math.min(file.length(), 256 * 1024)];
            int count = input.read(data);
            return count <= 0 ? "" : new String(data, 0, count, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void writeAtomic(File target, String content) throws IOException {
        File partial = new File(target.getParentFile(), target.getName() + ".partial");
        try (FileOutputStream output = new FileOutputStream(partial)) {
            output.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (!partial.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            throw new IOException("无法保存电脑软件列表");
        }
    }
}