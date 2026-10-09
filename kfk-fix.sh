#!/bin/bash
set -e
echo "===== log.dirs config ====="
grep -E '^log.dirs|^log.dir|^zookeeper.connect' ~/software/kafka/config/server.properties

echo "===== stop stale kafka ====="
pkill -9 -f 'kafka.Kafka' 2>/dev/null || true
sleep 3

LOGDIR=$(grep -E '^log.dirs=' ~/software/kafka/config/server.properties | cut -d= -f2 | tr -d '\r')
LOGDIR=${LOGDIR:-/tmp/kafka-logs}
echo "using logdir: $LOGDIR"

echo "===== current meta.properties ====="
cat "$LOGDIR/meta.properties" 2>/dev/null || echo "no meta.properties"

echo "===== backup & clear ====="
if [ -d "$LOGDIR" ]; then
  mv "$LOGDIR" "${LOGDIR}.bak-$(date +%H%M%S)"
  echo "moved old logdir aside"
fi

echo "===== start kafka ====="
cd ~/software/kafka
nohup bin/kafka-server-start.sh config/server.properties > ~/nac/logs/kafka-start.out 2>&1 &
echo "kafka launching, pid $!"
