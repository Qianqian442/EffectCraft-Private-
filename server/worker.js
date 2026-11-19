const PBKDF2_ITERATIONS = 600000;
const USER_SESSION_SECONDS = 7 * 24 * 60 * 60;
const ADMIN_SESSION_SECONDS = 30 * 60;

const json = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
});
const now = () => new Date().toISOString();
const id = () => crypto.randomUUID();
const b64 = (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes)));
const unb64 = (text) => Uint8Array.from(atob(text), c => c.charCodeAt(0));
const tokenString = (bytes) => b64(bytes).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");

async function sha256(text) {
  return b64(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));
}
async function hashPassword(password) {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations: PBKDF2_ITERATIONS }, key, 256);
  return "pbkdf2_sha256$" + PBKDF2_ITERATIONS + "$" + b64(salt) + "$" + b64(bits);
}
async function verifyPassword(password, encoded) {
  const parts = String(encoded || "").split("$");
  if (parts.length !== 4 || parts[0] !== "pbkdf2_sha256") return false;
  const iterations = Number(parts[1]);
  if (!Number.isInteger(iterations) || iterations < 100000 || iterations > 1000000) return false;
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = new Uint8Array(await crypto.subtle.deriveBits({
    name: "PBKDF2", hash: "SHA-256", salt: unb64(parts[2]), iterations
  }, key, 256));
  const expected = unb64(parts[3]);
  if (bits.length !== expected.length) return false;
  let diff = 0;
  for (let i = 0; i < bits.length; i++) diff |= bits[i] ^ expected[i];
  return diff === 0;
}
async function issueSession(env, principalId, role, deviceId = "") {
  const token = tokenString(crypto.getRandomValues(new Uint8Array(32)));
  const tokenHash = await sha256(token);
  const created = new Date();
  const ttl = role === "admin" ? ADMIN_SESSION_SECONDS : USER_SESSION_SECONDS;
  const expires = new Date(created.getTime() + ttl * 1000).toISOString();
  await env.DB.prepare("INSERT INTO sessions(token_hash,principal_id,role,device_id,expires_at,created_at) VALUES(?,?,?,?,?,?)")
    .bind(tokenHash, principalId, role, deviceId, expires, created.toISOString()).run();
  return { accessToken: token, expiresAt: expires };
}
async function sessionFrom(request, env) {
  const header = request.headers.get("authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token) return null;
  const hash = await sha256(token);
  const row = await env.DB.prepare(
    "SELECT token_hash,principal_id,role,expires_at,revoked_at FROM sessions WHERE token_hash=?"
  ).bind(hash).first();
  if (!row || row.revoked_at || row.expires_at <= now()) return null;
  return { ...row, tokenHash: hash };
}
async function audit(env, actorId, actorRole, action, targetId = "", reason = "", result = "ok") {
  await env.DB.prepare(
    "INSERT INTO audit_log(id,actor_id,actor_role,action,target_id,reason,created_at,result) VALUES(?,?,?,?,?,?,?,?)"
  ).bind(id(), actorId, actorRole, action, targetId, reason, now(), result).run();
}
async function bootstrapAdmin(env) {
  const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM admins").first();
  if (count && Number(count.n) > 0) return;
  const bootstrap = env.ADMIN_BOOTSTRAP_PASSWORD;
  if (!bootstrap || bootstrap.length < 12) return;
  const username = (env.ADMIN_BOOTSTRAP_USERNAME || "admin").trim();
  const passwordHash = await hashPassword(bootstrap);
  await env.DB.prepare(
    "INSERT OR IGNORE INTO admins(id,username,password_hash,must_change_password,active,created_at,updated_at) VALUES(?,?,?,?,?,?,?)"
  ).bind(id(), username, passwordHash, 1, 1, now(), now()).run();
}
async function requireAdmin(request, env) {
  const session = await sessionFrom(request, env);
  if (!session || session.role !== "admin") return { error: json({ error: "管理员身份验证失败，请重新登录。" }, 401) };
  const admin = await env.DB.prepare("SELECT id,username,must_change_password,active FROM admins WHERE id=?")
    .bind(session.principal_id).first();
  if (!admin || !admin.active) return { error: json({ error: "管理员账号已停用。" }, 403) };
  if (admin.must_change_password) return { error: json({ error: "首次登录必须先修改管理员密码。" }, 403) };
  return { session, admin };
}
async function requireUser(request, env) {
  const session = await sessionFrom(request, env);
  if (!session || session.role !== "user") return { error: json({ error: "账号登录已失效，请重新登录。" }, 401) };
  const user = await env.DB.prepare("SELECT id,qq_id,wechat_id,display_name,status FROM users WHERE id=?")
    .bind(session.principal_id).first();
  if (!user) return { error: json({ error: "账号不存在。" }, 404) };
  const ban = await env.DB.prepare("SELECT reason FROM bans WHERE account_id=? AND active=1 LIMIT 1")
    .bind(user.id).first();
  if (ban) return { error: json({ error: "账号已封禁：" + ban.reason }, 403) };
  const allowed = await env.DB.prepare("SELECT account_id FROM allowlist WHERE account_id=?").bind(user.id).first();
  if (!allowed) return { error: json({ error: "账号尚未加入白名单，请联系管理员。" }, 403) };
  return { session, user };
}
async function bodyOf(request) {
  try { return await request.json(); } catch { return null; }
}
function validContact(qq, wechat) {
  const q = String(qq || "").trim();
  const w = String(wechat || "").trim();
  const qOk = !q || /^[0-9]{5,12}$/.test(q);
  const wOk = !w || /^[A-Za-z][A-Za-z0-9_-]{5,19}$/.test(w);
  return (q || w) && qOk && wOk;
}
function validAvatar(text) {
  if (!text) return true;
  if (typeof text !== "string" || text.length > 700 * 1024) return false;
  try {
    const bytes = unb64(text);
    if (bytes.length > 512 * 1024) return false;
    const png = bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47;
    const jpeg = bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
    const webp = String.fromCharCode(...bytes.slice(0,4)) === "RIFF" && String.fromCharCode(...bytes.slice(8,12)) === "WEBP";
    return png || jpeg || webp;
  } catch { return false; }
}
async function register(request, env) {
  const body = await bodyOf(request);
  if (!body || !validContact(body.qqId, body.wechatId))
    return json({ error: "QQ 号或微信号至少填写一项，并检查格式。" }, 400);
  if (typeof body.password !== "string" || body.password.length < 10 || body.password.length > 128)
    return json({ error: "密码长度必须为 10 至 128 位。" }, 400);
  if (!validAvatar(body.avatarBase64)) return json({ error: "头像格式无效或超过 512 KB。" }, 400);
  const qq = String(body.qqId || "").trim() || null;
  const wx = String(body.wechatId || "").trim() || null;
  const userId = id();
  try {
    const passwordHash = await hashPassword(body.password);
    await env.DB.prepare(
      "INSERT INTO users(id,qq_id,wechat_id,display_name,avatar_base64,password_hash,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)"
    ).bind(userId, qq, wx, String(body.displayName || "").trim().slice(0,60),
      body.avatarBase64 || null, passwordHash, "pending", now(), now()).run();
  } catch (e) {
    return json({ error: "注册失败：QQ/微信号可能已注册。" }, 409);
  }
  return json({ ok: true, accountId: userId, accountStatus: "pending",
    whitelistRequired: true, message: "注册申请已提交，请等待管理员加入白名单。" }, 201);
}
async function userLogin(request, env) {
  const body = await bodyOf(request);
  const account = String(body?.account || "").trim();
  const password = String(body?.password || "");
  if (!account || !password) return json({ error: "请输入账号和密码。" }, 400);
  const user = await env.DB.prepare("SELECT * FROM users WHERE qq_id=? OR wechat_id=? LIMIT 1")
    .bind(account, account).first();
  if (!user || !(await verifyPassword(password, user.password_hash)))
    return json({ error: "账号或密码错误。" }, 401);
  const ban = await env.DB.prepare("SELECT reason FROM bans WHERE account_id=? AND active=1 LIMIT 1")
    .bind(user.id).first();
  if (ban) return json({ error: "账号已封禁：" + ban.reason }, 403);
  const allow = await env.DB.prepare("SELECT account_id FROM allowlist WHERE account_id=?").bind(user.id).first();
  if (!allow) return json({ error: "账号尚未加入白名单，请联系管理员。", accountStatus: "pending", whitelistRequired: true }, 403);
  const issued = await issueSession(env, user.id, "user", String(body.deviceId || ""));
  return json({ ...issued, accountStatus: "active", displayName: user.display_name });
}
async function adminLogin(request, env) {
  await bootstrapAdmin(env);
  const body = await bodyOf(request);
  const username = String(body?.username || "").trim();
  const password = String(body?.password || "");
  if (!username || !password) return json({ error: "请输入管理员账号和密码。" }, 400);
  const admin = await env.DB.prepare("SELECT * FROM admins WHERE username=? AND active=1").bind(username).first();
  if (!admin || !(await verifyPassword(password, admin.password_hash)))
    return json({ error: "管理员账号或密码错误。" }, 401);
  const issued = await issueSession(env, admin.id, "admin", String(body.deviceId || ""));
  return json({ ...issued, role: "admin", mustChangePassword: !!admin.must_change_password });
}
async function changePassword(request, env) {
  const session = await sessionFrom(request, env);
  if (!session) return json({ error: "登录会话失效，请重新登录。" }, 401);
  const body = await bodyOf(request);
  const current = String(body?.currentPassword || "");
  const next = String(body?.newPassword || "");
  if (next.length < 12 || next.length > 128) return json({ error: "新密码长度必须为 12 至 128 位。" }, 400);
  if (session.role === "admin") {
    const admin = await env.DB.prepare("SELECT * FROM admins WHERE id=?").bind(session.principal_id).first();
    if (!admin || !(await verifyPassword(current, admin.password_hash)))
      return json({ error: "当前管理员密码错误。" }, 401);
    const nextHash = await hashPassword(next);
    await env.DB.prepare("UPDATE admins SET password_hash=?,must_change_password=0,updated_at=? WHERE id=?")
      .bind(nextHash, now(), admin.id).run();
  } else {
    const user = await env.DB.prepare("SELECT * FROM users WHERE id=?").bind(session.principal_id).first();
    if (!user || !(await verifyPassword(current, user.password_hash)))
      return json({ error: "当前账号密码错误。" }, 401);
    const nextHash = await hashPassword(next);
    await env.DB.prepare("UPDATE users SET password_hash=?,updated_at=? WHERE id=?")
      .bind(nextHash, now(), user.id).run();
  }
  await env.DB.prepare("UPDATE sessions SET revoked_at=? WHERE principal_id=? AND role=? AND revoked_at IS NULL")
    .bind(now(), session.principal_id, session.role).run();
  await audit(env, session.principal_id, session.role, "password.change", session.principal_id, "password rotated");
  return json({ ok: true, reloginRequired: true, message: "密码已修改，旧会话全部失效，请使用新密码重新登录。" });
}
async function adminOverview(env) {
  const a = await env.DB.prepare("SELECT COUNT(*) AS n FROM users").first();
  const b = await env.DB.prepare("SELECT COUNT(DISTINCT account_id) AS n FROM bans WHERE active=1").first();
  const w = await env.DB.prepare("SELECT COUNT(*) AS n FROM allowlist").first();
  return json({ accountCount: Number(a?.n || 0), bannedCount: Number(b?.n || 0), whitelistedCount: Number(w?.n || 0) });
}
async function adminAccounts(env) {
  const result = await env.DB.prepare(
    "SELECT u.id,u.qq_id,u.wechat_id,u.display_name,u.status,u.created_at," +
    "CASE WHEN a.account_id IS NULL THEN 0 ELSE 1 END AS is_whitelisted," +
    "CASE WHEN b.account_id IS NULL THEN 0 ELSE 1 END AS is_banned " +
    "FROM users u LEFT JOIN allowlist a ON a.account_id=u.id " +
    "LEFT JOIN (SELECT DISTINCT account_id FROM bans WHERE active=1) b ON b.account_id=u.id " +
    "ORDER BY u.created_at DESC LIMIT 500"
  ).all();
  const accounts = (result.results || []).map(x => ({
    id:x.id, qqId:x.qq_id, wechatId:x.wechat_id, displayName:x.display_name,
    status:x.status, isWhitelisted:!!x.is_whitelisted, isBanned:!!x.is_banned, createdAt:x.created_at
  }));
  return json({ accounts });
}
async function mutateAllowlist(request, env, admin) {
  const body = await bodyOf(request);
  const accountId = String(body?.accountId || "");
  const reason = String(body?.reason || "").trim();
  if (!accountId || !reason) return json({ error: "账号 ID 和操作原因必填。" }, 400);
  const user = await env.DB.prepare("SELECT id FROM users WHERE id=?").bind(accountId).first();
  if (!user) return json({ error: "账号不存在。" }, 404);
  await env.DB.prepare("INSERT INTO allowlist(account_id,reason,created_by,created_at) VALUES(?,?,?,?) " +
    "ON CONFLICT(account_id) DO UPDATE SET reason=excluded.reason,created_by=excluded.created_by,created_at=excluded.created_at")
    .bind(accountId, reason, admin.id, now()).run();
  await env.DB.prepare("UPDATE users SET status='active',updated_at=? WHERE id=?").bind(now(), accountId).run();
  await audit(env, admin.id, "admin", "allowlist.add", accountId, reason);
  return json({ ok:true, accountId });
}
async function removeAllowlist(accountId, request, env, admin) {
  const body = await bodyOf(request);
  const reason = String(body?.reason || "").trim();
  if (!reason) return json({ error: "移除白名单必须填写原因。" }, 400);
  await env.DB.prepare("DELETE FROM allowlist WHERE account_id=?").bind(accountId).run();
  await env.DB.prepare("UPDATE users SET status='pending',updated_at=? WHERE id=?").bind(now(), accountId).run();
  await env.DB.prepare("UPDATE sessions SET revoked_at=? WHERE principal_id=? AND role='user' AND revoked_at IS NULL")
    .bind(now(), accountId).run();
  await audit(env, admin.id, "admin", "allowlist.remove", accountId, reason);
  return json({ ok:true, accountId });
}
async function banAccount(request, env, admin) {
  const body = await bodyOf(request);
  const accountId = String(body?.accountId || "");
  const reason = String(body?.reason || "").trim();
  if (!accountId || !reason) return json({ error: "账号 ID 和封禁原因必填。" }, 400);
  const user = await env.DB.prepare("SELECT id FROM users WHERE id=?").bind(accountId).first();
  if (!user) return json({ error: "账号不存在。" }, 404);
  await env.DB.prepare("INSERT INTO bans(id,account_id,reason,active,created_by,created_at,updated_at) VALUES(?,?,?,?,?,?,?)")
    .bind(id(), accountId, reason, 1, admin.id, now(), now()).run();
  await env.DB.prepare("UPDATE users SET status='banned',updated_at=? WHERE id=?").bind(now(), accountId).run();
  await env.DB.prepare("UPDATE sessions SET revoked_at=? WHERE principal_id=? AND role='user' AND revoked_at IS NULL")
    .bind(now(), accountId).run();
  await audit(env, admin.id, "admin", "account.ban", accountId, reason);
  return json({ ok:true, accountId });
}
async function unbanAccount(accountId, request, env, admin) {
  const body = await bodyOf(request);
  const reason = String(body?.reason || "").trim();
  if (!reason) return json({ error: "解除封禁必须填写原因。" }, 400);
  await env.DB.prepare("UPDATE bans SET active=0,updated_at=? WHERE account_id=? AND active=1")
    .bind(now(), accountId).run();
  const allowed = await env.DB.prepare("SELECT account_id FROM allowlist WHERE account_id=?").bind(accountId).first();
  await env.DB.prepare("UPDATE users SET status=?,updated_at=? WHERE id=?")
    .bind(allowed ? "active" : "pending", now(), accountId).run();
  await audit(env, admin.id, "admin", "account.unban", accountId, reason);
  return json({ ok:true, accountId });
}
async function auditEntries(env) {
  const result = await env.DB.prepare("SELECT actor_id,actor_role,action,target_id,reason,created_at,result FROM audit_log ORDER BY created_at DESC LIMIT 300").all();
  return json({ entries: result.results || [] });
}

async function route(request, env) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const method = request.method.toUpperCase();
  if (method === "OPTIONS") return new Response(null, { status:204, headers:{
    "access-control-allow-origin":"*", "access-control-allow-methods":"GET,POST,DELETE,OPTIONS",
    "access-control-allow-headers":"authorization,content-type"
  }});
  if (path === "/health" && method === "GET") return json({ ok:true, service:"future-tech-account-api", time:now() });
  if (path === "/auth/register" && method === "POST") return register(request, env);
  if (path === "/auth/login" && method === "POST") return userLogin(request, env);
  if (path === "/admin/login" && method === "POST") return adminLogin(request, env);
  if (path === "/auth/password/change" && method === "POST") return changePassword(request, env);
  if (path === "/auth/logout" && method === "POST") {
    const session = await sessionFrom(request, env);
    if (!session) return json({ ok:true });
    await env.DB.prepare("UPDATE sessions SET revoked_at=? WHERE token_hash=?").bind(now(), session.tokenHash).run();
    return json({ ok:true });
  }
  if (path === "/account/me" && method === "GET") {
    const auth = await requireUser(request, env); if (auth.error) return auth.error;
    return json({ id:auth.user.id, qqId:auth.user.qq_id, wechatId:auth.user.wechat_id, displayName:auth.user.display_name, status:auth.user.status });
  }
  if (path.startsWith("/admin/")) {
    const auth = await requireAdmin(request, env); if (auth.error) return auth.error;
    if (path === "/admin/overview" && method === "GET") return adminOverview(env);
    if (path === "/admin/accounts" && method === "GET") return adminAccounts(env);
    if (path === "/admin/allowlist" && method === "POST") return mutateAllowlist(request, env, auth.admin);
    if (path.startsWith("/admin/allowlist/") && method === "DELETE") return removeAllowlist(decodeURIComponent(path.split("/").pop()), request, env, auth.admin);
    if (path === "/admin/bans" && method === "POST") return banAccount(request, env, auth.admin);
    if (path.startsWith("/admin/bans/") && method === "DELETE") return unbanAccount(decodeURIComponent(path.split("/").pop()), request, env, auth.admin);
    if (path === "/admin/audit" && method === "GET") return auditEntries(env);
  }
  return json({ error:"接口不存在。" }, 404);
}

export default {
  async fetch(request, env) {
    try { return await route(request, env); }
    catch (error) {
      // Never include request bodies, passwords, or bearer tokens in errors/logs.
      return json({ error:"服务器内部错误，请联系管理员。", requestId:id() }, 500);
    }
  }
};
