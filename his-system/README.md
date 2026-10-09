# HIS 医院信息系统

技术栈：JDK 1.8 + Spring Boot 2.0.9 + Spring Cloud Finchley + Spring Cloud Alibaba(Nacos) + Spring Security + MyBatis + MySQL + Redis + Kafka + OnlyOffice；前端 Vue2 + Element-UI + Axios + ECharts。

## 模块

| 服务 | 端口 | 说明 |
|------|------|------|
| his-gateway | 8080 | 网关：路由、JWT 鉴权、限流、HMAC 签名、安全响应头 |
| his-auth | 8081 | 认证：BCrypt 登录、图形验证码、防爆破 |
| his-patient | 8082 | 患者档案：AES-256-GCM 加密存储 + 脱敏展示 |
| his-outpatient | 8083 | 门诊：挂号（悲观锁防超卖、幂等）、处方开立 |
| his-pharmacy | 8084 | 药房：处方审核、发药（乐观锁扣减库存）、发药流水、统计 |
| his-emr | 8085 | 电子病历 + OnlyOffice 集成 |
| his-inpatient | 8086 | 住院：入院、床位分配、出院 |
| his-billing | 8087 | 缴费：处方缴费后置为待发药 |

## 一键启动

### 1. 启动中间件
```bash
cd his-system
docker-compose up -d        # MySQL / Redis / Nacos / Kafka
```
MySQL 容器首次启动会自动执行 `sql/his_ddl.sql` 初始化表和种子数据。

### 2. Maven 镜像配置
将 `settings.xml` 复制到 `~/.m2/settings.xml`（强制阿里云镜像）。

### 3. 启动后端
```bash
cd his-system/backend
mvn clean install -DskipTests
# 按顺序启动各服务（或在 IDE 中分别运行 *Application）
mvn -pl his-gateway spring-boot:run
mvn -pl his-auth spring-boot:run
# ... 其余服务同理
```

本地调试绕过网关直连时，设置环境变量 `GATEWAY_TRUST_ENABLED=false`。

### 4. 启动前端
```bash
cd his-system/frontend
npm install
npm run dev      # http://localhost:8088
```

## 测试账号
| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| doctor | admin123 | 医生 |
| pharmacist | admin123 | 药师 |
| nurse | admin123 | 护士 |

## 发药闭环演示
1. 用 `doctor` 登录，为患者开处方（状态 PENDING_PAY）
2. 调用 `POST /api/billing/pay/{处方ID}` 或前端缴费，处方变 PAID
3. 用 `pharmacist` 登录 → 药房管理 → 待发药处方 → 审核通过 → 确认发药
4. 库存扣减（乐观锁防超发）、发药流水落库、处方状态变 DISPENSED
5. 发药统计页查看 ECharts 图表

## 安全设计（等保合规）
- **认证**：JWT + Redis 白名单（登出即时失效）、滑动续期 8h
- **防爆破**：IP 5次/5min 锁 15min，账号 10次/5min 锁 30min；图形验证码一次性防重放
- **防刷限流**：Redis + Lua 原子计数（INCR+EXPIRE 原子），验证码/登录/接口分级限流
- **幂等**：Redis SETNX + SpEL key，挂号/发药/缴费防重复提交
- **并发一致**：挂号悲观锁防号源超卖；发药乐观锁（version CAS）防库存超发
- **SQL 注入**：MyBatis 全部 `#{}` 预编译；动态排序字段白名单校验
- **XSS**：后端 XssFilter 全局转义 + 前端 Vue 插值默认转义；安全响应头（CSP/X-Frame-Options 等）
- **敏感数据**：身份证/手机号 AES-256-GCM 加密存储，列表脱敏展示
- **越权防护**：网关剥离客户端伪造头重新注入；前端 v-permission 按钮级控制；写接口需登录
- **网关信任**：HMAC-SHA256(ts+method+path) ±300s 签名，直连服务端口返回 403
- **密钥**：JWT/加密/内部通信密钥全部环境变量注入，无硬编码

## 密钥环境变量（生产必须修改）
- `JWT_SECRET`、`PHONE_CRYPTO_PASSWORD`、`NAC_INTERNAL_SECRET`、`ONLYOFFICE_SECRET`
