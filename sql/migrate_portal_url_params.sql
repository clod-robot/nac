-- ============================================================
-- 幂等迁移：Portal URL 参数采集
-- 内容：
--   1) sys_auth_log    新增 ssid / nas_name 两列
--   2) sys_online_session 新增 ssid / nas_name 两列
--   3) sys_portal_config 新增 5 个 URL 参数名映射配置项
-- 适用：已存在的库（MySQL 5.7+ / 8.0）。可重复执行，不会报错。
-- ============================================================

-- ---------- 1) sys_auth_log ----------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_auth_log' AND COLUMN_NAME = 'ssid');
SET @s := IF(@c = 0,
  'ALTER TABLE sys_auth_log ADD COLUMN ssid VARCHAR(32) DEFAULT NULL COMMENT ''无线SSID(Portal URL参数)''',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_auth_log' AND COLUMN_NAME = 'nas_name');
SET @s := IF(@c = 0,
  'ALTER TABLE sys_auth_log ADD COLUMN nas_name VARCHAR(64) DEFAULT NULL COMMENT ''NAS/AC设备名(Portal URL参数)''',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 2) sys_online_session ----------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_online_session' AND COLUMN_NAME = 'ssid');
SET @s := IF(@c = 0,
  'ALTER TABLE sys_online_session ADD COLUMN ssid VARCHAR(32) DEFAULT NULL COMMENT ''无线SSID(Portal URL参数)''',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_online_session' AND COLUMN_NAME = 'nas_name');
SET @s := IF(@c = 0,
  'ALTER TABLE sys_online_session ADD COLUMN nas_name VARCHAR(64) DEFAULT NULL COMMENT ''NAS/AC设备名(Portal URL参数)''',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------- 3) sys_portal_config 配置项 ----------
INSERT INTO sys_portal_config (config_key, config_value, remark)
SELECT 'urlParamUserIp', 'wlanuserip,userip,uaddress', '终端IP参数名(如华为wlanuserip/H3C source-address)'
WHERE NOT EXISTS (SELECT 1 FROM sys_portal_config WHERE config_key = 'urlParamUserIp');
INSERT INTO sys_portal_config (config_key, config_value, remark)
SELECT 'urlParamUserMac', 'wlanusermac,mac,usermac,umac', '终端MAC参数名'
WHERE NOT EXISTS (SELECT 1 FROM sys_portal_config WHERE config_key = 'urlParamUserMac');
INSERT INTO sys_portal_config (config_key, config_value, remark)
SELECT 'urlParamNasIp', 'wlanacip,nasip,acip,gw_address', 'NAS/AC设备IP参数名'
WHERE NOT EXISTS (SELECT 1 FROM sys_portal_config WHERE config_key = 'urlParamNasIp');
INSERT INTO sys_portal_config (config_key, config_value, remark)
SELECT 'urlParamSsid', 'ssid,wlanacssid', '无线SSID参数名'
WHERE NOT EXISTS (SELECT 1 FROM sys_portal_config WHERE config_key = 'urlParamSsid');
INSERT INTO sys_portal_config (config_key, config_value, remark)
SELECT 'urlParamNasName', 'wlanacname,acname,nasname,gw_id', 'NAS/AC设备名称参数名'
WHERE NOT EXISTS (SELECT 1 FROM sys_portal_config WHERE config_key = 'urlParamNasName');
