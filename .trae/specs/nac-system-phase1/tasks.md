# NAC 准入控制系统（一期）- 实现计划

> 依赖关系：T1 骨架 → T2 安全底座 → T3 网关 / T4 DDL → T5 认证 → T6 短信 / T7 Portal / T8 日志 → T9 前端 → T10 高并发整合验证

## Task 1: nac-system 父工程骨架与技术栈
- **Status**: `completed`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 新建 `nac-system/` 多模块 Maven 父工程，模块：`nac-common`、`nac-gateway`、`nac-auth`、`nac-user`、`nac-radius`、`nac-log`。
  - 父 pom：`java.version=27`、Spring Boot 4.1.x、Spring Cloud 2025.x、dependencyManagement 统一版本；repositories 仅阿里云（public/spring/central）且 snapshots disabled。
  - 项目 `settings.xml`（mirror 强制阿里云）、`docker-compose.yml`（MySQL8/Redis/Nacos/Kafka-KRaft）。
  - 各子模块最小可编译骨架（pom + Application 启动类 + bootstrap/application.yml）。
- **Acceptance Criteria Addressed**: AC-1, AC-9
- **Test Requirements**:
  - `rule` TR-1.1: `mvn -q -DskipTests -pl nac-common,nac-gateway,nac-auth package` 编译成功；证据为构建输出无 ERROR。
  - `rule` TR-1.2: 父 pom repositories 无 maven central 直连、snapshots 均 disabled；证据为 grep 结果。

## Task 2: nac-common 安全底座
- **Status**: `completed`
- **Priority**: high
- **Depends On**: T1
- **Description**:
  - 迁移升级 his-common 为 nac-common（包名 `com.nac.common`），适配 Spring Boot 4 / Jakarta 命名空间。
  - 落地：统一返回 `Result`/`ResultCode`、全局异常处理、`JwtUtil`、`RedisUtil`、`SqlInjectionChecker`（`#{}` 主防线 + 排序白名单）、`XssHttpServletRequestWrapper` + 过滤器、`AesCryptoUtil`(AES-256-GCM 随机 IV)、`HmacUtil`、`SensitiveUtil`、`PhoneCryptoUtil`（加密+脱敏）、限流 `@RateLimit`+切面（Redis+Lua 原子 INCR+EXPIRE）、幂等 `@Idempotent`+切面（SETNX）、角色 `@RequireRole`+切面、`UserContext`、`ThreadPoolConfig`（认证线程池 2×CPU）、`@OperationLog` AOP（敏感参数脱敏）、RedisKey 常量。
  - 密钥全部 `${ENV:默认占位}` 注入，无硬编码。
- **Acceptance Criteria Addressed**: AC-2, AC-6, AC-8
- **Test Requirements**:
  - `rule` TR-2.1: 存在全部安全能力类且编译通过；证据为源码路径列表。
  - `rule` TR-2.2: `AesCryptoUtil` 加解密自环通过（明文→密文→明文一致，IV 随机）；证据为单元测试输出。
  - `rule` TR-2.3: 限流切面在 Redis 可用时 Lua 原子计数生效；证据为单测或自环日志。
  - `rule` TR-2.4: 全模块 grep 无硬编码密钥（除 `${ENV:默认}` 占位）；证据为检索结果。

## Task 3: nac-gateway 网关
- **Status**: `completed`
- **Priority**: high
- **Depends On**: T2
- **Description**:
  - 基于 Spring Cloud Gateway（WebFlux）实现：白名单（精确匹配 `/api/auth/login`、`/api/auth/captcha/**`、`/api/portal/**` 仅 GET、`/free-auth/check`）、Bearer Token 鉴权（JWT 签名校验 + Redis 白名单 + 滑动续期 8h）、剥离并重新注入 `X-User-Id/X-User-Name/X-User-Role`、`SignGlobalFilter`（HMAC-SHA256(ts+method+path) ±300s）、`SecurityHeaderFilter`（CSP/X-Frame-Options: DENY/nosniff/no-referrer/Cache-Control: no-store）、分级限流。
  - 下游 `GatewayTrustFilter` 校验签名，直连端口返回 403。
- **Acceptance Criteria Addressed**: AC-3
- **Test Requirements**:
  - `rule` TR-3.1: 无 token 访问受保护接口返回 401；证据为 curl 响应。
  - `rule` TR-3.2: 伪造 `X-User-Role` 头被剥离（下游取不到伪造值）；证据为下游日志/接口断言。
  - `rule` TR-3.3: 直连 nac-auth 端口（无网关签名）返回 403；证据为 curl 响应。
  - `rule` TR-3.4: `/api/portal/**` 匿名 PUT 被拦截（仅 GET 放行）；证据为 curl 响应 401/403。

## Task 4: 数据库 DDL 与 MyBatis 基础
- **Status**: `completed`
- **Priority**: high
- **Depends On**: T1
- **Description**:
  - `sql/nac_ddl.sql`：`sys_user`、`sys_role`、`sys_user_role`、`sys_sms_record`、`sys_log`、`sys_portal_config`、`sys_phone_audit`、`sys_online_session`（预留）。无外键、字段注释完整、utf8mb4。
  - 种子数据：admin 账号（BCrypt）、基础角色。
  - MyBatis 配置（禁用 MP），各模块 mapper 接口 + XML，SQL 显式列名（无 `SELECT *`）、全部 `#{}`。
- **Acceptance Criteria Addressed**: AC-8
- **Test Requirements**:
  - `rule` TR-4.1: DDL 在 MySQL8 执行成功且无外键；证据为执行输出 + `SHOW CREATE TABLE`。
  - `rule` TR-4.2: 全工程 grep `SELECT *` 与 mapper `${` 拼接无命中；证据为检索结果。
  - `rule` TR-4.3: admin 账号密码 BCrypt 校验通过；证据为登录测试。

## Task 5: nac-auth 认证服务（账号密码登录）
- **Status**: `completed`
- **Priority**: high
- **Depends On**: T2, T4
- **Description**:
  - 管理账号登录：`/api/auth/login`（BCrypt 校验 + 图形验证码一次性校验 + 登录失败锁定）、`/api/auth/logout`（删 Redis token 即时失效）、`/api/auth/captcha`（图形验证码生成存 Redis、验证后销毁）。
  - `LoginAttemptService`：同 IP 5次/5min 锁15min、同账号 10次/5min 锁30min；Redis 故障降级允许认证。
  - token：UUID Opaque + Redis 白名单 8h TTL + 滑动续期；登录幂等锁（traceId 10s 窗口返回首次结果）。
- **Acceptance Criteria Addressed**: AC-4
- **Test Requirements**:
  - `rule` TR-5.1: 正确凭证+验证码登录返回 token 且 Redis 存 key；证据为接口响应 + redis-cli。
  - `rule` TR-5.2: 错误验证码拒绝；验证码验证后销毁（重放失败）；证据为接口测试。
  - `rule` TR-5.3: 同 IP 连续 5 次失败后锁定 15min（第 6 次拒绝）；证据为接口测试 + Redis key TTL。
  - `rule` TR-5.4: 登出后 token 立即失效（Redis key 删除）；证据为登出后再请求返回 401。

## Task 6: 短信网关抽象 + 三云实现 + 短信认证
- **Status**: `completed`
- **Priority**: high
- **Depends On**: T5
- **Description**:
  - `SmsGateway` 接口 + `SmsGatewayFactory`（按配置切换）；实现 `AliyunSmsGateway`、`TencentSmsGateway`、`HuaweiSmsGateway`，凭证 `${*_ACCESS_KEY_ID/SECRET}` 注入；未配置时可用 `MockSmsGateway` 保证闭环。
  - 短信验证码：后端生成、Redis 以 HMAC-SHA256 盲索引存储（不存明文手机号）、60s 冷却、每号每日 10 条、5 次失败失效；发送需图形验证码前置。
  - 手机号 AES-256-GCM 加密入库、脱敏展示；`SmsVerifyService` 日志脱敏。
- **Acceptance Criteria Addressed**: AC-5, AC-6
- **Test Requirements**:
  - `rule` TR-6.1: 未过图形验证码请求短信被拒；60s 内重复请求被冷却拒绝；证据为接口测试。
  - `rule` TR-6.2: 同一号当日第 11 次被拒；连续错 5 次验证码失效；证据为接口测试。
  - `rule` TR-6.3: Redis 中验证码 key 为 HMAC-SHA256 盲索引（无明文手机号）；证据为 redis-cli 查看。
  - `rule` TR-6.4: DB 中手机号为 AES 密文，接口列表脱敏，日志无明文；证据为 DB 查询 + 接口响应 + 日志。
  - `rule` TR-6.5: 三云网关实现类存在且凭证为环境变量注入（无硬编码）；证据为源码 grep。

## Task 7: Portal 认证后端
- **Status**: `completed`
- **Priority**: medium
- **Depends On**: T2, T5
- **Description**:
  - `/api/portal/config`（GET，匿名，返回认证后放通开关/访客VLAN/授权时长）、`/api/portal/auth`（提交认证）、Portal 配置管理写接口（admin）。
  - `portalToken` = `Base64(sessionId:ts)` + 随机 UUID（不可反推手机号）；配置开关 `phoneAuditEnabled` 预留。
  - 配置变更缓存失效（30s TTL 进程缓存）。
- **Acceptance Criteria Addressed**: AC-7
- **Test Requirements**:
  - `rule` TR-7.1: 匿名 GET `/api/portal/config` 返回配置；匿名 PUT 被网关拦截；证据为 curl 测试。
  - `rule` TR-7.2: 提交 Portal 认证返回 portalToken，解码后不含明文手机号；证据为接口响应 + token 解码。
  - `rule` TR-7.3: admin 可写 Portal 配置，非 admin 写被拒；证据为接口测试。

## Task 8: nac-log 操作日志服务
- **Status**: `completed`
- **Priority**: medium
- **Depends On**: T2, T4
- **Description**:
  - `@OperationLog` AOP 落库 `sys_log`（操作人、时间、IP、方法、参数脱敏、结果）；写操作自动记录。
  - 日志查询服务端分页（limit/offset + count）、按 `id DESC` 排序；未过滤 count 走 Redis 缓存 60s。
  - 日志删除接口不对外暴露（仅 DB 直连清除）。
- **Acceptance Criteria Addressed**: AC-2(操作日志)
- **Test Requirements**:
  - `rule` TR-8.1: 一次写操作产生一条 sys_log 且敏感参数为 `******`；证据为 DB 查询。
  - `rule` TR-8.2: 日志分页查询走 limit/offset 且按 id DESC；证据为 mapper SQL + 接口测试。

## Task 9: 前端 Vue3 工程
- **Status**: `completed`
- **Priority**: medium
- **Depends On**: T5, T6, T7（接口契约）
- **Description**:
  - `nac-system/frontend`：Vue3 + Vite + element-plus + axios + qs + echarts + pinia + vue-router4。
  - 页面：登录页（账号密码+图形验证码）、短信认证登录、主框架布局（侧边栏/顶栏）、Portal 认证页、Portal 配置页、操作日志页（骨架）。
  - 路由守卫（未登录跳登录）、`v-permission` 按钮级权限指令（非 admin 隐藏写按钮）、token 本地存储、axios 拦截器（qs 序列化、统一错误处理）、安全响应头配合。
- **Acceptance Criteria Addressed**: AC-10, AC-7
- **Test Requirements**:
  - `rule` TR-9.1: `npm run build` 成功；证据为构建输出。
  - `rule` TR-9.2: 未登录访问受保护路由跳转登录页；登录后写操作按钮对非 admin 隐藏；证据为页面操作。
  - `rubric` TR-9.3: 前端权限与越权防护完备度；scale 1-5；anchors 1/3/5 同 AC-10；threshold >= 4；证据为源码审查 + 页面操作。

## Task 10: 高并发异步化整合与端到端验证
- **Status**: `completed`
- **Priority**: medium
- **Depends On**: T3, T5, T6, T8
- **Description**:
  - 认证业务独立线程池（2×CPU）、Logback 全异步（8K 队列 neverBlock）、Redis 会话异步落库（pipeline）、Kafka 生产者守护线程预热 + max.block.ms=100、认证事件异步写 Kafka 消峰。
  - 降级：Redis 不可用时熔断（30s 跳过 + 后台半开探测）、登录失败锁定降级允许。
  - 端到端自环验证：账号密码登录 → 短信认证 → Portal 认证 → 操作日志记录，全链路跑通。
- **Acceptance Criteria Addressed**: AC-11
- **Test Requirements**:
  - `rule` TR-10.1: 认证链路配置独立线程池且日志 appender 为 AsyncAppender；证据为配置文件。
  - `rule` TR-10.2: 端到端自环（登录→短信→Portal→日志）全链路成功；证据为请求响应序列。
  - `rubric` TR-10.3: 认证链路高并发友好度；scale 1-5；anchors 1/3/5 同 AC-11；threshold >= 4；证据为源码审查。
