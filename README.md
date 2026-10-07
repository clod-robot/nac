# NAC 网络准入控制系统

基于微服务架构的企业网络准入控制（NAC）系统，支持 **Portal 认证、短信认证、802.1X（RADIUS PAP/CHAP）认证**，统一管理后台、操作审计与安全合规。

## 技术栈与组件版本

### 运行/构建环境
| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | **21 LTS**（Temurin 21.0.12） | 编译/运行基线，最稳定 |
| Maven | 3.9.x | 构建工具 |
| Node.js | 18+（建议 20/22） | 前端构建 |

### 后端（backend/，Spring Boot 微服务）
| 组件 | 版本 |
|---|---|
| Spring Boot | **3.3.5** |
| Spring Cloud | **2023.0.3**（Leyton） |
| Spring Cloud Gateway（WebFlux） | 随 Spring Cloud 2023 |
| MyBatis Spring Boot Starter | 3.0.4 |
| Druid（druid-spring-boot-3-starter） | 1.2.23 |
| MySQL Connector/J | 8.4.0 |
| Netty | **4.1.138.Final**（RADIUS UDP，CVE 已修复） |
| JJWT | 0.12.6 |
| Hutool | 5.8.32 |
| Apache Commons Lang3 | 3.17.0 |
| Fastjson2 | 2.0.53 |
| 阿里云短信 dysmsapi20170525 | 2.0.24 |
| 腾讯云短信 tencentcloud-sdk-java-sms | 3.2.18 |
| Lombok | 1.18.34 |
| Spring Security Crypto | 随 Spring Boot（BCrypt） |
| Spring Data Redis / Spring Kafka | 随 Spring Boot |

### 前端（frontend/，Vue3 + Vite）
| 组件 | 版本 |
|---|---|
| Vue | 3.5.8 |
| Vite | 5.4.6 |
| Element Plus | 2.8.4 |
| @element-plus/icons-vue | 2.3.1 |
| Vue Router | 4.4.3 |
| Pinia | 2.2.2 |
| Axios | 1.7.7 |
| ECharts | 5.5.1 |
| qs | 6.13.0 |

### 中间件
| 组件 | 建议版本 |
|---|---|
| MySQL | 8.x |
| Redis | 6.x / 7.x |
| Kafka | 3.x |

## 模块结构
```
nac-system/
├── backend/                 # 后端微服务（Maven 多模块）
│   ├── nac-common/          # 公共安全底座（JWT/Redis/限流/幂等/加解密/XSS/越权防护/审计）
│   ├── nac-gateway/         # 网关（鉴权、签名、安全头注入）  :8080
│   ├── nac-auth/            # 登录、短信、Portal、RADIUS 口令管理  :8081
│   ├── nac-user/            # 用户/终端管理  :8082
│   ├── nac-radius/          # RADIUS 认证计费（Netty UDP 1812/1813）  :8083
│   └── nac-log/             # 操作日志审计（Kafka 消费落库）  :8084
├── frontend/                # 前端（Vue3 + Element Plus）
├── sql/                     # 数据库 DDL（nac_ddl.sql）
├── docker-compose.yml       # 本地中间件编排
└── settings.xml             # Maven 阿里云镜像配置
```

## 快速开始

### 1. 准备中间件
```bash
docker compose up -d        # 启动 MySQL / Redis / Kafka
mysql -uroot -p < sql/nac_ddl.sql
```

### 2. 后端编译与启动
```bash
cd backend
mvn -DskipTests clean package
# 依次启动各服务（或 java -jar 各模块 target/*.jar）
```
服务端口：gateway 8080、auth 8081、user 8082、radius 8083、log 8084。

### 3. 前端
```bash
cd frontend
npm install
npm run dev      # 开发
npm run build    # 构建产物在 dist/
```

## 安全合规
- 口令 BCrypt 存储；手机号 AES-256-GCM 加密 + HMAC 盲索引；RADIUS 口令 AES-256-GCM 可逆存储
- SQL 全量 `#{}` 预编译、禁止 `SELECT *`；网关统一鉴权防越权（`@RequireRole`）
- Redis + Lua 限流/幂等防爆破；操作全量审计（Kafka → 落库，等保留痕）
- 密钥/共享密钥均经环境变量注入，无硬编码

## 开源协议
本项目采用 **GNU AGPL-3.0** 开源协议（见 [LICENSE](./LICENSE)）。
- 任何人可自由下载、安装、使用，欢迎提交 Issue / Bug / 改进建议。
- Copyleft：对本项目的修改或基于它的衍生作品，须以相同协议公开源码；即使仅通过网络提供服务（SaaS），也须向用户提供修改后的对应源码。

## 默认配置（环境变量覆盖）
`MYSQL_HOST/PORT/DB/USER/PASSWORD`、`REDIS_HOST/PORT`、`KAFKA_SERVERS`、`RADIUS_SHARED_SECRET`、`NAC_INTERNAL_SECRET`、`PHONE_CRYPTO_PASSWORD`。

> 初始管理员账号由 `nac-auth` 启动初始化器自动创建（密码 BCrypt）。
