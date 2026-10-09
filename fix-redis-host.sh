#!/bin/bash
cp ~/nac/nac.sh ~/nac/nac.sh.bak-$(date +%H%M%S)
sed -i 's|nac-auth-1.0-SNAPSHOT.jar:1024m:--eapol.enabled=false|nac-auth-1.0-SNAPSHOT.jar:1024m:--eapol.enabled=false --spring.redis.host=127.0.0.1|' ~/nac/nac.sh
grep -n "nac-auth:" ~/nac/nac.sh
bash ~/nac/nac.sh stop nac-auth
sleep 2
bash ~/nac/nac.sh start nac-auth
echo "waiting for startup..."
for i in $(seq 1 40); do
  sleep 2
  if ss -tln | grep -q ':9001 ' && ss -uln | grep -q ':1812'; then
    echo "PORTS_READY after $((i*2))s"
    break
  fi
done
sleep 8
cd ~/nac/bench && taskset -c 3 python3 radius_bench.py --server 127.0.0.1 smoke 2>&1 | tail -3
grep -n "Started.*Application\|Tomcat started\|RADIUS" ~/nac/logs/nac-auth.out | tail -3
