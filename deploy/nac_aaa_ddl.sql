-- =====================================================================
-- NAC AAA 全要素补齐 DDL（NAS 注册 / 计费会话 / 用户授权字段）
-- 库：nac_db    字符集：utf8mb4    幂等：可重复执行
-- 用法：mysql -uroot -p --default-character-set=utf8mb4 nac_db < nac_aaa_ddl.sql
-- =====================================================================

-- 1) 接入设备注册表（交换机/AP 自动发现 + 管理端维护）
CREATE TABLE IF NOT EXISTS `sys_nas_device` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `nas_ip`           VARCHAR(64)  NOT NULL COMMENT 'NAS UDP 源 IP',
  `nas_identifier`   VARCHAR(255) DEFAULT NULL COMMENT '上报的 NAS-Identifier（主机名）',
  `device_name`      VARCHAR(128) DEFAULT NULL COMMENT '管理端备注名称',
  `vendor`           VARCHAR(64)  DEFAULT NULL COMMENT '厂商',
  `shared_secret`    VARCHAR(128) NOT NULL DEFAULT 'testing123' COMMENT '该设备共享密钥',
  `enabled`          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  `auto_discovered`  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否自动发现',
  `auth_pkts`        BIGINT       NOT NULL DEFAULT 0 COMMENT '认证报文累计',
  `acct_pkts`        BIGINT       NOT NULL DEFAULT 0 COMMENT '计费报文累计',
  `bad_secret_pkts`  BIGINT       NOT NULL DEFAULT 0 COMMENT '签名校验失败次数（密钥错）',
  `reject_pkts`      BIGINT       NOT NULL DEFAULT 0 COMMENT '认证拒绝次数',
  `last_nas_port`    VARCHAR(128) DEFAULT NULL COMMENT '最近上报 NAS-Port-Id',
  `last_called_station` VARCHAR(128) DEFAULT NULL COMMENT '最近 Called-Station-Id（SSID/MAC）',
  `first_seen`       DATETIME     DEFAULT NULL,
  `last_seen`        DATETIME     DEFAULT NULL,
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_nas_ip` (`nas_ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RADIUS 接入设备（NAS）注册表';

-- 2) RADIUS 计费会话表（在线终端 + 上网记录权威数据，RFC2866）
CREATE TABLE IF NOT EXISTS `sys_radius_session` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `acct_session_id`    VARCHAR(64)  NOT NULL COMMENT 'Acct-Session-Id',
  `username`           VARCHAR(128) DEFAULT NULL,
  `nas_ip`             VARCHAR(64)  DEFAULT NULL,
  `nas_identifier`     VARCHAR(255) DEFAULT NULL,
  `calling_station_id` VARCHAR(64)  DEFAULT NULL COMMENT '终端 MAC',
  `called_station_id`  VARCHAR(128) DEFAULT NULL COMMENT '设备 MAC/SSID',
  `framed_ipaddress`   VARCHAR(64)  DEFAULT NULL COMMENT '终端 IP（DHCP 后由 Interim 上报）',
  `nas_port`           VARCHAR(128) DEFAULT NULL COMMENT '接入端口',
  `auth_type`          VARCHAR(32)  DEFAULT NULL COMMENT 'PEAP/EAP-MSCHAPv2/EAP-MD5/PAP/CHAP',
  `vlan_id`            VARCHAR(32)  DEFAULT NULL COMMENT '授权 VLAN',
  `status`             TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1在线 0离线',
  `login_time`         DATETIME     DEFAULT NULL,
  `last_update`        DATETIME     DEFAULT NULL,
  `logout_time`        DATETIME     DEFAULT NULL,
  `session_time`       INT          NOT NULL DEFAULT 0 COMMENT '在线秒数',
  `input_octets`       BIGINT       NOT NULL DEFAULT 0 COMMENT '上行字节（64位）',
  `output_octets`      BIGINT       NOT NULL DEFAULT 0 COMMENT '下行字节（64位）',
  `input_packets`      BIGINT       NOT NULL DEFAULT 0,
  `output_packets`     BIGINT       NOT NULL DEFAULT 0,
  `terminate_cause`    VARCHAR(64)  DEFAULT NULL COMMENT '下线原因（中文）',
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_acct_session_id` (`acct_session_id`),
  KEY `idx_username` (`username`),
  KEY `idx_status` (`status`),
  KEY `idx_nas_ip` (`nas_ip`),
  KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RADIUS 计费会话（在线终端/上网记录）';

-- 3) sys_radius_user 增加授权字段（幂等：列已存在则跳过）
DROP PROCEDURE IF EXISTS `nac_add_user_cols`;
DELIMITER //
CREATE PROCEDURE `nac_add_user_cols`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'sys_radius_user'
                   AND COLUMN_NAME = 'vlan_id') THEN
    ALTER TABLE `sys_radius_user` ADD COLUMN `vlan_id` VARCHAR(32) NULL COMMENT '授权 VLAN（空/0 不下发）' AFTER `enabled`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'sys_radius_user'
                   AND COLUMN_NAME = 'session_timeout') THEN
    ALTER TABLE `sys_radius_user` ADD COLUMN `session_timeout` INT NOT NULL DEFAULT 0 COMMENT '会话超时秒数，0=系统默认' AFTER `vlan_id`;
  END IF;
END //
DELIMITER ;
CALL `nac_add_user_cols`();
DROP PROCEDURE IF EXISTS `nac_add_user_cols`;

-- 4) 修正（历史笔误防护）：若误建为 TININT 则改正
-- （MySQL 8 无 TININT 类型，建表会直接失败；此注释仅作说明）

-- =====================================================================
-- 以下为「短信认证手机号加密留存 + 审查揭示」补齐（等保合规）
-- =====================================================================

-- 5) portal_config 增加「手机号审查模式」开关（幂等：列已存在则跳过）
DROP PROCEDURE IF EXISTS `nac_add_portal_col`;
DELIMITER //
CREATE PROCEDURE `nac_add_portal_col`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'portal_config'
                   AND COLUMN_NAME = 'phone_audit_enabled') THEN
    ALTER TABLE `portal_config` ADD COLUMN `phone_audit_enabled` TINYINT(1) NOT NULL DEFAULT 0
      COMMENT '手机号审查模式：1=开启（admin 可揭示完整手机号） 0=关闭' AFTER `sms_region`;
  END IF;
END //
DELIMITER ;
CALL `nac_add_portal_col`();
DROP PROCEDURE IF EXISTS `nac_add_portal_col`;

-- 6) 手机号加密留存表（短信认证访客手机号，仅存 AES-256-GCM 密文，明文不落库）
CREATE TABLE IF NOT EXISTS `sys_phone_record` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `masked_phone`       VARCHAR(16)  NOT NULL COMMENT '脱敏号（前3后4，如 138****1234）',
  `phone_cipher`       VARCHAR(256) NOT NULL COMMENT '手机号密文 Base64(12B IV + AES-256-GCM 密文+标签)',
  `session_id`         VARCHAR(64)  DEFAULT NULL COMMENT '关联 Portal 在线会话 ID',
  `calling_station_id` VARCHAR(64)  DEFAULT NULL COMMENT '终端 MAC',
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_masked` (`masked_phone`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信认证手机号加密留存（安全审查用）';

-- 7) 手机号揭示审计表（admin 开启审查模式后每次查看完整号均留痕，供安全部门审查）
CREATE TABLE IF NOT EXISTS `sys_phone_audit` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `operator`     VARCHAR(64) NOT NULL COMMENT '操作人（admin 用户名）',
  `masked_phone` VARCHAR(16) NOT NULL COMMENT '被查看的脱敏号',
  `session_id`   VARCHAR(64) DEFAULT NULL COMMENT '关联会话 ID',
  `client_ip`    VARCHAR(64) DEFAULT NULL COMMENT '操作来源 IP',
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator`),
  KEY `idx_masked` (`masked_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手机号揭示审计（谁/何时/从哪个IP看了哪个号）';

-- =====================================================================
-- 以下为「实名上网」补齐（等保/网安法：上网行为可追溯到真实自然人）
-- =====================================================================

-- 8) sys_radius_user 增加实名字段（幂等：列已存在则跳过）
DROP PROCEDURE IF EXISTS `nac_add_user_realname_cols`;
DELIMITER //
CREATE PROCEDURE `nac_add_user_realname_cols`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_radius_user' AND COLUMN_NAME = 'real_name') THEN
    ALTER TABLE `sys_radius_user` ADD COLUMN `real_name` VARCHAR(64) DEFAULT NULL COMMENT '真实姓名' AFTER `session_timeout`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_radius_user' AND COLUMN_NAME = 'phone') THEN
    ALTER TABLE `sys_radius_user` ADD COLUMN `phone` VARCHAR(32) DEFAULT NULL COMMENT '手机号（脱敏展示）' AFTER `real_name`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_radius_user' AND COLUMN_NAME = 'department') THEN
    ALTER TABLE `sys_radius_user` ADD COLUMN `department` VARCHAR(128) DEFAULT NULL COMMENT '部门' AFTER `phone`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_radius_user' AND COLUMN_NAME = 'employee_id') THEN
    ALTER TABLE `sys_radius_user` ADD COLUMN `employee_id` VARCHAR(64) DEFAULT NULL COMMENT '工号' AFTER `department`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_radius_user' AND COLUMN_NAME = 'is_verified') THEN
    ALTER TABLE `sys_radius_user` ADD COLUMN `is_verified` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已实名 1是 0否' AFTER `employee_id`;
  END IF;
END //
DELIMITER ;
CALL `nac_add_user_realname_cols`();
DROP PROCEDURE IF EXISTS `nac_add_user_realname_cols`;

-- 9) sys_radius_session 增加 real_name 快照列（用户改名后历史记录仍可追溯到人）
DROP PROCEDURE IF EXISTS `nac_add_session_realname_col`;
DELIMITER //
CREATE PROCEDURE `nac_add_session_realname_col`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_radius_session' AND COLUMN_NAME = 'real_name') THEN
    ALTER TABLE `sys_radius_session` ADD COLUMN `real_name` VARCHAR(64) DEFAULT NULL COMMENT '真实姓名快照' AFTER `username`;
  END IF;
END //
DELIMITER ;
CALL `nac_add_session_realname_col`();
DROP PROCEDURE IF EXISTS `nac_add_session_realname_col`;

-- =====================================================================
-- 以下为「Portal 认证后 MAC 旁路放通（跨厂商通用 MAB）」补齐
-- =====================================================================

-- 10) portal_config 增加 MAC 旁路放通配置列（幂等）
DROP PROCEDURE IF EXISTS `nac_add_portal_mab_cols`;
DELIMITER //
CREATE PROCEDURE `nac_add_portal_mab_cols`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'portal_config' AND COLUMN_NAME = 'mab_enabled') THEN
    ALTER TABLE `portal_config` ADD COLUMN `mab_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Portal认证后MAC旁路放通开关 1开 0关' AFTER `phone_audit_enabled`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'portal_config' AND COLUMN_NAME = 'guest_vlan_id') THEN
    ALTER TABLE `portal_config` ADD COLUMN `guest_vlan_id` VARCHAR(16) DEFAULT NULL COMMENT '访客授权VLAN（空=不下发）' AFTER `mab_enabled`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'portal_config' AND COLUMN_NAME = 'grant_timeout') THEN
    ALTER TABLE `portal_config` ADD COLUMN `grant_timeout` INT NOT NULL DEFAULT 28800 COMMENT 'MAC授权有效期秒，默认8小时' AFTER `guest_vlan_id`;
  END IF;
END //
DELIMITER ;
CALL `nac_add_portal_mab_cols`();
DROP PROCEDURE IF EXISTS `nac_add_portal_mab_cols`;

-- 11) Portal MAC 旁路授权表（Portal 认证成功登记，交换机 MAB 命中即放通）
CREATE TABLE IF NOT EXISTS `sys_portal_grant` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `mac`          VARCHAR(32)  NOT NULL COMMENT '终端MAC（归一化大写冒号，唯一）',
  `session_id`   VARCHAR(64)  DEFAULT NULL COMMENT '关联Portal在线会话ID',
  `masked_phone` VARCHAR(16)  DEFAULT NULL COMMENT '短信访客脱敏号（账号密码认证为用户名）',
  `vlan_id`      VARCHAR(16)  DEFAULT NULL COMMENT '授权访客VLAN',
  `nas_ip`       VARCHAR(64)  DEFAULT NULL COMMENT '最近MAB请求来源NAS IP（强制下线DM目标）',
  `grant_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  `expire_time`  DATETIME     NOT NULL COMMENT '授权过期时间（MAB重认证到期被拒）',
  `status`       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1=有效 0=已撤销',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mac` (`mac`),
  KEY `idx_status_expire` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Portal认证后MAC旁路放通授权（跨厂商MAB）';
