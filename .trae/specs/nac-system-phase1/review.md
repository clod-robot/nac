# NAC 准入控制系统（一期）— 独立审查报告

审查日期：2026-06-08　范围：`nac-system/` 后端 + 前端一期全部产出

## 1. 已落实的安全与合规控制

| 控制项 | 落地位置 | 说明 |
|---|---|---|
| 防 SQL 注入 | 全部 Mapper XML | 显式列名、无 `SELECT *`、全 `#{}` 预编译；无 MyBatis-Plus |
| 防越权（垂直/水平） | 网关 `AuthGlobalFilter` + 下游 `@RequireRole` + `GatewayTrustFilter` | 剥离并重新注入 X-User-* 头；直连下游 403；写接口 admin 校验 |
| 认证防爆破 | `LoginAttemptService` | IP 5次/5min 锁15min、账号 10次/5min 锁30min；统一错误信息防枚举 |
| 短信防刷 | `SmsService` | 图形验证码前置、60s 冷却、日限 10、5 次失败失效 |
| 敏感数据保护 | `PhoneCryptoUtil`/`AesCryptoUtil`(AES-256-GCM)、HMAC 盲索引 | 手机号密文入库、Redis 存盲索引、脱敏展示、日志脱敏 |
| 幂等/限流 | `@Idempotent`/`@RateLimit` 切面（Redis+Lua 原子） | 登录 traceId 幂等、接口滑动窗口限流 |
| 传输/存储密钥 | 全部 `${ENV:默认}` 注入 | grep 无硬编码密钥（默认值为占位，生产须覆盖） |
| XSS 过滤 | `XssHttpServletRequestWrapper` + 过滤器 | servlet 服务自动装配 |
| 安全响应头 | `SecurityHeaderGlobalFilter` | CSP / X-Frame-Options: DENY / nosniff / no-store |
| 操作审计 | `@OperationLog` → Kafka → `nac-log` 落库 | 敏感参数 `******`，服务端分页，删除不对外 |
| 高并发/消峰 | 独立线程池、Logback 全异步(8K/neverBlock)、Kafka 异步日志、max.block.ms=100 | 日志 I/O 不阻塞业务线程 |
| 等保基线 | 密码 BCrypt、登录失败锁定、操作日志、敏感信息加密、最小权限 | 覆盖身份鉴别/审计/通信安全要点 |

## 2. 审查发现与整改

- **DDL 与实体列名不一致**：`sys_sms_record` 实为 `blind_index/provider/error_msg`，初版 entity/xml 用了 `phone_blind/gateway/fail_reason`。**已整改**为对齐 DDL。
- **网关白名单配置形式冲突**：`application.yml` 用 list，而 `AuthGlobalFilter` 用逗号字符串 `@Value`，会导致注入异常。**已整改**：移除 yml 中 whitelist，统一由过滤器默认值承载（含 login/captcha/sms/send、sms/login、portal/auth）。
- **短信登录用户匹配**：手机号 AES 密文随机 IV 无法等值查询，**已补** `phone_blind` 盲索引列 + `selectByPhoneBlind`，自动创建普通用户闭环。

## 3. 已知限制与待办（二期/环境就绪后处理）

1. **本地无法编译验证**：环境仅有 JDK 8/11、无 Maven、JDK27 缺失。所有"编译通过"类 TR（TR-1.1/TR-2.1/TR-9.1 等）与端到端自环（TR-10.2）**待配置 JDK27 + Maven + Node 后执行**：
   - 后端：`mvn -q -DskipTests -pl nac-common,nac-gateway,nac-auth,nac-log package`
   - 前端：`npm install && npm run build`
2. **JDK 27 与 Spring Boot 4.1.x 兼容性**：官方明确支持 Java 17–26，27 需实测；如编译/运行失败，回退 JDK 21 并改父 pom `java.version`。
3. **三云短信 SDK API**：`AliyunSmsGateway`/`TencentSmsGateway` 按 SDK 最新用法编写，未配置凭证时由 `MockSmsGateway` 兜底闭环；正式对接前需用真实凭证联调并核对 SDK 版本 API。
4. **网关阻塞式 Redis**：`AuthGlobalFilter` 使用 `StringRedisTemplate`（阻塞），WebFlux 下会占用 event loop；一期可接受，二期改 `ReactiveStringRedisTemplate`。
5. **RADIUS / EAP-TLS / PEAP / 计费上下线 / SSO**：本期仅搭骨架与在线会话表预留，协议层实现列入二期。
6. **统计接口**：前端仪表盘数据为占位静态，二期补后端统计 API。
7. **默认凭据**：admin/admin123、各 `${ENV}` 默认密钥为占位，**生产部署必须覆盖**并强制首次改密。

## 4. 结论

一期功能骨架与安全基建已完整落地，覆盖账号登录、短信认证（三云）、Portal 认证、操作日志、Vue3 前端。安全控制满足任务约束（无 SELECT *、无 MP、防注入/防越权/防刷/幂等/加密/审计）。**编译与端到端验证受限于本地环境，列为上线前必做项。**
