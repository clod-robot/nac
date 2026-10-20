#!/bin/bash
# NAC 业务服务开机自启（幂等：端口已监听则跳过）。等中间件就绪后按序拉起。
LOG=/home/mao/nac-autostart.log
exec >> $LOG 2>&1
echo "[$(date)] start-nac begin"

# --- 环境变量：用 export 显式导出，避免 env $VAR 被 word splitting 吞掉带下划线的值 ---
# 口令已脱敏：部署时请先注入真实环境变量，或将占位符替换为真实值
export REDIS_PASSWORD="${REDIS_PASSWORD:-<REDIS_PASSWORD>}"
export MYSQL_USER="${MYSQL_USER:-nac}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-<MYSQL_PASSWORD>}"
export KAFKA_SERVERS="127.0.0.1:9092"
export NACOS_SERVER="127.0.0.1:8848"
export JAVA_HOME="/usr/local/jdk"

# 等待中间件就绪（mysql/redis/kafka/nacos）
for i in $(seq 1 60); do
  ok=1
  for p in 3306 6379 9092 8848; do ss -ltn 2>/dev/null | grep -q ":$p " || ok=0; done
  [ $ok -eq 1 ] && break
  sleep 2
done

start_svc() {
  local name=$1 port=$2
  if ss -ltn 2>/dev/null | grep -q ":$port "; then echo "skip $name($port) already up"; return; fi
  cd /home/mao
  nohup /usr/local/jdk/bin/java -jar "nac-$name.jar" --server.port=$port > /home/mao/$name.log 2>&1 &
  echo "started $name($port) pid=$!"
}

# 业务服务先注册到 Nacos，网关最后切流量
start_svc auth 8081
start_svc user 8082
start_svc radius 8083
start_svc log 8084
sleep 20
start_svc gateway 8080

# 关键校验：RADIUS 必须真正监听 1812(认证)/1813(计费)，否则 802.1X 链路是断的
wait_udp() { local port=$1 tries=$2; for i in $(seq 1 $tries); do ss -lun 2>/dev/null | grep -q ":$port " && return 0; sleep 2; done; return 1; }
if wait_udp 1812 15 && wait_udp 1813 5; then
  echo "RADIUS OK: udp/1812 + udp/1813 listening"
else
  echo "RADIUS WARN: 1812/1813 未监听！802.1X 认证链路不可用，请查 /home/mao/radius.log"
fi
echo "[$(date)] start-nac done"
