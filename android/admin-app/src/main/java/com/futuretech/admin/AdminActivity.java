package com.futuretech.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.futuretech.shared.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin administration client. The server, not this APK, authorizes every privileged action.
 * Reinstalling the APK requires a new online login with the latest server password.
 */
public final class AdminActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private android.os.Handler ui;
    private LinearLayout content;
    private TextView status;
    private String accountToken;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new android.os.Handler(getMainLooper());
        buildBase();
        // Admin credentials and tokens are deliberately memory-only.
        // Every app launch, reinstall or re-download requires the current server password.
        showLogin();
    }

    private void buildBase() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(0xFFF2F5FA);

        TextView title = new TextView(this);
        title.setText("未来科技管理版");
        title.setTextSize(26);
        title.setTextColor(0xFF17243A);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(58)));

        status = new TextView(this);
        status.setText("正在检查管理员会话…");
        status.setTextColor(0xFF53627A);
        status.setPadding(0, dp(10), 0, dp(12));
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void showLogin() {
        LinearLayout form = form();
        EditText account = field("管理员账号");
        EditText password = field("当前管理员密码");
        password.setInputType(129);
        form.addView(account);
        form.addView(password);
        new AlertDialog.Builder(this).setTitle("管理员登录")
                .setMessage("首次安装或重新下载后，需要使用服务器当前有效的管理员密码登录。修改密码后旧密码立即失效。")
                .setView(form).setCancelable(false)
                .setPositiveButton("登录", (d, w) -> {
                    JSONObject body = new JSONObject();
                    try {
                        body.put("username", account.getText().toString().trim());
                        body.put("password", password.getText().toString());
                        body.put("deviceId", deviceId());
                    } catch (Exception ignored) {}
                    post("/admin/login", body, null, result -> {
                        accountToken = result.optString("accessToken", "");
                        if (accountToken.isEmpty()) { message("服务器没有发放有效会话。"); return; }
                        if (result.optBoolean("mustChangePassword", false)) showChangePassword(true);
                        else loadDashboard();
                    });
                })
                .setNegativeButton("退出", (d,w)->finish()).show();
    }

    private void showChangePassword(boolean required) {
        LinearLayout form = form();
        EditText oldPassword = field("当前密码");
        oldPassword.setInputType(129);
        EditText newPassword = field("新密码（至少 12 位）");
        newPassword.setInputType(129);
        EditText confirm = field("再次输入新密码");
        confirm.setInputType(129);
        form.addView(oldPassword);
        form.addView(newPassword);
        form.addView(confirm);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(required ? "首次登录：必须修改密码" : "修改管理员密码")
                .setMessage("密码只在本次 HTTPS 请求中提交；服务器只保存密码哈希。重新下载或重新安装管理版时，必须使用修改后的最新密码登录。")
                .setView(form).setPositiveButton("确认修改", null)
                .setNegativeButton(required ? "退出" : "取消", (d,w)-> { if (required) finish(); })
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            String next = newPassword.getText().toString();
            if (next.length() < 12) { message("新密码至少 12 位"); return; }
            if (!next.equals(confirm.getText().toString())) { message("两次输入的新密码不一致"); return; }
            JSONObject body = new JSONObject();
            try { body.put("currentPassword", oldPassword.getText().toString()); body.put("newPassword", next); }
            catch (Exception ignored) {}
            post("/auth/password/change", body, accountToken, result -> {
                dialog.dismiss();
                accountToken = null; // Server revokes all admin sessions after password rotation.
                message("管理员密码已更新，旧密码和旧会话全部失效。");
                showLogin(); // Next login must use the new server password.
            });
        }));
        dialog.show();
    }

    private void loadDashboard() {
        if (accountToken == null || accountToken.isEmpty()) { showLogin(); return; }
        content.removeAllViews();
        status.setText("正在从服务器读取管理数据…");
        get("/admin/overview", accountToken, overview -> {
            status.setText("服务器连接正常 · 管理员已验证");
            content.addView(card("注册账号：" + overview.optInt("accountCount") +
                    "\n封禁账号：" + overview.optInt("bannedCount") +
                    "\n白名单账号：" + overview.optInt("whitelistedCount")));
            content.addView(button("账号列表 / 白名单 / 封号", v -> loadAccounts()));
            content.addView(button("修改管理员密码", v -> showChangePassword(false)));
            content.addView(button("退出管理员登录", v -> logout()));
        });
    }

    private void loadAccounts() {
        get("/admin/accounts", accountToken, result -> {
            content.removeAllViews();
            content.addView(button("返回管理首页", v -> loadDashboard()));
            JSONArray accounts = result.optJSONArray("accounts");
            if (accounts == null || accounts.length() == 0) {
                content.addView(card("暂无账号"));
                return;
            }
            for (int i=0;i<accounts.length();i++) {
                JSONObject a = accounts.optJSONObject(i);
                if (a == null) continue;
                String id = a.optString("id");
                String label = a.optString("displayName", "未命名账号") +
                        "\nQQ：" + a.optString("qqId", "-") +
                        "\n微信：" + a.optString("wechatId", "-") +
                        "\n状态：" + a.optString("status") +
                        "\n白名单：" + (a.optBoolean("isWhitelisted") ? "已加入" : "未加入");
                content.addView(card(label));
                LinearLayout actions = new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                Button allow = buttonSmall(a.optBoolean("isWhitelisted") ? "移出白名单" : "加入白名单",
                        v -> changeAllowlist(id, !a.optBoolean("isWhitelisted")));
                Button ban = buttonSmall("封禁 / 解封", v -> promptReason(id, "ban"));
                actions.addView(allow, new LinearLayout.LayoutParams(0, dp(48), 1));
                actions.addView(ban, new LinearLayout.LayoutParams(0, dp(48), 1));
                content.addView(actions);
            }
        });
    }

    private void changeAllowlist(String id, boolean allow) {
        promptReason(id, allow ? "allow" : "deny");
    }

    private void promptReason(String id, String operation) {
        EditText reason = field("操作原因（必填）");
        new AlertDialog.Builder(this).setTitle("管理员操作").setView(reason)
                .setNegativeButton("取消", null)
                .setPositiveButton("提交", (d,w)-> {
                    String why = reason.getText().toString().trim();
                    if (why.isEmpty()) { message("必须填写操作原因"); return; }
                    JSONObject body = new JSONObject();
                    try {
                        body.put("accountId", id);
                        body.put("reason", why);
                    } catch (Exception ignored) {}
                    if ("allow".equals(operation)) post("/admin/allowlist", body, accountToken, x -> loadAccounts());
                    else if ("deny".equals(operation)) deleteWithBody("/admin/allowlist/" + id, body, x -> loadAccounts());
                    else {
                        new AlertDialog.Builder(this).setTitle("选择封禁操作")
                                .setItems(new String[]{"封禁账号", "解除封禁"}, (dd,which)-> {
                                    if (which==0) post("/admin/bans", body, accountToken, x -> loadAccounts());
                                    else deleteWithBody("/admin/bans/" + id, body, x -> loadAccounts());
                                }).show();
                    }
                }).show();
    }

    private void logout() {
        JSONObject empty = new JSONObject();
        post("/auth/logout", empty, accountToken, x -> {
            accountToken = null;
            message("已退出管理员登录");
            showLogin();
        });
    }

    private void get(String path, String token, java.util.function.Consumer<JSONObject> ok) {
        request("GET", path, null, token, ok);
    }
    private void post(String path, JSONObject body, String token, java.util.function.Consumer<JSONObject> ok) {
        request("POST", path, body, token, ok);
    }
    private void deleteWithBody(String path, JSONObject body, java.util.function.Consumer<JSONObject> ok) {
        request("DELETE", path, body, accountToken, ok);
    }
    private void request(String method, String path, JSONObject body, String token, java.util.function.Consumer<JSONObject> ok) {
        if (BuildConfig.API_BASE_URL.isEmpty()) { message("账号服务器尚未部署，管理功能暂不可用。"); return; }
        io.execute(() -> {
            try {
                JSONObject result = ApiClient.request(BuildConfig.API_BASE_URL, method, path, body, token);
                ui.post(() -> ok.accept(result));
            } catch (Exception e) {
                ui.post(() -> {
                    status.setText("服务器验证失败");
                    message(e.getMessage());
                    if (e instanceof ApiClient.ApiException && ((ApiClient.ApiException)e).statusCode == 401) {
                        accountToken = null;
                        showLogin();
                    }
                });
            }
        });
    }

    private LinearLayout form() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(8), dp(16), dp(8));
        return l;
    }
    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true); e.setHint(hint); return e;
    }
    private Button button(String text, View.OnClickListener action) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setOnClickListener(action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52)); lp.topMargin = dp(8);
        b.setLayoutParams(lp); return b;
    }
    private Button buttonSmall(String text, View.OnClickListener action) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(12); b.setOnClickListener(action); return b;
    }
    private TextView card(String text) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(15); t.setTextColor(0xFF26354C);
        t.setBackgroundColor(0xFFFFFFFF); t.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8); t.setLayoutParams(lp); return t;
    }
    private String deviceId() {
        android.content.SharedPreferences p = getSharedPreferences("admin_device", MODE_PRIVATE);
        String id = p.getString("id", null);
        if (id == null) { id = java.util.UUID.randomUUID().toString(); p.edit().putString("id", id).apply(); }
        return id;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void message(String value) { Toast.makeText(this, value == null ? "操作失败" : value, Toast.LENGTH_LONG).show(); }
    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }
}
