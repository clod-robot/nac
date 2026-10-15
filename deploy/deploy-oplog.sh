#!/bin/bash
# 操作日志功能部署：建表 + 切换 jar + 重启
set -e
APP_DIR=~/nac/app
BAK=~/nac/app/backup-0917-oplog
mkdir -p "$BAK"

# 1) 建表（IF NOT EXISTS 幂等）
mysql -uroot -p'${SUDO_PW}' nac_db 2>/dev/null <<'SQL'
CREATE TABLE IF NOT EXISTS sys_operation_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username    VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
  module      VARCHAR(64)  DEFAULT NULL COMMENT '功能模块',
  action      VARCHAR(128) DEFAULT NULL COMMENT '操作动作',
  biz_type    VARCHAR(32)  DEFAULT NULL COMMENT '业务类型',
  method      VARCHAR(10)  DEFAULT NULL COMMENT 'HTTP 方法',
  url         VARCHAR(256) DEFAULT NULL COMMENT '请求路径',
  params      TEXT         DEFAULT NULL COMMENT '请求参数 JSON（脱敏）',
  ip          VARCHAR(64)  DEFAULT NULL COMMENT '来源 IP',
  result      VARCHAR(16)  DEFAULT NULL COMMENT '执行结果 SUCCESS/FAIL',
  error_msg   VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
  cost_ms     BIGINT       DEFAULT 0    COMMENT '耗时毫秒',
  event_time  VARCHAR(32)  DEFAULT NULL COMMENT '操作时间(文本)',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '落库时间',
  PRIMARY KEY (id),
  KEY idx_op_username (username),
  KEY idx_op_module (module),
  KEY idx_op_biz_type (biz_type),
  KEY idx_op_result (result),
  KEY idx_op_event_time (event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志表（只写不删）';
SQL
echo "[1] sys_operation_log table:"
mysql -uroot -p'${SUDO_PW}' nac_db -e "SHOW CREATE TABLE sys_operation_log\G" 2>/dev/null | head -3

# Kafka 新 topic 由生产者自动创建（missing-topics-fatal=false），无需手工建

# 2) 备份并切换 jar
for s in nac-gateway nac-auth nac-user nac-device nac-config nac-log nac-monitor; do
  jar="$s-1.0-SNAPSHOT.jar"
  if [ -f "$APP_DIR/$jar" ]; then
    cp "$APP_DIR/$jar" "$BAK/$jar"
  fi
  cp "/tmp/jars-new/$jar" "$APP_DIR/$jar"
done
echo "[2] jars swapped, backup at $BAK"

# 3) 重启全部服务
for s in nac-gateway nac-auth nac-user nac-device nac-config nac-log nac-monitor; do
  bash ~/nac/nac.sh stop "$s" 2>&1 | tail -1
done
sleep 3
for s in nac-gateway nac-auth nac-user nac-device nac-config nac-log nac-monitor; do
  bash ~/nac/nac.sh start "$s" 2>&1 | tail -1
done
echo "[3] restart issued"
