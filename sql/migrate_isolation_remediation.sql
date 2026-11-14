-- ============================================================
-- 幂等迁移：应用校验弹窗自定义 + 隔离区/修复区 + 补丁库/病毒库联动
-- 内容：
--   1) sys_software_policy 增加校验模式与策略级弹窗覆盖
--   2) sys_popup_template 全局弹窗模板
--   3) sys_isolation_config 隔离区/修复区下发配置（ACL / VLAN）
--   4) sys_isolation_record 隔离记录
--   5) sys_patch_lib / sys_av_lib 补丁库、病毒库
--   6) sys_remediation_rule / sys_remediation_task 修复规则与任务
-- 适用：MySQL 5.7+ / 8.0，可重复执行。
-- ============================================================

DROP PROCEDURE IF EXISTS nac_add_column;
DELIMITER $$
CREATE PROCEDURE nac_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl VARCHAR(512))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
        SET @s = CONCAT('ALTER TABLE ', tbl, ' ADD COLUMN ', ddl);
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL nac_add_column('sys_software_policy', 'check_mode',
    'check_mode VARCHAR(16) NOT NULL DEFAULT ''REALTIME'' COMMENT ''REALTIME=每次心跳校验 / ONCE=认证时+首次心跳通过后不再校验''');
CALL nac_add_column('sys_software_policy', 'popup_title', 'popup_title VARCHAR(128) DEFAULT NULL COMMENT ''策略级弹窗标题（空=用全局模板）''');
CALL nac_add_column('sys_software_policy', 'popup_content', 'popup_content VARCHAR(2000) DEFAULT NULL COMMENT ''策略级弹窗正文（空=用全局模板）''');
CALL nac_add_column('sys_software_policy', 'popup_action_text', 'popup_action_text VARCHAR(64) DEFAULT NULL COMMENT ''弹窗按钮文案''');
CALL nac_add_column('sys_software_policy', 'popup_action_url', 'popup_action_url VARCHAR(512) DEFAULT NULL COMMENT ''弹窗跳转地址（下载/修复引导）''');

DROP PROCEDURE IF EXISTS nac_add_column;

-- 全局弹窗模板（单行 id=1）：策略未覆盖时使用
CREATE TABLE IF NOT EXISTS sys_popup_template (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    title        VARCHAR(128)  NOT NULL DEFAULT '终端安全合规提醒',
    content      VARCHAR(2000) NOT NULL DEFAULT '检测到您的终端未运行必需的安全软件：{software}。请开启后重试，或点击下方按钮获取安装指引。',
    action_text  VARCHAR(64)            DEFAULT '立即处理',
    action_url   VARCHAR(512)           DEFAULT NULL,
    delay_sec    INT           NOT NULL DEFAULT 30 COMMENT '允许暂缓的秒数（0=不允许暂缓）',
    allow_delay  TINYINT       NOT NULL DEFAULT 1 COMMENT '1=允许暂缓 0=必须立即处理',
    enabled      TINYINT       NOT NULL DEFAULT 1,
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='终端合规弹窗全局模板';

INSERT INTO sys_popup_template (id, title, content, action_text, action_url, delay_sec, allow_delay, enabled)
SELECT 1, '终端安全合规提醒',
       '检测到您的终端未运行必需的安全软件：{software}。请开启后重试，或点击下方按钮获取安装指引。',
       '立即处理', NULL, 30, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_popup_template WHERE id = 1);

-- 隔离区/修复区下发配置（单行 id=1）
CREATE TABLE IF NOT EXISTS sys_isolation_config (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    enabled                TINYINT      NOT NULL DEFAULT 0 COMMENT '1=校验未过进入隔离区（不再直接拒绝）',
    mode                   VARCHAR(8)   NOT NULL DEFAULT 'ACL' COMMENT 'ACL=Filter-Id 下发 / VLAN=隔离 VLAN 下发',
    quarantine_acl         VARCHAR(64)           DEFAULT 'NAC-QUARANTINE' COMMENT '隔离区 ACL 名（Filter-Id）',
    remediation_acl        VARCHAR(64)           DEFAULT 'NAC-REMEDIATION' COMMENT '修复区 ACL 名（Filter-Id）',
    quarantine_vlan        INT                   DEFAULT 300 COMMENT '隔离区 VLAN',
    remediation_vlan       INT                   DEFAULT 301 COMMENT '修复区 VLAN',
    remediation_portal_url VARCHAR(512)           DEFAULT NULL COMMENT '终端进入修复区后打开的修复门户地址',
    remediation_server_ips VARCHAR(512)           DEFAULT NULL COMMENT '修复区放通的补丁/病毒库服务器 IP（逗号分隔）',
    auto_release           TINYINT      NOT NULL DEFAULT 1 COMMENT '1=合规后自动释放 0=人工释放',
    release_hold_minutes   INT          NOT NULL DEFAULT 30 COMMENT '自动释放前最短隔离时长(分钟，0=立即)',
    update_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='隔离区/修复区下发配置';

INSERT INTO sys_isolation_config (id, enabled, mode, quarantine_acl, remediation_acl, quarantine_vlan,
                                  remediation_vlan, remediation_portal_url, remediation_server_ips,
                                  auto_release, release_hold_minutes)
SELECT 1, 0, 'ACL', 'NAC-QUARANTINE', 'NAC-REMEDIATION', 300, 301, NULL, NULL, 1, 30
WHERE NOT EXISTS (SELECT 1 FROM sys_isolation_config WHERE id = 1);

-- 隔离记录：一个 MAC 同时最多一条 ACTIVE（应用层保证）
CREATE TABLE IF NOT EXISTS sys_isolation_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    mac             VARCHAR(32)  NOT NULL COMMENT '终端 MAC（归一化 12 位 hex）',
    ip              VARCHAR(64)           DEFAULT NULL,
    username        VARCHAR(64)           DEFAULT NULL,
    nas_ip          VARCHAR(64)           DEFAULT NULL,
    zone            VARCHAR(16)  NOT NULL DEFAULT 'QUARANTINE' COMMENT 'QUARANTINE=隔离区 / REMEDIATION=修复区',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE=隔离中 / RELEASED=已释放',
    reason          VARCHAR(255)          DEFAULT NULL COMMENT '进入隔离原因',
    missing_json    TEXT                  DEFAULT NULL COMMENT '缺失软件/补丁/病毒库 JSON',
    channel_type    VARCHAR(8)            DEFAULT NULL COMMENT '实际下发通道 ACL/VLAN',
    channel_value   VARCHAR(64)           DEFAULT NULL COMMENT 'ACL 名或 VLAN ID',
    enter_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_check_time DATETIME              DEFAULT NULL,
    release_time    DATETIME              DEFAULT NULL,
    release_cause   VARCHAR(255)          DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_mac (mac),
    KEY idx_status (status),
    KEY idx_enter_time (enter_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='终端隔离/修复区记录';

-- 补丁库
CREATE TABLE IF NOT EXISTS sys_patch_lib (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(128) NOT NULL COMMENT '补丁名称/KB 号',
    version      VARCHAR(64)           DEFAULT NULL,
    os_type      VARCHAR(16)  NOT NULL DEFAULT 'WINDOWS',
    severity     VARCHAR(16)           DEFAULT NULL COMMENT 'CRITICAL/HIGH/MEDIUM/LOW',
    sha256       VARCHAR(64)           DEFAULT NULL,
    file_url     VARCHAR(512)          DEFAULT NULL COMMENT '补丁下载地址',
    install_cmd  VARCHAR(512)          DEFAULT NULL,
    silent_args  VARCHAR(255)          DEFAULT NULL,
    description  VARCHAR(500)          DEFAULT NULL,
    enabled      TINYINT      NOT NULL DEFAULT 1,
    publish_time DATETIME              DEFAULT NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_enabled (enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='补丁库';

-- 病毒库
CREATE TABLE IF NOT EXISTS sys_av_lib (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(128) NOT NULL COMMENT '杀毒软件名称',
    vendor       VARCHAR(64)           DEFAULT NULL,
    version      VARCHAR(64)           DEFAULT NULL COMMENT '病毒库版本',
    sha256       VARCHAR(64)           DEFAULT NULL,
    file_url     VARCHAR(512)          DEFAULT NULL,
    install_cmd  VARCHAR(512)          DEFAULT NULL,
    silent_args  VARCHAR(255)          DEFAULT NULL,
    description  VARCHAR(500)          DEFAULT NULL,
    enabled      TINYINT      NOT NULL DEFAULT 1,
    publish_time DATETIME              DEFAULT NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_enabled (enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='病毒库';

-- 修复规则：缺失项 -> 修复动作
CREATE TABLE IF NOT EXISTS sys_remediation_rule (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(128) NOT NULL,
    trigger_type  VARCHAR(32)  NOT NULL COMMENT 'SOFTWARE_MISSING / AV_OUTDATED / PATCH_MISSING',
    trigger_value VARCHAR(255)          DEFAULT NULL COMMENT '触发值（软件名/病毒库名/补丁名，空=任意）',
    action_type   VARCHAR(32)  NOT NULL COMMENT 'INSTALL_PATCH / UPDATE_AV / OPEN_URL',
    target_id     BIGINT                DEFAULT NULL COMMENT 'sys_patch_lib 或 sys_av_lib 主键',
    action_url    VARCHAR(512)          DEFAULT NULL COMMENT 'OPEN_URL 动作地址',
    enabled       TINYINT      NOT NULL DEFAULT 1,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_enabled (enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='修复规则';

-- 修复任务：终端进入修复区后按规则生成
CREATE TABLE IF NOT EXISTS sys_remediation_task (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    record_id   BIGINT                DEFAULT NULL COMMENT 'sys_isolation_record.id',
    mac         VARCHAR(32)  NOT NULL,
    rule_id     BIGINT                DEFAULT NULL,
    lib_type    VARCHAR(16)  NOT NULL COMMENT 'PATCH / AV / URL',
    lib_id      BIGINT                DEFAULT NULL,
    title       VARCHAR(128)          DEFAULT NULL,
    action_type VARCHAR(32)           DEFAULT NULL,
    file_url    VARCHAR(512)          DEFAULT NULL,
    install_cmd VARCHAR(512)          DEFAULT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
    result_msg  VARCHAR(500)          DEFAULT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    finish_time DATETIME              DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_mac (mac),
    KEY idx_record (record_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='修复任务';
