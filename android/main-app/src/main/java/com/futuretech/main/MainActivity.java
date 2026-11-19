package com.futuretech.main;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.futuretech.shared.ApiClient;
import com.futuretech.shared.AvatarEncoder;
import com.futuretech.shared.TokenVault;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private android.os.Handler ui;
    private TextView deviceInfo;
    private TextView runtimeStatus;
    private Button toggleButton;
    private Uri selectedAvatar;
    private TextView softwareStatus;
    private TextView accountStatus;
    private Button logoutButton;
    private boolean authenticatedThisLaunch = false;
    private static final int PICK_AVATAR = 1002;
    private static final int PICK_SOFTWARE = 1001;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new android.os.Handler(getMainLooper());
        buildUi();
        updateDeviceInfo();
        updateSessionControls();
        handleIncomingIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void buildUi() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xFFF2F5FA);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.future_tech_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(112), dp(112));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        logoParams.topMargin = dp(8);
        content.addView(logo, logoParams);

        TextView title = new TextView(this);
        title.setText("未来科技");
        title.setTextSize(30);
        title.setTextColor(0xFF17243A);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(18), 0, dp(4));
        content.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Windows 兼容运行环境");
        subtitle.setTextSize(15);
        subtitle.setTextColor(0xFF66758C);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(20));
        content.addView(subtitle);

        runtimeStatus = new TextView(this);
        runtimeStatus.setText("兼容环境：已关闭");
        runtimeStatus.setTextSize(18);
        runtimeStatus.setTextColor(0xFF29364B);
        runtimeStatus.setGravity(Gravity.CENTER);
        runtimeStatus.setPadding(0, dp(18), 0, dp(18));
        content.addView(runtimeStatus);

        toggleButton = button("开启兼容环境", v -> toggleRuntime());
        toggleButton.setMinHeight(dp(64));
        content.addView(toggleButton, new LinearLayout.LayoutParams(-1, dp(68)));

        deviceInfo = new TextView(this);
        deviceInfo.setTextSize(13);
        deviceInfo.setTextColor(0xFF53627A);
        deviceInfo.setPadding(0, dp(22), 0, dp(16));
        content.addView(deviceInfo);

        content.addView(button("添加电脑软件安装包", v -> chooseSoftwarePackage()));
        softwareStatus = new TextView(this);
        softwareStatus.setText("添加 Windows .exe / .msi 安装包或 .zip 便携软件包。安装成功后，软件会显示在未来科技电脑桌面，可创建快捷图标。");
        softwareStatus.setTextSize(12);
        softwareStatus.setTextColor(0xFF66758C);
        softwareStatus.setPadding(0, dp(8), 0, dp(8));
        content.addView(softwareStatus);
        content.addView(button("我的电脑软件 / 安装包", v -> showSoftwareLibrary()));
        content.addView(button("运行内存设置", v -> showRuntimeMemorySettings()));

        accountStatus = new TextView(this);
        accountStatus.setText("账号状态：未登录（首次使用请先注册）");
        accountStatus.setTextSize(14);
        accountStatus.setTextColor(0xFF53627A);
        accountStatus.setGravity(Gravity.CENTER);
        accountStatus.setPadding(0, dp(18), 0, dp(8));
        content.addView(accountStatus);
        content.addView(button("注册账号（首次使用）", v -> showRegister()));
        content.addView(button("已有账号？登录", v -> showLogin()));
        logoutButton = button("退出当前会话", v -> logout());
        logoutButton.setVisibility(View.GONE);
        content.addView(logoutButton);

        TextView foot = new TextView(this);
        foot.setText("兼容环境只在本应用私有目录内运行。关闭后停止本应用启动的 Windows 兼容进程，不修改 Android 系统分区。");
        foot.setTextColor(0xFF77849A);
        foot.setTextSize(12);
        foot.setPadding(0, dp(24), 0, dp(8));
        content.addView(foot);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(8);
        return b;
    }

    private void toggleRuntime() {
        java.io.File manifest = new java.io.File(getFilesDir(), "runtime/runtime-manifest.json");
        if (!manifest.isFile()) {
            new AlertDialog.Builder(this).setTitle("兼容组件尚未安装")
                    .setMessage("当前版本先完成了应用界面和设备识别。Wine、Box64/Box86、Linux 运行环境及图形组件仍需完成移植、签名和设备测试，因此暂时不能启动 Windows 程序。")
                    .setPositiveButton("知道了", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("运行组件需要验证")
                .setMessage("已发现运行环境清单，但尚未验证组件签名、完整性和设备兼容性。为避免执行损坏或不可信代码，本版本不会启动它。")
                .setPositiveButton("确定", null).show();
    }

    private void updateDeviceInfo() {
        StringBuilder info = new StringBuilder();
        info.append("CPU ABI：");
        if (Build.SUPPORTED_ABIS != null) {
            for (int i = 0; i < Build.SUPPORTED_ABIS.length; i++) {
                if (i > 0) info.append(" / ");
                info.append(Build.SUPPORTED_ABIS[i]);
            }
        }
        info.append("\nAndroid：").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")");

        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memory);
        info.append("\n物理内存：").append(memory.totalMem / (1024L * 1024L)).append(" MB");
        info.append("\n可用内存：").append(memory.availMem / (1024L * 1024L)).append(" MB");
        info.append("\n运行内存策略：").append(getMemoryPolicyLabel())
                .append("（不设 1.5 GB 固定上限，按设备余量动态使用，可按需达到数 GB）");
        info.append("\n应用可用存储：").append(getFilesDir().getUsableSpace() / (1024L * 1024L)).append(" MB");

        String vulkanVersion = "未声明";
        String vulkanLevel = "未声明";
        PackageManager pm = getPackageManager();
        FeatureInfo[] features = pm.getSystemAvailableFeatures();
        if (features != null) {
            for (FeatureInfo feature : features) {
                if (PackageManager.FEATURE_VULKAN_HARDWARE_VERSION.equals(feature.name)) {
                    vulkanVersion = String.format(java.util.Locale.ROOT, "0x%08X", feature.version);
                } else if (PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL.equals(feature.name)) {
                    vulkanLevel = String.format(java.util.Locale.ROOT, "0x%08X", feature.version);
                }
            }
        }
        info.append("\nOpenGL ES：由系统图形栈提供（运行时版本待测）");
        info.append("\nVulkan 硬件版本：").append(vulkanVersion);
        info.append("\nVulkan 硬件级别：").append(vulkanLevel);
        info.append("\nAPI 服务器：").append(BuildConfig.API_BASE_URL.isEmpty() ? "未配置" : "已配置");
        deviceInfo.setText(info);
    }

    private void chooseSoftwarePackage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_SOFTWARE);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        java.util.ArrayList<Uri> uris = new java.util.ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        addIncomingUri(uris, seen, intent.getData());
        ClipData clip = intent.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++)
                addIncomingUri(uris, seen, clip.getItemAt(i).getUri());
        }
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri stream = null;
            if (Build.VERSION.SDK_INT >= 33) stream = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            else {
                //noinspection deprecation
                Object value = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (value instanceof Uri) stream = (Uri) value;
            }
            addIncomingUri(uris, seen, stream);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            java.util.ArrayList<Uri> streams;
            if (Build.VERSION.SDK_INT >= 33)
                streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
            else {
                //noinspection deprecation
                streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            }
            if (streams != null) for (Uri stream : streams) addIncomingUri(uris, seen, stream);
        }
        for (Uri uri : uris) confirmIncomingSoftware(uri);
    }

    private void addIncomingUri(java.util.ArrayList<Uri> uris, java.util.HashSet<String> seen, Uri uri) {
        if (uri != null && seen.add(uri.toString())) uris.add(uri);
    }

    private boolean isAeProjectOrPlugin(String name) {
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".aep") || lower.endsWith(".aepx") || lower.endsWith(".aex");
    }

    private boolean isWindowsSoftwarePackage(String name) {
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".exe") || lower.endsWith(".msi") || lower.endsWith(".zip");
    }

    private void confirmIncomingSoftware(Uri uri) {
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) { /* Share providers may grant temporary access only; copy immediately. */ }
        final String incomingName = displayName(uri);
        if (isAeProjectOrPlugin(incomingName)) {
            toast("这是 AE 工程或插件，请在 Ae 中导入；未来科技只负责运行 Windows 电脑软件。");
            return;
        }
        if (!isWindowsSoftwarePackage(incomingName)) {
            toast("这不是支持的电脑软件包。请选择 Windows .exe / .msi 安装包或 .zip 便携软件包。");
            return;
        }
        if (softwareStatus != null) softwareStatus.setText("正在安全添加软件包：" + incomingName);
        io.execute(() -> {
            try {
                File inbox = new File(getFilesDir(), "software/installers");
                PrivateImportStore.ImportedFile imported =
                        PrivateImportStore.copyFromUri(getContentResolver(), uri, inbox);
                ui.post(() -> {
                    if (softwareStatus != null)
                        softwareStatus.setText("已添加安装包：" + imported.originalName + "（尚未安装）");
                    new AlertDialog.Builder(this).setTitle("电脑软件安装包已添加")
                            .setMessage(imported.summary()
                                    + "\n\n当前 APK 还没有集成 Wine / Box64 / Box86 完整运行引擎，因此这里只完成安全保存，尚未安装或运行软件。"
                                    + "运行引擎集成后，下一步会在兼容环境中安装，并把已安装软件加入电脑桌面。")
                            .setPositiveButton("查看软件包", (d, w) -> showSoftwareLibrary())
                            .setNegativeButton("完成", null).show();
                });
            } catch (Exception error) {
                ui.post(() -> {
                    if (softwareStatus != null) softwareStatus.setText("添加失败：" + incomingName);
                    toast("添加软件包失败：" + (error.getMessage() == null ? "无法读取文件" : error.getMessage()));
                });
            }
        });
    }

    private void showSoftwareLibrary() {
        File inbox = new File(getFilesDir(), "software/installers");
        java.util.ArrayList<String> entries = PrivateImportStore.history(inbox);
        if (entries.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("我的电脑软件")
                    .setMessage("还没有添加电脑软件安装包。\n\n支持 Windows .exe / .msi 安装包和 .zip 便携软件包。安装成功后，软件名称和图标会出现在未来科技的电脑桌面；可按需创建快捷图标。")
                    .setPositiveButton("添加软件", (d, w) -> chooseSoftwarePackage()).show();
            return;
        }
        String[] rows = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) rows[i] = "待安装 · " + entries.get(i).replace("\n", " · ");
        new AlertDialog.Builder(this).setTitle("软件安装包（待安装）")
                .setItems(rows, (d, which) -> new AlertDialog.Builder(this).setTitle("软件包详情")
                        .setMessage(entries.get(which)
                                + "\n\n状态：已导入，尚未安装。\n安装成功后才能生成真实软件图标和快捷方式。"
                                + "\n当前兼容运行引擎尚未集成，暂不能启动安装程序。")
                        .setPositiveButton("确定", null).show())
                .setNegativeButton("关闭", null).show();
    }

    private void showRuntimeMemorySettings() {
        String[] labels = {"自动动态分配（推荐）", "性能优先（按需使用数 GB）", "系统优先（降低后台占用）"};
        String[] values = {"auto", "performance", "system"};
        String current = getSharedPreferences("future_runtime", MODE_PRIVATE).getString("memory_policy", "auto");
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) selected = i;
        new AlertDialog.Builder(this).setTitle("运行内存策略")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    getSharedPreferences("future_runtime", MODE_PRIVATE).edit()
                            .putString("memory_policy", values[which]).apply();
                    updateDeviceInfo();
                    dialog.dismiss();
                    toast("内存策略已保存：" + labels[which] + "。运行引擎接入后生效。");
                }).setNegativeButton("取消", null).show();
    }

    private String getMemoryPolicyLabel() {
        String value = getSharedPreferences("future_runtime", MODE_PRIVATE).getString("memory_policy", "auto");
        if ("performance".equals(value)) return "性能优先";
        if ("system".equals(value)) return "系统优先";
        return "自动动态分配";
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_SOFTWARE) confirmIncomingSoftware(uri);
        if (requestCode == PICK_AVATAR) {
            selectedAvatar = uri;
            Toast.makeText(this, "头像已选择，登录后可上传到账号服务器", Toast.LENGTH_SHORT).show();
        }
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return uri.getLastPathSegment() == null ? "未知文件" : uri.getLastPathSegment();
    }

    private EditText field(String hint, boolean secret) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        if (secret) e.setInputType(129);
        return e;
    }

    private LinearLayout form() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        return box;
    }

    private void showRegister() {
        selectedAvatar = null;
        LinearLayout box = form();
        EditText qq = field("QQ 号（可选）", false);
        EditText wx = field("微信号（可选）", false);
        EditText nickname = field("昵称（可选）", false);
        EditText password = field("设置密码（至少 10 位）", true);
        Button avatar = button("从手机相册选择头像", v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("image/*");
            pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(pick, PICK_AVATAR);
        });
        box.addView(qq); box.addView(wx); box.addView(nickname); box.addView(password); box.addView(avatar);
        new AlertDialog.Builder(this).setTitle("注册未来科技账号")
                .setMessage("QQ 号和微信号至少填写一项，二者不要求同时填写。")
                .setView(box).setNegativeButton("取消", null)
                .setPositiveButton("注册", (d, w) -> {
                    String q = qq.getText().toString().trim();
                    String x = wx.getText().toString().trim();
                    String p = password.getText().toString();
                    if (q.isEmpty() && x.isEmpty()) { toast("QQ 或微信至少填写一项"); return; }
                    if (p.length() < 10) { toast("密码至少 10 位"); return; }
                    JSONObject body = new JSONObject();
                    try {
                        if (!q.isEmpty()) body.put("qqId", q);
                        if (!x.isEmpty()) body.put("wechatId", x);
                        body.put("displayName", nickname.getText().toString().trim());
                        body.put("password", p);
                        body.put("deviceId", deviceId());
                    } catch (Exception avatarError) {
                        toast("头像读取失败：" + avatarError.getMessage());
                        return;
                    }
                    callAuth("/auth/register", body, "注册申请已提交");
                }).show();
    }

    private void showLogin() {
        LinearLayout box = form();
        EditText account = field("QQ 号或微信号", false);
        EditText password = field("密码", true);
        box.addView(account); box.addView(password);
        new AlertDialog.Builder(this).setTitle("登录未来科技")
                .setView(box).setNegativeButton("取消", null)
                .setPositiveButton("登录", (d, w) -> {
                    JSONObject body = new JSONObject();
                    try {
                        body.put("account", account.getText().toString().trim());
                        body.put("password", password.getText().toString());
                        body.put("deviceId", deviceId());
                    } catch (Exception ignored) {}
                    callAuth("/auth/login", body, "登录成功");
                }).show();
    }

    private String deviceId() {
        android.content.SharedPreferences p = getSharedPreferences("future_device", MODE_PRIVATE);
        String id = p.getString("id", null);
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
            p.edit().putString("id", id).apply();
        }
        return id;
    }

    private void callAuth(String path, JSONObject body, String success) {
        if (BuildConfig.API_BASE_URL.isEmpty()) { toast("账号服务器尚未部署，暂时不能注册或登录"); return; }
        io.execute(() -> {
            try {
                if ("/auth/register".equals(path) && selectedAvatar != null) {
                    body.put("avatarBase64", com.futuretech.shared.AvatarEncoder.readBase64(getContentResolver(), selectedAvatar));
                    String mime = getContentResolver().getType(selectedAvatar);
                    if (mime != null) body.put("avatarMimeType", mime);
                }
                JSONObject result = ApiClient.request(BuildConfig.API_BASE_URL, "POST", path, body, null);
                boolean registration = "/auth/register".equals(path);
                String token = result.optString("accessToken", "");
                String refresh = result.optString("refreshToken", "");
                if (!registration && !token.isEmpty()) TokenVault.saveSession(this, token, refresh);
                String state = result.optString("accountStatus", "active");
                ui.post(() -> {
                    if (!registration && !token.isEmpty()) authenticatedThisLaunch = true;
                    updateSessionControls();
                    String message = registration
                            ? "账号已创建，状态：" + state
                                + (result.optBoolean("whitelistRequired", false)
                                    ? "\n账号还需管理员批准。批准后请手动登录。"
                                    : "\n注册和登录是分开的，请返回主页点击“已有账号？登录”手动登录。")
                            : "登录成功。";
                    new AlertDialog.Builder(this).setTitle(success).setMessage(message)
                            .setPositiveButton("确定", null).show();
                });
            } catch (Exception e) { ui.post(() -> toast(e.getMessage())); }
        });
    }

    private void updateSessionControls() {
        if (accountStatus != null) accountStatus.setText(authenticatedThisLaunch
                ? "账号状态：已登录（本次启动已完成服务器验证）"
                : "账号状态：未登录（首次使用请先注册；已有账号请手动登录）");
        if (logoutButton != null) logoutButton.setVisibility(authenticatedThisLaunch ? View.VISIBLE : View.GONE);
    }

    private void logout() {
        io.execute(() -> {
            try {
                String token = TokenVault.read(this);
                if (token != null && !BuildConfig.API_BASE_URL.isEmpty()) {
                    ApiClient.request(BuildConfig.API_BASE_URL, "POST", "/auth/logout", new JSONObject(), token);
                }
            } catch (Exception ignored) {}
            TokenVault.clear(this);
            authenticatedThisLaunch = false;
            ui.post(() -> {
                updateSessionControls();
                toast("已退出当前会话");
            });
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message == null ? "操作失败" : message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
