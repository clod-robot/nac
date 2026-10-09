#!/bin/bash
# NAC 微服务运维脚本（JDK8）
# 用法: bash nac.sh {start-all|stop-all|status|start <svc>|stop <svc>}
export JAVA_HOME=$HOME/software/jdk1.8.0_202
export PATH=$JAVA_HOME/bin:$PATH

APP_DIR=$HOME/nac/app
LOG_DIR=$HOME/nac/logs
mkdir -p "$APP_DIR" "$LOG_DIR"

# 服务名:JAR:堆内存:额外参数
SERVICES=(
  "nac-gateway:nac-gateway-1.0-SNAPSHOT.jar:384m:"
  "nac-auth:nac-auth-1.0-SNAPSHOT.jar:1024m:--eapol.enabled=false"
  "nac-device:nac-device-1.0-SNAPSHOT.jar:384m:"
  "nac-user:nac-user-1.0-SNAPSHOT.jar:384m:"
  "nac-log:nac-log-1.0-SNAPSHOT.jar:384m:"
  "nac-config:nac-config-1.0-SNAPSHOT.jar:384m:"
  "nac-monitor:nac-monitor-1.0-SNAPSHOT.jar:384m:"
)

start_one() {
  local name="$1" jar="$2" heap="$3" extra="$4"
  if pgrep -f "app/$jar" >/dev/null; then
    echo "[SKIP] $name already running"
    return
  fi
  echo "[START] $name (heap=$heap)"
  nohup java -server -Xms"$heap" -Xmx"$heap" -XX:+UseG1GC \
    -jar "$APP_DIR/$jar" $extra \
    > "$LOG_DIR/$name.out" 2>&1 &
  echo $! > "$LOG_DIR/$name.pid"
}

stop_one() {
  local name="$1" jar="$2"
  local pids=$(pgrep -f "app/$jar")
  if [ -n "$pids" ]; then
    echo "[STOP] $name -> $pids"
    kill $pids 2>/dev/null
    sleep 3
    pkill -9 -f "app/$jar" 2>/dev/null
  else
    echo "[SKIP] $name not running"
  fi
}

case "$1" in
  start)
    for s in "${SERVICES[@]}"; do
      IFS=':' read -r name jar heap extra <<< "$s"
      [ "$name" = "$2" ] && start_one "$name" "$jar" "$heap" "$extra"
    done
    ;;
  stop)
    for s in "${SERVICES[@]}"; do
      IFS=':' read -r name jar heap extra <<< "$s"
      [ "$name" = "$2" ] && stop_one "$name" "$jar"
    done
    ;;
  start-all)
    # 网关先起，其余按顺序启动（注册中心已独立运行）
    for s in "${SERVICES[@]}"; do
      IFS=':' read -r name jar heap extra <<< "$s"
      start_one "$name" "$jar" "$heap" "$extra"
      sleep 2
    done
    ;;
  stop-all)
    for s in "${SERVICES[@]}"; do
      IFS=':' read -r name jar heap extra <<< "$s"
      stop_one "$name" "$jar"
    done
    ;;
  status)
    echo "==== Java processes ===="
    ps -eo pid,rss,%cpu,cmd | grep -E 'app/nac-|nacos-server' | grep -v grep | awk '{printf "%-8s RSS=%-8sMB CPU=%-5s %s\n",$1,int($2/1024),$3,$4" "$5}'
    echo "==== Listen ports ===="
    ss -tulnp 2>/dev/null | grep -E ':8848|:9000|:9001|:9002|:9003|:9004|:9005|:9006|:1812' | awk '{print $1,$5,$7}'
    ;;
  *)
    echo "Usage: bash nac.sh {start-all|stop-all|status|start <svc>|stop <svc>}"
    ;;
esac
