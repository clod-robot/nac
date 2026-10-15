#!/bin/bash
set -e
TS=$(date +%Y%m%d_%H%M%S)
cp /home/mao/nac/app/nac-auth-1.0-SNAPSHOT.jar /home/mao/nac/app/nac-auth.bak-$TS.jar
cp /tmp/nac-auth-new.jar /home/mao/nac/app/nac-auth-1.0-SNAPSHOT.jar
bash /home/mao/nac/nac.sh stop nac-auth || true
sleep 2
bash /home/mao/nac/nac.sh start nac-auth
sleep 12
PID=$(pgrep -f nac-auth-1.0-SNAPSHOT.jar | head -1)
echo "PID=$PID"
grep -a 'PEAP TLS' /home/mao/nac/logs/nac-auth.out | tail -2
tail -n 5 /home/mao/nac/logs/nac-auth.out
