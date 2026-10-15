#!/bin/bash
# 启动 Nacos standalone（JDK8），若已运行则跳过
if pgrep -f nacos-server.jar >/dev/null; then
  echo "Nacos already running"
  exit 0
fi
export JAVA_HOME=$HOME/software/jdk1.8.0_202
cd ~/software/nacos/bin
bash startup.sh -m standalone
echo "Nacos launch command returned, waiting for port 8848..."
for i in $(seq 1 40); do
  sleep 2
  if curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8848/nacos/ 2>/dev/null | grep -qE '200|302'; then
    echo "Nacos is UP after $((i*2))s"
    exit 0
  fi
done
echo "Nacos failed to come up in 80s"
tail -30 ~/software/nacos/logs/start.out 2>/dev/null
exit 1
