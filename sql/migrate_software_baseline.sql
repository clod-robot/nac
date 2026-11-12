-- ============================================================
-- 幂等迁移：终端软件基线识别（认证策略）
-- 内容：
--   1) 新建 sys_software_policy（软件基线策略：进程名 + 文件哈希）
--   2) sys_portal_config 新增开关/模式/共享密钥 3 个配置项
-- 适用：已存在的库（MySQL 5.7+ / 8.0）。可重复执行，不会报错。
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_software_policy (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name         VARCHAR(64)  NOT NULL COMMENT '软件显示名',
    process_name VARCHAR(128) NOT NULL COMMENT '进程名（不含路径，可带 .exe，匹配时忽略扩展名与大小写）',
    sha256       VARCHAR(64)           DEFAULT NULL COMMENT '可执行文件 SHA256（小写 hex，空=仅校验进程名）',
    match_mode   VARCHAR(16)  NOT NULL DEFAULT 'PROCESS' COMMENT 'PROCESS=仅进程名 / HASH=进程名+文件哈希',
    is_required  TINYINT      NOT NULL DEFAULT 1 COMMENT '1=必须满足 0=仅记录',
    enabled      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    remark       VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_enabled (enabled),
    KEY idx_process_name (process_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='终端软件基线策略';

-- 开关：默认关闭，避免升级后立即阻断既有终端
INSERT INTO sys_portal_config (config_key, config_value, remark)
SELECT 'softwareBaselineEnabled', 'false', '终端软件基线认证开关(true=必须运行指定软件才能认证)'
WHERE NOT EXISTS (SELECT 1 FROM sys_portal_config WHERE config_key = 'softwareBaselineEnabled');

-- 判定模式：ANY=满足任一必需项即可 / ALL=全部必需项都要满足
INSERT INTO sys_portal_config (config_key, config_value, remark)
SELECT 'softwareBaselineMode', 'ANY', '软件基线判定模式(ANY=任一满足/ALL=全部满足)'
WHERE NOT EXISTS (SELECT 1 FROM sys_portal_config WHERE config_key = 'softwareBaselineMode');

-- 挑战应答 / 心跳上报共享密钥（与客户端内置值一致；含 secret 关键字，不会经 /api/portal/config 下发）
INSERT INTO sys_portal_config (config_key, config_value, remark)
SELECT 'softwareBaselineSecret', 'SWA-K9x7Qp2Lm4Vt8Zs1Rb6Nw3Hd5Jc0FgYa', '软件基线共享密钥(HMAC-SHA256，需与客户端内置一致)'
WHERE NOT EXISTS (SELECT 1 FROM sys_portal_config WHERE config_key = 'softwareBaselineSecret');
