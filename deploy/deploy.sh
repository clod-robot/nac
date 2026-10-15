#!/bin/bash
# deploy.sh — NAC 全量部署脚本（在目标服务器上执行）
# 前置条件：以 root 或有 sudo 权限的用户运行；已将 nac-dist.tar.gz 解压到当前目录
set -e

# ================= 可配置参数（部署前按目标环境修改）=================
RUN_USER=${RUN_USER:-nac}          # 运行服务的系统用户
INSTALL_DIR=${INSTALL_DIR:-/home/$RUN_USER/nac}
MW_DIR=${MW_DIR:-/home/$RUN_USER/software}
MYSQL_ROOT_PASS=${MYSQL_ROOT_PASS:-NacRoot@2026}
NAC_DB_PASS=${NAC_DB_PASS:-${MYSQL_PASSWORD}}
REDIS_PASS=${REDIS_PASS:-NacRedis@2026}
INTERNAL_SECRET=${INTERNAL_SECRET:-NacInternal@2026-Gw2Svc}
KEYSTORE_PASS=${KEYSTORE_PASS:-NceRadius@2026}
CA_CERT_PASS=${CA_CERT_PASS:-NceRadius@2026}   # 客户端 p12 导出密码
# =================================================================

DIST_DIR="$(cd "$(dirname "$0")" && pwd)"
OS=$(cat /etc/os-release 2>/dev/null | grep ^ID= | cut -d= -f2 | tr -d '"')
echo "=== NAC 全量部署 ==="
echo "OS=$OS RUN_USER=$RUN_USER INSTALL_DIR=$INSTALL_DIR"

# ---- 1. 系统依赖 ----
echo "=== [1/9] 安装系统依赖 ==="
if command -v apt-get >/dev/null; then
  apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    nginx redis-server mysql-server openssl iproute2 curl tar gzip
  systemctl enable redis-server mysql nginx
elif command -v yum >/dev/null; then
  yum install -y -q epel-release 2>/dev/null || true
  yum install -y -q nginx redis mysql-server openssl iproute curl tar gzip
  systemctl enable redis mysqld nginx
else
  echo "不支持的操作系统: $OS"; exit 1
fi

# 创建运行用户
id "$RUN_USER" >/dev/null 2>&1 || useradd -m -s /bin/bash "$RUN_USER"
mkdir -p "$INSTALL_DIR"/{app,certs,logs} "$MW_DIR"
chown -R "$RUN_USER:$RUN_USER" "$INSTALL_DIR" "$MW_DIR" 2>/dev/null || true

# ---- 2. 部署中间件 ----
echo "=== [2/9] 部署中间件（JDK/Nacos/Kafka/ZK）==="
cp -r "$DIST_DIR/middleware/jdk"        "$MW_DIR/jdk1.8.0_202"
cp -r "$DIST_DIR/middleware/nacos"      "$MW_DIR/nacos"
cp -r "$DIST_DIR/middleware/kafka"      "$MW_DIR/kafka"
cp -r "$DIST_DIR/middleware/zookeeper"  "$MW_DIR/zookeeper"
chown -R "$RUN_USER:$RUN_USER" "$MW_DIR"
# JDK 环境变量
grep -q 'JAVA_HOME' /home/$RUN_USER/.bashrc 2>/dev/null || \
  echo 'export JAVA_HOME=$HOME/software/jdk1.8.0_202; export PATH=$JAVA_HOME/bin:$PATH' \
  >> /home/$RUN_USER/.bashrc

# ---- 3. MySQL 初始化 ----
echo "=== [3/9] 初始化 MySQL ==="
systemctl start mysql 2>/dev/null || systemctl start mysqld
sleep 3
# 建库 + 用户 + 授权（SQL 写临时文件，兼容 apt 无密码 / yum 临时密码两种情况）
cat > /tmp/nac_init.sql <<SQL
ALTER USER 'root'@'localhost' IDENTIFIED BY '$MYSQL_ROOT_PASS';
CREATE DATABASE IF NOT EXISTS nac_db DEFAULT CHARSET utf8mb4;
CREATE USER IF NOT EXISTS 'nac'@'%' IDENTIFIED BY '$NAC_DB_PASS';
CREATE USER IF NOT EXISTS 'nac'@'localhost' IDENTIFIED BY '$NAC_DB_PASS';
GRANT ALL ON nac_db.* TO 'nac'@'%';
GRANT ALL ON nac_db.* TO 'nac'@'localhost';
FLUSH PRIVILEGES;
SQL
# 依次尝试：无密码 -> 密码；新环境通常无密码即可连
if mysql -uroot < /tmp/nac_init.sql 2>/dev/null; then
  echo "  MySQL root 无密码初始化成功"
elif mysql -uroot -p"$MYSQL_ROOT_PASS" < /tmp/nac_init.sql 2>/dev/null; then
  echo "  MySQL root 密码初始化成功"
else
  echo "  WARN: MySQL 初始化失败，请手动执行 /tmp/nac_init.sql"
fi
mysql -uroot -p"$MYSQL_ROOT_PASS" nac_db < "$DIST_DIR/sql/nac_db.sql" 2>/dev/null && \
  echo "  数据导入完成" || echo "  WARN: 数据导入失败（库可能已存在）"
rm -f /tmp/nac_init.sql

# ---- 4. Redis 配置密码 ----
echo "=== [4/9] 配置 Redis ==="
systemctl start redis-server
redis-cli CONFIG SET requirepass "$REDIS_PASS" 2>/dev/null || \
  sed -i "s/^# requirepass .*/requirepass $REDIS_PASS/" /etc/redis/redis.conf 2>/dev/null
echo "requirepass $REDIS_PASS" >> /etc/redis/redis.conf 2>/dev/null
systemctl restart redis-server 2>/dev/null || true

# ---- 5. 部署应用与证书 ----
echo "=== [5/9] 部署应用 jar 与证书 ==="
cp "$DIST_DIR/app/"*.jar  "$INSTALL_DIR/app/"
cp "$DIST_DIR/certs/"*    "$INSTALL_DIR/certs/"
chown -R "$RUN_USER:$RUN_USER" "$INSTALL_DIR"
chmod 600 "$INSTALL_DIR/certs/nac-ca.key"

# ---- 6. 部署运维脚本 ----
echo "=== [6/9] 部署运维脚本 ==="
cp "$DIST_DIR/scripts/nac.sh"                "$INSTALL_DIR/"
cp "$DIST_DIR/scripts/ensure-server-cert.sh" "$INSTALL_DIR/"
cp "$DIST_DIR/scripts/boot-after-reboot.sh"  "$INSTALL_DIR/"
cp "$DIST_DIR/scripts/nginx-nce.conf"        /etc/nginx/conf.d/nce.conf 2>/dev/null || \
  cp "$DIST_DIR/scripts/nginx-nce.conf"       /etc/nginx/sites-enabled/nce 2>/dev/null
chmod +x "$INSTALL_DIR/"*.sh
chown -R "$RUN_USER:$RUN_USER" "$INSTALL_DIR"

# 修正脚本中的用户路径（源服务器是 mao，目标是 $RUN_USER）
sed -i "s|/home/mao/nac|$INSTALL_DIR|g" "$INSTALL_DIR/nac.sh"
sed -i "s|/home/mao/nac|$INSTALL_DIR|g" "$INSTALL_DIR/ensure-server-cert.sh"
sed -i "s|/home/mao/software|$MW_DIR|g"  "$INSTALL_DIR/nac.sh"

# ---- 7. 部署前端 ----
echo "=== [7/9] 部署前端 ==="
rm -rf /usr/share/nginx/html/nce
cp -r "$DIST_DIR/frontend/nce" /usr/share/nginx/html/nce
nginx -t && systemctl restart nginx

# ---- 8. 启动中间件 + 首次证书生成 ----
echo "=== [8/9] 启动中间件 ==="
export JAVA_HOME="$MW_DIR/jdk1.8.0_202"; export PATH=$JAVA_HOME/bin:$PATH
# Zookeeper
su - "$RUN_USER" -c "cd $MW_DIR/zookeeper && nohup bin/zkServer.sh start >/dev/null 2>&1" || true
sleep 3
# Kafka
su - "$RUN_USER" -c "cd $MW_DIR/kafka && nohup bin/kafka-server-start.sh config/server.properties > $INSTALL_DIR/logs/kafka-start.out 2>&1 &" || true
sleep 5
# Nacos
su - "$RUN_USER" -c "cd $MW_DIR/nacos && nohup bin/startup.sh -m standalone > $INSTALL_DIR/logs/nacos.out 2>&1" || true
sleep 10
# 首次生成服务器证书（按目标机器 IP）
su - "$RUN_USER" -c "bash $INSTALL_DIR/ensure-server-cert.sh" || true

# ---- 9. 启动微服务 ----
echo "=== [9/9] 启动微服务 ==="
# 写入密码到 nac.sh 启动参数（通过环境变量方式更安全，这里写入脚本）
sed -i "s|--radius.eap-tls.truststore-password=changeit|--radius.eap-tls.truststore-password=changeit|" "$INSTALL_DIR/nac.sh"
su - "$RUN_USER" -c "bash $INSTALL_DIR/nac.sh start-all"
sleep 15

# ---- 验证 ----
echo "=== 部署验证 ==="
bash "$INSTALL_DIR/nac.sh" status
echo "=== 健康检查 ==="
curl -s -o /dev/null -w "  网关 /api/cert/ca -> HTTP %{http_code}\n" http://127.0.0.1:9000/api/cert/ca || true
curl -s -o /dev/null -w "  Nginx 前端 -> HTTP %{http_code}\n"        http://127.0.0.1/        || true
echo ""
echo "============================================"
echo "  部署完成！"
echo "  管理地址: http://$(hostname -I | awk '{print $1}')/"
echo "  运行用户: $RUN_USER"
echo "  启停命令: bash $INSTALL_DIR/nac.sh {start-all|stop-all|status}"
echo "  客户端证书签发: 管理员登录后 POST /api/cert/gen-client?cn=用户名"
echo "  客户端 CA 下载:  GET  /api/cert/ca"
echo "============================================"
