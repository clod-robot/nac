# NAC 准入控制系统（一期）- 产品需求文档

## Overview
- **Summary**: 在现有 `his-system` 微服务安全脚手架基础上，新建 `nac-system` 工程，升级技术栈至 JDK 27 + Spring Boot 4.1.x + Spring Cloud + Spring Security + Netty + Vue3，搭建 NAC（网络准入控制）系统一期：工程骨架 + 安全基础设施 + 三云短信认证登录闭环 + Portal 认证界面。
- **Purpose**: 为后续 RADIUS/PEAP/EAP-TLS 协议实现、认证计费、上下线、SSO 等核心能力提供稳固、安全、高并发的工程底座，并率先交付可端到端运行的短信认证与 Portal 认证能力。
- **Target Users**: 网络运维管理员（管理后台）、接入终端用户（Portal 认证页）。

## Goals
- 搭建 `nac-system` 多模块 Maven 工程，技术栈强制 JDK 27 + Spring Boot 4.1.x，Maven 强制阿里云镜像。
- 迁移并升级 `his-system` 的安全脚手架（his-common/gateway/auth）为 nac 安全底座，覆盖等保合规要点：防 SQL 注入、XSS、越权、敏感信息加密、密钥环境变量注入、防刷、幂等、原子化、高并发。
- 实现短信认证登录闭环：抽象短信网关并落地阿里云/腾讯云/华为云三家实现（可配置切换），含图形验证码前置、60s 冷却、每日限额、失败次数失效等防刷机制。
- 实现 Portal 认证后端接口与前端界面（Portal 认证页、Portal 配置管理页）。
- 前端升级为 Vue3 + element-plus + axios + qs + echarts，含登录页、主框架、权限按钮级控制。

## Non-Goals（一期不交付，后续迭代）
- RADIUS/PEAP/EAP-TLS 协议的完整认证计费实现（一期仅搭建 `nac-radius` 模块骨架与 Netty UDP 端口监听，协议处理留待二期）。
- 802.1X 客户端、CoA/DM 动态授权、计费完整链路。
- 与真实交换机的联调（一期提供接口契约与本地自环测试）。
- 管理后台全量业务页面（设备管理、上网记录、在线终端等）一期仅搭骨架与权限框架，业务页面后续迭代。

## Background & Context
- 现有 `his-system/backend` 是 JDK 1.8 + Spring Boot 2.0.9 + Spring Cloud Finchley 的微服务脚手架，安全能力齐全（网关鉴权、Redis+Lua 限流、SETNX 幂等、XSS 过滤、SQL 注入白名单、AES-256-GCM 加密、HMAC 签名、越权防护），可作为 nac 底座复用。
- 现有 `his-system/frontend` 是 Vue2 + Element-UI，需升级为 Vue3 + element-plus。
- 项目记忆中沉淀了大量 NAC 领域约束（RADIUS 签名顺序、PEAP 配置、手机号加密脱敏、Redis 盲索引、网关签名、防刷规则等），一期安全基建须遵循这些约束。
- 技术风险：Spring Boot 4.1.x 官方支持 Java 17–26，JDK 27 无官方明确支持但高版本 JDK 向下兼容，需实测；Spring Cloud Alibaba 对 Spring Boot 4.x 的适配可能滞后，必要时 Nacos 走原生 client 或保留 discovery 可选。

## Functional Requirements
- **FR-1 工程骨架**: 新建 `nac-system` 多模块 Maven 父工程（JDK 27 / Spring Boot 4.1.x / Spring Cloud 2025.x），模块含 `nac-common`、`nac-gateway`、`nac-auth`、`nac-user`、`nac-radius`、`nac-log`，父 pom 与各子模块 pom 强制阿里云镜像仓库、禁用 snapshots。
- **FR-2 安全底座 nac-common**: 迁移升级统一返回 Result、统一异常处理、JWT 工具、Redis 工具、SQL 注入检查、XSS 过滤、AES-256-GCM 加解密、HMAC 工具、敏感脱敏、限流注解+切面（Redis+Lua 原子）、幂等注解+切面（SETNX）、角色注解+切面（防越权）、用户上下文、线程池配置、操作日志 AOP。
- **FR-3 网关 nac-gateway**: 路由、白名单（仅 `/api/auth/login`、`/api/auth/captcha/**`、`/api/portal/**` 精确 GET、`/free-auth/check`）、Bearer Token 鉴权（JWT 签名 + Redis 白名单 + 滑动续期 8h）、剥离并重新注入 `X-User-*` 头、HMAC-SHA256 内部签名（±300s）、安全响应头（CSP/X-Frame-Options/nosniff 等）、分级限流。
- **FR-4 认证服务 nac-auth**: 管理账号登录（BCrypt 校验密码 + 图形验证码一次性 + 登录失败锁定：IP 5次/5min 锁15min、账号 10次/5min 锁30min）、登出（Redis 删 key 即时失效）、图形验证码生成与校验。
- **FR-5 短信认证**: 抽象 `SmsGateway` 接口，实现阿里云/腾讯云/华为云三家（凭证走环境变量注入，不明文），短信验证码后端生成、Redis 盲索引（HMAC-SHA256）存储、60s 冷却、每号每日 10 条、5 次失败失效；手机号 AES-256-GCM 加密存储、脱敏展示。
- **FR-6 Portal 认证**: Portal 认证页接口（获取配置、提交认证）、Portal 配置管理（认证后放通开关、访客 VLAN、授权时长）、`portalToken` 使用 `Base64(sessionId:ts)` + 随机 UUID（不可反推手机号）。
- **FR-7 前端 Vue3**: Vue3 + element-plus + axios + qs + echarts 工程，含登录页（账号密码+图形验证码）、短信认证登录、主框架布局、路由守卫、权限指令（按钮级）、Portal 认证页、Portal 配置页。
- **FR-8 数据库 DDL**: `sql/nac_ddl.sql` 含 `sys_user`、`sys_role`、`sys_sms_record`、`sys_log`、`sys_portal_config`、`sys_phone_audit` 等表，无外键、字段注释完整、不使用 `SELECT *`、MyBatis 全部 `#{}` 预编译。
- **FR-9 操作日志**: 管理后台写操作通过 AOP 记录到 `sys_log`（敏感参数脱敏为 `******`），日志查询服务端分页（limit/offset）、按 id DESC 排序。

## Non-Functional Requirements
- **NFR-1 安全合规（等保）**: 防 SQL 注入（MyBatis `#{}` + 动态排序白名单）、防 XSS（后端全局转义 + 安全响应头）、防越权（网关头剥离 + 角色切面 + 前端按钮级）、敏感数据 AES-256-GCM 加密存储 + 脱敏展示、密钥全部环境变量注入无硬编码、密码 BCrypt。
- **NFR-2 高并发多线程**: 认证业务独立线程池（默认 2×CPU）、日志全异步（Logback 8K 队列 neverBlock）、Kafka 生产者守护线程预热 + max.block.ms=100、Redis 会话异步落库。
- **NFR-3 消峰填谷**: 认证事件异步写 Kafka 再落库，避免阻塞主链路。
- **NFR-4 幂等防刷**: 限流 Redis+Lua 原子（INCR+EXPIRE 单事务）、验证码一次性销毁防重放、登录失败共享锁定、写接口幂等切面。
- **NFR-5 严格原子化**: 限流计数 Lua 原子、关键写操作数据库事务、缓存更新并发安全。
- **NFR-6 构建**: Maven 强制阿里云镜像（父 pom repositories + 项目 settings.xml 双保险）、禁用 snapshots。

## Constraints
- **Technical**: JDK 27、Spring Boot 4.1.x、Spring Cloud 2025.x、Spring Security、Netty、Redis、MySQL 8、Kafka(KRaft)、MyBatis（禁用 MyBatis-Plus）、Vue3、element-plus；SQL 禁止 `SELECT *`；禁止外键。
- **Business**: 仅 `/api/auth/login`、`/api/auth/captcha/**`、`/api/portal/**`(精确 GET)、`/free-auth/check` 白名单；其余需有效 token；POST/PUT/DELETE/PATCH 需 admin 角色（`/api/auth/logout` 例外）。
- **Dependencies**: MySQL、Redis、Nacos、Kafka 中间件（docker-compose 提供）；三云短信需对应 AccessKey（环境变量注入）。
- **密钥**: `JWT_SECRET`、`PHONE_CRYPTO_PASSWORD`、`NAC_INTERNAL_SECRET`、各云短信 `*_ACCESS_KEY_ID/SECRET` 全部环境变量注入，默认值仅本地占位。

## Assumptions
- 本地开发不直连中间件时通过环境变量/配置开关降级（Redis 不可用时限流降级放行、登录失败锁定降级允许认证）。
- 三云短信网关一期以可切换的抽象实现，未配置凭证的网关返回明确错误，不影响登录主流程的账号密码方式。
- Spring Cloud Alibaba 若未适配 Spring Boot 4.1，Nacos 服务发现降级为可选（不阻塞启动）。

## Open Questions
- [ ] Spring Cloud Alibaba 是否有适配 Spring Boot 4.1 的版本？若无，Nacos discovery 走原生 client 或暂关闭。
- [ ] 一期是否需要对接真实短信网关实测，还是先用 mock 实现保证闭环可运行？（默认先提供三家真实 SDK 实现 + 可切换 mock）

## Acceptance Criteria

### AC-1: 工程骨架与技术栈版本
- **Type**: `rule`
- **Given**: 已创建 `nac-system` 工程
- **When**: 检查父 pom 与子模块 pom
- **Then**: 父 pom 指定 `java.version=27`、Spring Boot 4.1.x、Spring Cloud 2025.x；含 nac-common/nac-gateway/nac-auth/nac-user/nac-radius/nac-log 模块；repositories 仅阿里云且 snapshots disabled
- **Pass Condition**: `mvn -q -DskipTests package` 在配置阿里云镜像后可成功编译所有模块（或至少 common/gateway/auth 三个核心模块编译通过）
- **Evidence**: 构建命令输出 + pom 文件内容

### AC-2: 安全底座能力完整
- **Type**: `rule`
- **Given**: nac-common 模块已实现
- **When**: 检查安全能力
- **Then**: 存在 JWT/Redis会话、限流(Lua原子)、幂等(SETNX)、SQL注入检查、XSS过滤、AES-256-GCM、HMAC、脱敏、角色切面、统一异常、统一返回、操作日志AOP
- **Pass Condition**: 上述能力类均存在且有单元测试或自环验证通过
- **Evidence**: 源码路径 + 测试输出

### AC-3: 网关鉴权与越权防护
- **Type**: `rule`
- **Given**: nac-gateway 已启动
- **When**: 1) 无 token 访问受保护接口 2) 携带伪造 `X-User-Role: admin` 头但无有效 token 3) 直连后端服务端口（绕过网关）
- **Then**: 1) 返回 401 2) 返回 401（头被剥离） 3) 返回 403（HMAC 签名校验失败）
- **Pass Condition**: 三种场景 HTTP 状态码符合预期
- **Evidence**: curl/接口测试请求响应

### AC-4: 管理账号登录安全
- **Type**: `rule`
- **Given**: nac-auth 已启动且存在 admin 账号
- **When**: 1) 正确账号密码+正确验证码登录 2) 错误验证码 3) 连续 5 次同 IP 失败
- **Then**: 1) 返回 token 并存入 Redis 2) 拒绝 3) 该 IP 锁定 15min
- **Pass Condition**: 登录成功返回 token；失败锁定生效
- **Evidence**: 接口测试 + Redis key 检查

### AC-5: 短信认证防刷闭环
- **Type**: `rule`
- **Given**: 短信网关已配置（或 mock）
- **When**: 1) 请求发送短信验证码（无图形验证码前置）2) 60s 内重复请求 3) 同一号当日第 11 次 4) 验证码连续错误 5 次
- **Then**: 1) 需先通过图形验证码 2) 被冷却拒绝 3) 超限拒绝 4) 验证码失效
- **Pass Condition**: 防刷规则全部生效，Redis 中手机号以 HMAC-SHA256 盲索引存储
- **Evidence**: 接口测试 + Redis key 内容检查

### AC-6: 敏感数据加密脱敏
- **Type**: `rule`
- **Given**: 手机号已存储
- **When**: 1) 查询数据库原始值 2) 接口列表展示 3) 日志输出
- **Then**: 1) AES-256-GCM 密文 2) 脱敏（前3后4） 3) 脱敏/无明文
- **Pass Condition**: 库内无明文手机号，展示与日志均脱敏
- **Evidence**: DB 查询 + 接口响应 + 日志文件

### AC-7: Portal 认证接口与界面
- **Type**: `rule`
- **Given**: Portal 配置已开启
- **When**: 访问 Portal 认证页并提交认证
- **Then**: 返回 portalToken（不可反推手机号），认证后放通配置生效
- **Pass Condition**: Portal 页可访问、提交认证成功、portalToken 格式为 Base64(sessionId:ts) 且含随机 UUID
- **Evidence**: 前端页面截图 + 接口响应

### AC-8: SQL 注入防护与无 SELECT *
- **Type**: `rule`
- **Given**: 所有 Mapper XML 与注解
- **When**: 全文检索 `SELECT *` 与 `${` 拼接
- **Then**: 无 `SELECT *`；动态参数均用 `#{}`；排序字段经白名单校验
- **Pass Condition**: grep 无命中违规模式
- **Evidence**: 检索结果

### AC-9: Maven 强制阿里云镜像
- **Type**: `rule`
- **Given**: 构建环境
- **When**: 执行 `mvn dependency:resolve` 不配置其他镜像
- **Then**: 所有依赖从阿里云仓库解析
- **Pass Condition**: 父 pom repositories 仅阿里云 + 项目 settings.xml mirror 强制阿里云
- **Evidence**: pom 与 settings.xml 内容

### AC-10: 前端 Vue3 权限控制
- **Type**: `rubric`
- **Dimension**: 前端权限与越权防护完备度
- **Scale**: 1-5
- **Anchors**: 1 = Vue2 残留或无权限控制；3 = Vue3 升级完成且有路由守卫；5 = Vue3 + element-plus 完整、路由守卫 + 按钮级权限指令、token 本地存储、写操作按钮对非 admin 隐藏
- **Pass Threshold**: >= 4
- **Evidence**: 前端源码 + 页面操作验证

### AC-11: 高并发与异步化
- **Type**: `rubric`
- **Dimension**: 认证链路高并发友好度
- **Scale**: 1-5
- **Anchors**: 1 = 全同步阻塞；3 = 有线程池但日志/落库阻塞主链路；5 = 认证独立线程池、日志全异步、会话异步落库、Kafka 消峰、降级熔断齐全
- **Pass Threshold**: >= 4
- **Evidence**: 线程池配置 + 异步组件源码
