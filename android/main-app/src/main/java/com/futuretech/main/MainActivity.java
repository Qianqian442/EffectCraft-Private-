package com.futuretech.main;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.ClipData;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
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
import org.json.JSONArray;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
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
    private static final String ACTION_LAUNCH_WINDOWS_APP = "com.futuretech.main.action.RUN_WINDOWS_APP";
    private static final String EXTRA_WINDOWS_APP_ID = "futuretech_windows_app_id";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new android.os.Handler(getMainLooper());
        buildUi();
        updateDeviceInfo();
        updateSessionControls();
        if (!handleInstalledAppShortcut(getIntent())) handleIncomingIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!handleInstalledAppShortcut(intent)) handleIncomingIntent(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshRuntimeStatus();
    }

    private void refreshRuntimeStatus() {
        int id = runtimeContainerId();
        boolean ready = runtimeReady(id);
        if (runtimeStatus != null)
            runtimeStatus.setText(ready ? "兼容环境：已初始化（默认 Windows 容器 " + id + "）" : "兼容环境：尚未初始化");
        if (toggleButton != null) toggleButton.setText(ready ? "打开 Windows 兼容环境" : "开启兼容环境");
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

        Button marketplaceButton = button("打开电脑应用商城", v -> startActivity(new Intent(this, ComputerMarketplaceActivity.class)));
        marketplaceButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.computer_marketplace_icon, 0, 0, 0);
        marketplaceButton.setCompoundDrawablePadding(dp(10));
        marketplaceButton.setMinHeight(dp(64));
        content.addView(marketplaceButton);
        content.addView(button("添加电脑软件安装包", v -> chooseSoftwarePackage()));
        softwareStatus = new TextView(this);
        softwareStatus.setText("添加 Windows .exe / .msi 安装包或 .zip 便携软件包。安装成功后，软件会显示在未来科技电脑桌面，可创建快捷图标。");
        softwareStatus.setTextSize(12);
        softwareStatus.setTextColor(0xFF66758C);
        softwareStatus.setPadding(0, dp(8), 0, dp(8));
        content.addView(softwareStatus);
        content.addView(button("我的电脑软件 / 安装包", v -> showSoftwareLibrary()));
        content.addView(button("已安装 Windows 软件", v -> showInstalledWindowsApps()));
        content.addView(button("扫描已安装软件 / 创建手机桌面图标", v -> scanInstalledApps()));
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
        // Winlator is merged as a non-exported library activity in this build.
        // Its first launch initializes the private Wine/Box64 root filesystem;
        // later launches open the container and shortcut manager.
        try {
            Intent runtime = new Intent();
            runtime.setClassName(this, "com.winlator.MainActivity");
            startActivity(runtime);
        } catch (Exception error) {
            new AlertDialog.Builder(this).setTitle("兼容环境尚未构建")
                    .setMessage("当前安装包没有包含完整的 Wine/Box64 运行模块。请使用经过运行模块编译和验证的版本。")
                    .setPositiveButton("确定", null).show();
        }
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
                                    + "\n\n安装包已安全保存到未来科技的私有目录。打开“我的电脑软件 / 安装包”，即可将它复制到 Windows 容器并启动安装或运行。")
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
        File inbox = WindowsSoftwareStore.inbox(this);
        JSONArray imported = WindowsSoftwareStore.importedPackages(inbox);
        JSONArray installed = WindowsSoftwareStore.installedApps(this);
        ArrayList<JSONObject> entries = new ArrayList<>();
        ArrayList<Boolean> installedEntries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        for (int i = 0; i < imported.length(); i++) {
            JSONObject item = imported.optJSONObject(i);
            if (item == null) continue;
            entries.add(item);
            installedEntries.add(false);
            labels.add("待安装 · " + item.optString("originalName", "未知软件包"));
        }
        for (int i = 0; i < installed.length(); i++) {
            JSONObject item = installed.optJSONObject(i);
            if (item == null) continue;
            entries.add(item);
            installedEntries.add(true);
            labels.add("已添加 · " + item.optString("name", "Windows 软件"));
        }
        if (entries.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("我的电脑软件")
                    .setMessage("还没有添加电脑软件。可以导入 .exe / .msi 安装包，或导入 .zip 便携软件包。安装后可扫描程序并添加到手机系统桌面。")
                    .setPositiveButton("添加软件", (d, w) -> chooseSoftwarePackage()).show();
            return;
        }
        String[] rows = labels.toArray(new String[0]);
        JSONObject[] data = entries.toArray(new JSONObject[0]);
        Boolean[] installedFlags = installedEntries.toArray(new Boolean[0]);
        new AlertDialog.Builder(this).setTitle("我的电脑软件")
                .setItems(rows, (d, which) -> {
                    JSONObject item = data[which];
                    if (installedFlags[which]) {
                        launchInstalledWindowsApp(item);
                    } else {
                        showPackageActions(item);
                    }
                })
                .setPositiveButton("添加软件", (d, w) -> chooseSoftwarePackage())
                .setNegativeButton("关闭", null).show();
    }

    private void showPackageActions(JSONObject record) {
        String name = record.optString("originalName", "Windows 软件包");
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        String action = lower.endsWith(".zip") ? "解压并选择可运行程序" : "在 Windows 兼容环境中安装 / 运行";
        new AlertDialog.Builder(this).setTitle(name)
                .setMessage(record.optString("kind", "Windows 软件包")
                        + "\n\n文件已保存。启动前会复制到未来科技自己的 Windows 容器，不会交给 Android 系统直接执行。")
                .setPositiveButton(action, (d, w) -> runImportedPackage(record))
                .setNegativeButton("取消", null).show();
    }

    private int runtimeContainerId() {
        return getSharedPreferences("future_runtime", MODE_PRIVATE).getInt("default_container_id", -1);
    }

    private boolean runtimeReady(int containerId) {
        if (containerId <= 0) return false;
        File rootfs = new File(getFilesDir(), "rootfs");
        File version = new File(rootfs, ".winlator/.rfs_version");
        File container = WindowsSoftwareStore.containerRoot(this, containerId);
        return version.isFile() && new File(container, ".container").isFile()
                && WindowsSoftwareStore.driveC(this, containerId).isDirectory();
    }

    private void showRuntimeRequired() {
        new AlertDialog.Builder(this).setTitle("先开启 Windows 兼容环境")
                .setMessage("首次使用需要先初始化 Wine 和 Box64 运行组件并创建默认 Windows 容器。初始化完成后，再从这里启动软件安装程序。")
                .setPositiveButton("开启兼容环境", (d, w) -> toggleRuntime())
                .setNegativeButton("稍后", null).show();
    }

    private void runImportedPackage(JSONObject record) {
        int containerId = runtimeContainerId();
        if (!runtimeReady(containerId)) {
            showRuntimeRequired();
            return;
        }
        try {
            File source = WindowsSoftwareStore.resolveImportedFile(WindowsSoftwareStore.inbox(this), record);
            String id = record.optString("id", "");
            if (id.isEmpty()) throw new IOException("软件包记录缺少 ID");
            String name = record.optString("originalName", source.getName());
            if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                io.execute(() -> {
                    try {
                        ArrayList<File> candidates = WindowsSoftwareStore.extractZipIntoContainer(this, containerId, source, id);
                        ui.post(() -> choosePortableExecutable(candidates, containerId, name));
                    } catch (Exception error) {
                        ui.post(() -> toast("ZIP 解压失败：" + error.getMessage()));
                    }
                });
                return;
            }
            io.execute(() -> {
                try {
                    File staged = WindowsSoftwareStore.copyIntoContainer(this, containerId, source, id);
                    ui.post(() -> {
                        Intent runtime = new Intent();
                        runtime.setClassName(this, "com.winlator.XServerDisplayActivity");
                        runtime.putExtra("container_id", containerId);
                        runtime.putExtra("exec_path", staged.getAbsolutePath());
                        startActivity(runtime);
                        if (softwareStatus != null) softwareStatus.setText("已在 Windows 兼容环境中启动：" + name
                                + "。完成安装程序后，返回这里扫描已安装软件并创建手机桌面快捷图标。");
                    });
                } catch (Exception error) {
                    ui.post(() -> toast("复制安装包到 Windows 容器失败：" + error.getMessage()));
                }
            });
        } catch (Exception error) {
            toast("启动 Windows 软件失败：" + error.getMessage());
        }
    }

    private void choosePortableExecutable(ArrayList<File> candidates, int containerId, String archiveName) {
        if (candidates == null || candidates.isEmpty()) {
            toast("压缩包内没有可运行的 EXE 文件");
            return;
        }
        String[] rows = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            File file = candidates.get(i);
            rows[i] = file.getName() + "\n" + file.getParent();
        }
        new AlertDialog.Builder(this).setTitle("选择要运行的程序")
                .setItems(rows, (d, which) -> {
                    File executable = candidates.get(which);
                    String defaultName = executable.getName();
                    if (defaultName.toLowerCase(java.util.Locale.ROOT).endsWith(".exe"))
                        defaultName = defaultName.substring(0, defaultName.length() - 4);
                    addInstalledAppAndShortcut(containerId, defaultName, executable);
                })
                .setNegativeButton("取消", null).show();
    }

    private void scanInstalledApps() {
        int containerId = runtimeContainerId();
        if (!runtimeReady(containerId)) {
            showRuntimeRequired();
            return;
        }
        if (softwareStatus != null) softwareStatus.setText("正在扫描 Windows 容器中的 Program Files 和桌面程序…");
        io.execute(() -> {
            try {
                ArrayList<File> files = WindowsSoftwareStore.scanInstalledExecutables(this, containerId);
                ui.post(() -> {
                    if (softwareStatus != null) softwareStatus.setText("扫描完成，发现 " + files.size() + " 个可选 EXE 程序。");
                    if (files.isEmpty()) {
                        toast("暂未发现已安装程序。请先在兼容环境中完成 Windows 软件安装。");
                        return;
                    }
                    String[] rows = new String[files.size()];
                    for (int i = 0; i < files.size(); i++) {
                        File file = files.get(i);
                        rows[i] = file.getName() + "\n" + file.getParent();
                    }
                    new AlertDialog.Builder(this).setTitle("选择要添加到手机桌面的软件")
                            .setItems(rows, (dialog, which) -> {
                                File executable = files.get(which);
                                String name = executable.getName();
                                if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".exe"))
                                    name = name.substring(0, name.length() - 4);
                                addInstalledAppAndShortcut(containerId, name, executable);
                            })
                            .setNegativeButton("取消", null).show();
                });
            } catch (Exception error) {
                ui.post(() -> toast("扫描失败：" + error.getMessage()));
            }
        });
    }

    private void addInstalledAppAndShortcut(int containerId, String defaultName, File executable) {
        EditText appName = field("手机桌面显示名称", false);
        appName.setText(defaultName);
        new AlertDialog.Builder(this).setTitle("添加 Windows 软件")
                .setMessage("所选程序：" + executable.getName()
                        + "\n保存后会创建一个 Android 手机系统桌面快捷图标。点击图标时由未来科技启动该 Windows 程序。")
                .setView(appName)
                .setPositiveButton("保存并创建桌面图标", (d, w) -> {
                    try {
                        JSONObject record = WindowsSoftwareStore.addInstalledApp(
                                this, containerId, appName.getText().toString().trim().isEmpty()
                                        ? defaultName : appName.getText().toString().trim(), executable);
                        pinAndroidShortcut(record);
                    } catch (Exception error) {
                        toast("保存软件失败：" + error.getMessage());
                    }
                })
                .setNegativeButton("取消", null).show();
    }

    private void pinAndroidShortcut(JSONObject record) {
        ShortcutManager manager = getSystemService(ShortcutManager.class);
        if (manager == null || !manager.isRequestPinShortcutSupported()) {
            toast("当前手机桌面不支持应用内创建快捷图标，请从未来科技的“已安装 Windows 软件”中启动。");
            return;
        }
        String id = record.optString("id", "");
        String name = record.optString("name", "Windows 软件");
        String shortName = name.length() > 10 ? name.substring(0, 10) : name;
        Intent launch = new Intent(this, MainActivity.class);
        launch.setAction(ACTION_LAUNCH_WINDOWS_APP);
        launch.putExtra(EXTRA_WINDOWS_APP_ID, id);
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ShortcutInfo shortcut = new ShortcutInfo.Builder(this, "ft-win-" + id)
                .setShortLabel(shortName)
                .setLongLabel(name)
                .setIcon(Icon.createWithResource(this, R.drawable.computer_marketplace_icon))
                .setIntent(launch)
                .build();
        if (manager.requestPinShortcut(shortcut, null))
            toast("已向手机系统桌面发送快捷图标添加请求，请在系统提示中确认。");
        else toast("手机桌面暂未接受快捷图标请求。");
    }

    private boolean handleInstalledAppShortcut(Intent intent) {
        if (intent == null || !ACTION_LAUNCH_WINDOWS_APP.equals(intent.getAction())) return false;
        String id = intent.getStringExtra(EXTRA_WINDOWS_APP_ID);
        JSONObject record = WindowsSoftwareStore.findInstalledApp(this, id == null ? "" : id);
        if (record == null) {
            toast("找不到该 Windows 软件记录，请从未来科技重新添加快捷图标。");
        } else {
            launchInstalledWindowsApp(record);
        }
        return true;
    }

    private void launchInstalledWindowsApp(JSONObject record) {
        int containerId = record.optInt("containerId", -1);
        String path = record.optString("execPath", "");
        if (!runtimeReady(containerId) || path.isEmpty() || !new File(path).isFile()) {
            showRuntimeRequired();
            return;
        }
        Intent runtime = new Intent();
        runtime.setClassName(this, "com.winlator.XServerDisplayActivity");
        runtime.putExtra("container_id", containerId);
        runtime.putExtra("exec_path", path);
        startActivity(runtime);
    }

    private void showInstalledWindowsApps() {
        JSONArray apps = WindowsSoftwareStore.installedApps(this);
        if (apps.length() == 0) {
            new AlertDialog.Builder(this).setTitle("已安装 Windows 软件")
                    .setMessage("列表还为空。完成 Windows 软件安装后，点击“扫描已安装软件 / 创建手机桌面图标”，选择程序即可添加。")
                    .setPositiveButton("扫描软件", (d, w) -> scanInstalledApps()).show();
            return;
        }
        String[] labels = new String[apps.length()];
        JSONObject[] records = new JSONObject[apps.length()];
        for (int i = 0; i < apps.length(); i++) {
            records[i] = apps.optJSONObject(i);
            labels[i] = records[i] == null ? "未知程序" : records[i].optString("name", "Windows 软件");
        }
        new AlertDialog.Builder(this).setTitle("已安装 Windows 软件")
                .setItems(labels, (d, which) -> launchInstalledWindowsApp(records[which]))
                .setPositiveButton("扫描更多软件", (d, w) -> scanInstalledApps())
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
