-- NAC 准入控制系统 DDL（MySQL 8，utf8mb4，无外键，关系通过业务字段维护）
-- 由 docker-compose 首次启动自动执行；手工执行：mysql -uroot -p < nac_ddl.sql

CREATE DATABASE IF NOT EXISTS nac DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE nac;

-- 管理用户
CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64)  NOT NULL COMMENT '登录账号',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    real_name     VARCHAR(64)           DEFAULT NULL COMMENT '姓名',
    phone_cipher  VARCHAR(255)          DEFAULT NULL COMMENT '手机号 AES-256-GCM 密文',
    phone_blind   VARCHAR(64)           DEFAULT NULL COMMENT '手机号 HMAC-SHA256 盲索引（用于等值查询）',
    radius_password_cipher VARCHAR(255)  DEFAULT NULL COMMENT 'RADIUS 专用可逆口令 AES-256-GCM 密文（CHAP/PAP 校验需明文）',
    role_code     VARCHAR(32)  NOT NULL DEFAULT 'user' COMMENT '角色编码（业务字段，无外键）',
    dept          VARCHAR(64)           DEFAULT NULL COMMENT '归属部门',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
    terminal_limit INT         NOT NULL DEFAULT 5 COMMENT '终端数上限（每账号允许接入终端数）',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_phone_blind (phone_blind)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='管理用户';

-- 角色
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    role_code   VARCHAR(32) NOT NULL COMMENT '角色编码',
    role_name   VARCHAR(64) NOT NULL COMMENT '角色名称',
    description VARCHAR(255)         DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色';

-- 短信发送记录
CREATE TABLE IF NOT EXISTS sys_sms_record (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    phone_cipher VARCHAR(255) NOT NULL COMMENT '手机号密文',
    blind_index  VARCHAR(64)  NOT NULL COMMENT '手机号 HMAC-SHA256 盲索引',
    provider     VARCHAR(16)           DEFAULT NULL COMMENT '网关 aliyun/tencent/huawei/mock',
    biz_type     VARCHAR(32)           DEFAULT NULL COMMENT '业务类型',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0待发 1成功 2失败',
    error_msg    VARCHAR(255)          DEFAULT NULL,
    ip           VARCHAR(64)           DEFAULT NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_blind_time (blind_index, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='短信发送记录';

-- 操作日志
CREATE TABLE IF NOT EXISTS sys_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    operator    VARCHAR(64)           DEFAULT NULL,
    operator_id BIGINT                DEFAULT NULL,
    role        VARCHAR(32)           DEFAULT NULL,
    ip          VARCHAR(64)           DEFAULT NULL,
    method      VARCHAR(128)          DEFAULT NULL,
    operation   VARCHAR(255)          DEFAULT NULL,
    params      TEXT                  DEFAULT NULL COMMENT '参数（敏感字段已脱敏）',
    status      VARCHAR(32)           DEFAULT NULL,
    cost_ms     INT                   DEFAULT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志';

-- Portal 配置（key-value）
CREATE TABLE IF NOT EXISTS sys_portal_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    config_key   VARCHAR(64)  NOT NULL,
    config_value VARCHAR(255)          DEFAULT NULL,
    remark       VARCHAR(255)          DEFAULT NULL,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='Portal 配置';

-- 手机号查看审计
CREATE TABLE IF NOT EXISTS sys_phone_audit (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    operator     VARCHAR(64)           DEFAULT NULL,
    ip           VARCHAR(64)           DEFAULT NULL,
    phone_cipher VARCHAR(255)          DEFAULT NULL COMMENT '被查看的手机号密文',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='手机号查看审计';

-- 在线会话（预留，二期 RADIUS 计费驱动）
CREATE TABLE IF NOT EXISTS sys_online_session (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    acct_session_id VARCHAR(64) NOT NULL COMMENT '计费会话ID',
    username_mask VARCHAR(64)           DEFAULT NULL COMMENT '脱敏用户名',
    mac           VARCHAR(32)           DEFAULT NULL,
    nas_ip        VARCHAR(64)           DEFAULT NULL,
    framed_ip     VARCHAR(64)           DEFAULT NULL,
    vlan_id       INT                   DEFAULT NULL,
    status        TINYINT      NOT NULL DEFAULT 1,
    start_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session (acct_session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='在线会话';

-- RADIUS 计费历史明细（等保审计留痕，append-only）
CREATE TABLE IF NOT EXISTS radius_acct_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    acct_session_id VARCHAR(64)           DEFAULT NULL COMMENT '计费会话ID',
    username_mask   VARCHAR(64)           DEFAULT NULL COMMENT '脱敏用户名',
    mac             VARCHAR(32)           DEFAULT NULL COMMENT '终端MAC',
    nas_ip          VARCHAR(64)           DEFAULT NULL COMMENT 'NAS设备IP',
    status_type     TINYINT               DEFAULT NULL COMMENT '1 Start 2 Stop 3 Interim',
    session_time    BIGINT                DEFAULT NULL COMMENT '会话时长(秒)',
    input_octets    BIGINT                DEFAULT NULL COMMENT '入方向字节',
    output_octets   BIGINT                DEFAULT NULL COMMENT '出方向字节',
    terminate_cause INT                   DEFAULT NULL COMMENT '计费终止原因',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_session (acct_session_id),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='RADIUS 计费历史';

-- 认证日志（登录 / Portal / RADIUS 全量认证留痕，append-only，等保审计）
CREATE TABLE IF NOT EXISTS sys_auth_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    auth_type   VARCHAR(20)  NOT NULL COMMENT 'login/portal/radius/sms',
    username    VARCHAR(64)           DEFAULT NULL COMMENT '账号或脱敏账号',
    phone       VARCHAR(32)           DEFAULT NULL COMMENT '脱敏手机号',
    mac         VARCHAR(32)           DEFAULT NULL COMMENT '终端MAC',
    ip          VARCHAR(64)           DEFAULT NULL COMMENT '客户端IP',
    nas_ip      VARCHAR(64)           DEFAULT NULL COMMENT 'NAS设备IP',
    result      TINYINT      NOT NULL COMMENT '1成功 0失败',
    message     VARCHAR(255)          DEFAULT NULL COMMENT '结果说明',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_auth_type (auth_type),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='认证日志';

-- 角色种子数据（admin 账号由 nac-auth 启动初始化器保证存在，密码 BCrypt）
INSERT INTO sys_role (role_code, role_name, description)
SELECT 'admin', '超级管理员', '系统管理员'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'admin');
INSERT INTO sys_role (role_code, role_name, description)
SELECT 'user', '普通用户', '只读用户'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'user');

-- Portal 默认配置
INSERT INTO sys_portal_config (config_key, config_value, remark)
SELECT 'mabEnabled', 'true', '认证后放通开关'
WHERE NOT EXISTS (SELECT 1 FROM sys_portal_config WHERE config_key = 'mabEnabled');
INSERT INTO sys_portal_config (config_key, config_value, remark)
SELECT 'visitorVlan', '100', '访客VLAN'
WHERE NOT EXISTS (SELECT 1 FROM sys_portal_config WHERE config_key = 'visitorVlan');
INSERT INTO sys_portal_config (config_key, config_value, remark)
SELECT 'grantMinutes', '480', '授权时长(分钟)'
WHERE NOT EXISTS (SELECT 1 FROM sys_portal_config WHERE config_key = 'grantMinutes');
INSERT INTO sys_portal_config (config_key, config_value, remark)
SELECT 'phoneAuditEnabled', 'false', '手机号审查模式'
WHERE NOT EXISTS (SELECT 1 FROM sys_portal_config WHERE config_key = 'phoneAuditEnabled');
