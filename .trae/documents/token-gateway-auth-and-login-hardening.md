# Token 鉴权网关 + 登录硬化（限频 / 幂等 / 可吊销 token）

## Context

当前 NAC 项目（后端 `C:\Users\king\Downloads\Nac` / 前端 `D:\迅雷下载\cloud`）已有：

- ✅ 后端图形验证码：`CaptchaService.generate()` + `GET /api/auth/captcha/img`，`AuthController.login` / `PortalController.sendSmsCode` 已强制服务端校验、校验后销毁防重放
- ✅ 后端短信验证码生成：`SmsVerifyService.sendCode`（6 位、Redis+内存降级、5min TTL）
- ✅ 短信接口防刷三层：图形验证码前置 → 60s 冷却 → 单号每日 10 条上限 → 错 5 次作废
- ✅ 短信接口幂等：`PortalController.sendSmsCode` 用 traceId + Redis SETNX 60s 锁
- ✅ 前端 token 存 sessionStorage（合规：关闭浏览器即失效）—— **保持不变**

但仍缺失用户当前请求的核心部分：

1. **token 不可伪造 + 可吊销**：`AuthController.login` 现签发的是裸 `Base64(user:ts)`，任何人可伪造；登出未真正失效 token。
2. **网关层鉴权过滤器**：`nac-gateway` 当前仅有路由配置，无任何 Java 过滤器，导致 `/api/devices/**`、`/api/users/**` 等业务接口可被无 token 直接访问。
3. **登录接口限频 + 幂等**：`AuthController.login` 无防爆破限频，无幂等锁 —— 双击登录按钮可重复创建 `OnlineSession` + 重复投递 Kafka 事件。

本方案补齐上述三项，使「token 存浏览器 + 客户每次必须登录才能进后端 + 登录对接验证码 + 短信后端生成 + 防刷 + 幂等」整条链路在服务端真正落地。

## 方案选型

**Opaque UUID token + Redis 白名单 + 网关过滤器**（不用 JWT）：
- token = `UUID.randomUUID()`，存 `nac:auth:web:token:<token>` → JSON `{username, role, loginTime}` in Redis，TTL 8h（滑动续期）
- 网关过滤器校验 `Authorization: Bearer <token>` 头 → 查 Redis 白名单 → 不存在返 401，存在则放行
- 登出 = `DEL nac:auth:web:token:<token>`，立即失效

理由：与项目现有模式一致（`InMemoryUserStore` 在线会话也用 Redis + UUID + TTL），无需引入 JWT 库，可即时吊销，Redis 在 127.0.0.1 本机环回 sub-ms 延迟可接受。

## 实施步骤

### 1. nac-gateway 新增网关鉴权过滤器

**文件：`C:\Users\king\Downloads\Nac\nac-gateway\pom.xml`**
- 新增依赖 `spring-boot-starter-data-redis`（网关需查 Redis 白名单）

**文件：`C:\Users\king\Downloads\Nac\nac-gateway\src\main\resources\application.yml`**
- 新增 `spring.redis` 块（host: 127.0.0.1, password: MAO5201314sm, database: 0, timeout: 3000ms, lettuce.shutdown-timeout: 200ms）—— 与 `nac-auth/application.yml` 一致

**文件：`C:\Users\king\Downloads\Nac\nac-gateway\src\main\java\com\nac\gateway\GatewayApplication.java`**
- 注解改为 `@SpringBootApplication(scanBasePackages = "com.nac")`，让新建的过滤器被扫描

**新建：`C:\Users\king\Downloads\Nac\nac-gateway\src\main\java\com\nac\gateway\AuthGatewayFilter.java`**
- 实现 `GlobalFilter, Ordered`，`Ordered.HIGHEST_PRECEDENCE`
- 注入 `StringRedisTemplate`
- 公共白名单（直接放行，不校验 token）：
  - `/api/auth/login`、`/api/auth/captcha/**`（登录前必须能取验证码 + 登录）
  - `/api/portal/**`（Portal 认证页 —— 这是访客上网前认证，无管理 token）
  - `/api/free-auth/**`（免认证资源 MAC 白名单判定）
- 其他 `/api/**` 路径：
  - 提取 `Authorization: Bearer <token>` 头
  - 查 `nac:auth:web:token:<token>` 是否存在 + 读 JSON 解析 `{username, role}`
  - 缺失/无效 → 返 401 JSON `{code: 401, message: "未登录或会话已过期"}`（Content-Type: application/json）
  - 有效 → 滑动续期（`EXPIRE` 重置 8h）+ 向下游注入 `X-User-Name` / `X-User-Role` 头（后续下游服务可选用）
- SSE 特殊处理：`/api/monitor/stream` 路径支持 `?token=xxx` 查询参数兜底（浏览器 EventSource 无法设 Authorization 头），其它路径不接受查询参数 token

### 2. nac-auth 新增 WebTokenService

**新建：`C:\Users\king\Downloads\Nac\nac-auth\src\main\java\com\nac\auth\store\WebTokenService.java`**
- `@Service`，注入 `StringRedisTemplate`
- Redis key 前缀：`nac:auth:web:token:`
- TTL 默认 8h（可配置 `web.token-ttl-seconds: 28800`）
- 方法：
  - `String createToken(String username, String role)` —— 生成 UUID，存 JSON `{username, role, loginTime}`，返回 UUID
  - `WebTokenPayload validate(String token)` —— 查 Redis，返回 payload 或 null（Redis 故障返 null，让网关 401 拒绝，符合安全优先）
  - `void revoke(String token)` —— `DEL` key（登出立即失效）
  - `void extend(String token)` —— `EXPIRE` 重置 TTL（滑动续期，仅在 token 仍存在时）
- 内部 record/class `WebTokenPayload { username, role, loginTime }`，复用 `com.nac.common.util.JsonUtil` 序列化

### 3. 改造 AuthController 登录与登出

**文件：`C:\Users\king\Downloads\Nac\nac-auth\src\main\java\com\nac\auth\controller\AuthController.java`**

**注入新依赖**：`WebTokenService webTokenService`、`StringRedisTemplate redisTemplate`

**`login` 方法改造**：
- 原有 captcha 校验保留（验证码前置 + 销毁防重放）
- 新增**幂等锁**：在 captcha 校验成功后、密码校验前，`nac:auth:login:idem:<username>:<captchaId>` SETNX 10s TTL —— 重复请求返首次结果。注意：captcha 已被首次校验销毁，重复请求理论上过不了 captcha，幂等锁是双保险（防双击 + 防网络重试触发重复 Kafka 事件）。具体做法：
  - SETNX 成功（首次）→ 继续走密码校验 → 将最终 `Result` JSON 序列化后存入 `nac:auth:login:result:<idemKey>` 10s TTL
  - SETNX 失败（重复）→ 直接读 `nac:auth:login:result:<idemKey>` 返回首次结果
- 新增**防爆破限频**（失败计数 + IP/用户锁定，模式参考 `SmsVerifyService`）：
  - 失败时 `INCR nac:auth:login:fail:ip:<ip>` TTL 300s → 达 5 次 → 设 `nac:auth:login:lock:ip:<ip>` 900s
  - 失败时 `INCR nac:auth:login:fail:user:<username>` TTL 300s → 达 10 次 → 设 `nac:auth:login:lock:user:<username>` 1800s
  - 登录入口先查锁定 key，命中直接返 401「账号/IP 已锁定，请 X 分钟后再试」
  - 成功时清两个 fail 计数 + 不清 lock（让惩罚期完整执行）
- 改造 token 签发：`Base64(user:ts)` → `webTokenService.createToken(username, role)`
- IP 提取：从 `X-Forwarded-For` 头取首段，回退 `request.getRemoteAddr()`（用 `HttpServletRequest` 入参，需新增 `@RequestHeader` 或 HttpServletRequest 参数）
- 新增 `@RequestHeader(value = "Authorization", required = false) String authHeader` 以便后续日志记录

**`logout` 方法改造**：
- 从 `Authorization: Bearer <token>` 头提取 token（新增 `HttpServletRequest` 入参或 `@RequestHeader`）
- 调用 `webTokenService.revoke(token)` —— 真正失效 token
- 保留原 `radiusAuthService.forceOffline(username)` 调用清理 RADIUS 在线会话

### 4. 前端改造（保持 sessionStorage 不变）

**文件：`D:\迅雷下载\cloud\src\view\Login.vue`**
- `handleLogin`：在 login payload 新增 `traceId: uuid()` 字段（前端生成 UUID v4），用于配合后端幂等锁
- 加载状态 `loading=true` 已在登录开始时设置，自然防双击 —— traceId 是网络重试场景下的兜底
- sessionStorage 写入逻辑保持不变（line 292-298）

**文件：`D:\迅雷下载\cloud\src\api\auth.js`**
- `logout(username)` 签名不变，axios 拦截器已自动带 `Authorization` 头 —— 后端从 header 取 token 即可

**文件：`D:\迅雷下载\cloud\src\router\request.js`**
- 已有逻辑正确：请求拦截器从 sessionStorage 取 token 加 `Authorization` 头；401 响应清凭据跳登录页 —— 无需改动

**文件：`D:\迅雷下载\cloud\src\view\layout\Layout.vue`**
- `doLogout` 已调用 `logout(username)` 并清 sessionStorage/localStorage —— 无需改动
- 退出后后端 revoke token 即可让旧 token 失效

### 5. 重建 + 验证

**编译**：
```
mvn -pl nac-gateway,nac-auth -am clean package -DskipTests
```

**部署**（参照项目 memory：用 `nac.sh` stop+start，避免 pkill -f 误杀 jar）：
- 重新部署 nac-gateway 与 nac-auth 到 192.168.80.36
- 重启 nginx（已有 SSE unbuffered + index.html no-cache 配置）

**端到端验证**：
1. `curl -i http://<gateway>:9000/api/devices` → 401「未登录或会话已过期」
2. `curl http://<gateway>:9000/api/auth/captcha/img` → 200 返回 `{captchaId, img}`（公共白名单）
3. 同一 IP 用错误密码登录 6 次 → 第 6 次返「IP 已锁定」
4. 正确账号 + 正确验证码登录 → 返 `{token, username, role}`，token 存 sessionStorage
5. `curl -H "Authorization: Bearer <token>" http://<gateway>:9000/api/devices` → 200
6. 短时间内连续两次同样登录请求（同 traceId）→ 第二次返首次的 Result
7. 调用 `/api/auth/logout`（带 Authorization 头）→ 200
8. 旧 token 再访问 `/api/devices` → 401（token 已被 revoke）
9. Portal 认证页 `/portal` 仍可匿名访问 + `/api/portal/sms/send` 走原防刷流程
10. 浏览器实测：登录、空闲 30 分钟自动登出、手动退出登录 → 三种路径下旧 token 均失效

## 涉及文件清单

| 文件 | 操作 |
|---|---|
| `nac-gateway/pom.xml` | 加 spring-boot-starter-data-redis |
| `nac-gateway/src/main/resources/application.yml` | 加 spring.redis 块 |
| `nac-gateway/src/main/java/com/nac/gateway/GatewayApplication.java` | 改 scanBasePackages |
| `nac-gateway/src/main/java/com/nac/gateway/AuthGatewayFilter.java` | **新建**：GlobalFilter |
| `nac-auth/src/main/java/com/nac/auth/store/WebTokenService.java` | **新建**：UUID + Redis 白名单 |
| `nac-auth/src/main/java/com/nac/auth/controller/AuthController.java` | 改 login/logout：WebTokenService + 限频 + 幂等 |
| `D:\迅雷下载\cloud\src\view\Login.vue` | 加 traceId 字段 |
| `D:\迅雷下载\cloud\src\api\auth.js` | 无需改（拦截器已带 Authorization 头） |
| `D:\迅雷下载\cloud\src\router\request.js` | 无需改 |
| `D:\迅雷下载\cloud\src\view\layout\Layout.vue` | 无需改 |

## 风险与边界

- **Redis 故障**：网关过滤器无法校验 token → 全量 401 拒绝（安全优先），与 `InMemoryUserStore` 的容忍降级不同 —— 管理端宁可全锁也不放攻击者。Redis 已在 127.0.0.1 本机环回，可用性高。
- **SSE 鉴权**：`/api/monitor/stream` 用 EventSource 无法设 Authorization 头，提供 `?token=xxx` 兜底，仅此一条路径接受 query token。
- **滑动续期 vs 固定 TTL**：选滑动续期（每次请求 EXPIRE 重置 8h），活跃用户不被踢；非活跃用户 8h 后自然失效需重新登录，符合「每次必须登录」语义。
- **限频误伤**：NAT 出口多用户共享 IP，5 次/IP/5min 可能误伤。已配 username 维度独立计数（10 次/5min），用户名正确但密码错的场景下用户名锁定优先于 IP 锁定触发。可后续按需调参。
- **幂等锁窗口**：10s 足够覆盖正常网络重试 + 双击场景，不会阻塞合法的「输错密码重新登录」流程（captcha 已换新 captchaId）。
