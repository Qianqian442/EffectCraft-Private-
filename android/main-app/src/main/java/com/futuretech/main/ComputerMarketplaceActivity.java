package com.futuretech.main;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.futuretech.shared.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Phone-side Windows software marketplace. Package bytes are downloaded to this phone. */
public final class ComputerMarketplaceActivity extends Activity {
    private static final long MAX_PACKAGE_BYTES = 8L * 1024L * 1024L * 1024L;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ArrayList<CatalogItem> items = new ArrayList<>();
    private LinearLayout results;
    private EditText searchInput;
    private TextView status;
    private String selectedCategory = "";
    private boolean receiverRegistered;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (id > 0L) verifyCompletedDownload(id);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else {
            //noinspection deprecation
            registerReceiver(downloadReceiver, filter);
        }
        receiverRegistered = true;
        loadCatalog();
    }

    @Override protected void onResume() {
        super.onResume();
        reconcileDownloads();
    }

    private void buildUi() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF3F6F8);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("返回");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(76), dp(48)));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.computer_marketplace_icon);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        iconParams.leftMargin = dp(12);
        top.addView(icon, iconParams);

        TextView title = new TextView(this);
        title.setText("电脑应用商城");
        title.setTextColor(0xFF15243B);
        title.setTextSize(23);
        title.setTypeface(null, 1);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.leftMargin = dp(12);
        top.addView(title, titleParams);
        root.addView(top);

        TextView subtitle = new TextView(this);
        subtitle.setText("为手机上的 Windows 兼容环境查找电脑软件");
        subtitle.setTextColor(0xFF68778B);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(12), 0, dp(10));
        root.addView(subtitle);

        LinearLayout searchRow = new LinearLayout(this);
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(15);
        searchInput.setHint("搜索软件名称、开发者或分类");
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button search = new Button(this);
        search.setText("搜索");
        search.setAllCaps(false);
        search.setOnClickListener(v -> renderItems());
        searchRow.addView(search, new LinearLayout.LayoutParams(dp(84), dp(50)));
        root.addView(searchRow);

        android.widget.HorizontalScrollView categoryScroll = new android.widget.HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout categories = new LinearLayout(this);
        categories.setOrientation(LinearLayout.HORIZONTAL);
        String[][] choices = {
            {"全部", ""}, {"办公", "office"}, {"开发", "development"},
            {"工具", "utility"}, {"图像", "graphics"}, {"其他", "other"}
        };
        for (String[] pair : choices) {
            Button button = new Button(this);
            button.setText(pair[0]);
            button.setTextSize(12);
            button.setAllCaps(false);
            button.setOnClickListener(v -> {
                selectedCategory = pair[1];
                renderItems();
            });
            categories.addView(button, new LinearLayout.LayoutParams(-2, dp(42)));
        }
        categoryScroll.addView(categories);
        root.addView(categoryScroll);

        status = new TextView(this);
        status.setText("正在连接电脑软件目录…");
        status.setTextColor(0xFF42536A);
        status.setTextSize(13);
        status.setPadding(0, dp(12), 0, dp(8));
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView foot = new TextView(this);
        foot.setText("下载文件保存在这部手机上。商城只展示已登记的软件来源、版本和校验信息；下载完成不代表该软件已兼容。");
        foot.setTextColor(0xFF7B8798);
        foot.setTextSize(11);
        foot.setPadding(0, dp(10), 0, 0);
        root.addView(foot);
        setContentView(root);
    }

    private void loadCatalog() {
        status.setText("正在读取已登记的软件目录…");
        io.execute(() -> {
            try {
                JSONObject response = ApiClient.request(BuildConfig.API_BASE_URL,
                        "GET", "/marketplace/catalog", null, null);
                JSONArray array = response.optJSONArray("items");
                ArrayList<CatalogItem> loaded = new ArrayList<>();
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject row = array.optJSONObject(i);
                        if (row == null) continue;
                        CatalogItem item = CatalogItem.fromJson(row);
                        if (item != null) loaded.add(item);
                    }
                }
                ui.post(() -> {
                    items.clear();
                    items.addAll(loaded);
                    status.setText("目录已更新 · " + loaded.size() + " 个软件条目");
                    renderItems();
                });
            } catch (Exception error) {
                ui.post(() -> {
                    status.setText("暂时无法连接商城目录，请检查账号服务器及商城接口配置。");
                    renderEmpty("商城目录暂不可用", error.getMessage());
                });
            }
        });
    }

    private void renderItems() {
        results.removeAllViews();
        String query = searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (CatalogItem item : items) {
            if (!selectedCategory.isEmpty() && !selectedCategory.equals(item.category)) continue;
            String all = (item.name + " " + item.publisher + " " + item.description + " " + item.category)
                    .toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !all.contains(query)) continue;
            results.addView(itemCard(item));
            shown++;
        }
        if (shown == 0) {
            renderEmpty(items.isEmpty() ? "商城暂时没有已审核的软件" : "没有找到匹配的软件",
                    items.isEmpty() ? "目录为空时不会显示虚构的可安装项目。软件条目需要登记官方来源、授权状态、架构和校验值。"
                            : "请尝试其他关键词或选择“全部”分类。");
        }
    }

    private android.view.View itemCard(CatalogItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFFFFFFFF);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);

        TextView name = new TextView(this);
        name.setText(item.name + (item.version.isEmpty() ? "" : "  " + item.version));
        name.setTextColor(0xFF17243A);
        name.setTextSize(18);
        name.setTypeface(null, 1);
        card.addView(name);

        TextView detail = new TextView(this);
        detail.setText(item.publisher + " · " + item.architecture + " · " + item.packageType
                + (item.expectedBytes > 0 ? " · " + formatBytes(item.expectedBytes) : ""));
        detail.setTextColor(0xFF66758C);
        detail.setTextSize(12);
        detail.setPadding(0, dp(5), 0, dp(4));
        card.addView(detail);

        TextView description = new TextView(this);
        description.setText(item.description);
        description.setTextColor(0xFF35445A);
        description.setTextSize(13);
        card.addView(description);

        TextView notes = new TextView(this);
        notes.setText("兼容性：" + (item.compatibility.isEmpty() ? "尚未验证" : item.compatibility)
                + " · 许可：" + (item.license.isEmpty() ? "待核实" : item.license));
        notes.setTextColor(0xFF7A6040);
        notes.setTextSize(11);
        notes.setPadding(0, dp(6), 0, dp(8));
        card.addView(notes);

        Button action = new Button(this);
        action.setAllCaps(false);
        if (item.canDownloadDirectly()) {
            action.setText("下载到手机");
            action.setOnClickListener(v -> beginDownload(item));
        } else {
            action.setText("查看官方软件来源");
            action.setOnClickListener(v -> openOfficialSource(item));
        }
        card.addView(action);
        return card;
    }

    private void renderEmpty(String heading, String explanation) {
        results.removeAllViews();
        TextView empty = new TextView(this);
        empty.setText(heading + "\n\n" + (explanation == null ? "" : explanation));
        empty.setTextColor(0xFF52627A);
        empty.setTextSize(14);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(18), dp(28), dp(18), dp(28));
        results.addView(empty);
    }

    private void openOfficialSource(CatalogItem item) {
        Uri source = safeHttps(item.sourceUrl);
        if (source == null) {
            toast("该软件没有可验证的官方 HTTPS 来源地址");
            return;
        }
        try { startActivity(new Intent(Intent.ACTION_VIEW, source)); }
        catch (Exception error) { toast("无法打开官方来源页面"); }
    }

    private void beginDownload(CatalogItem item) {
        if (!item.canDownloadDirectly()) {
            toast("该条目不允许商城直接分发，请前往官方来源");
            return;
        }
        File base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (base == null) {
            toast("手机没有可用的下载存储空间");
            return;
        }
        File directory = new File(base, "FutureTechnology");
        long required = item.expectedBytes + 32L * 1024L * 1024L;
        if ((!directory.isDirectory() && !directory.mkdirs()) || directory.getUsableSpace() < required) {
            toast("手机存储空间不足，无法下载此软件包");
            return;
        }
        File target = new File(directory, item.id + "-" + safeFilePart(item.version) + extensionFor(item.packageType));
        if (target.exists() && !target.delete()) {
            toast("无法替换旧下载文件，请先清理商城下载目录");
            return;
        }
        try {
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("Android 下载服务不可用");
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(item.downloadUrl));
            request.setTitle(item.name);
            request.setDescription("未来科技电脑软件商城 · 保存到手机");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS,
                    "FutureTechnology/" + target.getName());
            long id = manager.enqueue(request);
            JSONObject metadata = new JSONObject();
            metadata.put("id", item.id);
            metadata.put("name", item.name);
            metadata.put("path", target.getAbsolutePath());
            metadata.put("expectedBytes", item.expectedBytes);
            metadata.put("sha256", item.sha256.toLowerCase(Locale.ROOT));
            getSharedPreferences("marketplace_downloads", MODE_PRIVATE).edit()
                    .putString("download." + id, metadata.toString()).apply();
            status.setText("正在下载 " + item.name + "，文件保存在手机本地存储…");
            watchDownload(id);
        } catch (Exception error) {
            toast("无法启动下载：" + (error.getMessage() == null ? "未知错误" : error.getMessage()));
        }
    }

    private void watchDownload(long id) {
        ui.postDelayed(new Runnable() {
            @Override public void run() {
                JSONObject metadata = downloadMetadata(id);
                if (metadata == null) return;
                DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (manager == null) return;
                try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
                    if (cursor == null || !cursor.moveToFirst()) return;
                    int state = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    long total = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    long done = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    if (state == DownloadManager.STATUS_RUNNING || state == DownloadManager.STATUS_PENDING
                            || state == DownloadManager.STATUS_PAUSED) {
                        int percent = total > 0 ? (int) Math.min(100L, done * 100L / total) : 0;
                        status.setText("正在下载 " + metadata.optString("name") + " · " + percent + "%");
                        ui.postDelayed(this, 900);
                    } else if (state == DownloadManager.STATUS_FAILED) {
                        int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
                        status.setText("下载失败：" + metadata.optString("name") + "（错误 " + reason + "）");
                    }
                } catch (Exception ignored) {}
            }
        }, 500);
    }

    private JSONObject downloadMetadata(long id) {
        String value = getSharedPreferences("marketplace_downloads", MODE_PRIVATE)
                .getString("download." + id, null);
        if (value == null) return null;
        try { return new JSONObject(value); } catch (Exception error) { return null; }
    }

    private void reconcileDownloads() {
        java.util.Map<String, ?> all = getSharedPreferences("marketplace_downloads", MODE_PRIVATE).getAll();
        for (String key : all.keySet()) {
            if (!key.startsWith("download.")) continue;
            try {
                long id = Long.parseLong(key.substring("download.".length()));
                DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (manager == null) return;
                try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
                    if (cursor == null || !cursor.moveToFirst()) continue;
                    int state = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    if (state == DownloadManager.STATUS_SUCCESSFUL) verifyCompletedDownload(id);
                    else if (state == DownloadManager.STATUS_PENDING || state == DownloadManager.STATUS_RUNNING
                            || state == DownloadManager.STATUS_PAUSED) watchDownload(id);
                }
            } catch (Exception ignored) {}
        }
    }

    private void verifyCompletedDownload(long id) {
        JSONObject metadata = downloadMetadata(id);
        if (metadata == null) return;
        File file = new File(metadata.optString("path", ""));
        io.execute(() -> {
            try {
                if (!file.isFile()) throw new IllegalStateException("下载文件不存在");
                if (file.length() != metadata.getLong("expectedBytes"))
                    throw new SecurityException("下载文件大小与商城登记值不一致");
                String digest = sha256(file);
                if (!digest.equalsIgnoreCase(metadata.getString("sha256")))
                    throw new SecurityException("SHA-256 校验失败，文件可能损坏或被替换");
                getSharedPreferences("marketplace_downloads", MODE_PRIVATE).edit()
                        .putString("verified." + id, metadata.toString()).remove("download." + id).apply();
                ui.post(() -> new AlertDialog.Builder(this).setTitle("软件包下载并校验完成")
                        .setMessage(metadata.optString("name") + "\n大小：" + formatBytes(file.length())
                                + "\nSHA-256：" + digest + "\n\n文件已保存在手机：" + file.getAbsolutePath()
                                + "\n\n当前 Windows 兼容安装引擎尚未完成集成，因此这里只确认下载和完整性，不会假装已经安装软件。")
                        .setPositiveButton("确定", null).show());
            } catch (Exception error) {
                // A failed integrity check must never leave a package marked as verified.
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                getSharedPreferences("marketplace_downloads", MODE_PRIVATE).edit()
                        .remove("download." + id).remove("verified." + id).apply();
                ui.post(() -> toast("软件包校验失败，已删除该下载文件：" + error.getMessage()));
            }
        });
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) if (count > 0) digest.update(buffer, 0, count);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte b : digest.digest()) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }

    private static Uri safeHttps(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            Uri uri = Uri.parse(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null ? uri : null;
        } catch (Exception error) { return null; }
    }

    private static String extensionFor(String type) {
        if ("MSI".equalsIgnoreCase(type)) return ".msi";
        if ("ZIP".equalsIgnoreCase(type)) return ".zip";
        return ".exe";
    }

    private static String safeFilePart(String value) {
        String result = value == null ? "latest" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (result.isEmpty()) return "latest";
        return result.substring(0, Math.min(48, result.length()));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.ROOT, "%.2f GiB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private void toast(String message) {
        Toast.makeText(this, message == null ? "操作失败" : message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
        }
        ui.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    private static final class CatalogItem {
        final String id, category, name, publisher, description, version, architecture,
                packageType, sourceUrl, downloadUrl, sha256, license, compatibility;
        final long expectedBytes;
        final boolean redistributionAllowed;

        private CatalogItem(JSONObject o) {
            id = o.optString("id", "");
            category = o.optString("category", "other");
            name = o.optString("name", "");
            publisher = o.optString("publisher", "未知发布者");
            description = o.optString("description", "");
            version = o.optString("version", "");
            architecture = o.optString("architecture", "未知架构");
            packageType = o.optString("packageType", "").toUpperCase(Locale.ROOT);
            sourceUrl = o.optString("sourceUrl", "");
            downloadUrl = o.optString("downloadUrl", "");
            sha256 = o.optString("sha256", "");
            license = o.optString("license", "待核实");
            compatibility = o.optString("compatibility", "尚未验证");
            expectedBytes = o.optLong("expectedBytes", 0L);
            redistributionAllowed = o.optInt("redistributionAllowed", 0) == 1
                    || o.optBoolean("redistributionAllowed", false);
        }

        static CatalogItem fromJson(JSONObject o) {
            CatalogItem item = new CatalogItem(o);
            if (!item.id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}") || item.name.trim().isEmpty()) return null;
            if (item.name.length() > 100 || item.publisher.length() > 120 || item.description.length() > 1000) return null;
            if (!"EXE".equals(item.packageType) && !"MSI".equals(item.packageType) && !"ZIP".equals(item.packageType)) return null;
            if (safeHttps(item.sourceUrl) == null) return null;
            return item;
        }

        boolean canDownloadDirectly() {
            return redistributionAllowed && safeHttps(downloadUrl) != null
                    && expectedBytes > 0 && expectedBytes <= MAX_PACKAGE_BYTES
                    && sha256.matches("(?i)[0-9a-f]{64}");
        }
    }
}
