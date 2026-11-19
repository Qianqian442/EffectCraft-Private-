package com.futuretech.main;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
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
    private TextView importStatus;
    private static final int PICK_AVATAR = 1002;
    private static final int PICK_FILE = 1001;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new android.os.Handler(getMainLooper());
        buildUi();
        updateDeviceInfo();
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

        content.addView(button("导入电脑工程 / Windows 程序", v -> chooseFile()));
        importStatus = new TextView(this);
        importStatus.setText("导入文件会先复制到应用私有目录并计算 SHA-256；不会自动执行 EXE 或插件。");
        importStatus.setTextSize(12);
        importStatus.setTextColor(0xFF66758C);
        importStatus.setPadding(0, dp(8), 0, dp(8));
        content.addView(importStatus);
        content.addView(button("查看导入记录", v -> showImportedFiles()));
        content.addView(button("注册 / 登录账号", v -> showAccountMenu()));
        content.addView(button("退出登录", v -> logout()));

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
        info.append("\n总内存：").append(memory.totalMem / (1024L * 1024L)).append(" MB");
        info.append("\nAPI 服务器：").append(BuildConfig.API_BASE_URL.isEmpty() ? "未配置" : "已配置");
        deviceInfo.setText(info);
    }

    private void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_FILE);
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
            java.util.ArrayList<Uri> streams = null;
            if (Build.VERSION.SDK_INT >= 33)
                streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
            else {
                //noinspection deprecation
                streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            }
            if (streams != null) for (Uri stream : streams) addIncomingUri(uris, seen, stream);
        }

        for (Uri uri : uris) confirmIncomingFile(uri);
    }

    private void addIncomingUri(java.util.ArrayList<Uri> uris, java.util.HashSet<String> seen, Uri uri) {
        if (uri == null) return;
        String key = uri.toString();
        if (seen.add(key)) uris.add(uri);
    }

    private void confirmIncomingFile(Uri uri) {
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) { /* Share providers may grant temporary access only; copy immediately. */ }
        final String incomingName = displayName(uri);
        if (importStatus != null) importStatus.setText("正在安全导入：" + incomingName);
        io.execute(() -> {
            try {
                File inbox = new File(getFilesDir(), "imports");
                PrivateImportStore.ImportedFile imported =
                        PrivateImportStore.copyFromUri(getContentResolver(), uri, inbox);
                ui.post(() -> {
                    String result = imported.summary()
                            + "\n\n已保存到未来科技的应用私有目录。文件尚未被解析、安装或执行。";
                    if (importStatus != null) importStatus.setText("最近导入：" + imported.originalName + " · " + imported.kind);
                    new AlertDialog.Builder(this).setTitle("文件已保存")
                            .setMessage(result)
                            .setPositiveButton("查看导入记录", (d, w) -> showImportedFiles())
                            .setNegativeButton("完成", null).show();
                });
            } catch (Exception error) {
                ui.post(() -> {
                    if (importStatus != null) importStatus.setText("导入失败：" + incomingName);
                    toast("导入失败：" + (error.getMessage() == null ? "无法读取文件" : error.getMessage()));
                });
            }
        });
    }

    private void showImportedFiles() {
        java.util.ArrayList<String> entries = PrivateImportStore.history(new File(getFilesDir(), "imports"));
        if (entries.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("导入记录")
                    .setMessage("还没有导入文件。可以从 QQ「用其他软件打开」或本机文件选择器导入。")
                    .setPositiveButton("确定", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("导入记录（最近 100 条）")
                .setItems(entries.toArray(new String[0]), (d, which) ->
                        new AlertDialog.Builder(this).setTitle("文件记录").setMessage(entries.get(which))
                                .setPositiveButton("确定", null).show())
                .setNegativeButton("关闭", null).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_FILE) confirmIncomingFile(uri);
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

    private void showAccountMenu() {
        new AlertDialog.Builder(this).setTitle("未来科技账号")
                .setItems(new String[]{"注册新账号", "登录已有账号"}, (d, which) -> {
                    if (which == 0) showRegister(); else showLogin();
                }).show();
    }

    private void showRegister() {
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
                        String avatarBase64 = AvatarEncoder.readBase64(getContentResolver(), selectedAvatar);
                        if (avatarBase64 != null) body.put("avatarBase64", avatarBase64);
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
                String token = result.optString("accessToken", "");
                String refresh = result.optString("refreshToken", "");
                if (!token.isEmpty()) TokenVault.saveSession(this, token, refresh);
                String state = result.optString("accountStatus", "active");
                ui.post(() -> new AlertDialog.Builder(this).setTitle(success)
                        .setMessage("服务器账户状态：" + state + (result.optBoolean("whitelistRequired", false) ? "\n账号需要管理员白名单批准。" : ""))
                        .setPositiveButton("确定", null).show());
            } catch (Exception e) { ui.post(() -> toast(e.getMessage())); }
        });
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
            ui.post(() -> toast("已退出登录"));
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
