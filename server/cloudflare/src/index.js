const enc = new TextEncoder();
const ACCESS_TTL = 900;
const REFRESH_TTL = 2592000;
class ApiError extends Error { constructor(status, message) { super(message); this.status = status; } }

function reply(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "x-content-type-options": "nosniff" }
  });
}
function now() { return new Date().toISOString(); }
function b64url(bytes) {
  let value = "";
  for (const b of bytes) value += String.fromCharCode(b);
  return btoa(value).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}
function fromB64url(value) {
  const s = value.replace(/-/g, "+").replace(/_/g, "/");
  return Uint8Array.from(atob(s + "=".repeat((4 - s.length % 4) % 4)), c => c.charCodeAt(0));
}
function randomBytes(size) { const b = new Uint8Array(size); crypto.getRandomValues(b); return b; }
function randomId() { return crypto.randomUUID(); }
async function sha256(value) { return b64url(new Uint8Array(await crypto.subtle.digest("SHA-256", enc.encode(value)))); }
async function hashPassword(password, salt, env) {
  const iterations = Math.max(310000, Number(env.PASSWORD_HASH_ITERATIONS || 600000));
  const key = await crypto.subtle.importKey("raw", enc.encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", salt: fromB64url(salt), iterations, hash: "SHA-256" }, key, 256);
  return b64url(new Uint8Array(bits));
}
async function newPasswordHash(password, env) {
  if (typeof password !== "string" || password.length < 10 || password.length > 256)
    throw new ApiError(400, "密码长度必须为 10 到 256 个字符");
  const salt = b64url(randomBytes(16));
  return { salt, hash: await hashPassword(password, salt, env) };
}
async function signAccess(account, sid, env) {
  if (!env.JWT_SECRET || env.JWT_SECRET.length < 32) throw new Error("JWT_SECRET is not configured");
  const issued = Math.floor(Date.now() / 1000);
  const ttl = Math.max(300, Number(env.ACCESS_TOKEN_TTL_SECONDS || ACCESS_TTL));
  const h = b64url(enc.encode(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const p = b64url(enc.encode(JSON.stringify({ sub: account.id, sid, role: account.role, iat: issued, exp: issued + ttl })));
  const input = h + "." + p;
  const key = await crypto.subtle.importKey("raw", enc.encode(env.JWT_SECRET), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", key, enc.encode(input)));
  return input + "." + b64url(sig);
}
async function verifyAccess(token, env) {
  if (!env.JWT_SECRET || env.JWT_SECRET.length < 32) throw new Error("JWT_SECRET is not configured");
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const key = await crypto.subtle.importKey("raw", enc.encode(env.JWT_SECRET), { name: "HMAC", hash: "SHA-256" }, false, ["verify"]);
  const ok = await crypto.subtle.verify("HMAC", key, fromB64url(parts[2]), enc.encode(parts[0] + "." + parts[1]));
  if (!ok) return null;
  let p;
  try { p = JSON.parse(new TextDecoder().decode(fromB64url(parts[1]))); } catch { return null; }
  if (!p.sub || !p.sid || !p.exp || p.exp <= Math.floor(Date.now() / 1000)) return null;
  return p;
}
async function bodyJson(request) {
  const body = await request.text();
  if (body.length > 1024 * 1024) throw new ApiError(413, "请求内容过大");
  try { return JSON.parse(body || "{}"); } catch { throw new ApiError(400, "JSON 格式无效"); }
}
async function getSetting(db, key, fallback) {
  const row = await db.prepare("SELECT value FROM settings WHERE key=?").bind(key).first();
  return row ? row.value : fallback;
}
async function setSetting(db, key, value) {
  await db.prepare("INSERT INTO settings(key,value,updated_at) VALUES(?,?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_at=excluded.updated_at")
    .bind(key, value, now()).run();
}
async function limited(db, key) {
  const hash = await sha256(key);
  const ts = Math.floor(Date.now() / 1000);
  const old = await db.prepare("SELECT window_start,attempt_count FROM login_rate_limits WHERE key_hash=?").bind(hash).first();
  if (!old || ts - old.window_start >= 900) {
    await db.prepare("INSERT INTO login_rate_limits(key_hash,window_start,attempt_count) VALUES(?,?,1) ON CONFLICT(key_hash) DO UPDATE SET window_start=excluded.window_start,attempt_count=1")
      .bind(hash, ts).run();
    return true;
  }
  if (old.attempt_count >= 12) return false;
  await db.prepare("UPDATE login_rate_limits SET attempt_count=attempt_count+1 WHERE key_hash=?").bind(hash).run();
  return true;
}
async function ensureAdmin(env) {
  if (!env.ADMIN_BOOTSTRAP_ACCOUNT || !env.ADMIN_BOOTSTRAP_PASSWORD) return;
  const exists = await env.DB.prepare("SELECT id FROM accounts WHERE qq_id=? OR wechat_id=? LIMIT 1")
    .bind(env.ADMIN_BOOTSTRAP_ACCOUNT, env.ADMIN_BOOTSTRAP_ACCOUNT).first();
  if (exists) return;
  const pw = await newPasswordHash(env.ADMIN_BOOTSTRAP_PASSWORD, env);
  const id = randomId(), timestamp = now();
  await env.DB.prepare("INSERT INTO accounts(id,qq_id,display_name,password_salt,password_hash,role,status,is_whitelisted,must_change_password,created_at,updated_at) VALUES(?,?,?,?,?,'super_admin','active',1,1,?,?)")
    .bind(id, env.ADMIN_BOOTSTRAP_ACCOUNT, "未来科技管理员", pw.salt, pw.hash, timestamp, timestamp).run();
}
async function createSession(account, deviceId, env) {
  const sid = randomId();
  const refresh = b64url(randomBytes(48));
  const refreshHash = await sha256(refresh);
  const expiry = new Date(Date.now() + Math.max(3600, Number(env.REFRESH_TOKEN_TTL_SECONDS || REFRESH_TTL) ) * 1000).toISOString();
  await env.DB.prepare("INSERT INTO sessions(id,account_id,device_id,refresh_hash,created_at,expires_at,last_seen_at) VALUES(?,?,?,?,?,?,?)")
    .bind(sid, account.id, deviceId || null, refreshHash, now(), expiry, now()).run();
  return {
    accessToken: await signAccess(account, sid, env),
    refreshToken: refresh,
    expiresAt: new Date(Date.now() + Math.max(300, Number(env.ACCESS_TOKEN_TTL_SECONDS || ACCESS_TTL)) * 1000).toISOString()
  };
}
async function auth(request, env) {
  const header = request.headers.get("authorization") || "";
  const match = header.match(/^Bearer\s+(.+)$/i);
  if (!match) throw new ApiError(401, "需要登录");
  const payload = await verifyAccess(match[1], env);
  if (!payload) throw new ApiError(401, "登录会话无效或已过期");
  const row = await env.DB.prepare("SELECT a.*,s.revoked_at AS session_revoked,s.expires_at AS session_expiry FROM sessions s JOIN accounts a ON a.id=s.account_id WHERE s.id=? AND a.id=?")
    .bind(payload.sid, payload.sub).first();
  if (!row || row.session_revoked || Date.parse(row.session_expiry) <= Date.now()) throw new ApiError(401, "登录会话已失效");
  if (row.status === "banned") throw new ApiError(403, "账号已被封禁");
  if (row.status !== "active") throw new ApiError(403, "账号尚未获准使用");
  const whitelist = (await getSetting(env.DB, "whitelist_mode", "false")) === "true";
  if (whitelist && !row.is_whitelisted) throw new ApiError(403, "账号尚未加入管理员白名单");
  return { account: row, sid: payload.sid };
}
function adminOnly(session) {
  if (!session || !["admin", "super_admin"].includes(session.account.role)) throw new ApiError(403, "需要管理员权限");
  if (session.account.must_change_password) throw new ApiError(403, "首次登录必须先修改管理员密码");
}
async function addAudit(db, actor, action, target, reason, request) {
  await db.prepare("INSERT INTO admin_audit(id,actor_id,action,target_id,reason,result,created_at,request_id) VALUES(?,?,?,?,?,'success',?,?)")
    .bind(randomId(), actor.id, action, target || null, reason || "", now(), request.headers.get("cf-ray") || null).run();
}
async function addDevice(db, accountId, deviceId) {
  if (!deviceId || typeof deviceId !== "string" || deviceId.length > 128) return;
  const t = now();
  await db.prepare("INSERT INTO devices(id,account_id,first_seen_at,last_seen_at) VALUES(?,?,?,?) ON CONFLICT(account_id,id) DO UPDATE SET last_seen_at=excluded.last_seen_at,revoked_at=NULL")
    .bind(deviceId, accountId, t, t).run();
}
async function login(body, request, env) {
  await ensureAdmin(env);
  const id = String(body.account || "").trim();
  if (!id || typeof body.password !== "string") throw new ApiError(400, "请输入账号和密码");
  const ip = request.headers.get("cf-connecting-ip") || "unknown";
  if (!await limited(env.DB, ip + ":" + id.toLowerCase())) throw new ApiError(429, "登录尝试过多，请稍后再试");
  const account = await env.DB.prepare("SELECT * FROM accounts WHERE qq_id=? OR wechat_id=? LIMIT 1").bind(id, id).first();
  if (!account) throw new ApiError(401, "账号或密码不正确");
  if (await hashPassword(body.password, account.password_salt, env) !== account.password_hash)
    throw new ApiError(401, "账号或密码不正确");
  if (account.status === "banned") throw new ApiError(403, "账号已被封禁");
  if (account.status !== "active") throw new ApiError(403, "账号尚未获准使用");
  const whitelist = (await getSetting(env.DB, "whitelist_mode", "false")) === "true";
  if (whitelist && !account.is_whitelisted) throw new ApiError(403, "账号尚未加入管理员白名单");
  await addDevice(env.DB, account.id, body.deviceId);
  await env.DB.prepare("UPDATE accounts SET last_login_at=?,updated_at=? WHERE id=?").bind(now(), now(), account.id).run();
  return { ...(await createSession(account, body.deviceId, env)), accountId: account.id, role: account.role,
    accountStatus: account.status, mustChangePassword: !!account.must_change_password, whitelistRequired: false };
}
async function register(body, env) {
  const qq = String(body.qqId || "").trim();
  const wx = String(body.wechatId || "").trim();
  if (!qq && !wx) throw new ApiError(400, "QQ 号和微信号至少填写一项");
  if (qq.length > 64 || wx.length > 128) throw new ApiError(400, "账号字段过长");
  const pw = await newPasswordHash(body.password, env);
  const id = randomId(), t = now();
  let avatarKey = null;
  if (body.avatarBase64) {
    if (!env.AVATARS) throw new ApiError(503, "头像存储未配置");
    const mime = String(body.avatarMimeType || "");
    if (!["image/png", "image/jpeg", "image/webp"].includes(mime)) throw new ApiError(400, "头像只支持 PNG、JPEG、WebP");
    if (String(body.avatarBase64).length > 700000) throw new ApiError(413, "头像文件过大");
    const bytes = Uint8Array.from(atob(body.avatarBase64), c => c.charCodeAt(0));
    if (bytes.length > 512 * 1024) throw new ApiError(413, "头像文件过大");
    const png = mime === "image/png" && bytes[0] === 137 && bytes[1] === 80 && bytes[2] === 78 && bytes[3] === 71;
    const jpg = mime === "image/jpeg" && bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255;
    const webp = mime === "image/webp" && String.fromCharCode(...bytes.slice(0,4)) === "RIFF" && String.fromCharCode(...bytes.slice(8,12)) === "WEBP";
    if (!(png || jpg || webp)) throw new ApiError(400, "头像图片内容与格式不匹配");
    avatarKey = "avatars/" + id + (png ? ".png" : webp ? ".webp" : ".jpg");
    await env.AVATARS.put(avatarKey, bytes, { httpMetadata: { contentType: mime, cacheControl: "public, max-age=86400" } });
  }
  const whitelist = (await getSetting(env.DB, "whitelist_mode", "false")) === "true";
  const status = whitelist ? "pending" : "active";
  try {
    await env.DB.prepare("INSERT INTO accounts(id,qq_id,wechat_id,display_name,avatar_key,password_salt,password_hash,role,status,is_whitelisted,must_change_password,created_at,updated_at) VALUES(?,?,?,?,?,?,?,'user',?,0,0,?,?)")
      .bind(id, qq || null, wx || null, String(body.displayName || "").slice(0,80), avatarKey, pw.salt, pw.hash, status, t, t).run();
  } catch (e) {
    if (avatarKey && env.AVATARS) await env.AVATARS.delete(avatarKey);
    throw new ApiError(409, "该 QQ 号或微信号已注册");
  }
  await addDevice(env.DB, id, body.deviceId);
  return { accountId:id, accountStatus:status, whitelistRequired:whitelist, avatarUrl:avatarKey ? "/api/v1/" + avatarKey : null };
}
async function changePassword(session, body, env) {
  if (typeof body.currentPassword !== "string" || typeof body.newPassword !== "string") throw new ApiError(400, "请输入当前密码和新密码");
  if (body.newPassword.length < 12 || body.newPassword.length > 256) throw new ApiError(400, "新密码至少 12 位");
  if (await hashPassword(body.currentPassword, session.account.password_salt, env) !== session.account.password_hash)
    throw new ApiError(401, "当前密码不正确");
  const pw = await newPasswordHash(body.newPassword, env), t = now();
  await env.DB.prepare("UPDATE accounts SET password_salt=?,password_hash=?,must_change_password=0,updated_at=? WHERE id=?")
    .bind(pw.salt,pw.hash,t,session.account.id).run();
  // Revoke every old session, including the session used for this password change.
  // A fresh install must authenticate again with the latest password.
  await env.DB.prepare("UPDATE sessions SET revoked_at=? WHERE account_id=? AND revoked_at IS NULL")
    .bind(t,session.account.id).run();
  return { passwordChanged:true, mustChangePassword:false };
}
async function refresh(body, env) {
  if (!body.refreshToken) throw new ApiError(400, "缺少刷新令牌");
  const hash = await sha256(body.refreshToken);
  const row = await env.DB.prepare("SELECT s.*,a.role,a.status,a.is_whitelisted FROM sessions s JOIN accounts a ON a.id=s.account_id WHERE s.refresh_hash=?")
    .bind(hash).first();
  if (!row || row.revoked_at || Date.parse(row.expires_at)<=Date.now() || row.status!=="active") throw new ApiError(401,"刷新令牌无效或已失效");
  const whitelist = (await getSetting(env.DB,"whitelist_mode","false"))==="true";
  if (whitelist && !row.is_whitelisted) throw new ApiError(403,"账号尚未加入白名单");
  const next = b64url(randomBytes(48)), nextHash = await sha256(next), t = now();
  const expiry = new Date(Date.now()+Math.max(3600,Number(env.REFRESH_TOKEN_TTL_SECONDS || REFRESH_TTL))*1000).toISOString();
  await env.DB.prepare("UPDATE sessions SET refresh_hash=?,expires_at=?,last_seen_at=? WHERE id=?").bind(nextHash,expiry,t,row.id).run();
  return { accessToken:await signAccess({id:row.account_id,role:row.role},row.id,env),refreshToken:next };
}
async function adminRoute(request, url, session, env) {
  adminOnly(session);
  const db = env.DB;
  if (request.method==="GET" && url.pathname==="/api/v1/admin/overview") {
    const rows = await Promise.all([
      db.prepare("SELECT COUNT(*) n FROM accounts WHERE role='user'").first(),
      db.prepare("SELECT COUNT(*) n FROM accounts WHERE role='user' AND status='banned'").first(),
      db.prepare("SELECT COUNT(*) n FROM accounts WHERE role='user' AND is_whitelisted=1").first()
    ]);
    return reply({accountCount:rows[0].n,bannedCount:rows[1].n,whitelistedCount:rows[2].n,whitelistMode:(await getSetting(db,"whitelist_mode","false"))==="true"});
  }
  if (request.method==="GET" && url.pathname==="/api/v1/admin/accounts") {
    const rows = await db.prepare("SELECT id,qq_id AS qqId,wechat_id AS wechatId,display_name AS displayName,role,status,is_whitelisted AS isWhitelisted,created_at AS createdAt,last_login_at AS lastLoginAt FROM accounts WHERE role='user' ORDER BY created_at DESC LIMIT 500").all();
    return reply({accounts:rows.results || []});
  }
  if (request.method==="POST" && url.pathname==="/api/v1/admin/allowlist") {
    const b=await bodyJson(request);
    if (!b.accountId || !b.reason) throw new ApiError(400,"必须填写账号和原因");
    await db.prepare("UPDATE accounts SET is_whitelisted=1,status=CASE WHEN status='pending' THEN 'active' ELSE status END,updated_at=? WHERE id=?").bind(now(),b.accountId).run();
    await addAudit(db,session.account,"allowlist.add",b.accountId,b.reason,request);
    return reply({success:true});
  }
  if (request.method==="DELETE" && url.pathname.startsWith("/api/v1/admin/allowlist/")) {
    const id=decodeURIComponent(url.pathname.split("/").pop()), b=await bodyJson(request);
    if (!b.reason) throw new ApiError(400,"必须填写原因");
    const wl=(await getSetting(db,"whitelist_mode","false"))==="true";
    await db.prepare("UPDATE accounts SET is_whitelisted=0,status=CASE WHEN ?=1 AND role='user' AND status<>'banned' THEN 'pending' ELSE status END,updated_at=? WHERE id=?").bind(wl?1:0,now(),id).run();
    if (wl) await db.prepare("UPDATE sessions SET revoked_at=? WHERE account_id=? AND revoked_at IS NULL").bind(now(),id).run();
    await addAudit(db,session.account,"allowlist.remove",id,b.reason,request);
    return reply({success:true});
  }
  if (request.method==="POST" && url.pathname==="/api/v1/admin/bans") {
    const b=await bodyJson(request);
    if (!b.accountId || !b.reason) throw new ApiError(400,"必须填写账号和原因");
    const target=await db.prepare("SELECT id,role FROM accounts WHERE id=?").bind(b.accountId).first();
    if (!target || target.role!=="user") throw new ApiError(404,"普通账号不存在");
    const t=now();
    await db.prepare("UPDATE accounts SET status='banned',updated_at=? WHERE id=?").bind(t,target.id).run();
    await db.prepare("UPDATE sessions SET revoked_at=? WHERE account_id=? AND revoked_at IS NULL").bind(t,target.id).run();
    await db.prepare("INSERT INTO bans(id,account_id,reason,actor_id,created_at) VALUES(?,?,?,?,?)").bind(randomId(),target.id,b.reason,session.account.id,t).run();
    await addAudit(db,session.account,"account.ban",target.id,b.reason,request);
    return reply({success:true});
  }
  if (request.method==="DELETE" && url.pathname.startsWith("/api/v1/admin/bans/")) {
    const id=decodeURIComponent(url.pathname.split("/").pop()), b=await bodyJson(request);
    if (!b.reason) throw new ApiError(400,"必须填写解封原因");
    const wl=(await getSetting(db,"whitelist_mode","false"))==="true";
    const target=await db.prepare("SELECT id,role,is_whitelisted FROM accounts WHERE id=?").bind(id).first();
    if (!target || target.role!=="user") throw new ApiError(404,"普通账号不存在");
    const state=wl && !target.is_whitelisted ? "pending" : "active";
    await db.prepare("UPDATE accounts SET status=?,updated_at=? WHERE id=?").bind(state,now(),id).run();
    await db.prepare("UPDATE bans SET ended_at=? WHERE account_id=? AND ended_at IS NULL").bind(now(),id).run();
    await addAudit(db,session.account,"account.unban",id,b.reason,request);
    return reply({success:true,accountStatus:state});
  }
  if (request.method==="POST" && url.pathname==="/api/v1/admin/whitelist-mode") {
    if (session.account.role!=="super_admin") throw new ApiError(403,"只有超级管理员可以修改全局白名单");
    const b=await bodyJson(request);
    if (typeof b.enabled!=="boolean" || !b.reason) throw new ApiError(400,"缺少开关或原因");
    await setSetting(db,"whitelist_mode",String(b.enabled));
    await addAudit(db,session.account,"whitelist.mode",null,b.reason,request);
    return reply({success:true,enabled:b.enabled});
  }
  if (request.method==="GET" && url.pathname==="/api/v1/admin/audit") {
    const rows=await db.prepare("SELECT id,actor_id AS actorId,action,target_id AS targetId,reason,result,created_at AS createdAt FROM admin_audit ORDER BY created_at DESC LIMIT 200").all();
    return reply({events:rows.results || []});
  }
  throw new ApiError(404,"管理员接口不存在");
}
async function route(request, env) {
  const url=new URL(request.url);
  if (request.method==="OPTIONS") return new Response(null,{status:204,headers:{"access-control-allow-origin":"*","access-control-allow-methods":"GET,POST,DELETE,OPTIONS","access-control-allow-headers":"authorization,content-type"}});
  if (request.method==="GET" && url.pathname==="/api/v1/health") return reply({ok:true,service:"future-tech-account-api",time:now()});
  if (!env.DB) throw new ApiError(503,"D1 数据库尚未绑定");
  if (request.method==="POST" && url.pathname==="/api/v1/auth/register") return reply(await register(await bodyJson(request),env),201);
  if (request.method==="POST" && url.pathname==="/api/v1/auth/login") return reply(await login(await bodyJson(request),request,env));
  if (request.method==="POST" && url.pathname==="/api/v1/auth/refresh") return reply(await refresh(await bodyJson(request),env));

  if (request.method==="POST" && url.pathname==="/api/v1/auth/password/change") {
    const session=await auth(request,env);
    return reply(await changePassword(session,await bodyJson(request),env));
  }
  if (request.method==="POST" && url.pathname==="/api/v1/auth/logout") {
    const session=await auth(request,env);
    await env.DB.prepare("UPDATE sessions SET revoked_at=? WHERE id=?").bind(now(),session.sid).run();
    return reply({success:true});
  }

  let session=null;
  if (request.headers.get("authorization")) session=await auth(request,env);
  if (url.pathname.startsWith("/api/v1/admin/")) {
    if (!session) session=await auth(request,env);
    return await adminRoute(request,url,session,env);
  }
  if (request.method==="GET" && url.pathname==="/api/v1/runtime/manifest") {
    if (!session) session=await auth(request,env);
    return reply({available:false,components:[],message:"Windows 兼容运行组件尚未发布"});
  }
  if (request.method==="POST" && url.pathname==="/api/v1/runtime/session") {
    if (!session) session=await auth(request,env);
    return reply({available:false,status:"not_integrated",message:"Windows 兼容运行组件尚未完成构建与设备验证"},409);
  }
  throw new ApiError(404,"接口不存在");
}
export default {
  async fetch(request,env) {
    try { return await route(request,env); }
    catch (error) {
      const status=error instanceof ApiError ? error.status : 500;
      if (status===500) console.error("future-tech-api-error",request.headers.get("cf-ray")||"no-ray",error && error.message ? error.message : "unknown");
      return reply({error:true,message:status===500?"服务器内部错误":error.message},status);
    }
  }
};
